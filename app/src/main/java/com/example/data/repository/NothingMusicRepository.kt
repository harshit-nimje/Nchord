package com.example.data.repository

import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.InAppDownloadManager
import com.example.data.local.MusicDao
import com.example.data.local.NothingPreferencesStore
import com.example.data.local.PinnedLibraryItemEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.RecentCollectionEntity
import com.example.data.local.SavedTrackEntity
import com.example.data.local.ScrobbleEntity
import com.example.data.local.UserPlaylistEntity
import com.example.data.model.AudioQualityTier
import com.example.data.model.AudioSourceType
import com.example.data.model.CollectionPageState
import com.example.data.model.LyricLine
import com.example.data.model.RecentGridTile
import com.example.data.model.RecentTileKind
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.model.SpotifyProfileState
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist
import com.example.data.remote.FullStreamEngine
import com.example.data.remote.NetworkModule
import com.example.data.remote.ResolvedStreamResult
import com.example.data.remote.ScrobblerEngine
import com.example.data.remote.SpotifyPkceHelper
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SpotifySearchPayload(
    val tracks: List<TrackItem>,
    val playlists: List<SuggestedPlaylist>,
    val usedLiveSpotifyApi: Boolean
)

enum class PinToggleOutcome {
    PINNED,
    UNPINNED,
    LIMIT_REACHED
}

