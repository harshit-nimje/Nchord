package com.example.data.local

import android.content.Context
import com.example.data.model.TrackItem
import com.example.data.remote.NetworkModule
import com.example.data.remote.ResolvedStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Strictly in-app offline track vault.
 * Downloads audio files into private app-internal storage (`context.filesDir/offline_tracks`)
 * with atomic temporary-file writes. Never writes to external storage or system DownloadManager.
 */
class InAppDownloadManager(private val context: Context) {

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_FOLDER_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private val _downloadProgressFlow = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressFlow: StateFlow<Map<String, Float>> = _downloadProgressFlow.asStateFlow()

    fun sanitizeTrackIdForVault(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(84)
        return cleaned.ifBlank { "track_${rawId.hashCode().toUInt()}" }
    }

    fun inferExtension(resolved: ResolvedStreamResult): String {
        val container = resolved.containerLabel.uppercase()
        val codec = resolved.codecLabel.uppercase()
        val url = resolved.url.orEmpty().substringBefore("?").lowercase()
        return when {
            codec.contains("FLAC") || container.contains("FLAC") || url.endsWith(".flac") -> "flac"
            container.contains("WEBM") || codec.contains("OPUS") || url.endsWith(".webm") -> "webm"
            url.endsWith(".mp3") || codec.contains("MP3") -> "mp3"
            else -> "m4a"
        }
    }

    fun buildInternalVaultTarget(baseDir: File, trackId: String, extension: String): File {
        val safeName = "${sanitizeTrackIdForVault(trackId)}.${extension.trimStart('.')}"
        val target = File(baseDir, safeName)
        require(target.canonicalPath.startsWith(baseDir.canonicalPath)) {
            "Offline file path must remain confined inside internal vault directory"
        }
        return target
    }

    fun verifyLocalFile(localPath: String?): File? {
        if (localPath.isNullOrBlank()) return null
        return runCatching {
            val file = File(localPath)
            if (file.exists() && file.isFile && file.length() > 8_192L &&
                file.canonicalPath.startsWith(context.filesDir.canonicalPath)
            ) {
                file
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun downloadToInternalVault(
        track: TrackItem,
        resolved: ResolvedStreamResult
    ): Result<DownloadedTrackEntity> = withContext(Dispatchers.IO) {
        val streamUrl = resolved.url
        if (streamUrl.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No audio stream URL available to download"))
        }

        _downloadProgressFlow.update { it + (track.id to 0.03f) }

        val ext = inferExtension(resolved)
        val finalFile = buildInternalVaultTarget(vaultDir, track.id, ext)
        val tempFile = File(vaultDir, "${finalFile.name}.part")

        try {
            val reqBuilder = Request.Builder().url(streamUrl)
            val headers = resolved.requestHeaders.ifEmpty {
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                )
            }
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            NetworkModule.okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty audio stream body")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastEmitted = 0.03f
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val pct = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.98f)
                                if (pct - lastEmitted >= 0.04f) {
                                    lastEmitted = pct
                                    _downloadProgressFlow.update { it + (track.id to pct) }
                                }
                            } else if (downloaded % (256 * 1024L) == 0L) {
                                val pseudo = (0.15f + (downloaded / (6f * 1024f * 1024f))).coerceAtMost(0.92f)
                                _downloadProgressFlow.update { it + (track.id to pseudo) }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 8_192L) {
                tempFile.delete()
                throw IllegalStateException("Downloaded audio stream was incomplete")
            }

            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            val entity = DownloadedTrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                localFilePath = finalFile.absolutePath,
                fileSizeBytes = finalFile.length(),
                codecLabel = resolved.codecLabel,
                bitrateKbps = resolved.bitrateKbps,
                durationMs = resolved.durationMs.takeIf { it > 0L } ?: track.durationMs,
                sourceTypeName = track.sourceType.name,
                youtubeVideoId = track.youtubeVideoId,
                externalSpotifyUri = track.externalSpotifyUri,
                spotifyTrackId = track.spotifyTrackId,
                albumId = track.albumId,
                artistId = track.artistId,
                downloadedAt = System.currentTimeMillis()
            )
            Result.success(entity)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            Result.failure(e)
        } finally {
            _downloadProgressFlow.update { it - track.id }
        }
    }

    suspend fun deleteFromInternalVault(localPath: String?) = withContext(Dispatchers.IO) {
        if (localPath.isNullOrBlank()) return@withContext
        runCatching {
            val file = File(localPath)
            if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            }
        }
    }

    companion object {
        const val VAULT_FOLDER_NAME = "offline_tracks"
    }
}
