package com.example.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight 127.0.0.1 loopback HTTP/1.1 streaming proxy for Android's MediaPlayer (Stagefright).
 *
 * Streams audio bytes progressively via [OkHttpClient] (preserving custom headers, modern TLS,
 * redirects, and Stagefright `Range` seek requests) so high-bitrate 320kbps / lossless tracks
 * start playing immediately without waiting for a full-file download.
 */
internal class LocalAudioStreamProxy(
    private val httpClient: OkHttpClient
) {
    data class StreamTarget(
        val upstreamUrl: String,
        val headers: Map<String, String>
    )

    private val proxySecret = UUID.randomUUID().toString().replace("-", "")
    private val registeredStreams = ConcurrentHashMap<String, StreamTarget>()
    private val isClosed = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "NchordLoopbackProxy").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    private fun ensureBoundPort(): Int {
        val existing = serverSocket
        if (existing != null && !existing.isClosed) {
            return existing.localPort
        }
        synchronized(this) {
            val recheck = serverSocket
            if (recheck != null && !recheck.isClosed) {
                return recheck.localPort
            }
            if (isClosed.get()) return -1
            val bound = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            serverSocket = bound
            executor.execute { acceptLoop(bound) }
            return bound.localPort
        }
    }

    /**
     * Registers [upstreamUrl] + [headers] under [streamKey] and returns a `http://127.0.0.1:<port>/...`
     * URL authenticated with a per-process secret token. Keeps only recent entries bounded.
     */
    fun registerStream(
        streamKey: String,
        upstreamUrl: String,
        headers: Map<String, String>
    ): String? {
        if (isClosed.get()) return null
        val port = try {
            ensureBoundPort()
        } catch (e: Exception) {
            Log.e("LocalAudioStreamProxy", "Failed binding loopback socket", e)
            return null
        }
        if (port <= 0) return null

        if (registeredStreams.size > 8) {
            val iterator = registeredStreams.keys.iterator()
            if (iterator.hasNext()) {
                registeredStreams.remove(iterator.next())
            }
        }
        val cleanKey = streamKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        registeredStreams[cleanKey] = StreamTarget(upstreamUrl = upstreamUrl, headers = headers)
        return "http://127.0.0.1:$port/stream/$cleanKey?token=$proxySecret"
    }

    private fun acceptLoop(bound: ServerSocket) {
        while (!isClosed.get() && !bound.isClosed) {
            val client = try {
                bound.accept()
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                Log.w("LocalAudioStreamProxy", "Accept error", e)
                continue
            }
            executor.execute { handleClientSocket(client) }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 15_000
                client.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                if (method != "GET" && method != "HEAD") {
                    writeSimpleError(client, 405, "Method Not Allowed")
                    return
                }

                val pathAndQuery = parts[1]
                val parsed = parseStreamRequestPath(pathAndQuery)
                if (parsed == null || parsed.second != proxySecret) {
                    writeSimpleError(client, 403, "Forbidden")
                    return
                }

                var rangeHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(':').trim()
                    }
                }

                val target = registeredStreams[parsed.first]
                if (target == null) {
                    writeSimpleError(client, 404, "Not Found")
                    return
                }

                val upstreamReqBuilder = Request.Builder()
                    .url(target.upstreamUrl)
                    .header("Accept-Encoding", "identity")

                val defaultUa = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                if (target.headers.isEmpty()) {
                    upstreamReqBuilder.header("User-Agent", defaultUa)
                } else {
                    target.headers.forEach { (k, v) -> upstreamReqBuilder.header(k, v) }
                }
                if (!rangeHeader.isNullOrBlank()) {
                    upstreamReqBuilder.header("Range", rangeHeader)
                }
                if (method == "HEAD") {
                    upstreamReqBuilder.head()
                } else {
                    upstreamReqBuilder.get()
                }

                httpClient.newCall(upstreamReqBuilder.build()).execute().use { upstreamResp ->
                    val statusCode = upstreamResp.code
                    val statusMessage = upstreamResp.message.ifBlank {
                        if (statusCode == 206) "Partial Content" else "OK"
                    }
                    val out = BufferedOutputStream(client.getOutputStream(), 32 * 1024)
                    val headerSb = StringBuilder(256)
                    headerSb.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusMessage).append("\r\n")

                    val contentType = upstreamResp.header("Content-Type") ?: "audio/mp4"
                    headerSb.append("Content-Type: ").append(contentType).append("\r\n")
                    upstreamResp.header("Content-Range")?.let {
                        headerSb.append("Content-Range: ").append(it).append("\r\n")
                    }
                    val acceptRanges = upstreamResp.header("Accept-Ranges") ?: "bytes"
                    headerSb.append("Accept-Ranges: ").append(acceptRanges).append("\r\n")
                    val contentLength = upstreamResp.header("Content-Length")
                        ?: upstreamResp.body?.contentLength()?.takeIf { it >= 0L }?.toString()
                    if (contentLength != null) {
                        headerSb.append("Content-Length: ").append(contentLength).append("\r\n")
                    }
                    headerSb.append("Connection: close\r\n\r\n")
                    out.write(headerSb.toString().toByteArray(Charsets.US_ASCII))
                    out.flush()

                    if (method != "HEAD" && upstreamResp.isSuccessful) {
                        upstreamResp.body?.byteStream()?.use { bodyStream ->
                            val buffer = ByteArray(32 * 1024)
                            while (!isClosed.get()) {
                                val read = bodyStream.read(buffer)
                                if (read < 0) break
                                if (read > 0) {
                                    out.write(buffer, 0, read)
                                }
                            }
                            out.flush()
                        }
                    }
                }
            } catch (_: SocketException) {
                // Normal when MediaPlayer closes Range connection early during seek or track skip
            } catch (e: Exception) {
                Log.d("LocalAudioStreamProxy", "Loopback stream closed: ${e.message}")
            }
        }
    }

    private fun writeSimpleError(socket: Socket, code: Int, message: String) {
        val resp = "HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(resp.toByteArray(Charsets.US_ASCII))
            flush()
        }
    }

    fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        registeredStreams.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        executor.shutdownNow()
    }

    companion object {
        /**
         * Extracts `(streamKey, token)` from `/stream/<streamKey>?token=<secret>`.
         */
        internal fun parseStreamRequestPath(pathAndQuery: String): Pair<String, String>? {
            val pathPart = pathAndQuery.substringBefore('?')
            if (!pathPart.startsWith("/stream/")) return null
            val key = pathPart.removePrefix("/stream/").trim()
            if (key.isEmpty() || key.contains('/')) return null
            val queryPart = pathAndQuery.substringAfter('?', "")
            if (queryPart.isEmpty()) return null
            var token: String? = null
            for (pair in queryPart.split('&')) {
                val eq = pair.indexOf('=')
                if (eq > 0 && pair.substring(0, eq) == "token") {
                    token = pair.substring(eq + 1)
                    break
                }
            }
            val validToken = token?.takeIf { it.isNotBlank() } ?: return null
            return key to validToken
        }
    }
}