class NothingMusicRepository(
    private val musicDao: MusicDao,
    private val prefsStore: NothingPreferencesStore,
    private val downloadManager: InAppDownloadManager
) {
    private val lrcLibApi = NetworkModule.lrcLibApi
    private val spotifyAccountsApi = NetworkModule.spotifyAccountsApi
    private val spotifyWebApi = NetworkModule.spotifyWebApi
    private val httpClient = NetworkModule.okHttpClient
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val lyricsCache = LruCache<String, List<LyricLine>>(48)

    val downloadProgressFlow: StateFlow<Map<String, Float>> = downloadManager.downloadProgressFlow

    val downloadedTracksFlow: Flow<List<TrackItem>> = musicDao.observeDownloadedTracks().map { rows ->
        rows.map { it.toDomain() }
    }

    val downloadedTrackIdsFlow: Flow<Set<String>> = musicDao.observeDownloadedTrackIds().map { it.toSet() }

    val pinnedItemKeysFlow: Flow<List<String>> = musicDao.observePinnedItems().map { rows ->
        rows.map { it.itemKey }
    }

    val recentCollectionsFlow: Flow<List<RecentGridTile>> = combine(
        musicDao.observeRecentCollections(limit = 8),
        musicDao.observePinnedItems()
    ) { rows, pinned ->
        val pinnedKeys = pinned.map { it.itemKey }.toSet()
        rows.map { row ->
            val kind = runCatching { RecentTileKind.valueOf(row.kindName) }
                .getOrDefault(RecentTileKind.CURATED_PLAYLIST)
            RecentGridTile(
                id = row.id,
                title = row.title,
                subtitle = row.subtitle,
                artworkUrl = row.artworkUrl,
                kind = kind,
                referenceId = row.referenceId,
                isPinned = row.id in pinnedKeys
            )
        }
    }

    val savedTracksFlow: Flow<List<TrackItem>> = musicDao.observeSavedTracks().map { list ->
        list.map { it.toDomain() }
    }

    val savedTrackIdsFlow: Flow<Set<String>> = musicDao.observeSavedTrackIds().map { it.toSet() }

    val recentScrobblesFlow: Flow<List<TrackItem>> = musicDao.observeRecentScrobbles().map { list ->
        list.map { it.toDomain() }
    }

    val allScrobblesFlow: Flow<List<ScrobbleEntity>> = musicDao.observeAllScrobbles()

    val pendingScrobblesCountFlow: Flow<Int> = musicDao.observePendingScrobbleCount()

    val scrobblerCredsFlow = prefsStore.scrobblerCredsFlow

    val totalScrobblesFlow: Flow<Int> = musicDao.observeTotalScrobbleCount()

    val totalStreamedMsFlow: Flow<Long> = musicDao.observeTotalStreamedMs()

    val userPlaylistsFlow: Flow<List<UserPlaylist>> = combine(
        musicDao.observeUserPlaylists(),
        musicDao.observeAllPlaylistTracks(),
        musicDao.observePinnedItems()
    ) { playlists, allTracks, pinnedRows ->
        val grouped = allTracks.groupBy { it.playlistId }
        val pinMap = pinnedRows.associate { it.itemKey to it.pinnedAt }
        playlists.map { entity ->
            val pTracks = grouped[entity.id].orEmpty()
            val mosaic = pTracks.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }
                .distinct()
                .take(4)
            val pinKey = "upl_${entity.id}"
            val pinnedAt = pinMap[pinKey] ?: 0L
            UserPlaylist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                coverArtworkUrl = entity.coverArtworkUrl.ifBlank { mosaic.firstOrNull().orEmpty() },
                mosaicUrls = mosaic,
                trackCount = pTracks.size,
                spotifyPlaylistId = entity.spotifyPlaylistId,
                youtubePlaylistId = entity.youtubePlaylistId,
                updatedAt = entity.updatedAt,
                isPinned = pinnedAt > 0L,
                pinnedAt = pinnedAt
            )
        }.sortedWith(
            compareByDescending<UserPlaylist> { it.isPinned }
                .thenBy { if (it.isPinned) it.pinnedAt else Long.MAX_VALUE }
                .thenByDescending { it.updatedAt }
        )
    }

    fun observeUserPlaylistTracks(playlistId: String): Flow<List<TrackItem>> =
        musicDao.observeTracksForPlaylist(playlistId).map { rows ->
            rows.map { it.toDomain() }
        }

    val glyphSettingsFlow = prefsStore.glyphSettingsFlow

    val spotifyAccessTokenFlow = prefsStore.spotifyAccessTokenFlow

    suspend fun saveUserSpotifyClientId(clientId: String) {
        prefsStore.setUserSpotifyClientId(clientId.trim())
    }

    suspend fun prepareSpotifyAuthUri(overrideClientId: String? = null): Uri? {
        val clientId = (overrideClientId ?: prefsStore.getUserSpotifyClientId()).trim()
        if (clientId.length < 10) return null
        prefsStore.setUserSpotifyClientId(clientId)
        val verifier = SpotifyPkceHelper.generateCodeVerifier()
        prefsStore.savePkceVerifier(verifier)
        val challenge = SpotifyPkceHelper.generateCodeChallenge(verifier)
        return SpotifyPkceHelper.buildAuthorizationUri(clientId, challenge)
    }

    suspend fun handleSpotifyOAuthCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val clientId = prefsStore.getUserSpotifyClientId()
            if (clientId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Spotify Client ID not configured"))
            }
            val verifier = prefsStore.getPkceVerifier()
                ?: return@withContext Result.failure(IllegalStateException("Missing PKCE verifier"))
            val tokenDto = spotifyAccountsApi.exchangeAuthorizationCode(
                clientId = clientId,
                code = code,
                redirectUri = NetworkModule.SPOTIFY_REDIRECT_URI,
                codeVerifier = verifier
            )
            prefsStore.saveSpotifyTokens(
                accessToken = tokenDto.accessToken,
                refreshToken = tokenDto.refreshToken,
                expiresInSec = tokenDto.expiresIn
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("NothingMusicRepo", "Spotify PKCE token exchange failed", e)
            Result.failure(e)
        }
    }

    private suspend fun getValidSpotifyBearer(): String? {
        val (access, refresh, expiresAt) = prefsStore.getSpotifyTokenTuple()
        if (access.isNullOrBlank()) return null
        if (System.currentTimeMillis() < expiresAt) {
            return "Bearer $access"
        }
        val clientId = prefsStore.getUserSpotifyClientId()
        if (!refresh.isNullOrBlank() && clientId.isNotBlank()) {
            return try {
                val refreshed = spotifyAccountsApi.refreshAccessToken(
                    clientId = clientId,
                    refreshToken = refresh
                )
                prefsStore.saveSpotifyTokens(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    expiresInSec = refreshed.expiresIn
                )
                "Bearer ${refreshed.accessToken}"
            } catch (e: Exception) {
                Log.w("NothingMusicRepo", "Spotify token refresh failed", e)
                null
            }
        }
        return null
    }

    suspend fun disconnectSpotify() {
        prefsStore.clearSpotifySession()
    }

    // =========================================================================
    // SPOTIFY DASHBOARD, TWO-WAY SYNC (LIKES, PLAYLISTS, STATS), & SEARCH
    // =========================================================================

    private fun spotifyGetJson(url: String, bearer: String): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", bearer)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful || resp.code == 204) return null
                val body = resp.body?.string().orEmpty()
                if (body.startsWith("{")) JSONObject(body) else null
            }
        }.getOrNull()
    }

    suspend fun fetchSpotifyDashboard(): Result<Triple<SpotifyProfileState, List<SpotifyPlaylistSummary>, List<TrackItem>>> =
        withContext(Dispatchers.IO) {
            try {
                val bearer = getValidSpotifyBearer()
                    ?: return@withContext Result.failure(IllegalStateException("Spotify not connected"))

                val meJson = spotifyGetJson("https://api.spotify.com/v1/me", bearer)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to read Spotify profile"))

                val playlistsJson = spotifyGetJson("https://api.spotify.com/v1/me/playlists?limit=20", bearer)
                val topTracksJson = spotifyGetJson("https://api.spotify.com/v1/me/top/tracks?limit=10&time_range=short_term", bearer)
                val topArtistsJson = spotifyGetJson("https://api.spotify.com/v1/me/top/artists?limit=8&time_range=short_term", bearer)
                val savedTracksJson = spotifyGetJson("https://api.spotify.com/v1/me/tracks?limit=20", bearer)
                val recentJson = spotifyGetJson("https://api.spotify.com/v1/me/player/recently-played?limit=15", bearer)
                val playbackJson = spotifyGetJson("https://api.spotify.com/v1/me/player", bearer)

                val userId = meJson.optString("id")
                val avatarUrl = meJson.optJSONArray("images")?.optJSONObject(0)?.optString("url")
                val topArtists = buildList {
                    val items = topArtistsJson?.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val name = items.optJSONObject(i)?.optString("name").orEmpty()
                            if (name.isNotBlank()) add(name)
                        }
                    }
                }

                val playlists = buildList {
                    val items = playlistsJson?.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val p = items.optJSONObject(i) ?: continue
                            val id = p.optString("id")
                            val name = p.optString("name")
                            if (id.isBlank() || name.isBlank()) continue
                            val tracksObj = p.optJSONObject("tracks") ?: p.optJSONObject("items")
                            val totalCount = tracksObj?.optInt("total", 0) ?: 0
                            add(
                                SpotifyPlaylistSummary(
                                    id = id,
                                    name = name,
                                    description = p.optString("description")
                                        .replace(Regex("<.*?>"), "")
                                        .trim(),
                                    imageUrl = p.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                                    trackCount = totalCount,
                                    ownerName = p.optJSONObject("owner")?.optString("display_name")
                                        ?.ifBlank { "Spotify" } ?: "Spotify"
                                )
                            )
                        }
                    }
                }

                val savedTotalCount = savedTracksJson?.optInt("total", 0) ?: 0
                val savedTracksList = buildList {
                    val items = savedTracksJson?.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val trackObj = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                            parseSpotifyTrackJson(trackObj)?.let { add(it) }
                        }
                    }
                }

                // Sync Spotify Liked Songs into local Room Vault (non-destructive ignore so local timestamps stay intact)
                if (savedTracksList.isNotEmpty()) {
                    runCatching {
                        musicDao.insertSavedTracksIgnore(savedTracksList.map { it.toSavedEntity() })
                    }
                }

                // Sync Spotify Recently Played into local Scrobble Stats (deduplicated)
                val recentItems = recentJson?.optJSONArray("items")
                if (recentItems != null) {
                    for (i in 0 until recentItems.length()) {
                        val entry = recentItems.optJSONObject(i) ?: continue
                        val tObj = entry.optJSONObject("track") ?: continue
                        val domain = parseSpotifyTrackJson(tObj) ?: continue
                        if (musicDao.countScrobblesForTrack(domain.id) == 0) {
                            musicDao.insertScrobble(
                                ScrobbleEntity(
                                    trackId = domain.id,
                                    title = domain.title,
                                    artist = domain.artist,
                                    album = domain.album,
                                    artworkUrl = domain.artworkUrl,
                                    audioStreamUrl = null,
                                    durationMs = domain.durationMs.coerceAtLeast(60_000L),
                                    sourceTypeName = AudioSourceType.SPOTIFY.name,
                                    spotifyTrackId = domain.spotifyTrackId
                                )
                            )
                        }
                    }
                }

                val topTracks = buildList {
                    val items = topTracksJson?.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val tObj = items.optJSONObject(i) ?: continue
                            parseSpotifyTrackJson(tObj)?.let { add(it) }
                        }
                    }
                    if (isEmpty()) {
                        addAll(savedTracksList)
                    }
                }.distinctBy { it.id }

                val nowPlayingItem = playbackJson?.optJSONObject("item")
                val nowPlayingArtists = buildList {
                    val arr = nowPlayingItem?.optJSONArray("artists")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val n = arr.optJSONObject(i)?.optString("name").orEmpty()
                            if (n.isNotBlank()) add(n)
                        }
                    }
                }.joinToString(", ").takeIf { it.isNotBlank() }

                val profile = SpotifyProfileState(
                    isConnected = true,
                    userId = userId,
                    displayName = meJson.optString("display_name").ifBlank { userId.ifBlank { "Spotify User" } },
                    email = meJson.optString("email"),
                    avatarUrl = avatarUrl,
                    productTier = meJson.optString("product", "free").uppercase(),
                    followers = meJson.optJSONObject("followers")?.optInt("total", 0) ?: 0,
                    savedTracksCount = savedTotalCount,
                    syncedPlaylistsCount = playlists.size,
                    topArtists = topArtists,
                    activeDeviceName = playbackJson?.optJSONObject("device")?.optString("name")?.takeIf { it.isNotBlank() },
                    currentlyPlayingTitle = nowPlayingItem?.optString("name")?.takeIf { it.isNotBlank() },
                    currentlyPlayingArtist = nowPlayingArtists
                )

                Result.success(Triple(profile, playlists, topTracks))
            } catch (e: Exception) {
                Log.e("NothingMusicRepo", "Failed fetching Spotify dashboard", e)
                Result.failure(e)
            }
        }

    suspend fun fetchSpotifyPlaylistTracks(playlistId: String): Result<List<TrackItem>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanId = playlistId.trim().substringAfterLast(":").substringBefore("?")
                if (cleanId.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Blank Spotify playlist ID"))
                }

                val bearer = getValidSpotifyBearer()
                if (bearer != null) {
                    val candidateEndpoints = listOf(
                        "https://api.spotify.com/v1/playlists/$cleanId/items?limit=50",
                        "https://api.spotify.com/v1/playlists/$cleanId/tracks?limit=50",
                        "https://api.spotify.com/v1/playlists/$cleanId"
                    )
                    for (endpoint in candidateEndpoints) {
                        val root = spotifyGetJson(endpoint, bearer) ?: continue
                        val items = root.optJSONArray("items")
                            ?: root.optJSONObject("tracks")?.optJSONArray("items")
                            ?: root.optJSONObject("items")?.optJSONArray("items")
                            ?: JSONArray()
                        val parsed = buildList {
                            for (i in 0 until items.length()) {
                                val row = items.optJSONObject(i) ?: continue
                                val trackObj = row.optJSONObject("track")
                                    ?: row.optJSONObject("item")
                                    ?: row.takeIf { it.has("name") && it.has("artists") }
                                    ?: continue
                                parseSpotifyTrackJson(trackObj)?.let { add(it) }
                            }
                        }
                        if (parsed.isNotEmpty()) {
                            return@withContext Result.success(parsed)
                        }
                    }
                }

                // Public / Editorial Spotify Embed fallback (works even in Dev Mode or without OAuth)
                val embedResolved = FullStreamEngine.fetchPublicSpotifyEmbedPlaylist("playlist", cleanId)
                if (embedResolved.third.isNotEmpty()) {
                    return@withContext Result.success(embedResolved.third)
                }

                Result.failure(IllegalStateException("Unable to load Spotify playlist tracks"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Resilient Spotify Search supporting:
     * 1. Pasted `open.spotify.com` Track / Album / Playlist URLs (with or without OAuth!).
     * 2. Connected Spotify `/v1/search?type=track,playlist&limit=10` (compliant with Spotify's 2025/2026 Dev Mode limit=10 rule + null-item resilience).
     * 3. Instant full-stream fallback when Spotify OAuth isn't connected yet so Spotify search never dead-ends!
     */
    suspend fun searchSpotifyCatalogRich(query: String): Result<SpotifySearchPayload> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = query.trim()
                val bearer = getValidSpotifyBearer()

                // Handle pasted Spotify URLs directly
                if (trimmed.contains("spotify.com/")) {
                    if (bearer != null) {
                        when {
                            trimmed.contains("/playlist/") -> {
                                val plId = trimmed.substringAfter("/playlist/").substringBefore("?").substringBefore("/")
                                val plTracks = fetchSpotifyPlaylistTracks(plId).getOrDefault(emptyList())
                                if (plTracks.isNotEmpty()) {
                                    return@withContext Result.success(
                                        SpotifySearchPayload(plTracks, emptyList(), usedLiveSpotifyApi = true)
                                    )
                                }
                            }
                            trimmed.contains("/track/") -> {
                                val trId = trimmed.substringAfter("/track/").substringBefore("?").substringBefore("/")
                                val trJson = spotifyGetJson("https://api.spotify.com/v1/tracks/$trId", bearer)
                                val parsed = trJson?.let { parseSpotifyTrackJson(it) }
                                if (parsed != null) {
                                    return@withContext Result.success(
                                        SpotifySearchPayload(listOf(parsed), emptyList(), usedLiveSpotifyApi = true)
                                    )
                                }
                            }
                        }
                    }
                    val publicResolved = FullStreamEngine.resolvePublicSpotifyLink(trimmed)
                    return@withContext Result.success(
                        SpotifySearchPayload(publicResolved, emptyList(), usedLiveSpotifyApi = bearer != null)
                    )
                }

                // Connected Spotify Web API Search (using limit=10 per Spotify Dev Mode spec)
                if (bearer != null) {
                    val encoded = URLEncoder.encode(trimmed, "UTF-8")
                    val root = spotifyGetJson(
                        "https://api.spotify.com/v1/search?q=$encoded&type=track,playlist&limit=10",
                        bearer
                    )
                    if (root != null) {
                        val tracks = buildList {
                            val items = root.optJSONObject("tracks")?.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val obj = items.optJSONObject(i) ?: continue
                                    parseSpotifyTrackJson(obj)?.let { add(it) }
                                }
                            }
                        }
                        val playlists = buildList {
                            val items = root.optJSONObject("playlists")?.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val p = items.optJSONObject(i) ?: continue
                                    val pid = p.optString("id")
                                    val pname = p.optString("name")
                                    if (pid.isBlank() || pname.isBlank()) continue
                                    val count = (p.optJSONObject("tracks") ?: p.optJSONObject("items"))
                                        ?.optInt("total", 20) ?: 20
                                    val owner = p.optJSONObject("owner")?.optString("display_name")
                                        ?.ifBlank { "Spotify" } ?: "Spotify"
                                    add(
                                        SuggestedPlaylist(
                                            id = "sp_search_$pid",
                                            title = pname,
                                            subtitle = "$owner • $count tracks",
                                            badge = "Spotify",
                                            artworkUrl = p.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty(),
                                            trackCount = count,
                                            externalPlaylistId = pid,
                                            isSpotifyPlaylist = true
                                        )
                                    )
                                }
                            }
                        }
                        if (tracks.isNotEmpty() || playlists.isNotEmpty()) {
                            return@withContext Result.success(
                                SpotifySearchPayload(tracks, playlists, usedLiveSpotifyApi = true)
                            )
                        }
                    }
                }

                // Seamless fallback when Spotify Client ID is not yet authorized
                val settings = prefsStore.glyphSettingsFlow.first()
                val fallbackTracks = FullStreamEngine.searchFullTracks(
                    query = trimmed,
                    limit = 20,
                    qualityTier = settings.audioQualityTier,
                    customPipedNode = settings.customPipedInstance
                )
                val fallbackPlaylists = FullStreamEngine.searchPlaylists(trimmed, limit = 6)
                Result.success(
                    SpotifySearchPayload(
                        tracks = fallbackTracks,
                        playlists = fallbackPlaylists,
                        usedLiveSpotifyApi = false
                    )
                )
            } catch (e: Exception) {
                Log.w("NothingMusicRepo", "Spotify search error, falling back", e)
                val fallback = FullStreamEngine.searchFullTracks(query, limit = 18)
                Result.success(SpotifySearchPayload(fallback, emptyList(), usedLiveSpotifyApi = false))
            }
        }

    private fun parseSpotifyTrackJson(obj: JSONObject): TrackItem? {
        val idVal = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val nameVal = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val artistsArr = obj.optJSONArray("artists")
        var primaryArtistId: String? = null
        val artistNames = buildList {
            if (artistsArr != null) {
                for (i in 0 until artistsArr.length()) {
                    val aObj = artistsArr.optJSONObject(i) ?: continue
                    if (primaryArtistId == null) {
                        primaryArtistId = aObj.optString("id").takeIf { it.isNotBlank() }
                    }
                    val aName = aObj.optString("name")
                    if (aName.isNotBlank()) add(aName)
                }
            }
        }.joinToString(", ").ifBlank { "Artist" }

        val albumObj = obj.optJSONObject("album")
        val albumName = albumObj?.optString("name").orEmpty()
        val artwork = albumObj?.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
        val durationMs = obj.optLong("duration_ms", 210_000L).coerceAtLeast(45_000L)
        val uri = obj.optString("uri").ifBlank { "spotify:track:$idVal" }

        return TrackItem(
            id = "sp_$idVal",
            title = nameVal,
            artist = artistNames,
            album = albumName,
            artworkUrl = artwork,
            audioStreamUrl = null, // Always resolve full-length HQ/Lossless stream via FullStreamEngine
            durationMs = durationMs,
            sourceType = AudioSourceType.SPOTIFY,
            externalSpotifyUri = uri,
            spotifyTrackId = idVal,
            artistId = primaryArtistId,
            streamQualityBadge = "SPOTIFY"
        )
    }

    private suspend fun resolveSpotifyTrackIdIfNeeded(track: TrackItem, bearer: String): String? {
        track.spotifyTrackId?.takeIf { it.isNotBlank() }?.let { return it }
        if (track.id.startsWith("sp_")) return track.id.removePrefix("sp_")
        track.externalSpotifyUri?.takeIf { it.startsWith("spotify:track:") }
            ?.removePrefix("spotify:track:")?.let { return it }

        val q = URLEncoder.encode("${track.title} ${track.artist.substringBefore(",")}", "UTF-8")
        val root = spotifyGetJson("https://api.spotify.com/v1/search?q=$q&type=track&limit=1", bearer)
        return root?.optJSONObject("tracks")
            ?.optJSONArray("items")
            ?.optJSONObject(0)
            ?.optString("id")
            ?.takeIf { it.isNotBlank() }
    }

    // =========================================================================
    // FULL STREAMING CATALOG, CAROUSEL, ALBUMS, ARTISTS & PLAYLISTS
    // =========================================================================

    suspend fun searchStreamingCatalog(query: String, limit: Int = 22): Result<List<TrackItem>> {
        return try {
            val settings = prefsStore.glyphSettingsFlow.first()
            if (query.trim().contains("music.youtube.com/playlist") || query.trim().contains("youtube.com/playlist")) {
                val imported = FullStreamEngine.importYoutubeMusicPlaylist(query.trim())
                if (imported != null) {
                    return Result.success(imported.second)
                }
            }
            if (query.trim().contains("spotify.com/")) {
                val sp = searchSpotifyCatalogRich(query.trim()).getOrNull()
                if (sp != null && sp.tracks.isNotEmpty()) {
                    return Result.success(sp.tracks)
                }
            }
            val tracks = FullStreamEngine.searchFullTracks(
                query = query,
                limit = limit,
                qualityTier = settings.audioQualityTier,
                customPipedNode = settings.customPipedInstance
            )
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e("NothingMusicRepo", "Catalog search failed for $query", e)
            Result.failure(e)
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 8): List<SuggestedPlaylist> {
        return runCatching {
            FullStreamEngine.searchPlaylists(query, limit)
        }.getOrDefault(emptyList())
    }

    suspend fun fetchHomeSuggestedPlaylists(
        customSeeds: List<Pair<String, String>>? = null
    ): List<SuggestedPlaylist> {
        return runCatching {
            if (customSeeds != null && customSeeds.isNotEmpty()) {
                FullStreamEngine.fetchSuggestedPlaylists(customSeeds)
            } else {
                FullStreamEngine.fetchSuggestedPlaylists()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchCarouselPlaylistTracks(playlist: SuggestedPlaylist): List<TrackItem> {
        val settings = prefsStore.glyphSettingsFlow.first()
        if (playlist.isSpotifyPlaylist && !playlist.externalPlaylistId.isNullOrBlank()) {
            val spTracks = fetchSpotifyPlaylistTracks(playlist.externalPlaylistId).getOrDefault(emptyList())
            if (spTracks.isNotEmpty()) return spTracks
        }
        return FullStreamEngine.fetchSuggestedPlaylistTracks(playlist, settings.audioQualityTier)
    }

    suspend fun fetchAlbumPage(seedTrack: TrackItem): CollectionPageState {
        val settings = prefsStore.glyphSettingsFlow.first()
        return FullStreamEngine.fetchAlbumCollection(seedTrack, settings.audioQualityTier)
    }

    suspend fun fetchArtistPage(
        artistName: String,
        artistIdHint: String? = null,
        fallbackArtwork: String = ""
    ): CollectionPageState {
        val settings = prefsStore.glyphSettingsFlow.first()
        return FullStreamEngine.fetchArtistCollection(
            artistName = artistName,
            artistIdHint = artistIdHint,
            fallbackArtwork = fallbackArtwork,
            qualityTier = settings.audioQualityTier
        )
    }

    // =========================================================================
    // USER PLAYLIST CRUD & EXTERNAL PLAYLIST IMPORT (SPOTIFY + YOUTUBE MUSIC)
    // =========================================================================

    suspend fun createUserPlaylist(
        name: String,
        description: String,
        syncToSpotify: Boolean = true
    ): UserPlaylist = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifBlank { "Untitled Mix" }
        val cleanDesc = description.trim()
        val id = "upl_${UUID.randomUUID().toString().take(8)}"
        var spotifyPlId: String? = null

        if (syncToSpotify) {
            val bearer = getValidSpotifyBearer()
            if (bearer != null) {
                runCatching {
                    val payload = JSONObject().apply {
                        put("name", cleanName)
                        put("description", cleanDesc.ifBlank { "Created in Nchord" })
                        put("public", false)
                    }.toString()
                    val req = Request.Builder()
                        .url("https://api.spotify.com/v1/me/playlists")
                        .header("Authorization", bearer)
                        .post(payload.toRequestBody(jsonMedia))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val obj = JSONObject(resp.body?.string().orEmpty())
                            spotifyPlId = obj.optString("id").takeIf { it.isNotBlank() }
                        }
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val entity = UserPlaylistEntity(
            id = id,
            name = cleanName,
            description = cleanDesc,
            spotifyPlaylistId = spotifyPlId,
            createdAt = now,
            updatedAt = now
        )
        musicDao.upsertUserPlaylist(entity)
        UserPlaylist(
            id = id,
            name = cleanName,
            description = cleanDesc,
            spotifyPlaylistId = spotifyPlId,
            updatedAt = now
        )
    }

    suspend fun renameUserPlaylist(playlistId: String, name: String, description: String) =
        withContext(Dispatchers.IO) {
            val cleanName = name.trim().ifBlank { return@withContext }
            musicDao.updateUserPlaylistDetails(playlistId, cleanName, description.trim())

            val existing = musicDao.getUserPlaylistById(playlistId)
            val spPlId = existing?.spotifyPlaylistId
            val bearer = getValidSpotifyBearer()
            if (!spPlId.isNullOrBlank() && bearer != null) {
                runCatching {
                    val payload = JSONObject().apply {
                        put("name", cleanName)
                        if (description.isNotBlank()) put("description", description.trim())
                    }.toString()
                    val req = Request.Builder()
                        .url("https://api.spotify.com/v1/playlists/$spPlId")
                        .header("Authorization", bearer)
                        .put(payload.toRequestBody(jsonMedia))
                        .build()
                    httpClient.newCall(req).execute().close()
                }
            }
        }

    suspend fun deleteUserPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        musicDao.deleteAllTracksForPlaylist(playlistId)
        musicDao.deleteUserPlaylist(playlistId)
        musicDao.deletePinnedItem("upl_$playlistId")
        musicDao.deleteRecentCollectionByRef(playlistId)
    }

    suspend fun togglePinLibraryItem(itemKey: String): PinToggleOutcome = withContext(Dispatchers.IO) {
        if (musicDao.isItemPinned(itemKey)) {
            musicDao.deletePinnedItem(itemKey)
            PinToggleOutcome.UNPINNED
        } else {
            val currentCount = musicDao.countPinnedItems()
            if (currentCount >= MAX_PINNED_ITEMS) {
                PinToggleOutcome.LIMIT_REACHED
            } else {
                musicDao.insertPinnedItem(PinnedLibraryItemEntity(itemKey = itemKey))
                PinToggleOutcome.PINNED
            }
        }
    }

    suspend fun recordRecentCollectionPlay(
        id: String,
        title: String,
        subtitle: String,
        artworkUrl: String,
        kind: RecentTileKind,
        referenceId: String
    ) = withContext(Dispatchers.IO) {
        musicDao.upsertRecentCollection(
            RecentCollectionEntity(
                id = id,
                title = title,
                subtitle = subtitle,
                artworkUrl = artworkUrl,
                kindName = kind.name,
                referenceId = referenceId,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addTrackToUserPlaylist(playlistId: String, track: TrackItem): Boolean =
        withContext(Dispatchers.IO) {
            val nextPos = musicDao.getMaxTrackPosition(playlistId) + 1
            musicDao.upsertPlaylistTrack(track.toPlaylistTrackEntity(playlistId, nextPos))
            val existing = musicDao.getUserPlaylistById(playlistId)
            if (existing != null) {
                val cover = existing.coverArtworkUrl.ifBlank { track.artworkUrl }
                musicDao.updateUserPlaylistCover(playlistId, cover)
                val spPlId = existing.spotifyPlaylistId
                if (!spPlId.isNullOrBlank()) {
                    addTrackToSpotifyPlaylist(spPlId, track)
                }
            }
            true
        }

    suspend fun addTrackToSpotifyPlaylist(spotifyPlaylistId: String, track: TrackItem): Boolean =
        withContext(Dispatchers.IO) {
            val bearer = getValidSpotifyBearer() ?: return@withContext false
            val spTrackId = resolveSpotifyTrackIdIfNeeded(track, bearer) ?: return@withContext false
            runCatching {
                val payload = JSONObject().apply {
                    put("uris", JSONArray().put("spotify:track:$spTrackId"))
                }.toString()
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/$spotifyPlaylistId/tracks")
                    .header("Authorization", bearer)
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun removeTrackFromUserPlaylist(playlistId: String, trackId: String) =
        withContext(Dispatchers.IO) {
            musicDao.removeTrackFromPlaylist(playlistId, trackId)
            val remaining = musicDao.getTracksForPlaylistOnce(playlistId)
            val reindexed = remaining.mapIndexed { idx, row -> row.copy(position = idx) }
            if (reindexed.isNotEmpty()) {
                musicDao.upsertPlaylistTracks(reindexed)
            }
        }

    suspend fun moveTrackInUserPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) =
        withContext(Dispatchers.IO) {
            val list = musicDao.getTracksForPlaylistOnce(playlistId).toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return@withContext
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            val updated = list.mapIndexed { index, item -> item.copy(position = index) }
            musicDao.upsertPlaylistTracks(updated)
            musicDao.updateUserPlaylistCover(playlistId, updated.firstOrNull()?.artworkUrl.orEmpty())
        }

    suspend fun cloneCollectionAsUserPlaylist(
        name: String,
        description: String,
        artworkUrl: String,
        tracks: List<TrackItem>,
        youtubePlaylistId: String? = null,
        spotifyPlaylistId: String? = null
    ): UserPlaylist = withContext(Dispatchers.IO) {
        val id = "upl_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val cover = artworkUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
        val entity = UserPlaylistEntity(
            id = id,
            name = name.trim().ifBlank { "Saved Playlist" },
            description = description.trim(),
            coverArtworkUrl = cover,
            spotifyPlaylistId = spotifyPlaylistId,
            youtubePlaylistId = youtubePlaylistId,
            createdAt = now,
            updatedAt = now
        )
        musicDao.upsertUserPlaylist(entity)
        val entries = tracks.mapIndexed { idx, t -> t.toPlaylistTrackEntity(id, idx) }
        if (entries.isNotEmpty()) {
            musicDao.upsertPlaylistTracks(entries)
        }
        UserPlaylist(
            id = id,
            name = entity.name,
            description = entity.description,
            coverArtworkUrl = cover,
            mosaicUrls = tracks.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
            trackCount = tracks.size,
            spotifyPlaylistId = spotifyPlaylistId,
            youtubePlaylistId = youtubePlaylistId,
            updatedAt = now
        )
    }

    suspend fun importExternalPlaylistUrl(rawUrl: String): Result<UserPlaylist> =
        withContext(Dispatchers.IO) {
            val clean = rawUrl.trim()
            if (clean.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Enter a YouTube Music or Spotify playlist URL"))
            }

            // 1. YouTube Music / YouTube Playlist URL or PL... ID
            if (clean.contains("youtube.com") || clean.contains("youtu.be") || clean.startsWith("PL") || clean.startsWith("VL") || clean.startsWith("RDCLAK")) {
                val imported = FullStreamEngine.importYoutubeMusicPlaylist(clean)
                if (imported != null) {
                    prefsStore.setYtmConnectedPlaylistUrl(clean)
                    val (summary, tracks) = imported
                    val saved = cloneCollectionAsUserPlaylist(
                        name = summary.title,
                        description = summary.subtitle,
                        artworkUrl = summary.artworkUrl,
                        tracks = tracks,
                        youtubePlaylistId = summary.externalPlaylistId
                    )
                    return@withContext Result.success(saved)
                }
            }

            // 2. Spotify Playlist or Album URL
            if (clean.contains("spotify.com/playlist/") || clean.contains("spotify.com/album/")) {
                val isAlbum = clean.contains("spotify.com/album/")
                val entityType = if (isAlbum) "album" else "playlist"
                val spId = clean.substringAfter("/$entityType/").substringBefore("?").substringBefore("/")
                val bearer = getValidSpotifyBearer()

                if (!isAlbum && bearer != null && spId.isNotBlank()) {
                    val meta = spotifyGetJson("https://api.spotify.com/v1/playlists/$spId", bearer)
                    val tracks = fetchSpotifyPlaylistTracks(spId).getOrDefault(emptyList())
                    if (tracks.isNotEmpty()) {
                        val saved = cloneCollectionAsUserPlaylist(
                            name = meta?.optString("name")?.ifBlank { "Spotify Playlist" } ?: "Spotify Playlist",
                            description = "Synced from Spotify • ${tracks.size} tracks",
                            artworkUrl = meta?.optJSONArray("images")?.optJSONObject(0)?.optString("url")
                                .orEmpty()
                                .ifBlank { tracks.first().artworkUrl },
                            tracks = tracks,
                            spotifyPlaylistId = spId
                        )
                        return@withContext Result.success(saved)
                    }
                }

                if (spId.isNotBlank()) {
                    val embedTriple = FullStreamEngine.fetchPublicSpotifyEmbedPlaylist(entityType, spId)
                    if (embedTriple.third.isNotEmpty()) {
                        val (embedTitle, embedArt, embedTracks) = embedTriple
                        val saved = cloneCollectionAsUserPlaylist(
                            name = embedTitle.ifBlank { if (isAlbum) "Spotify Album" else "Spotify Playlist" },
                            description = "Imported from Spotify • ${embedTracks.size} tracks",
                            artworkUrl = embedArt.ifBlank { embedTracks.first().artworkUrl },
                            tracks = embedTracks,
                            spotifyPlaylistId = if (!isAlbum) spId else null
                        )
                        return@withContext Result.success(saved)
                    }
                }

                val publicTracks = FullStreamEngine.resolvePublicSpotifyLink(clean)
                if (publicTracks.isNotEmpty()) {
                    val saved = cloneCollectionAsUserPlaylist(
                        name = publicTracks.first().album.ifBlank { "Imported Spotify Mix" },
                        description = "Imported via Spotify Link • ${publicTracks.size} tracks",
                        artworkUrl = publicTracks.first().artworkUrl,
                        tracks = publicTracks
                    )
                    return@withContext Result.success(saved)
                }
            }

            Result.failure(IllegalStateException("Could not import playlist. Ensure it is a public YouTube Music or Spotify playlist link."))
        }

    // =========================================================================
    // PLAYBACK STREAM RESOLUTION & LYRICS
    // =========================================================================

    suspend fun resolvePlayableStream(
        track: TrackItem,
        forceAlternate: Boolean = false,
        attemptIndex: Int = if (forceAlternate) 1 else 0
    ): ResolvedStreamResult {
        val effectiveAttempt = if (forceAlternate && attemptIndex == 0) 1 else attemptIndex
        // 1. Always prefer verified in-app downloaded offline file first (0ms instant start, works 100% offline)
        if (effectiveAttempt == 0) {
            val downloaded = musicDao.getDownloadedTrack(track.id)
            val verifiedFile = downloadManager.verifyLocalFile(downloaded?.localFilePath ?: track.localFilePath)
            if (verifiedFile != null) {
                return ResolvedStreamResult(
                    url = verifiedFile.absolutePath,
                    durationMs = downloaded?.durationMs?.takeIf { it > 0L } ?: track.durationMs,
                    codecLabel = downloaded?.codecLabel?.ifBlank { "Offline Master" } ?: "Offline Master",
                    containerLabel = "IN-APP VAULT",
                    bitrateKbps = downloaded?.bitrateKbps ?: 320,
                    sampleRateHz = 48_000,
                    bitDepth = 24,
                    sourceType = AudioSourceType.LOCAL_FILE
                )
            }
        }

        if (track.sourceType == AudioSourceType.LOCAL_FILE && !track.audioStreamUrl.isNullOrBlank()) {
            return ResolvedStreamResult(
                url = track.audioStreamUrl,
                durationMs = track.durationMs,
                codecLabel = "Local PCM / Lossless",
                containerLabel = "DEVICE",
                bitrateKbps = 1411,
                sampleRateHz = 48_000,
                bitDepth = 24,
                sourceType = AudioSourceType.LOCAL_FILE
            )
        }
        val settings = prefsStore.glyphSettingsFlow.first()
        return FullStreamEngine.resolveFullLengthStream(
            track = track,
            qualityTier = settings.audioQualityTier,
            customPipedNode = settings.customPipedInstance,
            forceAlternate = effectiveAttempt > 0,
            attemptIndex = effectiveAttempt
        )
    }

    suspend fun downloadTrackForOffline(track: TrackItem): Result<TrackItem> = withContext(Dispatchers.IO) {
        val existing = musicDao.getDownloadedTrack(track.id)
        if (existing != null && downloadManager.verifyLocalFile(existing.localFilePath) != null) {
            return@withContext Result.success(existing.toDomain())
        }
        val settings = prefsStore.glyphSettingsFlow.first()
        val resolved = FullStreamEngine.resolveFullLengthStream(
            track = track,
            qualityTier = settings.audioQualityTier,
            customPipedNode = settings.customPipedInstance,
            forceAlternate = false
        )
        downloadManager.downloadToInternalVault(track, resolved).map { savedEntity ->
            musicDao.upsertDownloadedTrack(savedEntity)
            savedEntity.toDomain()
        }
    }

    suspend fun removeOfflineDownload(trackId: String) = withContext(Dispatchers.IO) {
        val existing = musicDao.getDownloadedTrack(trackId)
        downloadManager.deleteFromInternalVault(existing?.localFilePath)
        musicDao.deleteDownloadedTrack(trackId)
    }

    suspend fun isTrackDownloadedOffline(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = musicDao.getDownloadedTrack(trackId) ?: return@withContext false
        downloadManager.verifyLocalFile(existing.localFilePath) != null
    }

    suspend fun prewarmTrackStream(track: TrackItem) {
        if (track.sourceType == AudioSourceType.LOCAL_FILE) return
        if (isTrackDownloadedOffline(track.id)) return
        val settings = prefsStore.glyphSettingsFlow.first()
        if (FullStreamEngine.getCachedStream(track.id, settings.audioQualityTier) != null) return
        runCatching { resolvePlayableStream(track) }
    }

    internal fun sanitizeTitleForLyrics(rawTitle: String): String {
        var cleaned = rawTitle.trim()
        // Remove parenthetical or bracketed feat/remaster/video tags while preserving core song title
        cleaned = cleaned.replace(
            Regex("""\s*[\(\[][^)\]]*(feat\.?|ft\.?|with\s|official|video|audio|lyrics?|visualizer|remaster|version|edit|live|mono|stereo|deluxe|from\s)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE),
            ""
        )
        // Strip trailing spaced dash suffixes (e.g. " - 2011 Remaster", " - Radio Edit") WITHOUT breaking hyphenated titles like "Anti-Hero"
        cleaned = cleaned.replace(
            Regex("""\s+[-–—]\s+.*(remaster|version|edit|mix|live|mono|stereo|deluxe|bonus|feat\.?|ft\.?|soundtrack|from\s|\b(19|20)\d{2}\b).*$""", RegexOption.IGNORE_CASE),
            ""
        )
        return cleaned.trim().ifBlank { rawTitle.trim() }
    }

    suspend fun fetchSyncedLyrics(
        trackTitle: String,
        artistName: String,
        durationMs: Long = 0L
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val durationSec = (durationMs / 1000L).toInt()
        val durationBucket = if (durationSec in 25..1800) durationSec / 4 else 0
        val cacheKey = "${trackTitle.lowercase().trim()}|${artistName.lowercase().trim()}|$durationBucket"
        lyricsCache.get(cacheKey)?.let { return@withContext it }

        val cleanTitle = sanitizeTitleForLyrics(trackTitle)
        val primaryArtist = artistName
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore(" feat.")
            .substringBefore(" ft.")
            .trim()
            .ifBlank { artistName.trim() }
        val wantsSpecialEdition = Regex(
            """\b(live|acoustic|remix|slowed|sped\s*up|extended|radio\s*edit)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(trackTitle)

        try {
            val candidates = mutableListOf<com.example.data.remote.LrcLibResponseDto>()

            // Pass 1: Exact match lookup when duration is known
            if (durationSec in 25..1800) {
                runCatching {
                    lrcLibApi.getExactLyrics(
                        trackName = cleanTitle,
                        artistName = primaryArtist,
                        durationSeconds = durationSec
                    )
                }.getOrNull()?.let { candidates.add(it) }
            }

            // Pass 2: Always run structured track_name + artist_name search if exact match wasn't within 2s
            val hasTightSyncedMatch = candidates.any {
                !it.syncedLyrics.isNullOrBlank() &&
                    (durationSec <= 25 || (it.duration != null && kotlin.math.abs(it.duration - durationSec) <= 2.5))
            }
            if (!hasTightSyncedMatch) {
                runCatching {
                    lrcLibApi.searchLyrics(trackName = cleanTitle, artistName = primaryArtist)
                }.getOrNull()?.let { candidates.addAll(it) }
            }

            // Pass 3: Full-text query search ("Title Artist") for collaborations / punctuation differences
            if (candidates.none { !it.syncedLyrics.isNullOrBlank() }) {
                runCatching {
                    lrcLibApi.searchLyricsByQuery(query = "$cleanTitle $primaryArtist")
                }.getOrNull()?.let { candidates.addAll(it) }
            }

            // Pass 4: Fallback title-only query filtered by artist token overlap
            if (candidates.isEmpty() && cleanTitle.length >= 3) {
                runCatching {
                    lrcLibApi.searchLyricsByQuery(query = cleanTitle)
                }.getOrNull()?.let { list ->
                    val artistToken = primaryArtist.lowercase().split(" ").firstOrNull { it.length >= 3 }
                    val filtered = if (artistToken != null) {
                        list.filter { it.artistName?.lowercase()?.contains(artistToken) == true }
                    } else list
                    candidates.addAll(filtered)
                }
            }

            fun scoreCandidate(dto: com.example.data.remote.LrcLibResponseDto): Int {
                var score = 0
                if (!dto.syncedLyrics.isNullOrBlank()) score += 110
                else if (!dto.plainLyrics.isNullOrBlank()) score += 35
                val dtoTitle = dto.trackName?.lowercase().orEmpty()
                val dtoArtist = dto.artistName?.lowercase().orEmpty()
                val dtoAlbum = dto.albumName?.lowercase().orEmpty()
                if (dtoTitle == cleanTitle.lowercase()) score += 48
                else if (dtoTitle.contains(cleanTitle.lowercase()) || cleanTitle.lowercase().contains(dtoTitle)) score += 22
                val pLow = primaryArtist.lowercase()
                if (pLow.isNotBlank() && (dtoArtist.contains(pLow) || pLow.contains(dtoArtist))) score += 42

                // Penalize live/remix/slowed/acoustic LRC timings when playing the studio album version
                if (!wantsSpecialEdition) {
                    val combinedMeta = "$dtoTitle $dtoAlbum"
                    if (Regex("""\b(live|acoustic|remix|slowed|sped\s*up|karaoke|instrumental|music\s*video|extended)\b""").containsMatchIn(combinedMeta)) {
                        score -= 70
                    }
                }

                if (durationSec > 25 && dto.duration != null && dto.duration > 0.0) {
                    val diff = kotlin.math.abs(dto.duration - durationSec)
                    score += when {
                        diff <= 1.2 -> 90
                        diff <= 2.5 -> 62
                        diff <= 5.0 -> 28
                        diff <= 10.0 -> -20
                        else -> -95
                    }
                }
                return score
            }

            val ranked = candidates.sortedByDescending { scoreCandidate(it) }
            for (cand in ranked) {
                val rawSynced = cand.syncedLyrics
                if (!rawSynced.isNullOrBlank()) {
                    val parsed = parseLrcTimestamps(rawSynced)
                    if (parsed.size >= 3) {
                        // Gentle tempo/cadence alignment if LRC duration slightly differs (1.5s..6s) due to remaster mastering rate
                        val candDurMs = ((cand.duration ?: 0.0) * 1000.0).toLong()
                        val aligned = if (durationMs > 45_000L && candDurMs > 45_000L) {
                            val diffMs = kotlin.math.abs(durationMs - candDurMs)
                            val ratio = durationMs.toDouble() / candDurMs.toDouble()
                            if (diffMs in 1_800L..6_500L && ratio in 0.96..1.04) {
                                parsed.map { line ->
                                    line.copy(timestampMs = (line.timestampMs * ratio).toLong().coerceAtLeast(0L))
                                }
                            } else {
                                parsed
                            }
                        } else {
                            parsed
                        }
                        lyricsCache.put(cacheKey, aligned)
                        return@withContext aligned
                    }
                }
            }

            // Plain lyrics from LRCLIB or Lyrics.ovh fallback — marked isSynced = false so UI never fakes karaoke timing
            val plainText = ranked.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
                ?: fetchLyricsOvhPlain(primaryArtist, cleanTitle)

            if (!plainText.isNullOrBlank()) {
                val lines = plainText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("Paroles de la chanson", ignoreCase = true) }
                if (lines.isNotEmpty()) {
                    val unsyncedLines = lines.mapIndexed { idx, line ->
                        LyricLine(timestampMs = idx * 3_200L, text = line, isSynced = false)
                    }
                    lyricsCache.put(cacheKey, unsyncedLines)
                    return@withContext unsyncedLines
                }
            }

            emptyList()
        } catch (e: Exception) {
            Log.w("NothingMusicRepo", "Lyrics lookup failed", e)
            emptyList()
        }
    }

    private fun fetchLyricsOvhPlain(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank()) return null
        return runCatching {
            val encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20")
            val encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val req = Request.Builder()
                .url("https://api.lyrics.ovh/v1/$encArtist/$encTitle")
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                JSONObject(body).optString("lyrics").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    internal fun parseLrcTimestamps(lrcRaw: String): List<LyricLine> {
        val offsetHeaderRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
        val tagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?]""")
        val inlineWordTagRegex = Regex("""<\d{1,3}:\d{2}(?:[\.:]\d{1,3})?>""")
        val result = ArrayList<LyricLine>()

        // LRC [offset:+/-N] tag: positive values shift lyrics earlier (subtract from timestamp)
        val headerOffsetMs = offsetHeaderRegex.find(lrcRaw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

        for (rawLine in lrcRaw.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            val matches = tagRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val textStart = matches.last().range.last + 1
            val lyricText = trimmed.substring(textStart)
                .replace(inlineWordTagRegex, "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (lyricText.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = match.groupValues[3]
                val millis = when (fracRaw.length) {
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    3 -> fracRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val rawTs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                val adjustedTs = (rawTs - headerOffsetMs).coerceAtLeast(0L)
                result.add(
                    LyricLine(
                        timestampMs = adjustedTs,
                        text = lyricText,
                        isSynced = true
                    )
                )
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    /**
     * Two-way Like / Save sync:
     * Updates local Room Vault immediately AND syncs PUT / DELETE `/v1/me/tracks` with Spotify if connected!
     */
    suspend fun toggleSaveTrack(track: TrackItem): Boolean = withContext(Dispatchers.IO) {
        val nowSaved = if (musicDao.isTrackSaved(track.id)) {
            musicDao.deleteSavedTrack(track.id)
            false
        } else {
            musicDao.insertSavedTrack(track.toSavedEntity())
            true
        }

        val bearer = getValidSpotifyBearer()
        if (bearer != null) {
            runCatching {
                val spId = resolveSpotifyTrackIdIfNeeded(track, bearer)
                if (!spId.isNullOrBlank()) {
                    val url = "https://api.spotify.com/v1/me/tracks?ids=$spId"
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("Authorization", bearer)
                    val req = if (nowSaved) {
                        reqBuilder.put("".toRequestBody(jsonMedia)).build()
                    } else {
                        reqBuilder.delete().build()
                    }
                    httpClient.newCall(req).execute().close()
                }
            }
        }
        nowSaved
    }

    suspend fun recordScrobbleAndSync(track: TrackItem, isOfflinePlay: Boolean = false) = withContext(Dispatchers.IO) {
        // 1. Record play in Nchord's local database (whether streamed or played from in-app offline vault)
        musicDao.insertScrobble(
            ScrobbleEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                audioStreamUrl = track.audioStreamUrl,
                durationMs = track.durationMs.coerceAtLeast(60_000L),
                sourceTypeName = track.sourceType.name,
                albumId = track.albumId,
                artistId = track.artistId,
                spotifyTrackId = track.spotifyTrackId,
                isOfflinePlay = isOfflinePlay,
                syncedToScrobbler = false
            )
        )

        // 2. Push Now Playing + flush any queued offline/online scrobbles to Last.fm or ListenBrainz
        val creds = prefsStore.getScrobblerCreds()
        if (creds.lastFmSessionKey.isNotBlank() || creds.listenBrainzToken.isNotBlank()) {
            ScrobblerEngine.submitNowPlaying(
                track = track,
                provider = creds.provider,
                lastFmSessionKey = creds.lastFmSessionKey,
                listenBrainzToken = creds.listenBrainzToken
            )
            flushPendingScrobblesToRemote()
        }
    }

    suspend fun flushPendingScrobblesToRemote(): Int = withContext(Dispatchers.IO) {
        val creds = prefsStore.getScrobblerCreds()
        if (creds.lastFmSessionKey.isBlank() && creds.listenBrainzToken.isBlank()) {
            return@withContext 0
        }
        val unsynced = musicDao.getUnsyncedScrobbles(limit = 40)
        if (unsynced.isEmpty()) return@withContext 0
        val deliveredIds = ScrobblerEngine.flushQueuedScrobbles(
            unsynced = unsynced,
            provider = creds.provider,
            lastFmSessionKey = creds.lastFmSessionKey,
            listenBrainzToken = creds.listenBrainzToken
        )
        if (deliveredIds.isNotEmpty()) {
            musicDao.markScrobblesSynced(deliveredIds)
        }
        deliveredIds.size
    }

    suspend fun connectLastFmAccount(
        username: String,
        passwordOptional: String
    ): Result<ScrobblerEngine.RemoteScrobblerSnapshot> = withContext(Dispatchers.IO) {
        ScrobblerEngine.connectLastFm(username, passwordOptional).map { (sessionKey, snapshot) ->
            prefsStore.saveLastFmCredentials(username, sessionKey)
            importRemoteRecentTracksIntoVaultIfEmpty(snapshot.recentTracks)
            flushPendingScrobblesToRemote()
            snapshot
        }
    }

    suspend fun connectListenBrainzAccount(
        username: String,
        tokenOptional: String
    ): Result<ScrobblerEngine.RemoteScrobblerSnapshot> = withContext(Dispatchers.IO) {
        ScrobblerEngine.connectAndFetchListenBrainz(username, tokenOptional).map { snapshot ->
            prefsStore.saveListenBrainzCredentials(username, tokenOptional)
            importRemoteRecentTracksIntoVaultIfEmpty(snapshot.recentTracks)
            flushPendingScrobblesToRemote()
            snapshot
        }
    }

    suspend fun refreshScrobblerRemoteSnapshot(): ScrobblerEngine.RemoteScrobblerSnapshot = withContext(Dispatchers.IO) {
        flushPendingScrobblesToRemote()
        val creds = prefsStore.getScrobblerCreds()
        when {
            creds.provider == "LISTENBRAINZ" && creds.listenBrainzUsername.isNotBlank() -> {
                ScrobblerEngine.fetchListenBrainzSnapshot(creds.listenBrainzUsername)
            }
            creds.lastFmUsername.isNotBlank() -> {
                ScrobblerEngine.fetchLastFmSnapshot(creds.lastFmUsername)
            }
            else -> ScrobblerEngine.RemoteScrobblerSnapshot()
        }
    }

    suspend fun disconnectScrobbler() {
        prefsStore.clearScrobblerSession()
    }

    private suspend fun importRemoteRecentTracksIntoVaultIfEmpty(recentFormatted: List<String>) {
        if (recentFormatted.isEmpty()) return
        val existingCount = musicDao.observeTotalScrobbleCount().first()
        if (existingCount > 0) return
        recentFormatted.take(5).forEach { entry ->
            val parts = entry.split(" — ", " – ", " - ", limit = 2)
            val artist = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "Artist" }
            val title = parts.getOrNull(1)?.trim().orEmpty().ifBlank { entry.trim() }
            val syntheticId = "scrobble_${(artist + title).lowercase().hashCode().toUInt()}"
            musicDao.insertScrobble(
                ScrobbleEntity(
                    trackId = syntheticId,
                    title = title,
                    artist = artist,
                    album = "Scrobbled History",
                    artworkUrl = "",
                    audioStreamUrl = null,
                    durationMs = 210_000L,
                    sourceTypeName = AudioSourceType.YTM_ADFREE.name,
                    syncedToScrobbler = true
                )
            )
        }
    }

    suspend fun sendSpotifyRemoteTransport(action: String): Boolean = withContext(Dispatchers.IO) {
        val bearer = getValidSpotifyBearer() ?: return@withContext false
        try {
            val response = when (action) {
                "NEXT" -> spotifyWebApi.skipNextRemote(bearer)
                "PREV" -> spotifyWebApi.skipPreviousRemote(bearer)
                "PAUSE" -> spotifyWebApi.pauseRemote(bearer)
                "PLAY" -> spotifyWebApi.resumeRemote(bearer)
                else -> return@withContext false
            }
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun completeOnboarding() = prefsStore.setOnboardingCompleted(true)
    suspend fun reopenOnboarding() = prefsStore.setOnboardingCompleted(false)
    suspend fun setYtmEngineEnabled(enabled: Boolean) = prefsStore.setYtmEngineEnabled(enabled)
    suspend fun setCustomPipedInstance(url: String) = prefsStore.setCustomPipedInstance(url)
    suspend fun setYtmConnectedPlaylistUrl(url: String) = prefsStore.setYtmConnectedPlaylistUrl(url)
    suspend fun setAudioQualityTier(tier: AudioQualityTier) = prefsStore.setAudioQualityTier(tier)
    suspend fun setMobileAudioQualityTier(tier: AudioQualityTier) = prefsStore.setMobileAudioQualityTier(tier)
    suspend fun setBitPerfectUsbDac(enabled: Boolean) = prefsStore.setBitPerfectUsbDac(enabled)
    suspend fun setPlaybackSpeed(speed: Float) = prefsStore.setPlaybackSpeed(speed)
    suspend fun setOledDarkMode(enabled: Boolean) = prefsStore.setOledDarkMode(enabled)
    suspend fun setGlyphReactive(enabled: Boolean) = prefsStore.setGlyphReactive(enabled)
    suspend fun setAutomixEnabled(enabled: Boolean) = prefsStore.setAutomixEnabled(enabled)
    suspend fun setTactileHaptics(enabled: Boolean) = prefsStore.setTactileHaptics(enabled)

    private fun DownloadedTrackEntity.toDomain(): TrackItem {
        val src = runCatching { AudioSourceType.valueOf(sourceTypeName) }
            .getOrDefault(AudioSourceType.YTM_ADFREE)
        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = localFilePath,
            durationMs = durationMs,
            sourceType = src,
            youtubeVideoId = youtubeVideoId,
            externalSpotifyUri = externalSpotifyUri,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId,
            streamQualityBadge = "OFFLINE • $codecLabel",
            isDownloaded = true,
            localFilePath = localFilePath
        )
    }

    private fun SavedTrackEntity.toDomain(): TrackItem {
        val src = runCatching { AudioSourceType.valueOf(sourceTypeName) }
            .getOrDefault(AudioSourceType.YTM_ADFREE)
        val vid = youtubeVideoId ?: if (id.startsWith("ytm_")) id.removePrefix("ytm_") else null
        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = audioStreamUrl,
            durationMs = durationMs,
            sourceType = src,
            youtubeVideoId = vid,
            externalSpotifyUri = externalSpotifyUri,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId
        )
    }

    private fun ScrobbleEntity.toDomain(): TrackItem {
        val src = runCatching { AudioSourceType.valueOf(sourceTypeName) }
            .getOrDefault(AudioSourceType.YTM_ADFREE)
        val vid = if (trackId.startsWith("ytm_")) trackId.removePrefix("ytm_") else null
        return TrackItem(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = audioStreamUrl,
            durationMs = durationMs,
            sourceType = src,
            youtubeVideoId = vid,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId
        )
    }

    private fun PlaylistTrackEntity.toDomain(): TrackItem {
        val src = runCatching { AudioSourceType.valueOf(sourceTypeName) }
            .getOrDefault(AudioSourceType.OPEN_AAC)
        return TrackItem(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = audioStreamUrl,
            durationMs = durationMs,
            sourceType = src,
            youtubeVideoId = youtubeVideoId,
            externalSpotifyUri = externalSpotifyUri,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId
        )
    }

    private fun TrackItem.toSavedEntity(): SavedTrackEntity {
        return SavedTrackEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = audioStreamUrl,
            durationMs = durationMs,
            sourceTypeName = sourceType.name,
            externalSpotifyUri = externalSpotifyUri,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId,
            youtubeVideoId = youtubeVideoId
        )
    }

    private fun TrackItem.toPlaylistTrackEntity(playlistId: String, position: Int): PlaylistTrackEntity {
        return PlaylistTrackEntity(
            playlistId = playlistId,
            trackId = id,
            position = position,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            audioStreamUrl = audioStreamUrl,
            durationMs = durationMs,
            sourceTypeName = sourceType.name,
            youtubeVideoId = youtubeVideoId,
            externalSpotifyUri = externalSpotifyUri,
            albumId = albumId,
            artistId = artistId,
            spotifyTrackId = spotifyTrackId
        )
    }

    companion object {
        const val MAX_PINNED_ITEMS = 4
    }
}
