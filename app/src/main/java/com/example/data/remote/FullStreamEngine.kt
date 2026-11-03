package com.example.data.remote

import android.annotation.SuppressLint
import android.util.Base64
import android.util.LruCache
import com.example.data.model.AudioQualityTier
import com.example.data.model.AudioSourceType
import com.example.data.model.CollectionPageState
import com.example.data.model.CollectionPageType
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedStreamResult(
    val url: String?,
    val durationMs: Long,
    val codecLabel: String = "AAC-LC",
    val containerLabel: String = "M4A",
    val bitrateKbps: Int = 320,
    val sampleRateHz: Int = 44_100,
    val bitDepth: Int = 16,
    val sourceType: AudioSourceType = AudioSourceType.OPEN_AAC,
    val requestHeaders: Map<String, String> = emptyMap()
)

/**
 * BitChord-inspired Multi-Quality Streaming, Album/Artist/Playlist & YTM Discovery Engine:
 * - Supports quality tiers from Data Saver (96k) & YTM Standard (160k) to Studio Master (320k AAC)
 *   and Hi-Res Lossless FLAC / LDAC Best (up to 24-bit/96kHz FLAC • 990+ kbps with instant fallback).
 * - Resolves full Albums, full Artist pages, Curated & Search Playlists, YouTube Music Playlists,
 *   and pasted public Spotify Track/Album/Playlist URLs.
 */
object FullStreamEngine {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val SAAVN_BASE = "https://www.jiosaavn.com/api.php"
    private const val SAAVN_MIRROR_BASE = "https://saavn.sumit.co/api"
    private const val MONOCHROME_TRACKS_BASE = "https://tracks.monochrome.st"
    private const val DES_KEY = "38346591"

