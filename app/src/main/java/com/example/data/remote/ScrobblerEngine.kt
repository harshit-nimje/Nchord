package com.example.data.remote

import com.example.BuildConfig
import com.example.data.local.ScrobbleEntity
import com.example.data.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Real two-way scrobbler & stats engine supporting:
 * 1. Last.fm (API v2.0 mobile session scrobbling + stats when LASTFM_API_KEY/SHARED_SECRET are set
 *    in AI Studio Secrets, plus automatic public Last.fm feed fallback when developer keys aren't set).
 * 2. ListenBrainz (https://api.listenbrainz.org/1/) — zero-developer-key popular open scrobbler
 *    supporting live `playing_now`, historical/offline batch scrobbles, listen count, and top artists.
 */
object ScrobblerEngine {

    private const val LASTFM_API_ROOT = "https://ws.audioscrobbler.com/2.0/"
    private const val LISTENBRAINZ_API_ROOT = "https://api.listenbrainz.org/1"

    data class RemoteScrobblerSnapshot(
        val totalPlayCount: Int = 0,
        val topArtists: List<Pair<String, Int>> = emptyList(),
        val recentTracks: List<String> = emptyList(),
        val statusSummary: String = ""
    )

    fun hasConfiguredLastFmApiKey(): Boolean {
        val key = runCatching { BuildConfig.LASTFM_API_KEY }.getOrDefault("")
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    fun hasConfiguredLastFmSecret(): Boolean {
        val secret = runCatching { BuildConfig.LASTFM_SHARED_SECRET }.getOrDefault("")
        return secret.isNotBlank() && !secret.startsWith("YOUR_")
    }

    /**
     * Computes Last.fm API v2.0 MD5 `api_sig`:
     * Sort all parameters (excluding `format` and `callback`) alphabetically by UTF-8 key,
     * concatenate `<key><value>`, append `sharedSecret`, and return lowercase hex MD5.
     */
    fun computeLastFmSignature(params: Map<String, String>, sharedSecret: String): String {
        val filtered = params
            .filterKeys { it != "format" && it != "callback" }
            .toSortedMap()
        val payload = buildString {
            filtered.forEach { (k, v) ->
                append(k)
                append(v)
            }
            append(sharedSecret)
        }
        val digest = MessageDigest.getInstance("MD5").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Authenticates Last.fm mobile session (`auth.getMobileSession`) if developer keys + password are provided.
     * If password is empty or developer keys aren't set yet, validates the Last.fm profile for stats/recent sync.
     */
    suspend fun connectLastFm(
        username: String,
        passwordOptional: String
    ): Result<Pair<String, RemoteScrobblerSnapshot>> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid Last.fm username"))
        }

        var sessionKey = ""
        if (passwordOptional.isNotBlank() && hasConfiguredLastFmApiKey() && hasConfiguredLastFmSecret()) {
            val apiKey = BuildConfig.LASTFM_API_KEY.trim()
            val secret = BuildConfig.LASTFM_SHARED_SECRET.trim()
            val params = mapOf(
                "api_key" to apiKey,
                "method" to "auth.getMobileSession",
                "password" to passwordOptional,
                "username" to cleanUser
            )
            val sig = computeLastFmSignature(params, secret)
            val form = FormBody.Builder()
                .add("method", "auth.getMobileSession")
                .add("username", cleanUser)
                .add("password", passwordOptional)
                .add("api_key", apiKey)
                .add("api_sig", sig)
                .add("format", "json")
                .build()

            val req = Request.Builder()
                .url(LASTFM_API_ROOT)
                .post(form)
                .build()

            runCatching {
                NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    sessionKey = json.optJSONObject("session")?.optString("key").orEmpty()
                }
            }
        }

        val snapshot = fetchLastFmSnapshot(cleanUser)
        val summary = when {
            sessionKey.isNotBlank() -> "Last.fm 2-way scrobbling + stats active"
            hasConfiguredLastFmApiKey() -> "Last.fm profile connected (add password for write scrobbling)"
            else -> "Last.fm public feed synced (set LASTFM_API_KEY in Secrets for live write scrobbling)"
        }
        Result.success(sessionKey to snapshot.copy(statusSummary = summary))
    }

    suspend fun fetchLastFmSnapshot(username: String): RemoteScrobblerSnapshot = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank()) return@withContext RemoteScrobblerSnapshot()

        if (hasConfiguredLastFmApiKey()) {
            val apiKey = BuildConfig.LASTFM_API_KEY.trim()
            val encodedUser = URLEncoder.encode(cleanUser, "UTF-8")
            var playCount = 0
            val topArtists = mutableListOf<Pair<String, Int>>()
            val recent = mutableListOf<String>()

            runCatching {
                val infoUrl = "$LASTFM_API_ROOT?method=user.getinfo&user=$encodedUser&api_key=$apiKey&format=json"
                NetworkModule.fastHttpClient.newCall(Request.Builder().url(infoUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val userObj = JSONObject(resp.body?.string().orEmpty()).optJSONObject("user")
                        playCount = userObj?.optString("playcount")?.toIntOrNull() ?: 0
                    }
                }
            }

            runCatching {
                val topUrl = "$LASTFM_API_ROOT?method=user.gettopartists&user=$encodedUser&limit=6&period=1month&api_key=$apiKey&format=json"
                NetworkModule.fastHttpClient.newCall(Request.Builder().url(topUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONObject(resp.body?.string().orEmpty())
                            .optJSONObject("topartists")
                            ?.optJSONArray("artist") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val name = item.optString("name")
                            val plays = item.optString("playcount").toIntOrNull() ?: 0
                            if (name.isNotBlank()) topArtists += (name to plays)
                        }
                    }
                }
            }

            runCatching {
                val recUrl = "$LASTFM_API_ROOT?method=user.getrecenttracks&user=$encodedUser&limit=8&api_key=$apiKey&format=json"
                NetworkModule.fastHttpClient.newCall(Request.Builder().url(recUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONObject(resp.body?.string().orEmpty())
                            .optJSONObject("recenttracks")
                            ?.optJSONArray("track") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val title = item.optString("name")
                            val artist = item.optJSONObject("artist")?.optString("#text").orEmpty()
                            if (title.isNotBlank()) {
                                recent += if (artist.isNotBlank()) "$artist — $title" else title
                            }
                        }
                    }
                }
            }

            if (playCount > 0 || topArtists.isNotEmpty() || recent.isNotEmpty()) {
                return@withContext RemoteScrobblerSnapshot(
                    totalPlayCount = playCount,
                    topArtists = topArtists,
                    recentTracks = recent,
                    statusSummary = "Synced with Last.fm"
                )
            }
        }

        // Key-free public Last.fm profile feed fallback so stats/recent sync never fail even before setting Secrets
        return@withContext fetchPublicLastFmFeedFallback(cleanUser)
    }

    private fun fetchPublicLastFmFeedFallback(username: String): RemoteScrobblerSnapshot {
        val encoded = URLEncoder.encode(username, "UTF-8")
        val recent = mutableListOf<String>()
        val artistCounts = linkedMapOf<String, Int>()
        var totalScrobbles = 0

        runCatching {
            val rssUrl = "https://ws.audioscrobbler.com/1.0/user/$encoded/recenttracks.rss"
            val req = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "Nchord/3.0 Android")
                .build()
            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    val itemTitles = Regex("<item>[\\s\\S]*?<title><!\\[CDATA\\[(.*?)]]></title>|<item>[\\s\\S]*?<title>(.*?)</title>")
                        .findAll(xml)
                        .map { (it.groupValues[1].ifBlank { it.groupValues[2] }).trim() }
                        .filter { it.isNotBlank() }
                        .toList()
                    itemTitles.take(10).forEach { entry ->
                        val decoded = entry
                            .replace("&amp;", "&")
                            .replace("&#39;", "'")
                            .replace("&quot;", "\"")
                        recent += decoded
                        val artist = decoded.substringBefore(" – ").substringBefore(" - ").trim()
                        if (artist.isNotBlank() && artist != decoded) {
                            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                        }
                    }
                }
            }
        }

        runCatching {
            val profileUrl = "https://www.last.fm/user/$encoded"
            val req = Request.Builder()
                .url(profileUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/131.0.0.0 Mobile")
                .build()
            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    val m = Regex("Scrobbles[\\s\\S]{0,260}?([0-9][0-9,.]*)").find(html)
                    val parsed = m?.groupValues?.getOrNull(1)?.replace(",", "")?.replace(".", "")?.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        totalScrobbles = parsed
                    }
                }
            }
        }

        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        return RemoteScrobblerSnapshot(
            totalPlayCount = totalScrobbles.coerceAtLeast(recent.size),
            topArtists = topArtists,
            recentTracks = recent,
            statusSummary = if (recent.isNotEmpty() || totalScrobbles > 0) {
                "Last.fm profile synced"
            } else {
                "Last.fm username saved"
            }
        )
    }

    suspend fun connectAndFetchListenBrainz(
        username: String,
        tokenOptional: String
    ): Result<RemoteScrobblerSnapshot> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a ListenBrainz username"))
        }

        if (tokenOptional.isNotBlank()) {
            runCatching {
                val validateReq = Request.Builder()
                    .url("$LISTENBRAINZ_API_ROOT/validate-token")
                    .header("Authorization", "Token ${tokenOptional.trim()}")
                    .build()
                NetworkModule.fastHttpClient.newCall(validateReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        if (!obj.optBoolean("valid", true)) {
                            return@withContext Result.failure(IllegalArgumentException("Invalid ListenBrainz user token"))
                        }
                    }
                }
            }
        }

        val snap = fetchListenBrainzSnapshot(cleanUser)
        Result.success(
            snap.copy(
                statusSummary = if (tokenOptional.isNotBlank()) {
                    "ListenBrainz 2-way scrobbling active"
                } else {
                    "ListenBrainz public telemetry connected"
                }
            )
        )
    }

    suspend fun fetchListenBrainzSnapshot(username: String): RemoteScrobblerSnapshot = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(username.trim(), "UTF-8")
        var listenCount = 0
        val topArtists = mutableListOf<Pair<String, Int>>()
        val recentTracks = mutableListOf<String>()

        runCatching {
            val countUrl = "$LISTENBRAINZ_API_ROOT/user/$encoded/listen-count"
            NetworkModule.fastHttpClient.newCall(Request.Builder().url(countUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    listenCount = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("payload")
                        ?.optInt("count", 0) ?: 0
                }
            }
        }

        runCatching {
            val artistsUrl = "$LISTENBRAINZ_API_ROOT/stats/user/$encoded/artists?count=6&range=month"
            NetworkModule.fastHttpClient.newCall(Request.Builder().url(artistsUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("payload")
                        ?.optJSONArray("artists") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val name = a.optString("artist_name")
                        val cnt = a.optInt("listen_count", 0)
                        if (name.isNotBlank()) topArtists += (name to cnt)
                    }
                }
            }
        }

        runCatching {
            val recUrl = "$LISTENBRAINZ_API_ROOT/user/$encoded/listens?count=8"
            NetworkModule.fastHttpClient.newCall(Request.Builder().url(recUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("payload")
                        ?.optJSONArray("listens") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val meta = arr.optJSONObject(i)?.optJSONObject("track_metadata") ?: continue
                        val t = meta.optString("track_name")
                        val a = meta.optString("artist_name")
                        if (t.isNotBlank()) recentTracks += "$a — $t"
                    }
                }
            }
        }

        RemoteScrobblerSnapshot(
            totalPlayCount = listenCount,
            topArtists = topArtists,
            recentTracks = recentTracks,
            statusSummary = "Synced with ListenBrainz"
        )
    }

    suspend fun submitNowPlaying(
        track: TrackItem,
        provider: String,
        lastFmSessionKey: String,
        listenBrainzToken: String
    ) = withContext(Dispatchers.IO) {
        if (provider == "LISTENBRAINZ" && listenBrainzToken.isNotBlank()) {
            runCatching {
                val payload = JSONObject().apply {
                    put("listen_type", "playing_now")
                    put(
                        "payload",
                        JSONArray().put(
                            JSONObject().put(
                                "track_metadata",
                                JSONObject().apply {
                                    put("artist_name", track.artist)
                                    put("track_name", track.title)
                                    if (track.album.isNotBlank()) put("release_name", track.album)
                                }
                            )
                        )
                    )
                }
                val req = Request.Builder()
                    .url("$LISTENBRAINZ_API_ROOT/submit-listens")
                    .header("Authorization", "Token $listenBrainzToken")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                NetworkModule.fastHttpClient.newCall(req).execute().close()
            }
        } else if (provider == "LASTFM" && lastFmSessionKey.isNotBlank() &&
            hasConfiguredLastFmApiKey() && hasConfiguredLastFmSecret()
        ) {
            runCatching {
                val apiKey = BuildConfig.LASTFM_API_KEY.trim()
                val secret = BuildConfig.LASTFM_SHARED_SECRET.trim()
                val params = mutableMapOf(
                    "api_key" to apiKey,
                    "artist" to track.artist,
                    "method" to "track.updateNowPlaying",
                    "sk" to lastFmSessionKey,
                    "track" to track.title
                )
                if (track.album.isNotBlank()) params["album"] = track.album
                val sig = computeLastFmSignature(params, secret)
                val formBuilder = FormBody.Builder()
                params.forEach { (k, v) -> formBuilder.add(k, v) }
                formBuilder.add("api_sig", sig)
                formBuilder.add("format", "json")
                val req = Request.Builder().url(LASTFM_API_ROOT).post(formBuilder.build()).build()
                NetworkModule.fastHttpClient.newCall(req).execute().close()
            }
        }
    }

    /**
     * Flushes queued local/offline scrobbles (including offline downloaded plays) to Last.fm or ListenBrainz.
     * Returns IDs of scrobbles confirmed delivered.
     */
    suspend fun flushQueuedScrobbles(
        unsynced: List<ScrobbleEntity>,
        provider: String,
        lastFmSessionKey: String,
        listenBrainzToken: String
    ): List<Long> = withContext(Dispatchers.IO) {
        if (unsynced.isEmpty()) return@withContext emptyList()

        if (provider == "LISTENBRAINZ" && listenBrainzToken.isNotBlank()) {
            val batch = unsynced.take(25)
            val ok = runCatching {
                val arr = JSONArray()
                batch.forEach { s ->
                    arr.put(
                        JSONObject().apply {
                            put("listened_at", (s.playedAt / 1000L).coerceAtLeast(1L))
                            put(
                                "track_metadata",
                                JSONObject().apply {
                                    put("artist_name", s.artist)
                                    put("track_name", s.title)
                                    if (s.album.isNotBlank()) put("release_name", s.album)
                                }
                            )
                        }
                    )
                }
                val bodyJson = JSONObject().apply {
                    put("listen_type", if (batch.size == 1) "single" else "import")
                    put("payload", arr)
                }
                val req = Request.Builder()
                    .url("$LISTENBRAINZ_API_ROOT/submit-listens")
                    .header("Authorization", "Token $listenBrainzToken")
                    .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                NetworkModule.okHttpClient.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            return@withContext if (ok) batch.map { it.scrobbleId } else emptyList()
        }

        if (provider == "LASTFM" && lastFmSessionKey.isNotBlank() &&
            hasConfiguredLastFmApiKey() && hasConfiguredLastFmSecret()
        ) {
            val apiKey = BuildConfig.LASTFM_API_KEY.trim()
            val secret = BuildConfig.LASTFM_SHARED_SECRET.trim()
            val syncedIds = mutableListOf<Long>()
            unsynced.take(20).forEach { s ->
                val ok = runCatching {
                    val params = mutableMapOf(
                        "api_key" to apiKey,
                        "artist" to s.artist,
                        "method" to "track.scrobble",
                        "sk" to lastFmSessionKey,
                        "timestamp" to ((s.playedAt / 1000L).coerceAtLeast(1L)).toString(),
                        "track" to s.title
                    )
                    if (s.album.isNotBlank()) params["album"] = s.album
                    val sig = computeLastFmSignature(params, secret)
                    val formBuilder = FormBody.Builder()
                    params.forEach { (k, v) -> formBuilder.add(k, v) }
                    formBuilder.add("api_sig", sig)
                    formBuilder.add("format", "json")
                    val req = Request.Builder().url(LASTFM_API_ROOT).post(formBuilder.build()).build()
                    NetworkModule.okHttpClient.newCall(req).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (ok) syncedIds += s.scrobbleId
            }
            return@withContext syncedIds
        }

        emptyList()
    }
}