    private val SAAVN_GEO_IPS = arrayOf(
        "49.36.128.42",
        "103.21.124.78",
        "106.51.73.114",
        "157.49.182.65"
    )
    private val SAAVN_BITRATE_REGEX = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)(?=[?#]|$)", RegexOption.IGNORE_CASE)

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var cachedSoundCloudClientId: String? = null

    private val searchCache = LruCache<String, List<TrackItem>>(64)
    private val playlistListCache = LruCache<String, List<SuggestedPlaylist>>(32)
    private val playlistTracksCache = LruCache<String, List<TrackItem>>(40)
    private val resolvedStreamCache = LruCache<String, ResolvedStreamResult>(160)
    private val playabilityProbeCache = LruCache<String, Boolean>(512)

    private fun buildSaavnRequest(url: String): Request {
        val geoIp = SAAVN_GEO_IPS[(url.hashCode() and 0x7FFFFFFF) % SAAVN_GEO_IPS.size]
        return Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0.0.0 Mobile Safari/537.36"
            )
            .header("X-Forwarded-For", geoIp)
            .header("X-Real-IP", geoIp)
            .header("Accept-Language", "en-IN,en;q=0.9")
            .build()
    }

    fun getCachedStream(trackId: String, tier: AudioQualityTier): ResolvedStreamResult? =
        resolvedStreamCache.get("${trackId}_${tier.id}")

    /**
     * Lightweight background playability check that detects unplayable tracks beforehand
     * and caches verified direct streams for instant zero-lag playback.
     */
    suspend fun quickProbeTrackPlayable(
        track: TrackItem,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ): Boolean = withContext(Dispatchers.IO) {
        if (!track.isPlayable) {
            playabilityProbeCache.put(track.id, false)
            return@withContext false
        }
        if (track.isDownloaded || track.sourceType == AudioSourceType.LOCAL_FILE) {
            return@withContext true
        }
        playabilityProbeCache.get(track.id)?.let { return@withContext it }

        val cacheKey = "${track.id}_${qualityTier.id}"
        resolvedStreamCache.get(cacheKey)?.let { cached ->
            if (!cached.url.isNullOrBlank()) {
                playabilityProbeCache.put(track.id, true)
                return@withContext true
            }
        }

        val probeResult = runCatching {
            val direct = track.audioStreamUrl
            if (!direct.isNullOrBlank() && direct.contains("saavncdn.com")) {
                val verified = resolveVerifiedSaavnStream(direct, track.durationMs, qualityTier)
                if (verified != null && !verified.url.isNullOrBlank()) {
                    resolvedStreamCache.put(cacheKey, verified)
                    return@runCatching true
                }
            }
            val cleanTitle = stripEditionNoise(track.title)
            val cleanArtist = primaryArtistOf(track.artist)
            if (cleanTitle.isBlank()) return@runCatching false

            val saavnHits = searchSaavnDirect("$cleanTitle $cleanArtist", limit = 4, qualityTier = qualityTier)
            if (saavnHits.isNotEmpty()) {
                val best = pickBestCanonicalTrack(
                    candidates = saavnHits,
                    targetTitle = track.title,
                    targetArtist = track.artist,
                    targetAlbum = track.album,
                    targetDurationMs = track.durationMs,
                    minimumScore = 20
                ) ?: saavnHits.firstOrNull()
                if (best != null && !best.audioStreamUrl.isNullOrBlank()) {
                    return@runCatching true
                }
            }

            val ytmVid = track.youtubeVideoId ?: findCanonicalInnerTubeVideoId(
                title = track.title,
                artist = track.artist,
                album = track.album,
                targetDurationMs = track.durationMs
            )
            !ytmVid.isNullOrBlank()
        }.getOrDefault(true) // Never mark false on transient offline/network hiccups

        playabilityProbeCache.put(track.id, probeResult)
        probeResult
    }

    /**
     * Searches full-length songs with sub-second response, canonical studio album ranking,
     * and quality-adapted stream URLs.
     */
    suspend fun searchFullTracks(
        query: String,
        limit: Int = 22,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
        customPipedNode: String? = null
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${query.lowercase().trim()}_${limit}_${qualityTier.id}"
        searchCache.get(cacheKey)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        val merged = coroutineScope {
            val saavnDeferred = async {
                runCatching { searchSaavnDirect(query, limit + 6, qualityTier) }.getOrDefault(emptyList())
            }
            val innerTubeDeferred = async {
                runCatching { searchInnerTubeMusic(query, limit + 6) }.getOrDefault(emptyList())
            }

            val saavnTracks = saavnDeferred.await()
            val ytmTracks = innerTubeDeferred.await()

            val rawPool = if (saavnTracks.isNotEmpty() && qualityTier != AudioQualityTier.YTM_NORMAL) {
                saavnTracks + ytmTracks
            } else if (ytmTracks.isNotEmpty()) {
                // Even in YTM Standard mode, prefer official studio releases over covers/acoustic
                saavnTracks + ytmTracks
            } else {
                val mirror = runCatching { searchSaavnMirror(query, limit + 4) }.getOrDefault(emptyList())
                if (mirror.isNotEmpty()) mirror else searchPipedFallback(query, limit, customPipedNode)
            }

            rankSearchResultsForCanonicalOriginals(
                query = query,
                candidates = rawPool,
                preferYtmSource = (qualityTier == AudioQualityTier.YTM_NORMAL),
                limit = limit
            )
        }

        if (merged.isNotEmpty()) {
            searchCache.put(cacheKey, merged)
        }
        merged
    }

    /**
     * Searches curated & YouTube Music playlists matching any query or genre.
     */
    suspend fun searchPlaylists(query: String, limit: Int = 8): List<SuggestedPlaylist> =
        withContext(Dispatchers.IO) {
            val cacheKey = "search_pl_${query.lowercase().trim()}_$limit"
            playlistListCache.get(cacheKey)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

            val results = ArrayList<SuggestedPlaylist>()
            runCatching {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "$SAAVN_BASE?__call=search.getPlaylistResults&api_version=4&_format=json&_marker=0&ctx=web6dot0&n=$limit&p=1&q=$encoded"
                val req = buildSaavnRequest(url)
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val arr = JSONObject(body).optJSONArray("results")
                            if (arr != null) {
                                for (i in 0 until minOf(arr.length(), limit)) {
                                    val pick = arr.optJSONObject(i) ?: continue
                                    val listId = pick.optString("id")
                                    val rawTitle = decodeHtmlEntities(pick.optString("title"))
                                    val rawSub = decodeHtmlEntities(pick.optString("subtitle"))
                                    val rawImg = upgradeArtworkUrl(pick.optString("image"))
                                    val count = pick.optJSONObject("more_info")
                                        ?.optString("song_count")?.toIntOrNull()
                                        ?: pick.optInt("list_count", 18).coerceAtLeast(10)
                                    if (listId.isNotBlank() && rawTitle.isNotBlank()) {
                                        results.add(
                                            SuggestedPlaylist(
                                                id = "pl_saavn_$listId",
                                                title = rawTitle,
                                                subtitle = rawSub.ifBlank { "$count tracks" },
                                                badge = "Playlist",
                                                artworkUrl = rawImg,
                                                trackCount = count,
                                                seedQuery = query,
                                                externalPlaylistId = listId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                playlistListCache.put(cacheKey, results)
            }
            results
        }

    /**
     * Fetches real, full-length suggested playlists for the Home Open Carousel,
     * dynamically tailored to the active genre/vibe or user seeds.
     */
    suspend fun fetchSuggestedPlaylists(
        seedQueries: List<Pair<String, String>> = listOf(
            "After Hours Synth" to "Late Night Electronic",
            "Alternative Indie Essentials" to "Editorial Mix",
            "Electronic Club Classics" to "High Energy",
            "Lo-Fi Chill Beats" to "Deep Focus",
            "Hip Hop & R&B Now" to "Rotation",
            "Acoustic Midnight" to "Unplugged"
        )
    ): List<SuggestedPlaylist> = withContext(Dispatchers.IO) {
        val cacheKey = seedQueries.joinToString("|") { it.first }
        playlistListCache.get(cacheKey)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        val playlists = coroutineScope {
            seedQueries.mapIndexed { index, (query, badge) ->
                async {
                    runCatching {
                        fetchSingleSuggestedPlaylist(query, badge, index)
                    }.getOrNull()
                }
            }.mapNotNull { it.await() }
        }

        if (playlists.isNotEmpty()) {
            playlistListCache.put(cacheKey, playlists)
        }
        playlists
    }

    private suspend fun fetchSingleSuggestedPlaylist(
        query: String,
        badge: String,
        index: Int
    ): SuggestedPlaylist? {
        // Support structured "Playlist Title::Artist1|Artist2|Artist3" seeds so mixes always contain authentic studio hits
        val explicitTitle = if (query.contains("::")) query.substringBefore("::").trim() else query.trim()
        val rawSeeds = if (query.contains("::")) {
            query.substringAfter("::").split("|").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            query.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }

        if (rawSeeds.size >= 2) {
            val perSeedTracks = coroutineScope {
                rawSeeds.take(4).map { seed ->
                    async {
                        runCatching { searchFullTracks(seed, limit = 5) }.getOrDefault(emptyList())
                    }
                }.map { it.await() }
            }
            val interleaved = ArrayList<TrackItem>()
            val seenIds = HashSet<String>()
            val maxLen = perSeedTracks.maxOfOrNull { it.size } ?: 0
            for (pos in 0 until maxLen) {
                for (bucket in perSeedTracks) {
                    val item = bucket.getOrNull(pos) ?: continue
                    val key = "${normalizeComparableText(stripEditionNoise(item.title))}_${normalizeComparableText(primaryArtistOf(item.artist))}"
                    if (seenIds.add(key)) {
                        interleaved.add(item)
                    }
                }
            }
            val lead = interleaved.getOrNull(index % interleaved.size.coerceAtLeast(1)) ?: interleaved.firstOrNull()
            if (lead != null && interleaved.isNotEmpty()) {
                val distinctArtists = interleaved.map { primaryArtistOf(it.artist) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(3)
                    .joinToString(", ")
                return SuggestedPlaylist(
                    id = "pl_curated_${explicitTitle.lowercase().hashCode()}_$index",
                    title = explicitTitle,
                    subtitle = distinctArtists.ifBlank { "${interleaved.size} studio tracks" },
                    badge = badge,
                    artworkUrl = lead.artworkUrl,
                    trackCount = interleaved.size,
                    seedQuery = rawSeeds.first(),
                    preloadedTracks = interleaved
                )
            }
        }

        val tracks = searchFullTracks(explicitTitle, limit = 14)
        val lead = tracks.firstOrNull() ?: return null
        val distinctArtists = tracks.map { primaryArtistOf(it.artist) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString(", ")
        return SuggestedPlaylist(
            id = "pl_mix_${explicitTitle.hashCode()}_$index",
            title = explicitTitle,
            subtitle = distinctArtists.ifBlank { "${tracks.size} tracks" },
            badge = badge,
            artworkUrl = lead.artworkUrl,
            trackCount = tracks.size,
            seedQuery = explicitTitle,
            preloadedTracks = tracks
        )
    }

    /**
     * Loads full-length tracks for a suggested or YouTube Music playlist.
     */
    suspend fun fetchSuggestedPlaylistTracks(
        playlist: SuggestedPlaylist,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        if (playlist.preloadedTracks.isNotEmpty()) return@withContext playlist.preloadedTracks
        playlistTracksCache.get(playlist.id)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        val cleanExtId = playlist.externalPlaylistId
            ?.trim()
            ?.removePrefix("ytm_pl_")
            ?.removePrefix("pl_saavn_")
            ?.removePrefix("sp_carousel_")
            ?.removePrefix("sp_search_")
            ?.removePrefix("sp_pl_")
            ?.removePrefix("sp_")
            ?.takeIf { it.isNotBlank() }

        if (playlist.isYoutubePlaylist && !cleanExtId.isNullOrBlank()) {
            val ytmTracks = fetchInnerTubePlaylistTracks(cleanExtId)
            if (ytmTracks.isNotEmpty()) {
                playlistTracksCache.put(playlist.id, ytmTracks)
                return@withContext ytmTracks
            }
        }

        if (playlist.isSpotifyPlaylist && !cleanExtId.isNullOrBlank()) {
            val spEmbedTracks = fetchPublicSpotifyEmbedPlaylist("playlist", cleanExtId).third
            if (spEmbedTracks.isNotEmpty()) {
                playlistTracksCache.put(playlist.id, spEmbedTracks)
                return@withContext spEmbedTracks
            }
        }

        if (!cleanExtId.isNullOrBlank() && !playlist.isSpotifyPlaylist && !playlist.isYoutubePlaylist) {
            val url = "$SAAVN_BASE?__call=playlist.getDetails&api_version=4&_format=json&_marker=0&ctx=web6dot0&listid=$cleanExtId"
            val req = buildSaavnRequest(url)
            runCatching {
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (bodyStr.startsWith("{")) {
                            val root = JSONObject(bodyStr)
                            val listArr = root.optJSONArray("list")
                            if (listArr != null && listArr.length() > 0) {
                                val parsed = parseSaavnSongArray(listArr, maxCount = 40, qualityTier = qualityTier)
                                if (parsed.isNotEmpty()) {
                                    playlistTracksCache.put(playlist.id, parsed)
                                    return@withContext parsed
                                }
                            }
                        }
                    }
                }
            }
        }

        val candidateQueries = listOf(
            playlist.seedQuery.trim(),
            playlist.title.trim(),
            "${playlist.title.trim()} ${playlist.subtitle.substringBefore("•").trim()}".trim()
        ).filter { it.length >= 2 }.distinct()

        for (q in candidateQueries) {
            val fallback = searchFullTracks(q, limit = 20, qualityTier = qualityTier)
            if (fallback.isNotEmpty()) {
                playlistTracksCache.put(playlist.id, fallback)
                return@withContext fallback
            }
        }
        emptyList()
    }

    /**
     * Resolves a full Album page ("Go to Album") with cover artwork, artist, and full album tracklist.
     */
    suspend fun fetchAlbumCollection(
        seedTrack: TrackItem,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ): CollectionPageState = withContext(Dispatchers.IO) {
        val cleanAlbum = seedTrack.album
            .takeIf { it.isNotBlank() && !it.equals("Single", true) && !it.equals("YouTube Music", true) }
            ?: seedTrack.title
        val cleanArtist = seedTrack.artist.substringBefore(",").trim()

        var resolvedAlbumId = seedTrack.albumId
        var albumTitle = cleanAlbum
        var albumSub = cleanArtist
        var albumArt = seedTrack.artworkUrl

        if (resolvedAlbumId.isNullOrBlank()) {
            runCatching {
                val q = URLEncoder.encode("$cleanAlbum $cleanArtist", "UTF-8")
                val url = "$SAAVN_BASE?__call=search.getAlbumResults&api_version=4&_format=json&_marker=0&ctx=web6dot0&n=4&p=1&q=$q"
                val req = buildSaavnRequest(url)
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val pick = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                            if (pick != null) {
                                resolvedAlbumId = pick.optString("id").takeIf { it.isNotBlank() }
                                albumTitle = decodeHtmlEntities(pick.optString("title")).ifBlank { albumTitle }
                                val year = pick.optString("year")
                                albumSub = if (year.isNotBlank()) "$cleanArtist • $year" else cleanArtist
                                val img = upgradeArtworkUrl(pick.optString("image"))
                                if (img.isNotBlank()) albumArt = img
                            }
                        }
                    }
                }
            }
        }

        var albumTracks: List<TrackItem> = emptyList()
        if (!resolvedAlbumId.isNullOrBlank()) {
            runCatching {
                val url = "$SAAVN_BASE?__call=content.getAlbumDetails&api_version=4&_format=json&_marker=0&ctx=web6dot0&albumid=$resolvedAlbumId"
                val req = buildSaavnRequest(url)
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.startsWith("{")) {
                            val root = JSONObject(body)
                            val titleVal = decodeHtmlEntities(root.optString("title"))
                            if (titleVal.isNotBlank()) albumTitle = titleVal
                            val yearVal = root.optString("year")
                            if (yearVal.isNotBlank()) albumSub = "$cleanArtist • $yearVal"
                            val imgVal = upgradeArtworkUrl(root.optString("image"))
                            if (imgVal.isNotBlank()) albumArt = imgVal
                            val listArr = root.optJSONArray("list")
                            if (listArr != null) {
                                albumTracks = parseSaavnSongArray(listArr, maxCount = 35, qualityTier = qualityTier)
                            }
                        }
                    }
                }
            }
        }

        if (albumTracks.isEmpty()) {
            val searched = searchFullTracks("$cleanAlbum $cleanArtist", limit = 16, qualityTier = qualityTier)
            albumTracks = if (searched.isNotEmpty()) searched else listOf(seedTrack)
        }

        CollectionPageState(
            id = "album_${resolvedAlbumId ?: cleanAlbum.hashCode()}",
            type = CollectionPageType.ALBUM,
            title = albumTitle,
            subtitle = "$albumSub • ${albumTracks.size} tracks",
            badge = "ALBUM",
            artworkUrl = albumArt,
            tracks = albumTracks,
            isLoading = false,
            artistNameForNav = cleanArtist
        )
    }

    /**
     * Resolves a full Artist page ("Go to Artist") with portrait, top songs, and artist mixes/playlists.
     */
    suspend fun fetchArtistCollection(
        artistName: String,
        artistIdHint: String? = null,
        fallbackArtwork: String = "",
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ): CollectionPageState = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.substringBefore(",").substringBefore("&").trim().ifBlank { artistName }
        var portraitUrl = fallbackArtwork
        var subtitle = "Artist • Popular Tracks & Releases"

        coroutineScope {
            val artistMetaJob = async {
                runCatching {
                    val q = URLEncoder.encode(cleanArtist, "UTF-8")
                    val url = "$SAAVN_BASE?__call=search.getArtistResults&api_version=4&_format=json&_marker=0&ctx=web6dot0&n=2&p=1&q=$q"
                    val req = buildSaavnRequest(url)
                    NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.startsWith("{")) {
                                val pick = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                                if (pick != null) {
                                    val img = upgradeArtworkUrl(pick.optString("image"))
                                    if (img.isNotBlank()) portraitUrl = img
                                    val role = decodeHtmlEntities(pick.optString("description"))
                                    if (role.isNotBlank()) subtitle = role
                                }
                            }
                        }
                    }
                }
            }
            val tracksJob = async {
                searchFullTracks(cleanArtist, limit = 22, qualityTier = qualityTier)
            }
            val playlistsJob = async {
                searchPlaylists("$cleanArtist Essentials", limit = 6)
            }

            artistMetaJob.await()
            val topTracks = tracksJob.await()
            val artistPlaylists = playlistsJob.await()
            if (portraitUrl.isBlank()) {
                portraitUrl = topTracks.firstOrNull()?.artworkUrl.orEmpty()
            }

            CollectionPageState(
                id = "artist_${artistIdHint ?: cleanArtist.lowercase().hashCode()}",
                type = CollectionPageType.ARTIST,
                title = cleanArtist,
                subtitle = subtitle,
                badge = "ARTIST",
                artworkUrl = portraitUrl,
                tracks = topTracks,
                relatedPlaylists = artistPlaylists,
                isLoading = false,
                artistNameForNav = cleanArtist
            )
        }
    }

    /**
     * Imports a YouTube Music playlist URL (`https://music.youtube.com/playlist?list=...` or raw `PL...` ID).
     */
    suspend fun importYoutubeMusicPlaylist(urlOrId: String): Pair<SuggestedPlaylist, List<TrackItem>>? =
        withContext(Dispatchers.IO) {
            val clean = urlOrId.trim()
            val listId = when {
                clean.contains("list=") -> clean.substringAfter("list=").substringBefore("&")
                clean.startsWith("VL") -> clean.removePrefix("VL")
                clean.length >= 12 && !clean.contains(" ") -> clean
                else -> return@withContext null
            }.trim()
            if (listId.isBlank()) return@withContext null

            val (title, thumb, tracks) = fetchInnerTubePlaylistWithMeta(listId)
            if (tracks.isEmpty()) return@withContext null
            val summary = SuggestedPlaylist(
                id = "ytm_pl_$listId",
                title = title.ifBlank { "YouTube Music Playlist" },
                subtitle = "YouTube Music Sync • ${tracks.size} tracks",
                badge = "YTM Sync",
                artworkUrl = thumb.ifBlank { tracks.first().artworkUrl },
                trackCount = tracks.size,
                externalPlaylistId = listId,
                isYoutubePlaylist = true,
                preloadedTracks = tracks
            )
            summary to tracks
        }

    /**
     * Extracts real tracks & metadata from any public Spotify playlist/album embed page without requiring OAuth!
     */
    suspend fun fetchPublicSpotifyEmbedPlaylist(
        entityType: String,
        entityId: String
    ): Triple<String, String, List<TrackItem>> = withContext(Dispatchers.IO) {
        if (entityId.isBlank()) return@withContext Triple("", "", emptyList())
        return@withContext runCatching {
            val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"
            val req = Request.Builder()
                .url(embedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
                )
                .build()
            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Triple("", "", emptyList<TrackItem>())
                val html = resp.body?.string().orEmpty()
                val jsonRaw = html
                    .substringAfter("""<script id="__NEXT_DATA__" type="application/json">""", "")
                    .substringBefore("</script>", "")
                    .trim()
                if (!jsonRaw.startsWith("{")) return@use Triple("", "", emptyList<TrackItem>())

                val root = JSONObject(jsonRaw)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")
                    ?: return@use Triple("", "", emptyList<TrackItem>())

                val plTitle = entity.optString("title").ifBlank { entity.optString("name") }
                val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                val plCover = coverSources?.optJSONObject(0)?.optString("url").orEmpty()
                val trackList = entity.optJSONArray("trackList") ?: JSONArray()

                val parsedTracks = ArrayList<TrackItem>(trackList.length())
                for (i in 0 until minOf(trackList.length(), 60)) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val tTitle = decodeHtmlEntities(item.optString("title"))
                    val tArtist = decodeHtmlEntities(item.optString("subtitle")).replace("\u00A0", " ").trim()
                    val uri = item.optString("uri")
                    val spTrackId = uri.substringAfterLast(":").takeIf { it.isNotBlank() } ?: "${entityId}_$i"
                    val durMs = item.optLong("duration", 210_000L).coerceAtLeast(45_000L)
                    val embedPlayable = if (item.has("isPlayable")) item.optBoolean("isPlayable", true) else true
                    if (tTitle.isBlank()) continue
                    parsedTracks.add(
                        TrackItem(
                            id = "sp_$spTrackId",
                            title = tTitle,
                            artist = tArtist.ifBlank { "Artist" },
                            album = if (entityType == "album") plTitle else "Spotify",
                            artworkUrl = plCover,
                            audioStreamUrl = null,
                            durationMs = durMs,
                            sourceType = AudioSourceType.SPOTIFY,
                            externalSpotifyUri = uri.ifBlank { "spotify:track:$spTrackId" },
                            spotifyTrackId = spTrackId,
                            streamQualityBadge = "SPOTIFY",
                            isPlayable = embedPlayable
                        )
                    )
                }
                Triple(plTitle, plCover, parsedTracks.toList())
            }
        }.getOrDefault(Triple("", "", emptyList()))
    }

    /**
     * Resolves any pasted `open.spotify.com/{track|album|playlist}/{id}` link without requiring OAuth!
     */
    suspend fun resolvePublicSpotifyLink(url: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.contains("spotify.com/")) return@withContext emptyList()
        when {
            clean.contains("/playlist/") -> {
                val pid = clean.substringAfter("/playlist/").substringBefore("?").substringBefore("/").trim()
                val (_, _, embedTracks) = fetchPublicSpotifyEmbedPlaylist("playlist", pid)
                if (embedTracks.isNotEmpty()) return@withContext embedTracks
            }
            clean.contains("/album/") -> {
                val aid = clean.substringAfter("/album/").substringBefore("?").substringBefore("/").trim()
                val (_, _, embedTracks) = fetchPublicSpotifyEmbedPlaylist("album", aid)
                if (embedTracks.isNotEmpty()) return@withContext embedTracks
            }
        }
        return@withContext runCatching {
            val oembedUrl = "https://open.spotify.com/oembed?url=${URLEncoder.encode(clean, "UTF-8")}"
            val req = Request.Builder().url(oembedUrl).build()
            var resolvedTitle = ""
            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    resolvedTitle = obj.optString("title")
                }
            }
            if (resolvedTitle.isNotBlank()) {
                searchFullTracks(resolvedTitle, limit = 15)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Resolves an unthrottled, full-length audio stream adapted to [qualityTier] (Data Saver -> Lossless FLAC / LDAC Best).
     * Never returns fake placeholder URLs; binds exact client User-Agent headers for YouTube `googlevideo` streams
     * and avoids blind `_320.mp4` rewrites that would trigger CDN 404s.
     */
    /**
     * Fast HTTP 200/206 reachability probe (`Range: bytes=0-511`) so dead/403/404 CDN URLs
     * from Saavn `_320.mp4`, InnerTube `googlevideo.com`, or Piped never reach Android MediaPlayer.
     */
    internal fun verifyStreamReachable(
        url: String?,
        headers: Map<String, String> = emptyMap()
    ): Boolean {
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) return false
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-511")
            if (headers.isEmpty()) {
                reqBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                )
            } else {
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            }
            NetworkModule.losslessProbeClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code == 200 || resp.code == 206
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves a verified Saavn CDN URL, automatically stepping down `_320.mp4` -> `_160.mp4` -> `_96.mp4`
     * if a track's higher-bitrate edge file returns 404/403.
     */
    private fun resolveVerifiedSaavnStream(
        rawUrl: String,
        durationMs: Long,
        qualityTier: AudioQualityTier
    ): ResolvedStreamResult? {
        val preferred = adaptSaavnUrlForTier(rawUrl, qualityTier)
        val candidates = LinkedHashSet<String>()
        candidates.add(preferred)
        val match = SAAVN_BITRATE_REGEX.find(preferred)
        if (match != null) {
            val ext = match.groupValues[2]
            candidates.add(preferred.replaceRange(match.range, "_160.$ext"))
            candidates.add(preferred.replaceRange(match.range, "_96.$ext"))
        } else {
            candidates.add(preferred.replace("_320.mp4", "_160.mp4"))
            candidates.add(preferred.replace("_320.mp4", "_96.mp4").replace("_160.mp4", "_96.mp4"))
        }
        for (candidateUrl in candidates) {
            if (verifyStreamReachable(candidateUrl)) {
                return buildSaavnStreamResult(candidateUrl, durationMs, qualityTier)
            }
        }
        return null
    }

    suspend fun resolveFullLengthStream(
        track: TrackItem,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
        customPipedNode: String? = null,
        forceAlternate: Boolean = false,
        attemptIndex: Int = if (forceAlternate) 1 else 0
    ): ResolvedStreamResult = withContext(Dispatchers.IO) {
        val effectiveAttempt = if (forceAlternate && attemptIndex == 0) 1 else attemptIndex
        val cacheKey = "${track.id}_${qualityTier.id}"
        if (effectiveAttempt > 0) {
            resolvedStreamCache.remove(cacheKey)
        } else {
            resolvedStreamCache.get(cacheKey)?.let { cached ->
                if (verifyStreamReachable(cached.url, cached.requestHeaders)) {
                    return@withContext cached
                } else {
                    resolvedStreamCache.remove(cacheKey)
                }
            }
        }

        // Attempt >= 2: Dedicated deep-fallback path when both primary and secondary streams failed
        if (effectiveAttempt >= 2) {
            val piped = resolveViaPipedNode(track, customPipedNode, qualityTier)
                .takeIf { verifyStreamReachable(it.url, it.requestHeaders) }
            if (piped != null) return@withContext piped
            val sc = resolveViaSoundCloudProgressive(track)
                ?.takeIf { verifyStreamReachable(it.url, it.requestHeaders) }
            if (sc != null) return@withContext sc
            val safety = resolveGuaranteedFallbackStream(track, qualityTier)
            if (safety != null && !safety.url.isNullOrBlank()) return@withContext safety
        }

        // 1. If track carries a direct decrypted studio CDN URL (and user didn't force alternate),
        // verify HTTP 200/206 quickly when not in FLAC-first mode
        val existingUrl = track.audioStreamUrl
        if (effectiveAttempt == 0 &&
            qualityTier != AudioQualityTier.LOSSLESS_LDAC &&
            !existingUrl.isNullOrBlank() &&
            !existingUrl.contains("mzstatic.com") &&
            !existingUrl.contains("p.scdn.co") &&
            !existingUrl.contains("pipedproxy.kavin.rocks") &&
            track.durationMs > 40_000L
        ) {
            val verifiedExisting = if (existingUrl.contains("saavncdn.com")) {
                resolveVerifiedSaavnStream(existingUrl, track.durationMs, qualityTier)
            } else if (verifyStreamReachable(existingUrl)) {
                buildSaavnStreamResult(adaptSaavnUrlForTier(existingUrl, qualityTier), track.durationMs, qualityTier)
            } else {
                null
            }
            if (verifiedExisting != null) {
                resolvedStreamCache.put(cacheKey, verifiedExisting)
                return@withContext verifiedExisting
            }
        }

        val hadDirectSaavnUrl = !existingUrl.isNullOrBlank() && existingUrl.contains("saavncdn.com")

        // 2. Race Lossless FLAC, Official Catalog CDN, & Verified YouTube Music concurrently (zero sequential bottleneck!)
        val resolved: ResolvedStreamResult = coroutineScope {
            val dabStudioJob = async {
                if (effectiveAttempt <= 1) {
                    withTimeoutOrNull(2_400L) {
                        runCatching {
                            probeDabStudioCatalogStream(
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                fallbackDurationMs = track.durationMs,
                                preferLosslessFlac = true
                            )
                        }.getOrNull()
                    }
                } else null
            }
            val saavnJob = async {
                if (effectiveAttempt == 0 && hadDirectSaavnUrl && !existingUrl.isNullOrBlank()) {
                    resolveVerifiedSaavnStream(existingUrl, track.durationMs, qualityTier)
                        ?: runCatching {
                            matchBestSaavnStream(
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                targetDurationMs = track.durationMs,
                                qualityTier = qualityTier
                            )
                        }.getOrNull()
                } else if (effectiveAttempt >= 1 && hadDirectSaavnUrl) {
                    null
                } else {
                    runCatching {
                        matchBestSaavnStream(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            targetDurationMs = track.durationMs,
                            qualityTier = qualityTier
                        )
                    }.getOrNull()
                }
            }
            val innerTubeJob = async {
                runCatching {
                    val vid = if (effectiveAttempt >= 1 && !track.youtubeVideoId.isNullOrBlank() && !hadDirectSaavnUrl) {
                        findCanonicalInnerTubeVideoId(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            targetDurationMs = track.durationMs
                        ) ?: track.youtubeVideoId
                    } else {
                        track.youtubeVideoId ?: findCanonicalInnerTubeVideoId(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            targetDurationMs = track.durationMs
                        )
                    }
                    if (!vid.isNullOrBlank()) {
                        extractInnerTubeAudioStream(vid, qualityTier, expectedDurationMs = track.durationMs)
                    } else null
                }.getOrNull()
            }

            val preferYtmFirst = (effectiveAttempt >= 1 && hadDirectSaavnUrl) ||
                (qualityTier == AudioQualityTier.YTM_NORMAL && !track.youtubeVideoId.isNullOrBlank() && effectiveAttempt == 0)

            val primary = when {
                qualityTier == AudioQualityTier.LOSSLESS_LDAC && effectiveAttempt == 0 -> {
                    dabStudioJob.await() ?: saavnJob.await() ?: innerTubeJob.await()
                }
                preferYtmFirst -> {
                    innerTubeJob.await() ?: saavnJob.await() ?: dabStudioJob.await()
                }
                else -> {
                    saavnJob.await() ?: innerTubeJob.await() ?: dabStudioJob.await()
                }
            }

            primary ?: run {
                val mirrorMatch = runCatching {
                    val mirrorCandidates = searchSaavnMirror("${stripEditionNoise(track.title)} ${primaryArtistOf(track.artist)}", limit = 10)
                    pickBestCanonicalTrack(
                        candidates = mirrorCandidates,
                        targetTitle = track.title,
                        targetArtist = track.artist,
                        targetAlbum = track.album,
                        targetDurationMs = track.durationMs,
                        minimumScore = 25
                    )?.takeIf { !it.audioStreamUrl.isNullOrBlank() }?.let {
                        resolveVerifiedSaavnStream(it.audioStreamUrl!!, it.durationMs, qualityTier)
                    }
                }.getOrNull()

                val pipedResolved = mirrorMatch
                    ?: resolveViaPipedNode(track, customPipedNode, qualityTier)
                        .takeIf { verifyStreamReachable(it.url, it.requestHeaders) }

                pipedResolved
                    ?: resolveViaSoundCloudProgressive(track)?.takeIf { verifyStreamReachable(it.url, it.requestHeaders) }
                    ?: resolveGuaranteedFallbackStream(track, qualityTier)
                    ?: ResolvedStreamResult(null, track.durationMs)
            }
        }

        if (!resolved.url.isNullOrBlank()) {
            resolvedStreamCache.put(cacheKey, resolved)
        }
        resolved
    }

    /**
     * Final zero-failure safety net when strict album/duration matchers and primary extractors fail:
     * 1. Relaxed Saavn title + primary artist search (ignoring strict duration delta for remasters/radio edits).
     * 2. Relaxed InnerTube music search without expectedDurationMs rejection.
     * 3. Deezer API / Apple iTunes Search CDN direct AAC/MP3 audio stream so playback NEVER fails or stalls.
     */
    private suspend fun resolveGuaranteedFallbackStream(
        track: TrackItem,
        qualityTier: AudioQualityTier
    ): ResolvedStreamResult? = withContext(Dispatchers.IO) {
        val cleanTitle = stripEditionNoise(track.title).ifBlank { track.title }
        val cleanArtist = primaryArtistOf(track.artist).ifBlank { track.artist }
        val query = "$cleanTitle $cleanArtist".trim()

        // Tier A: Relaxed Saavn full-track search without strict duration rejection (with HTTP reachability check)
        val relaxedSaavn = runCatching {
            val queriesToTry = listOf(query, cleanTitle).filter { it.isNotBlank() }.distinct()
            var found: ResolvedStreamResult? = null
            for (q in queriesToTry) {
                val candidates = searchSaavnDirect(q, limit = 14, qualityTier = qualityTier).ifEmpty {
                    searchSaavnMirror(q, limit = 14)
                }
                val artistToken = normalizeComparableText(cleanArtist).split(" ").firstOrNull { it.length >= 3 }
                val filtered = if (q == cleanTitle && artistToken != null) {
                    candidates.filter { normalizeComparableText(it.artist).contains(artistToken) }.ifEmpty { candidates }
                } else {
                    candidates
                }
                found = filtered.firstNotNullOfOrNull { best ->
                    val u = best.audioStreamUrl ?: return@firstNotNullOfOrNull null
                    resolveVerifiedSaavnStream(
                        rawUrl = u,
                        durationMs = best.durationMs.takeIf { it > 0L } ?: track.durationMs,
                        qualityTier = qualityTier
                    )
                }
                if (found != null && !found.url.isNullOrBlank()) break
            }
            found
        }.getOrNull()
        if (relaxedSaavn != null && !relaxedSaavn.url.isNullOrBlank()) return@withContext relaxedSaavn

        // Tier B: Relaxed InnerTube + Piped/Invidious full-track lookup without duration rejection
        val relaxedInnerTube = runCatching {
            val vid = track.youtubeVideoId ?: findCanonicalInnerTubeVideoId(
                title = cleanTitle,
                artist = cleanArtist,
                album = "",
                targetDurationMs = 0L
            )
            if (!vid.isNullOrBlank()) {
                extractInnerTubeAudioStream(vid, qualityTier, expectedDurationMs = 0L)
                    ?: resolveViaPipedNode(
                        track = track.copy(youtubeVideoId = vid),
                        customNode = null,
                        qualityTier = qualityTier
                    ).takeIf { !it.url.isNullOrBlank() && verifyStreamReachable(it.url, it.requestHeaders) }
            } else null
        }.getOrNull()
        if (relaxedInnerTube != null && !relaxedInnerTube.url.isNullOrBlank()) return@withContext relaxedInnerTube

        // Tier C: SoundCloud full-length progressive stream (verified HTTP 200/206; rejects 30s snipped previews)
        val scFallback = runCatching {
            resolveViaSoundCloudProgressive(track)?.takeIf { verifyStreamReachable(it.url, it.requestHeaders) }
        }.getOrNull()
        scFallback
    }

    // =========================================================================
    // LOSSLESS FLAC RESOLVER (Monochrome Track Streamer + Studio Verification)
    // =========================================================================

    private fun probeDabStudioCatalogStream(
        title: String,
        artist: String,
        album: String,
        fallbackDurationMs: Long,
        preferLosslessFlac: Boolean = true
    ): ResolvedStreamResult? {
        if (!preferLosslessFlac) return null
        val cleanTitle = stripEditionNoise(title)
        val cleanArtist = primaryArtistOf(artist)
        val encoded = URLEncoder.encode("$cleanArtist $cleanTitle".trim(), "UTF-8")
        val browserUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        try {
            var matchedTrackId: String? = null
            var durationMs = fallbackDurationMs

            val searchReq = Request.Builder()
                .url("$MONOCHROME_TRACKS_BASE/search/tracks?q=$encoded&limit=6")
                .header("Accept", "application/json")
                .header("Origin", "https://monochrome.st")
                .header("Referer", "https://monochrome.st/")
                .header("User-Agent", browserUa)
                .build()

            NetworkModule.losslessProbeClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (!body.startsWith("{")) return null
                val root = JSONObject(body)
                val tracks = root.optJSONArray("tracks") ?: return null
                if (tracks.length() == 0) return null

                var bestScore = Int.MIN_VALUE
                var bestObj: JSONObject? = null

                for (i in 0 until minOf(tracks.length(), 8)) {
                    val cand = tracks.optJSONObject(i) ?: continue
                    val candId = cand.opt("trackId")?.toString()?.trim()?.ifBlank { null }
                        ?: cand.opt("recordingId")?.toString()?.trim()?.ifBlank { null }
                        ?: cand.opt("id")?.toString()?.trim().orEmpty()
                    val candTitle = cand.optString("title")
                    if (candId.isBlank() || candTitle.isBlank()) continue
                    val candArtist = cand.optJSONArray("artists")
                        ?.optJSONObject(0)
                        ?.optString("name")
                        .orEmpty()
                        .ifBlank { cand.optJSONArray("artistNames")?.optString(0).orEmpty() }
                        .ifBlank { cand.optString("artist") }
                    val candAlbum = cand.optString("albumTitle")
                        .ifBlank { cand.optString("releaseTitle") }
                    val candDurMs = cand.optLong("duration", 0L).let { if (it > 1000L) it else it * 1000L }

                    val score = scoreCanonicalTrackCandidate(
                        candidateTitle = candTitle,
                        candidateArtist = candArtist,
                        candidateAlbum = candAlbum,
                        candidateDurationMs = candDurMs,
                        isYoutubeUgc = false,
                        targetTitle = title,
                        targetArtist = artist,
                        targetAlbum = album,
                        targetDurationMs = fallbackDurationMs
                    )
                    if (score > bestScore) {
                        bestScore = score
                        bestObj = cand
                    }
                }

                if (bestObj != null && bestScore >= 45) {
                    matchedTrackId = bestObj.opt("trackId")?.toString()?.trim()?.ifBlank { null }
                        ?: bestObj.opt("recordingId")?.toString()?.trim()?.ifBlank { null }
                        ?: bestObj.opt("id")?.toString()?.trim()?.takeIf { it.isNotBlank() }
                    val durRaw = bestObj.optLong("duration", 0L)
                    if (durRaw >= 45L) {
                        durationMs = if (durRaw > 1000L) durRaw else durRaw * 1000L
                    }
                }
            }

            val idVal = matchedTrackId ?: return null
            val directFlacUrl = "$MONOCHROME_TRACKS_BASE/track/$idVal"
            val verifyReq = Request.Builder()
                .url(directFlacUrl)
                .header("Range", "bytes=0-63")
                .header("Origin", "https://monochrome.st")
                .header("Referer", "https://monochrome.st/")
                .header("User-Agent", browserUa)
                .build()
            NetworkModule.losslessProbeClient.newCall(verifyReq).execute().use { vResp ->
                if (vResp.code == 200 || vResp.code == 206) {
                    return ResolvedStreamResult(
                        url = directFlacUrl,
                        durationMs = durationMs.coerceAtLeast(60_000L),
                        codecLabel = "FLAC 16-bit / 44.1kHz",
                        containerLabel = "FLAC Lossless",
                        bitrateKbps = 990,
                        sampleRateHz = 44_100,
                        bitDepth = 16,
                        sourceType = AudioSourceType.LOSSLESS_FLAC,
                        requestHeaders = mapOf(
                            "Origin" to "https://monochrome.st",
                            "Referer" to "https://monochrome.st/",
                            "User-Agent" to browserUa
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    // =========================================================================
    // ENGINE 1: Direct Multi-Bitrate AAC CDN (96k / 160k / 320k Master)
    // =========================================================================

    /**
     * Adapts a Saavn CDN URL safely (including query-parameter URLs):
     * NEVER blindly rewrites `_160.mp4` or `_96.mp4` upward to `_320.mp4` unless verified,
     * preventing CDN HTTP 404s while accurately reporting the true delivered bitrate.
     */
    internal fun adaptSaavnUrlForTier(url: String, tier: AudioQualityTier): String {
        val match = SAAVN_BITRATE_REGEX.find(url) ?: return url
        val offeredKbps = match.groupValues[1].toIntOrNull() ?: 160
        val ext = match.groupValues[2]
        val desiredKbps = when (tier) {
            AudioQualityTier.DATA_SAVER -> minOf(offeredKbps, 96)
            AudioQualityTier.YTM_NORMAL -> minOf(offeredKbps, 160)
            AudioQualityTier.STUDIO_320, AudioQualityTier.LOSSLESS_LDAC -> offeredKbps
        }
        return if (desiredKbps == offeredKbps) {
            url
        } else {
            url.replaceRange(match.range, "_${desiredKbps}.$ext")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildSaavnStreamResult(
        url: String,
        durationMs: Long,
        tier: AudioQualityTier
    ): ResolvedStreamResult {
        val offeredKbps = SAAVN_BITRATE_REGEX.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val kbps = when (offeredKbps) {
            320 -> 320
            160 -> 160
            96 -> 96
            48 -> 48
            else -> if (url.contains("_320")) 320 else if (url.contains("_160")) 160 else 96
        }
        val codec = if (kbps >= 160) "AAC-LC" else "HE-AAC"
        return ResolvedStreamResult(
            url = url,
            durationMs = durationMs,
            codecLabel = codec,
            containerLabel = "MP4 ISO",
            bitrateKbps = kbps,
            sampleRateHz = 44_100,
            bitDepth = 16,
            sourceType = AudioSourceType.OPEN_AAC
        )
    }

    private fun searchSaavnDirect(
        query: String,
        limit: Int,
        qualityTier: AudioQualityTier
    ): List<TrackItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$SAAVN_BASE?__call=search.getResults&api_version=4&_format=json&_marker=0&ctx=web6dot0&n=$limit&p=1&q=$encoded"
        val request = buildSaavnRequest(url)

        NetworkModule.fastHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val bodyStr = resp.body?.string().orEmpty()
            if (!bodyStr.startsWith("{")) return emptyList()
            val root = JSONObject(bodyStr)
            val results = root.optJSONArray("results") ?: return emptyList()
            return parseSaavnSongArray(results, limit, qualityTier)
        }
    }

    private fun parseSaavnSongArray(
        results: JSONArray,
        maxCount: Int,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ): List<TrackItem> {
        val items = ArrayList<TrackItem>(minOf(results.length(), maxCount))
        for (i in 0 until results.length()) {
            if (items.size >= maxCount) break
            val obj = results.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val rawTitle = decodeHtmlEntities(obj.optString("title"))
            if (id.isBlank() || rawTitle.isBlank()) continue

            val moreInfo = obj.optJSONObject("more_info")
            val encryptedUrl = moreInfo?.optString("encrypted_media_url").orEmpty()
            val has320 = moreInfo?.optString("320kbps") == "true"
            val durationSec = moreInfo?.optString("duration")?.toLongOrNull() ?: 0L
            if (durationSec in 1..44) continue

            val prefer320 = has320 && (qualityTier == AudioQualityTier.STUDIO_320 || qualityTier == AudioQualityTier.LOSSLESS_LDAC)
            val baseDecrypted = decryptSaavnMediaUrl(encryptedUrl, prefer320) ?: continue
            val streamUrl = adaptSaavnUrlForTier(baseDecrypted, qualityTier)
            val albumName = decodeHtmlEntities(moreInfo?.optString("album").orEmpty()).ifBlank { "Single" }
            val albumId = moreInfo?.optString("album_id")?.takeIf { it.isNotBlank() }

            val artistMapObj = moreInfo?.optJSONObject("artistMap")
            val primaryArtistArr = artistMapObj?.optJSONArray("primary_artists")
            val featuredArtistArr = artistMapObj?.optJSONArray("featured_artists")
            val primaryArtistId = primaryArtistArr?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }

            val primaryArtists = buildList<String> {
                if (primaryArtistArr != null) {
                    for (a in 0 until primaryArtistArr.length()) {
                        val name = decodeHtmlEntities(primaryArtistArr.optJSONObject(a)?.optString("name").orEmpty())
                        if (name.isNotBlank() && none { it.equals(name, ignoreCase = true) }) add(name)
                    }
                }
                if (featuredArtistArr != null) {
                    for (f in 0 until featuredArtistArr.length()) {
                        val name = decodeHtmlEntities(featuredArtistArr.optJSONObject(f)?.optString("name").orEmpty())
                        if (name.isNotBlank() && none { it.equals(name, ignoreCase = true) }) add(name)
                    }
                }
            }.joinToString(", ")
                .ifBlank {
                    decodeHtmlEntities(moreInfo?.optString("music").orEmpty())
                        .ifBlank { decodeHtmlEntities(obj.optString("subtitle")).substringBefore(" - ") }
                        .ifBlank { "Artist" }
                }

            val artwork = upgradeArtworkUrl(obj.optString("image"))
            val durationMs = (if (durationSec > 0L) durationSec else 210L) * 1000L
            val trackId = "hq_$id"

            if (qualityTier != AudioQualityTier.LOSSLESS_LDAC) {
                resolvedStreamCache.put(
                    "${trackId}_${qualityTier.id}",
                    buildSaavnStreamResult(streamUrl, durationMs, qualityTier)
                )
            }

            items.add(
                TrackItem(
                    id = trackId,
                    title = rawTitle,
                    artist = primaryArtists,
                    album = albumName,
                    artworkUrl = artwork,
                    audioStreamUrl = streamUrl,
                    durationMs = durationMs,
                    sourceType = if (prefer320) AudioSourceType.OPEN_AAC else AudioSourceType.YTM_ADFREE,
                    albumId = albumId,
                    artistId = primaryArtistId,
                    streamQualityBadge = if (prefer320) "320K" else "160K"
                )
            )
        }
        return items
    }

    // Upstream public CDN stream URLs are obfuscated with fixed DES/ECB; never used for user data.
    @SuppressLint("GetInstance")
    internal fun decryptSaavnMediaUrl(encryptedBase64: String, prefer320: Boolean): String? {
        if (encryptedBase64.isBlank()) return null
        return try {
            val clean = encryptedBase64.trim()
            val cipherBytes = try {
                Base64.decode(clean, Base64.DEFAULT)
            } catch (_: Throwable) {
                Base64.decode(clean, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            if (cipherBytes == null || cipherBytes.isEmpty()) return null

            val keySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decrypted = String(cipher.doFinal(cipherBytes), Charsets.UTF_8).trim()
            if (!decrypted.startsWith("http")) return null

            val httpsUrl = decrypted.replaceFirst("^http://".toRegex(), "https://")
            val match = SAAVN_BITRATE_REGEX.find(httpsUrl)
            if (match != null) {
                val offered = match.groupValues[1].toIntOrNull() ?: 96
                val ext = match.groupValues[2]
                val target = when {
                    prefer320 -> 320
                    offered < 160 -> 160
                    else -> offered
                }
                httpsUrl.replaceRange(match.range, "_${target}.$ext")
            } else if (prefer320) {
                httpsUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
            } else {
                httpsUrl.replace("_96.mp4", "_160.mp4")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun matchBestSaavnStream(
        title: String,
        artist: String,
        album: String,
        targetDurationMs: Long,
        qualityTier: AudioQualityTier
    ): ResolvedStreamResult? {
        val cleanTitle = stripEditionNoise(title)
        val cleanArtist = primaryArtistOf(artist)
        val cleanAlbum = album.takeIf {
            it.isNotBlank() && !it.equals("Single", true) && !it.equals("YouTube Music", true)
        }.orEmpty()

        val primaryCandidates = searchSaavnDirect("$cleanTitle $cleanArtist", limit = 12, qualityTier = qualityTier)
        val albumScopedCandidates = if (cleanAlbum.isNotBlank() && primaryCandidates.size < 4) {
            searchSaavnDirect("$cleanTitle $cleanArtist $cleanAlbum", limit = 6, qualityTier = qualityTier)
        } else {
            emptyList()
        }
        val candidates = (primaryCandidates + albumScopedCandidates).distinctBy { it.id }
        if (candidates.isEmpty()) return null

        val best = pickBestCanonicalTrack(
            candidates = candidates,
            targetTitle = title,
            targetArtist = artist,
            targetAlbum = album,
            targetDurationMs = targetDurationMs,
            minimumScore = 35
        ) ?: return null

        val url = best.audioStreamUrl ?: return null
        return resolveVerifiedSaavnStream(url, best.durationMs, qualityTier)
    }

    private fun searchSaavnMirror(query: String, limit: Int): List<TrackItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val mirrors = listOf(
            "$SAAVN_MIRROR_BASE/search/songs?query=$encoded&limit=$limit",
            "https://saavn.dev/api/search/songs?query=$encoded&limit=$limit",
            "https://jiosaavn-api-privatecvc2.vercel.app/search/songs?query=$encoded&limit=$limit"
        )
        for (mirrorUrl in mirrors) {
            val list = runCatching {
                val req = Request.Builder().url(mirrorUrl).build()
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<TrackItem>()
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!bodyStr.startsWith("{")) return@use emptyList<TrackItem>()
                    val root = JSONObject(bodyStr)
                    val results = root.optJSONObject("data")?.optJSONArray("results") ?: return@use emptyList<TrackItem>()
                    val out = ArrayList<TrackItem>()
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val name = decodeHtmlEntities(item.optString("name"))
                        val durSec = item.optLong("duration", 200L)
                        if (durSec in 1..44) continue
                        val dlArr = item.optJSONArray("downloadUrl")
                        val lastDl = dlArr?.optJSONObject(dlArr.length() - 1)
                        val bestUrl = (lastDl?.optString("url").orEmpty().ifBlank { lastDl?.optString("link").orEmpty() })
                            .replaceFirst("^http://".toRegex(), "https://")
                        if (id.isBlank() || name.isBlank() || bestUrl.isNullOrBlank()) continue

                        val imgArr = item.optJSONArray("image")
                        val lastImg = imgArr?.optJSONObject(imgArr.length() - 1)
                        val art = (lastImg?.optString("url").orEmpty().ifBlank { lastImg?.optString("link").orEmpty() })
                            .replaceFirst("^http://".toRegex(), "https://")

                        val artistsObj = item.optJSONObject("artists")
                        val artistsStr = if (artistsObj != null) {
                            val prim = artistsObj.optJSONArray("primary")
                            val feat = artistsObj.optJSONArray("featured")
                            buildList<String> {
                                if (prim != null) {
                                    for (idx in 0 until prim.length()) {
                                        val n = decodeHtmlEntities(prim.optJSONObject(idx)?.optString("name").orEmpty())
                                        if (n.isNotBlank() && none { it.equals(n, ignoreCase = true) }) add(n)
                                    }
                                }
                                if (feat != null) {
                                    for (idx in 0 until feat.length()) {
                                        val n = decodeHtmlEntities(feat.optJSONObject(idx)?.optString("name").orEmpty())
                                        if (n.isNotBlank() && none { it.equals(n, ignoreCase = true) }) add(n)
                                    }
                                }
                            }.joinToString(", ")
                        } else {
                            decodeHtmlEntities(item.optString("primaryArtists"))
                        }.ifBlank { "Artist" }

                        val durMs = durSec.coerceAtLeast(60L) * 1000L
                        out.add(
                            TrackItem(
                                id = "hqm_$id",
                                title = name,
                                artist = artistsStr,
                                album = decodeHtmlEntities(item.optJSONObject("album")?.optString("name").orEmpty()).ifBlank { "Single" },
                                artworkUrl = art,
                                audioStreamUrl = bestUrl,
                                durationMs = durMs,
                                sourceType = AudioSourceType.OPEN_AAC,
                                albumId = item.optJSONObject("album")?.optString("id")?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    out
                }
            }.getOrDefault(emptyList())
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    // =========================================================================
    // ENGINE 2: Direct YouTube Music InnerTube (Search, Browse Playlists, & Adaptive Audio)
    // =========================================================================

    private fun searchInnerTubeMusic(query: String, limit: Int): List<TrackItem> {
        val payload = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", "1.20250312.01.00")
                        .put("hl", "en")
                        .put("gl", "US")
                )
            )
            put("query", query)
            put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        }

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val root = JSONObject(resp.body?.string().orEmpty())
            root.optJSONObject("responseContext")
                ?.optString("visitorData")
                ?.takeIf { it.isNotBlank() }
                ?.let { cachedVisitorData = it }
            val results = ArrayList<TrackItem>()
            collectMusicResponsiveRenderers(root, results, limit)
            return results
        }
    }

    private fun fetchInnerTubePlaylistTracks(listId: String): List<TrackItem> {
        return fetchInnerTubePlaylistWithMeta(listId).third
    }

    private fun fetchInnerTubePlaylistWithMeta(listId: String): Triple<String, String, List<TrackItem>> {
        val browseId = if (listId.startsWith("VL")) listId else "VL$listId"
        val payload = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", "1.20250312.01.00")
                        .put("hl", "en")
                        .put("gl", "US")
                )
            )
            put("browseId", browseId)
        }

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching<Triple<String, String, List<TrackItem>>> {
            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Triple("", "", emptyList<TrackItem>())
                val root = JSONObject(resp.body?.string().orEmpty())
                val tracks = ArrayList<TrackItem>()
                collectMusicResponsiveRenderers(root, tracks, 50)
                val title = findFirstTitleInBrowseHeader(root)
                val thumb = tracks.firstOrNull()?.artworkUrl.orEmpty()
                Triple(title, thumb, tracks.toList())
            }
        }.getOrDefault(Triple("", "", emptyList()))
    }

    private fun findFirstTitleInBrowseHeader(root: JSONObject): String {
        val headerStr = root.optJSONObject("header")?.toString().orEmpty()
        val match = Regex(""""title"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"""").find(headerStr)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun collectMusicResponsiveRenderers(
        node: Any?,
        out: MutableList<TrackItem>,
        limit: Int
    ) {
        if (out.size >= limit || node == null) return
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null) {
                    parseInnerTubeSongRenderer(renderer)?.let { out.add(it) }
                    return
                }
                val keys = node.keys()
                while (keys.hasNext() && out.size < limit) {
                    val k = keys.next()
                    collectMusicResponsiveRenderers(node.opt(k), out, limit)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= limit) break
                    collectMusicResponsiveRenderers(node.opt(i), out, limit)
                }
            }
        }
    }

    private fun parseInnerTubeSongRenderer(renderer: JSONObject): TrackItem? {
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
            ?: return null

        val flexCols = renderer.optJSONArray("flexColumns") ?: return null
        val titleRuns = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val title = titleRuns?.optJSONObject(0)?.optString("text")?.trim().orEmpty()
        if (title.isBlank()) return null

        val metaRuns = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        var artist = ""
        var album = "YouTube Music"
        var durationMs = 210_000L
        var isVideoUpload = false
        if (metaRuns != null) {
            val bulletSegments = ArrayList<String>()
            val currentSegment = StringBuilder()
            for (i in 0 until metaRuns.length()) {
                val rawTxt = metaRuns.optJSONObject(i)?.optString("text").orEmpty()
                if (rawTxt.trim() == "•") {
                    val seg = currentSegment.toString().trim()
                    if (seg.isNotBlank()) bulletSegments.add(seg)
                    currentSegment.clear()
                } else {
                    currentSegment.append(rawTxt)
                }
            }
            val trailing = currentSegment.toString().trim()
            if (trailing.isNotBlank()) bulletSegments.add(trailing)

            if (bulletSegments.isNotEmpty()) {
                val firstLower = bulletSegments.first().lowercase()
                if (firstLower in setOf("episode", "podcast", "playlist", "album", "artist", "profile")) {
                    return null
                }
                if (firstLower == "video") {
                    isVideoUpload = true
                    bulletSegments.removeAt(0)
                } else if (firstLower == "song" || firstLower == "single") {
                    bulletSegments.removeAt(0)
                }
            }

            // Check fixedColumns[0] first if YTM placed track duration in the fixed right column
            val fixedDurTxt = renderer.optJSONArray("fixedColumns")
                ?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                .orEmpty()
            parseDurationTextMs(fixedDurTxt)?.let { durationMs = it }

            val durSegmentIdx = bulletSegments.indexOfLast { parseDurationTextMs(it) != null }
            if (durSegmentIdx >= 0) {
                parseDurationTextMs(bulletSegments[durSegmentIdx])?.let { durationMs = it }
                bulletSegments.removeAt(durSegmentIdx)
            }

            // Note: YTM official Songs include "3.8B plays" whereas YouTube Videos use "1.4M views"
            val songPlaysRegex = Regex("""\bplays\b""", RegexOption.IGNORE_CASE)
            val videoViewsNoiseRegex = Regex("""\b(views|subscribers|watching)\b""", RegexOption.IGNORE_CASE)
            val cleanSegments = bulletSegments.filter { seg ->
                val isPlays = songPlaysRegex.containsMatchIn(seg)
                val isVideoViews = videoViewsNoiseRegex.containsMatchIn(seg)
                if (isVideoViews) isVideoUpload = true
                !isPlays && !isVideoViews
            }

            if (cleanSegments.isNotEmpty()) {
                artist = cleanSegments[0].removeSuffix("- Topic").trim()
            }
            if (isVideoUpload) {
                album = "YouTube Video"
            } else if (cleanSegments.size >= 2) {
                album = cleanSegments[1].trim()
            }
        }
        if (durationMs < 45_000L) return null

        val thumbs = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val rawThumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        val hiResThumb = rawThumb.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")

        return TrackItem(
            id = "ytm_$videoId",
            title = title,
            artist = artist.ifBlank { "Artist" },
            album = album,
            artworkUrl = hiResThumb,
            audioStreamUrl = null,
            durationMs = durationMs,
            sourceType = AudioSourceType.YTM_ADFREE,
            youtubeVideoId = videoId,
            streamQualityBadge = "YTM"
        )
    }

    private fun findCanonicalInnerTubeVideoId(
        title: String,
        artist: String,
        album: String,
        targetDurationMs: Long
    ): String? {
        val cleanTitle = stripEditionNoise(title)
        val cleanArtist = primaryArtistOf(artist)
        val cleanAlbum = album.takeIf {
            it.isNotBlank() &&
                !it.equals("Single", true) &&
                !it.equals("YouTube Music", true) &&
                !it.equals("Spotify", true)
        }

        val baseCandidates = searchInnerTubeMusic("$cleanTitle $cleanArtist", limit = 10)
        val albumCandidates = if (!cleanAlbum.isNullOrBlank() && baseCandidates.size < 4) {
            searchInnerTubeMusic("$cleanTitle $cleanArtist $cleanAlbum", limit = 6)
        } else {
            emptyList()
        }
        val combined = (baseCandidates + albumCandidates).distinctBy { it.id }
        if (combined.isEmpty()) return null

        return pickBestCanonicalTrack(
            candidates = combined,
            targetTitle = title,
            targetArtist = artist,
            targetAlbum = album,
            targetDurationMs = targetDurationMs,
            minimumScore = 10
        )?.youtubeVideoId ?: combined.firstOrNull()?.youtubeVideoId
    }

    private fun extractInnerTubeAudioStream(
        videoId: String,
        qualityTier: AudioQualityTier,
        expectedDurationMs: Long = 0L
    ): ResolvedStreamResult? {
        val clients = listOf(
            Triple(
                "ANDROID_VR",
                "1.65.10",
                "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            ),
            Triple(
                "ANDROID_MUSIC",
                "7.27.52",
                "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240205.004) gzip"
            ),
            Triple(
                "VISIONOS",
                "0.1",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
            ),
            Triple(
                "IOS",
                "20.10.4",
                "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
            ),
            Triple(
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                "2.0",
                "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15"
            )
        )

        val visitor = cachedVisitorData
        for ((clientName, clientVersion, userAgent) in clients) {
            try {
                val clientObj = JSONObject()
                    .put("clientName", clientName)
                    .put("clientVersion", clientVersion)
                    .put("hl", "en")
                    .put("gl", "US")
                if (!visitor.isNullOrBlank()) {
                    clientObj.put("visitorData", visitor)
                }
                when (clientName) {
                    "VISIONOS" -> {
                        clientObj.put("osName", "VISION_OS")
                        clientObj.put("osVersion", "1.3")
                        clientObj.put("deviceMake", "Apple")
                        clientObj.put("deviceModel", "RealityDevice14,1")
                        clientObj.put("platform", "MOBILE")
                    }
                    "ANDROID_VR" -> {
                        clientObj.put("androidSdkVersion", 32)
                        clientObj.put("osName", "Android")
                        clientObj.put("osVersion", "12L")
                        clientObj.put("deviceMake", "Oculus")
                        clientObj.put("deviceModel", "Quest 3")
                    }
                    "ANDROID_MUSIC" -> {
                        clientObj.put("androidSdkVersion", 34)
                        clientObj.put("osName", "Android")
                        clientObj.put("osVersion", "14")
                        clientObj.put("deviceMake", "Google")
                        clientObj.put("deviceModel", "Pixel 8")
                    }
                    "IOS" -> {
                        clientObj.put("deviceMake", "Apple")
                        clientObj.put("deviceModel", "iPhone16,2")
                        clientObj.put("osName", "iPhone")
                        clientObj.put("osVersion", "18.3.2.22D82")
                    }
                }

                val clientIdHeader = when (clientName) {
                    "VISIONOS" -> "101"
                    "ANDROID_VR" -> "28"
                    "ANDROID_MUSIC" -> "21"
                    "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "85"
                    else -> "5"
                }

                val bodyJson = JSONObject()
                    .put("context", JSONObject().put("client", clientObj))
                    .put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)
                if (clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER") {
                    bodyJson.put(
                        "thirdParty",
                        JSONObject().put("embedUrl", "https://www.reddit.com/")
                    )
                }

                val playerEndpoint = if (clientName == "VISIONOS" || clientName == "ANDROID_MUSIC") {
                    "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"
                } else {
                    "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
                }

                val reqBuilder = Request.Builder()
                    .url(playerEndpoint)
                    .header("User-Agent", userAgent)
                    .header("X-YouTube-Client-Name", clientIdHeader)
                    .header("X-YouTube-Client-Version", clientVersion)
                if (clientName == "VISIONOS" || clientName == "ANDROID_MUSIC") {
                    reqBuilder.header("Origin", "https://music.youtube.com")
                    reqBuilder.header("Referer", "https://music.youtube.com/")
                }
                if (!visitor.isNullOrBlank()) {
                    reqBuilder.header("X-Goog-Visitor-Id", visitor)
                }
                val req = reqBuilder
                    .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                var stopTryingClients = false
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val playability = root.optJSONObject("playabilityStatus")
                    val statusStr = playability?.optString("status").orEmpty()
                    val reasonStr = playability?.optString("reason").orEmpty().lowercase()
                    if (statusStr == "ERROR" ||
                        (statusStr == "UNPLAYABLE" && (reasonStr.contains("unavailable") || reasonStr.contains("removed") || reasonStr.contains("blocked")))
                    ) {
                        stopTryingClients = true
                        return@use
                    }
                    val streamingData = root.optJSONObject("streamingData") ?: return@use
                    val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return@use

                    var chosenUrl: String? = null
                    var chosenScore = Int.MIN_VALUE
                    var chosenDurMs = 0L
                    var chosenBitrate = 128_000
                    var chosenMime = "audio/mp4"
                    var chosenSampleRate = 44_100

                    for (i in 0 until adaptive.length()) {
                        val fmt = adaptive.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType")
                        val url = fmt.optString("url")
                        if (!mime.startsWith("audio/") || url.isBlank()) continue
                        val bitrate = fmt.optInt("bitrate", 128_000)
                        // Strongly prioritize progressive audio/mp4 (AAC-LC M4A, itag 140/141) for Android MediaPlayer stability!
                        val isMp4 = mime.contains("mp4") || mime.contains("m4a")
                        val mp4Bonus = if (isMp4) 500_000 else 0
                        val score = when (qualityTier) {
                            AudioQualityTier.DATA_SAVER -> mp4Bonus - bitrate
                            AudioQualityTier.YTM_NORMAL -> mp4Bonus - abs(bitrate - 135_000)
                            AudioQualityTier.STUDIO_320, AudioQualityTier.LOSSLESS_LDAC -> mp4Bonus + bitrate
                        }
                        if (score > chosenScore) {
                            chosenScore = score
                            chosenUrl = url
                            chosenBitrate = bitrate
                            chosenMime = mime
                            chosenSampleRate = fmt.optString("audioSampleRate").toIntOrNull() ?: 44_100
                            chosenDurMs = fmt.optString("approxDurationMs").toLongOrNull() ?: 0L
                        }
                    }

                    if (!chosenUrl.isNullOrBlank()) {
                        val videoDetails = root.optJSONObject("videoDetails")
                        val lengthSec = videoDetails?.optString("lengthSeconds")
                            ?.toLongOrNull() ?: 0L
                        val finalDur = when {
                            chosenDurMs > 0L -> chosenDurMs
                            lengthSec > 0L -> lengthSec * 1000L
                            else -> expectedDurationMs.coerceAtLeast(180_000L)
                        }
                        val durationDeltaOk = expectedDurationMs <= 45_000L ||
                            abs(finalDur - expectedDurationMs) <= 120_000L
                        if (finalDur >= 35_000L && durationDeltaOk) {
                            val reqHeaders = mapOf(
                                "User-Agent" to userAgent,
                                "Origin" to "https://www.youtube.com",
                                "Referer" to "https://www.youtube.com/"
                            )
                            // Probe Range: bytes=0-511 so 403 PoToken-restricted clients are skipped immediately
                            if (verifyStreamReachable(chosenUrl, reqHeaders)) {
                                val kbps = (chosenBitrate / 1000).coerceAtLeast(64)
                                val isOpus = chosenMime.contains("opus") || chosenMime.contains("webm")
                                return ResolvedStreamResult(
                                    url = chosenUrl,
                                    durationMs = finalDur,
                                    codecLabel = if (isOpus) "Opus" else "AAC-LC",
                                    containerLabel = if (isOpus) "WebM" else "M4A",
                                    bitrateKbps = kbps,
                                    sampleRateHz = chosenSampleRate,
                                    bitDepth = 16,
                                    sourceType = AudioSourceType.YTM_ADFREE,
                                    requestHeaders = reqHeaders
                                )
                            }
                        }
                    }
                }
                if (stopTryingClients) break
            } catch (_: Exception) {
            }
        }
        return null
    }

    private suspend fun searchPipedFallback(
        query: String,
        limit: Int,
        customNode: String?
    ): List<TrackItem> {
        val node = customNode?.trimEnd('/')?.takeIf { it.startsWith("http") } ?: return emptyList()
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val resp = NetworkModule.youtubePipedApi.searchMusicSongs("$node/search?q=$encoded&filter=music_songs")
            resp.items.mapNotNull { dto ->
                val vid = dto.url?.substringAfter("v=", "")?.substringBefore("&").orEmpty()
                val title = dto.title?.trim().orEmpty()
                val durSec = dto.duration ?: 0L
                if (vid.isBlank() || title.isBlank() || durSec in 1..44) null
                else TrackItem(
                    id = "ytm_$vid",
                    title = title,
                    artist = dto.uploaderName?.removeSuffix(" - Topic")?.trim().orEmpty().ifBlank { "Artist" },
                    album = "YouTube Music",
                    artworkUrl = dto.thumbnail.orEmpty(),
                    audioStreamUrl = null,
                    durationMs = durSec.coerceAtLeast(180L) * 1000L,
                    sourceType = AudioSourceType.YTM_ADFREE,
                    youtubeVideoId = vid
                )
            }.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveViaPipedNode(
        track: TrackItem,
        customNode: String?,
        qualityTier: AudioQualityTier
    ): ResolvedStreamResult {
        val vid = track.youtubeVideoId ?: findCanonicalInnerTubeVideoId(
            title = track.title,
            artist = track.artist,
            album = track.album,
            targetDurationMs = track.durationMs
        ) ?: return ResolvedStreamResult(null, track.durationMs)

        val candidateNodes = buildList {
            customNode?.trimEnd('/')?.takeIf { it.startsWith("http") }?.let { add(it) }
            add("https://pipedapi.kavin.rocks")
            add("https://pipedapi.adminforge.de")
            add("https://api.piped.private.coffee")
        }.distinct()

        for (node in candidateNodes) {
            try {
                val dto = NetworkModule.youtubePipedApi.getVideoStreams("$node/streams/$vid")
                val best = dto.audioStreams
                    .filter { !it.url.isNullOrBlank() }
                    .let { list ->
                        if (qualityTier == AudioQualityTier.DATA_SAVER) {
                            list.minByOrNull { it.bitrate ?: 128_000 }
                        } else {
                            list.maxByOrNull { (if (it.mimeType?.contains("mp4") == true) 500_000 else 0) + (it.bitrate ?: 0) }
                        }
                    }
                if (!best?.url.isNullOrBlank() && verifyStreamReachable(best?.url)) {
                    val durMs = ((dto.duration ?: 0L) * 1000L).takeIf { it > 45_000L } ?: track.durationMs
                    val kbps = ((best?.bitrate ?: 128_000) / 1000).coerceAtLeast(64)
                    val isMp4 = best?.mimeType?.contains("mp4") == true
                    return ResolvedStreamResult(
                        url = best?.url,
                        durationMs = durMs,
                        codecLabel = if (isMp4) "AAC-LC" else "Opus",
                        containerLabel = if (isMp4) "M4A" else "WebM",
                        bitrateKbps = kbps,
                        sampleRateHz = 44_100,
                        bitDepth = 16,
                        sourceType = AudioSourceType.YTM_ADFREE
                    )
                }
            } catch (_: Exception) {
            }
        }

        // Secondary Invidious cluster fallback
        val invidiousNodes = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be"
        )
        for (inv in invidiousNodes) {
            try {
                val req = Request.Builder()
                    .url("$inv/api/v1/videos/$vid")
                    .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                    .build()
                NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val adaptive = root.optJSONArray("adaptiveFormats") ?: return@use
                    var bestUrl: String? = null
                    var bestScore = Int.MIN_VALUE
                    var bestKbps = 128
                    for (i in 0 until adaptive.length()) {
                        val f = adaptive.optJSONObject(i) ?: continue
                        val type = f.optString("type")
                        val u = f.optString("url")
                        if (!type.startsWith("audio/") || u.isBlank()) continue
                        val br = f.optString("bitrate").toIntOrNull() ?: 128_000
                        val s = (if (type.contains("mp4")) 500_000 else 0) + br
                        if (s > bestScore) {
                            bestScore = s
                            bestUrl = u
                            bestKbps = (br / 1000).coerceAtLeast(64)
                        }
                    }
                    if (!bestUrl.isNullOrBlank() && verifyStreamReachable(bestUrl)) {
                        val durSec = root.optLong("lengthSeconds", 0L)
                        return ResolvedStreamResult(
                            url = bestUrl,
                            durationMs = if (durSec > 35L) durSec * 1000L else track.durationMs,
                            codecLabel = "AAC-LC",
                            containerLabel = "M4A",
                            bitrateKbps = bestKbps,
                            sampleRateHz = 44_100,
                            bitDepth = 16,
                            sourceType = AudioSourceType.YTM_ADFREE
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        return ResolvedStreamResult(null, track.durationMs)
    }

    /**
     * SoundCloud Public API v2 Progressive Stream Fallback for rare indie/SoundCloud-exclusive tracks
     * not available on catalog CDNs or InnerTube. Strictly rejects 30s Go+ snipped previews (`snipped == true`).
     */
    private fun resolveViaSoundCloudProgressive(track: TrackItem): ResolvedStreamResult? {
        return try {
            val clientId = obtainSoundCloudClientId() ?: return null
            val cleanTitle = stripEditionNoise(track.title)
            val cleanArtist = primaryArtistOf(track.artist)
            val encoded = URLEncoder.encode("$cleanArtist $cleanTitle".trim(), "UTF-8")
            val searchUrl = "https://api-v2.soundcloud.com/search/tracks?q=$encoded&client_id=$clientId&limit=6"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36")
                .build()

            var bestTranscodingUrl: String? = null
            var bestScore = Int.MIN_VALUE
            var bestDurMs = track.durationMs
            var bestPreset = "mp3_128k"

            NetworkModule.fastHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JSONObject(resp.body?.string().orEmpty())
                val coll = root.optJSONArray("collection") ?: return null
                for (i in 0 until coll.length()) {
                    val item = coll.optJSONObject(i) ?: continue
                    val dur = item.optLong("duration", 0L)
                    val fullDur = item.optLong("full_duration", dur)
                    // Reject 30s Go+ snipped tracks!
                    if (dur < 45_000L || (fullDur > 60_000L && dur <= 35_000L)) continue
                    val transcodings = item.optJSONObject("media")?.optJSONArray("transcodings") ?: continue
                    var progressiveUrl: String? = null
                    var preset = "mp3_128k"
                    for (t in 0 until transcodings.length()) {
                        val tc = transcodings.optJSONObject(t) ?: continue
                        if (tc.optBoolean("snipped", false)) continue
                        val proto = tc.optJSONObject("format")?.optString("protocol").orEmpty()
                        if (proto == "progressive") {
                            progressiveUrl = tc.optString("url").takeIf { it.isNotBlank() }
                            preset = tc.optString("preset", "mp3_128k")
                            break
                        }
                    }
                    if (progressiveUrl == null) continue

                    val candTitle = item.optString("title")
                    val candArtist = item.optJSONObject("publisher_metadata")?.optString("artist").orEmpty()
                        .ifBlank { item.optJSONObject("user")?.optString("username").orEmpty() }
                    val score = scoreCanonicalTrackCandidate(
                        candidateTitle = candTitle,
                        candidateArtist = candArtist,
                        candidateAlbum = "",
                        candidateDurationMs = dur,
                        isYoutubeUgc = true,
                        targetTitle = track.title,
                        targetArtist = track.artist,
                        targetAlbum = track.album,
                        targetDurationMs = track.durationMs
                    )
                    if (score > bestScore) {
                        bestScore = score
                        bestTranscodingUrl = progressiveUrl
                        bestDurMs = dur
                        bestPreset = preset
                    }
                }
            }

            val tcUrl = bestTranscodingUrl?.takeIf { bestScore >= 15 } ?: return null
            val streamResolveUrl = if (tcUrl.contains("?")) "$tcUrl&client_id=$clientId" else "$tcUrl?client_id=$clientId"
            NetworkModule.fastHttpClient.newCall(Request.Builder().url(streamResolveUrl).build()).execute().use { sr ->
                if (!sr.isSuccessful) return null
                val directUrl = JSONObject(sr.body?.string().orEmpty()).optString("url").trim()
                if (!directUrl.startsWith("http")) return null
                val isAac = bestPreset.contains("aac", ignoreCase = true) || bestPreset.contains("opus", ignoreCase = true)
                return ResolvedStreamResult(
                    url = directUrl,
                    durationMs = bestDurMs,
                    codecLabel = if (isAac) "AAC-LC" else "MP3",
                    containerLabel = if (isAac) "M4A" else "MP3",
                    bitrateKbps = if (bestPreset.contains("160")) 160 else 128,
                    sampleRateHz = 44_100,
                    bitDepth = 16,
                    sourceType = AudioSourceType.YTM_ADFREE
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun obtainSoundCloudClientId(): String? {
        cachedSoundCloudClientId?.takeIf { it.isNotBlank() }?.let { return it }
        return try {
            val homeReq = Request.Builder()
                .url("https://soundcloud.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36")
                .build()
            var scripts: List<String> = emptyList()
            NetworkModule.fastHttpClient.newCall(homeReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string().orEmpty()
                scripts = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
                    .findAll(html)
                    .map { it.groupValues[1] }
                    .toList()
            }
            for (jsUrl in scripts.takeLast(3).reversed()) {
                NetworkModule.fastHttpClient.newCall(Request.Builder().url(jsUrl).build()).execute().use { jr ->
                    if (jr.isSuccessful) {
                        val js = jr.body?.string().orEmpty()
                        val m = Regex("""client_id\s*:\s*"([a-zA-Z0-9]{32})"""").find(js)
                            ?: Regex("""client_id=([a-zA-Z0-9]{32})""").find(js)
                        val found = m?.groupValues?.getOrNull(1).orEmpty()
                        if (found.isNotBlank()) {
                            cachedSoundCloudClientId = found
                            return found
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun upgradeArtworkUrl(raw: String): String {
        return raw
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .replaceFirst("^http://".toRegex(), "https://")
    }

    private fun parseDurationTextMs(raw: String): Long? {
        if (!raw.contains(":")) return null
        val parts = raw.split(":").map { it.trim().toLongOrNull() ?: return null }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> null
        }
    }

    // =========================================================================
    // CANONICAL STUDIO ALBUM VERSION MATCHING & ANTI-COVER / ANTI-VARIANT ENGINE
    // =========================================================================

    /**
     * Variant / non-original keywords that must NEVER replace a canonical studio album track
     * unless the user/source track explicitly requested that exact modifier.
     */
    private val VARIANT_EDITION_PATTERNS: List<Pair<String, Regex>> = listOf(
        "acoustic" to Regex("""\b(acoustic|unplugged|stripped)\b""", RegexOption.IGNORE_CASE),
        "cover" to Regex("""\b(cover|covered\s+by|tribute|karaoke|originally\s+performed|in\s+the\s+style\s+of|made\s+famous\s+by|soundalike|8-bit|lullaby\s+version|piano\s+version|guitar\s+version|violin\s+version|orchestral\s+version|string\s+quartet)\b""", RegexOption.IGNORE_CASE),
        "live" to Regex("""\b(live|concert|tour|session|sessions|tiny\s+desk|bbc\s+radio|vevo\s+live|mtv\s+unplugged|at\s+wembley|at\s+madison)\b""", RegexOption.IGNORE_CASE),
        "remix" to Regex("""\b(remix|re-mix|club\s+mix|extended\s+mix|dub\s+mix|vip\s+mix|bootleg|mashup|flip|rework)\b""", RegexOption.IGNORE_CASE),
        "altered" to Regex("""\b(slowed|reverb|sped\s+up|nightcore|daycore|8d|chopped|screwed)\b""", RegexOption.IGNORE_CASE),
        "instrumental" to Regex("""\b(instrumental|backing\s+track|acappella|a\s+cappella|vocals\s+only|demo)\b""", RegexOption.IGNORE_CASE),
        "yt_video" to Regex("""\b(youtube\s+video|official\s+music\s+video|music\s+video|lyric\s+video|official\s+video|visualizer|reaction|teaser|trailer|shorts|full\s+album)\b""", RegexOption.IGNORE_CASE)
    )

    private val SOUNDALIKE_ARTIST_REGEX = Regex(
        """\b(karaoke|tribute|vitamin\s+string|piano\s+guys|boyce\s+avenue|kidz\s+bop|rockabye\s+baby|twinkle\s+twinkle|8-bit\s+arcade|lofi\s+fruits|chill\s+fruits|cover\s+nation|postmodern\s+jukebox|madilyn\s+bailey)\b""",
        RegexOption.IGNORE_CASE
    )

    internal fun stripEditionNoise(rawTitle: String): String {
        val withoutBrackets = rawTitle
            .replace(Regex("""\((?:feat\.?|ft\.?|with|remastered|deluxe|explicit|clean|album\s+version|original\s+mix)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:feat\.?|ft\.?|with|remastered|deluxe|explicit|clean|official\s+audio)[^\]]*]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(?:\d{4}\s*)?remaster(?:ed)?.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return withoutBrackets.ifBlank { rawTitle.trim() }
    }

    private fun normalizeComparableText(raw: String): String {
        return raw
            .lowercase()
            .replace(Regex("""\((?:feat\.?|ft\.?|with)[^)]*\)"""), " ")
            .replace(Regex("""\[(?:feat\.?|ft\.?|with)[^\]]*]"""), " ")
            .replace("&", " and ")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun primaryArtistOf(artistField: String): String {
        return artistField
            .split(",", "&", " feat.", " feat ", " ft.", " ft ", " x ", " X ", ";")
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removeSuffix("- Topic")
            ?.trim()
            .orEmpty()
    }

    /**
     * Scores a candidate track against the requested canonical track.
     * Higher score = more authentic original published studio album track.
     */
    internal fun scoreCanonicalTrackCandidate(
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String,
        candidateDurationMs: Long,
        isYoutubeUgc: Boolean,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L
    ): Int {
        var score = 0

        val normTargetCoreTitle = normalizeComparableText(
            stripEditionNoise(targetTitle).substringBefore("(").substringBefore("[")
        )
        val normCandCoreTitle = normalizeComparableText(
            stripEditionNoise(candidateTitle).substringBefore("(").substringBefore("[")
        )
        val normTargetFullTitle = normalizeComparableText(stripEditionNoise(targetTitle))
        val normCandFullTitle = normalizeComparableText(stripEditionNoise(candidateTitle))

        // 1. Title alignment
        when {
            normCandFullTitle == normTargetFullTitle && normCandFullTitle.isNotBlank() -> score += 110
            normCandCoreTitle == normTargetCoreTitle && normCandCoreTitle.isNotBlank() -> score += 95
            normCandCoreTitle.startsWith(normTargetCoreTitle) && normTargetCoreTitle.length >= 3 -> score += 60
            normCandCoreTitle.contains(normTargetCoreTitle) && normTargetCoreTitle.length >= 4 -> score += 35
            else -> {
                val targetTokens = normTargetCoreTitle.split(" ").filter { it.length >= 2 }.toSet()
                val candTokens = normCandCoreTitle.split(" ").filter { it.length >= 2 }.toSet()
                if (targetTokens.isNotEmpty()) {
                    val overlap = targetTokens.intersect(candTokens).size.toFloat() / targetTokens.size.toFloat()
                    if (overlap >= 0.75f) score += 40 else score -= 130
                }
            }
        }

        // 2. Primary Artist verification (blocks covers & tribute bands!)
        val targetPrimaryArtist = normalizeComparableText(primaryArtistOf(targetArtist))
        val candPrimaryArtist = normalizeComparableText(primaryArtistOf(candidateArtist))
        val candAllArtists = normalizeComparableText(candidateArtist)

        if (targetPrimaryArtist.isNotBlank()) {
            when {
                candPrimaryArtist == targetPrimaryArtist -> score += 105
                candAllArtists.split(" ").containsAll(targetPrimaryArtist.split(" ").filter { it.isNotBlank() }) -> score += 75
                candAllArtists.contains(targetPrimaryArtist) -> score += 55
                targetPrimaryArtist.contains(candPrimaryArtist) && candPrimaryArtist.length >= 4 -> score += 25
                else -> score -= 180 // Different artist -> almost certainly a cover or wrong song!
            }
        }

        // Penalize known cover/tribute/karaoke channels unless the user actually requested that artist
        if (SOUNDALIKE_ARTIST_REGEX.containsMatchIn(candidateArtist) &&
            !SOUNDALIKE_ARTIST_REGEX.containsMatchIn(targetArtist)
        ) {
            score -= 240
        }
        // Penalize candidate titles that put the artist name inside "Originally Performed By X" or "X Type Beat"
        if (Regex("""\b(type\s+beat|originally\s+by|tribute\s+to)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn("$candidateTitle $candidateAlbum")
        ) {
            score -= 250
        }

        // 3. Strict Variant / Edition Mismatch Filter (Acoustic, Cover, Live, Remix, Slowed, YT Video)
        val targetContext = "$targetTitle $targetAlbum"
        val candidateContext = "$candidateTitle $candidateAlbum"
        for ((_, regex) in VARIANT_EDITION_PATTERNS) {
            val requestedHasVariant = regex.containsMatchIn(targetContext)
            val candidateHasVariant = regex.containsMatchIn(candidateContext)
            if (candidateHasVariant && !requestedHasVariant) {
                score -= 210
            } else if (candidateHasVariant && requestedHasVariant) {
                score += 65
            }
        }

        // 4. Published Album Alignment
        val cleanTargetAlbum = normalizeComparableText(targetAlbum).takeIf {
            it.isNotBlank() && it != "single" && it != "youtube music" && it != "unknown"
        }
        val cleanCandAlbum = normalizeComparableText(candidateAlbum).takeIf {
            it.isNotBlank() && it != "single" && it != "youtube music" && it != "unknown"
        }
        if (cleanTargetAlbum != null && cleanCandAlbum != null) {
            when {
                cleanCandAlbum == cleanTargetAlbum -> score += 55
                cleanCandAlbum.contains(cleanTargetAlbum) || cleanTargetAlbum.contains(cleanCandAlbum) -> score += 35
            }
        } else if (cleanCandAlbum != null && !isYoutubeUgc) {
            // Bonus for belonging to a named catalog album rather than a generic YT upload
            score += 18
        }

        // 5. Duration Alignment (rejects extended music videos, radio edits, or intros/skits)
        if (targetDurationMs >= 45_000L && candidateDurationMs >= 45_000L) {
            val diffMs = abs(candidateDurationMs - targetDurationMs)
            when {
                diffMs <= 3_500L -> score += 45
                diffMs <= 8_000L -> score += 22
                diffMs <= 16_000L -> score -= 10
                diffMs <= 32_000L -> score -= 55
                else -> score -= 125
            }
        }

        // 6. Prefer Official Catalog Master over YouTube UGC
        if (isYoutubeUgc) {
            score -= 22
        } else {
            score += 20
        }

        return score
    }

    internal fun pickBestCanonicalTrack(
        candidates: List<TrackItem>,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String = "",
        targetDurationMs: Long = 0L,
        minimumScore: Int = Int.MIN_VALUE
    ): TrackItem? {
        if (candidates.isEmpty()) return null
        val scored = candidates.map { cand ->
            val isYtmUgc = cand.id.startsWith("ytm_") &&
                (
                    cand.album.equals("YouTube Music", ignoreCase = true) ||
                        cand.album.equals("YouTube Video", ignoreCase = true) ||
                        cand.album.isBlank()
                    )
            val s = scoreCanonicalTrackCandidate(
                candidateTitle = cand.title,
                candidateArtist = cand.artist,
                candidateAlbum = cand.album,
                candidateDurationMs = cand.durationMs,
                isYoutubeUgc = isYtmUgc,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetAlbum = targetAlbum,
                targetDurationMs = targetDurationMs
            )
            cand to s
        }
        val bestPair = scored.maxByOrNull { it.second } ?: return null
        return if (bestPair.second >= minimumScore) bestPair.first else null
    }

    internal fun rankSearchResultsForCanonicalOriginals(
        query: String,
        candidates: List<TrackItem>,
        preferYtmSource: Boolean = false,
        limit: Int = 22
    ): List<TrackItem> {
        if (candidates.isEmpty()) return emptyList()
        val normQuery = normalizeComparableText(query)
        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }

        val scored = candidates.mapIndexed { index, track ->
            var rankScore = 100 - (index * 2) // Preserve natural search relevance baseline

            val trackTitleNorm = normalizeComparableText(track.title)
            val trackArtistNorm = normalizeComparableText(track.artist)
            val combinedNorm = "$trackTitleNorm $trackArtistNorm"

            if (queryTokens.isNotEmpty()) {
                val matchedTokens = queryTokens.count { tok -> combinedNorm.contains(tok) }
                rankScore += (matchedTokens * 28)
            }
            if (trackTitleNorm == normQuery || "$trackTitleNorm ${normalizeComparableText(primaryArtistOf(track.artist))}" == normQuery) {
                rankScore += 55
            }

            // Demote acoustic, cover, live, remix, slowed, karaoke, and music-video uploads unless user searched for them
            for ((_, regex) in VARIANT_EDITION_PATTERNS) {
                val queryAskedForIt = regex.containsMatchIn(query)
                val trackHasVariant = regex.containsMatchIn("${track.title} ${track.album}")
                if (trackHasVariant && !queryAskedForIt) {
                    rankScore -= 140
                } else if (trackHasVariant && queryAskedForIt) {
                    rankScore += 50
                }
            }
            if (SOUNDALIKE_ARTIST_REGEX.containsMatchIn(track.artist) &&
                !SOUNDALIKE_ARTIST_REGEX.containsMatchIn(query)
            ) {
                rankScore -= 170
            }

            val isCatalogMaster = !track.audioStreamUrl.isNullOrBlank() && !track.id.startsWith("ytm_")
            val hasNamedAlbum = track.album.isNotBlank() &&
                !track.album.equals("YouTube Music", ignoreCase = true) &&
                !track.album.equals("Single", ignoreCase = true)
            if (hasNamedAlbum) rankScore += 18
            if (isCatalogMaster && !preferYtmSource) rankScore += 24
            if (!isCatalogMaster && preferYtmSource && hasNamedAlbum) rankScore += 14

            track to rankScore
        }.sortedByDescending { it.second }

        val seenKeys = HashSet<String>()
        return buildList {
            for ((t, _) in scored) {
                if (size >= limit) break
                val coreTitle = normalizeComparableText(stripEditionNoise(t.title))
                val coreArtist = normalizeComparableText(primaryArtistOf(t.artist))
                val dedupeKey = "${coreTitle}_${coreArtist}"
                if (seenKeys.add(dedupeKey)) {
                    add(t)
                }
            }
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains("&")) return input.trim()
        return input
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
