package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.InAppDownloadManager
import com.example.data.local.NothingChordDatabase
import com.example.data.local.NothingPreferencesStore
import com.example.data.model.ArtistInsightStat
import com.example.data.model.AudioQualityTier
import com.example.data.model.AudioSourceType
import com.example.data.model.CollectionPageState
import com.example.data.model.CollectionPageType
import com.example.data.model.GlyphSettings
import com.example.data.model.HomeFeedFilter
import com.example.data.model.InsightsSnapshot
import com.example.data.model.LyricLine
import com.example.data.model.RecentGridTile
import com.example.data.model.RecentTileKind
import com.example.data.model.RepeatModeState
import com.example.data.model.ScrobblerConfigAndState
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.model.SpotifyProfileState
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist
import com.example.data.repository.NothingMusicRepository
import com.example.data.repository.PinToggleOutcome
import com.example.player.AudioEngineState
import com.example.player.NothingAudioEngine
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NothingTab(val route: String) {
    HOME("route_home"),
    SEARCH("route_search"),
    LIBRARY("route_library"),
    SOURCES("route_sources")
}

enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    PLAYLISTS("Playlists")
}

@Immutable
data class MoodChannel(
    val id: String,
    val label: String,
    val artistSeeds: List<String>,
    val releaseSeeds: List<String>,
    val playlistSeeds: List<Pair<String, String>>
)

val DEFAULT_MOOD_CHANNELS = listOf(
    MoodChannel(
        id = "foryou",
        label = "For You",
        artistSeeds = listOf("The Weeknd", "Daft Punk", "Tame Impala", "Frank Ocean", "Fred again.."),
        releaseSeeds = listOf("Daft Punk Random Access Memories", "Tame Impala Currents", "The Weeknd Dawn FM", "Justice Hyperdrama"),
        playlistSeeds = listOf(
            "After Hours Rotation::The Weeknd|Kavinsky|Gesaffelstein|Daft Punk" to "For You",
            "Indie Psych Signals::Tame Impala|MGMT|Empire of the Sun|Gorillaz" to "Daily Mix",
            "Warehouse Pulse::Fred again..|Disclosure|Kaytranada|Justice" to "Club",
            "Midnight Velvet::Frank Ocean|SZA|Daniel Caesar|Joji" to "Late Night"
        )
    ),
    MoodChannel(
        id = "synth",
        label = "Synthwave",
        artistSeeds = listOf("Kavinsky", "The Midnight", "M83", "Gesaffelstein", " Gunship"),
        releaseSeeds = listOf("Kavinsky OutRun", "M83 Hurry Up We're Dreaming", "The Weeknd After Hours", "Justice Cross"),
        playlistSeeds = listOf(
            "Nightcall Outrun::Kavinsky|The Midnight|M83|College" to "Synthwave",
            "Analog Chrome::Daft Punk|Justice|SebastiAn|Röyksopp" to "Analog",
            "Dark Clubbing::Gesaffelstein|Boy Harsher|Perturbator|Carpenter Brut" to "Midnight",
            "Neon Horizon::FM-84|Timecop1983|Gunship|Chromatics" to "Neon"
        )
    ),
    MoodChannel(
        id = "electronic",
        label = "Electronic",
        artistSeeds = listOf("Fred again..", "Disclosure", "RÜFÜS DU SOL", "Kaytranada", "Bicep"),
        releaseSeeds = listOf("Disclosure Settle", "Kaytranada 99.9%", "Bicep Isles", "ODESZA A Moment Apart"),
        playlistSeeds = listOf(
            "Boiler Room Energy::Fred again..|Four Tet|Skrillex|Overmono" to "Club",
            "UK Garage & House::Disclosure|Sammy Virji|Interplanetary Criminal|Conducta" to "Warehouse",
            "Melodic Afterhours::RÜFÜS DU SOL|Bicep|Lane 8|Ben Böhmer" to "Afterhours",
            "Future Bounce::Kaytranada|SG Lewis|Channel Tres|Franc Moody" to "Groove"
        )
    ),
    MoodChannel(
        id = "indie",
        label = "Alt & Indie",
        artistSeeds = listOf("Tame Impala", "Arctic Monkeys", "Radiohead", "The Strokes", "Beach House"),
        releaseSeeds = listOf("Arctic Monkeys AM", "Radiohead In Rainbows", "The Strokes Is This It", "Beach House Bloom"),
        playlistSeeds = listOf(
            "Modern Indie Icons::Tame Impala|Arctic Monkeys|The Strokes|Phoenix" to "Indie",
            "Dream Pop Haze::Beach House|Cocteau Twins|Slowdive|Men I Trust" to "Atmospheric",
            "Art Rock Essentials::Radiohead|Gorillaz|LCD Soundsystem|Interpol" to "Guitar",
            "Golden Hour Indie::Mac DeMarco|Clairo|Wallows|Balu Brigada" to "Editorial"
        )
    ),
    MoodChannel(
        id = "hiphop",
        label = "Hip-Hop",
        artistSeeds = listOf("Kendrick Lamar", "Travis Scott", "Metro Boomin", "Tyler, The Creator", "Kanye West"),
        releaseSeeds = listOf("Kendrick Lamar DAMN", "Metro Boomin Heroes & Villains", "Travis Scott ASTROWORLD", "Tyler The Creator IGOR"),
        playlistSeeds = listOf(
            "Vanguard Bars::Kendrick Lamar|J. Cole|Pusha T|Denzel Curry" to "Hip-Hop",
            "808 Architecture::Metro Boomin|Travis Scott|Future|Don Toliver" to "Heavy Bass",
            "Alt Rap Vision::Tyler, The Creator|A\$AP Rocky|Kid Cudi|Vince Staples" to "Lyrical",
            "Late Night Cypher::21 Savage|Drake|Baby Keem|JID" to "Rotation"
        )
    ),
    MoodChannel(
        id = "rnb",
        label = "R&B",
        artistSeeds = listOf("Frank Ocean", "SZA", "Brent Faiyaz", "Daniel Caesar", "Steve Lacy"),
        releaseSeeds = listOf("SZA SOS", "Frank Ocean Channel Orange", "Daniel Caesar Freudian", "Steve Lacy Gemini Rights"),
        playlistSeeds = listOf(
            "Alt R&B Spectrum::Frank Ocean|SZA|Steve Lacy|Blood Orange" to "Alt R&B",
            "After Midnight::Brent Faiyaz|Bryson Tiller|6LACK|PARTYNEXTDOOR" to "Velvet",
            "Warm Neo-Soul::Daniel Caesar|Anderson .Paak|Kali Uchis| Cleo Sol" to "Warm",
            "Silk & Sub-Bass::The Weeknd|Miguel|Giveon|H.E.R." to "Smooth"
        )
    ),
    MoodChannel(
        id = "lofi",
        label = "Lo-Fi",
        artistSeeds = listOf("Nujabes", "Joji", "Cigarettes After Sex", "J Dilla", "Khruangbin"),
        releaseSeeds = listOf("Nujabes Modal Soul", "Joji BALLADS 1", "Khruangbin Con Todo El Mundo", "Cigarettes After Sex"),
        playlistSeeds = listOf(
            "Analog Tape Study::Nujabes|J Dilla|uyama hiroto|Idealism" to "Focus",
            "Midnight Rain::Joji|Cigarettes After Sex|d4vd|keshi" to "Calm",
            "Warm Vinyl Grooves::Khruangbin|Tom Misch|FKJ| Yussef Dayes" to "Instrumental",
            "Static & Sleep::Brian Eno|Aphex Twin|Boards of Canada|Tycho" to "Ambient"
        )
    ),
    MoodChannel(
        id = "rock",
        label = "Rock",
        artistSeeds = listOf("Deftones", "Linkin Park", "Muse", "Pink Floyd", "Nirvana"),
        releaseSeeds = listOf("Deftones White Pony", "Pink Floyd Dark Side of the Moon", "Linkin Park Meteora", "Nirvana Nevermind"),
        playlistSeeds = listOf(
            "Heavy Shoegaze::Deftones|Loathe|Superheaven|My Bloody Valentine" to "Distortion",
            "Stadium Voltage::Muse|Linkin Park|Queens of the Stone Age|Foo Fighters" to "Anthems",
            "Analog Prog Classics::Pink Floyd|Led Zeppelin|Fleetwood Mac|David Bowie" to "Analog",
            "90s Grunge & Alt::Nirvana|Soundgarden|Smashing Pumpkins|Pearl Jam" to "Stage"
        )
    ),
    MoodChannel(
        id = "pop",
        label = "Pop",
        artistSeeds = listOf("Billie Eilish", "Charli xcx", "Dua Lipa", "Post Malone", "Lorde"),
        releaseSeeds = listOf("Billie Eilish HIT ME HARD AND SOFT", "Charli xcx BRAT", "Dua Lipa Future Nostalgia", "Lorde Melodrama"),
        playlistSeeds = listOf(
            "Club Pop Vanguard::Charli xcx|Troye Sivan|Caroline Polachek|PinkPantheress" to "Fresh",
            "Dark Alt-Pop::Billie Eilish|Lorde|Lana Del Rey|Olivia Rodrigo" to "Trending",
            "Nu-Disco Pop::Dua Lipa|Sabrina Carpenter|Chappell Roan|Kylie Minogue" to "Upbeat",
            "Widescreen Hooks::Post Malone|Harry Styles|Taylor Swift|Benson Boone" to "Rotation"
        )
    )
)

@Immutable
data class NothingUiState(
    val activeTab: NothingTab = NothingTab.HOME,
    val isFullPlayerOpen: Boolean = false,
    val isAccountPageOpen: Boolean = false,
    val homeFeedFilter: HomeFeedFilter = HomeFeedFilter.ALL,
    val selectedMoodId: String = DEFAULT_MOOD_CHANNELS.first().id,
    val activeMoodLabel: String = DEFAULT_MOOD_CHANNELS.first().label,
    val suggestedPlaylists: List<SuggestedPlaylist> = emptyList(),
    val spotifyCratePlaylists: List<SuggestedPlaylist> = emptyList(),
    val ytmSessionPlaylists: List<SuggestedPlaylist> = emptyList(),
    val vinylPressingPlaylists: List<SuggestedPlaylist> = emptyList(),
    val featuredTracks: List<TrackItem> = emptyList(),
    val moodTracks: List<TrackItem> = emptyList(),
    val isDiscoverLoading: Boolean = true,
    val isMoodRefreshing: Boolean = false,
    val discoverError: String? = null,

    // Universal Collection Stack (Playlists, Albums, Artists)
    val collectionStack: List<CollectionPageState> = emptyList(),

    // Search
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val searchUseSpotify: Boolean = false,
    val searchResults: List<TrackItem> = emptyList(),
    val searchPlaylistsResults: List<SuggestedPlaylist> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val searchNotice: String? = null,

    // Spotify & External Sync
    val spotifyProfile: SpotifyProfileState = SpotifyProfileState(),
    val spotifyPlaylists: List<SpotifyPlaylistSummary> = emptyList(),
    val spotifyTopTracks: List<TrackItem> = emptyList(),
    val isSpotifyLoading: Boolean = false,
    val spotifyStatusBanner: String? = null,

    val localImportedTracks: List<TrackItem> = emptyList(),

    // Playback Queue, Sleep Timer & Lyrics
    val queue: List<TrackItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val sleepTimerRemainingMinutes: Int? = null, // -1 means End of Track
    val lyrics: List<LyricLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val lyricsOffsetMs: Long = 0L,

    // Extended Long-Press Context Menu & Add-to-Playlist Sheets
    val contextMenuTrack: TrackItem? = null,
    val contextMenuPlaylistId: String? = null,
    val addToPlaylistTargetTrack: TrackItem? = null,

    val feedbackMessage: String? = null
) {
    val activeCollectionPage: CollectionPageState?
        get() = collectionStack.lastOrNull()
}

class NothingChordViewModel(
    private val appContext: Context,
    private val repository: NothingMusicRepository,
    private val audioEngine: NothingAudioEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(NothingUiState())
    val uiState: StateFlow<NothingUiState> = _uiState.asStateFlow()

    val audioState: StateFlow<AudioEngineState> = audioEngine.state

    val savedTracks: StateFlow<List<TrackItem>> = repository.savedTracksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val savedTrackIds: StateFlow<Set<String>> = repository.savedTrackIdsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptySet()
    )

    val recentScrobbles: StateFlow<List<TrackItem>> = repository.recentScrobblesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val userPlaylists: StateFlow<List<UserPlaylist>> = repository.userPlaylistsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val totalScrobbles: StateFlow<Int> = repository.totalScrobblesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = 0
    )

    val totalStreamedMs: StateFlow<Long> = repository.totalStreamedMsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = 0L
    )

    val downloadedTracks: StateFlow<List<TrackItem>> = repository.downloadedTracksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val downloadedTrackIds: StateFlow<Set<String>> = repository.downloadedTrackIdsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptySet()
    )

    val downloadProgress: StateFlow<Map<String, Float>> = repository.downloadProgressFlow

    val pinnedItemKeys: StateFlow<List<String>> = repository.pinnedItemKeysFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    private val _remoteScrobblerMeta = MutableStateFlow(ScrobblerConfigAndState())

    val scrobblerState: StateFlow<ScrobblerConfigAndState> = combine(
        repository.scrobblerCredsFlow,
        repository.pendingScrobblesCountFlow,
        _remoteScrobblerMeta
    ) { creds, pendingCount, remote ->
        ScrobblerConfigAndState(
            activeProvider = creds.provider,
            lastFmUsername = creds.lastFmUsername,
            lastFmConnected = creds.lastFmUsername.isNotBlank(),
            hasLastFmSessionKey = creds.lastFmSessionKey.isNotBlank(),
            listenBrainzUsername = creds.listenBrainzUsername,
            listenBrainzConnected = creds.listenBrainzUsername.isNotBlank(),
            remotePlayCount = remote.remotePlayCount,
            pendingOfflineScrobbles = pendingCount,
            remoteTopArtists = remote.remoteTopArtists,
            remoteRecentTracks = remote.remoteRecentTracks,
            isSyncing = remote.isSyncing,
            statusMessage = remote.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScrobblerConfigAndState()
    )

    val homeRecentGridTiles: StateFlow<List<RecentGridTile>> = combine(
        repository.recentCollectionsFlow,
        savedTracks,
        downloadedTracks,
        userPlaylists,
        _uiState
    ) { recentCols, saved, downloaded, userPls, state ->
        val tiles = ArrayList<RecentGridTile>(8)
        val seenIds = HashSet<String>()

        fun addUnique(tile: RecentGridTile) {
            if (tiles.size < 8 && seenIds.add(tile.id)) {
                tiles.add(tile)
            }
        }

        // 1. Always surface Liked Songs & On Repeat or Offline Vault when populated, plus latest played playlists
        recentCols.forEach { addUnique(it) }

        addUnique(
            RecentGridTile(
                id = "smart_saved",
                title = "Liked Songs",
                subtitle = "${saved.size} saved",
                artworkUrl = saved.firstOrNull()?.artworkUrl.orEmpty(),
                mosaicUrls = saved.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
                kind = RecentTileKind.LIKED_SONGS
            )
        )

        addUnique(
            RecentGridTile(
                id = "smart_on_repeat",
                title = "On Repeat",
                subtitle = "Heavy rotation",
                artworkUrl = state.moodTracks.firstOrNull()?.artworkUrl
                    ?: state.featuredTracks.firstOrNull()?.artworkUrl.orEmpty(),
                kind = RecentTileKind.ON_REPEAT
            )
        )

        if (downloaded.isNotEmpty()) {
            addUnique(
                RecentGridTile(
                    id = "smart_downloads",
                    title = "Offline Vault",
                    subtitle = "${downloaded.size} downloaded",
                    artworkUrl = downloaded.firstOrNull()?.artworkUrl.orEmpty(),
                    mosaicUrls = downloaded.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
                    kind = RecentTileKind.DOWNLOADS
                )
            )
        }

        userPls.take(4).forEach { pl ->
            addUnique(
                RecentGridTile(
                    id = "upl_${pl.id}",
                    title = pl.name,
                    subtitle = "${pl.trackCount} tracks",
                    artworkUrl = pl.coverArtworkUrl,
                    mosaicUrls = pl.mosaicUrls,
                    kind = RecentTileKind.USER_PLAYLIST,
                    referenceId = pl.id,
                    isPinned = pl.isPinned
                )
            )
        }

        state.spotifyPlaylists.take(4).forEach { sp ->
            addUnique(
                RecentGridTile(
                    id = "sp_${sp.id}",
                    title = sp.name,
                    subtitle = sp.ownerName,
                    artworkUrl = sp.imageUrl.orEmpty(),
                    kind = RecentTileKind.SPOTIFY_PLAYLIST,
                    referenceId = sp.id
                )
            )
        }

        val allCurated = state.suggestedPlaylists + state.ytmSessionPlaylists + state.spotifyCratePlaylists
        allCurated.forEach { cur ->
            addUnique(
                RecentGridTile(
                    id = "cur_${cur.id}",
                    title = cur.title,
                    subtitle = cur.badge,
                    artworkUrl = cur.artworkUrl,
                    kind = RecentTileKind.CURATED_PLAYLIST,
                    referenceId = cur.id
                )
            )
        }

        // Ensure clean even count (4, 6, or 8 tiles for 2-column × 2–4 rows layout)
        val count = tiles.size
        when {
            count >= 8 -> tiles.take(8)
            count >= 6 -> tiles.take(6)
            count >= 4 -> tiles.take(4)
            else -> tiles
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val insightsSnapshot: StateFlow<InsightsSnapshot> = combine(
        repository.allScrobblesFlow,
        downloadedTracks,
        scrobblerState,
        _uiState
    ) { scrobbles, downloads, scrobbler, state ->
        val localPlays = scrobbles.size
        val totalPlays = maxOf(localPlays, scrobbler.remotePlayCount)
        val localMinutes = scrobbles.sumOf { it.durationMs.coerceAtLeast(60_000L) } / 60_000L
        val effectiveMinutes = if (scrobbler.remotePlayCount > localPlays) {
            localMinutes + ((scrobbler.remotePlayCount - localPlays) * 3L)
        } else {
            localMinutes
        }
        val offlinePlays = scrobbles.count { it.isOfflinePlay }

        // 24-hour listening matrix
        val hourCounts = IntArray(24)
        val dayKeys = HashSet<Int>()
        val cal = Calendar.getInstance()
        scrobbles.forEach { s ->
            cal.timeInMillis = s.playedAt
            val h = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourCounts[h]++
            dayKeys.add(cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR))
        }
        val maxHourVal = (hourCounts.maxOrNull() ?: 1).coerceAtLeast(1)
        val peakHour = if (scrobbles.isNotEmpty()) {
            hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 22
        } else 22
        val hourlyNormalized = hourCounts.map { cnt ->
            (cnt.toFloat() / maxHourVal.toFloat()).coerceIn(0f, 1f)
        }

        val archetype = when (peakHour) {
            in 0..4 -> "MIDNIGHT VINYLIST"
            in 5..10 -> "DAWN SELECTOR"
            in 11..16 -> "DAYLIGHT ARCHIVIST"
            in 17..20 -> "GOLDEN HOUR CRATE"
            else -> "NOCTURNAL ANALOG"
        }

        // Merge local artist plays + remote scrobbler top artists + Spotify top artists
        val artistArtworkMap = HashMap<String, String>()
        scrobbles.forEach { s ->
            val primary = s.artist.substringBefore(",").trim()
            if (primary.isNotBlank() && s.artworkUrl.isNotBlank() && !artistArtworkMap.containsKey(primary)) {
                artistArtworkMap[primary] = s.artworkUrl
            }
        }
        (state.moodTracks + state.featuredTracks + downloads).forEach { t ->
            val primary = t.artist.substringBefore(",").trim()
            if (primary.isNotBlank() && t.artworkUrl.isNotBlank() && !artistArtworkMap.containsKey(primary)) {
                artistArtworkMap[primary] = t.artworkUrl
            }
        }

        val artistPlayCounts = linkedMapOf<String, Int>()
        val artistDurationMs = HashMap<String, Long>()
        scrobbles.forEach { s ->
            val primary = s.artist.substringBefore(",").trim().ifBlank { "Artist" }
            artistPlayCounts[primary] = (artistPlayCounts[primary] ?: 0) + 1
            artistDurationMs[primary] = (artistDurationMs[primary] ?: 0L) + s.durationMs.coerceAtLeast(180_000L)
        }
        scrobbler.remoteTopArtists.forEach { (name, count) ->
            val clean = name.trim()
            if (clean.isNotBlank()) {
                artistPlayCounts[clean] = maxOf(artistPlayCounts[clean] ?: 0, count)
            }
        }
        if (artistPlayCounts.isEmpty()) {
            state.spotifyProfile.topArtists.forEachIndexed { idx, name ->
                artistPlayCounts[name] = (12 - idx * 2).coerceAtLeast(2)
            }
        }

        val topArtists = artistPlayCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (name, plays) ->
                val mins = ((artistDurationMs[name] ?: (plays * 205_000L)) / 60_000L).toInt().coerceAtLeast(plays * 3)
                ArtistInsightStat(
                    name = name,
                    playCount = plays,
                    minutes = mins,
                    artworkUrl = artistArtworkMap[name].orEmpty()
                )
            }

        val topTrackGroup = scrobbles.groupBy { it.trackId }.maxByOrNull { it.value.size }
        val topTrack = topTrackGroup?.value?.firstOrNull()?.let { s ->
            TrackItem(
                id = s.trackId,
                title = s.title,
                artist = s.artist,
                album = s.album,
                artworkUrl = s.artworkUrl,
                audioStreamUrl = s.audioStreamUrl,
                durationMs = s.durationMs,
                sourceType = runCatching { AudioSourceType.valueOf(s.sourceTypeName) }
                    .getOrDefault(AudioSourceType.YTM_ADFREE)
            )
        } ?: state.spotifyTopTracks.firstOrNull() ?: state.featuredTracks.firstOrNull()

        InsightsSnapshot(
            totalMinutes = effectiveMinutes,
            totalPlays = totalPlays,
            offlinePlays = offlinePlays,
            downloadedTracksCount = downloads.size,
            uniqueArtistsCount = artistPlayCounts.size,
            streakDays = dayKeys.size.coerceAtLeast(if (totalPlays > 0) 1 else 0),
            peakHour = peakHour,
            collectorArchetype = archetype,
            hourlyMatrix = hourlyNormalized,
            topArtists = topArtists,
            topTrack = topTrack,
            topTrackPlays = topTrackGroup?.value?.size ?: if (topTrack != null) 1 else 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = InsightsSnapshot()
    )

    val glyphSettings: StateFlow<GlyphSettings> = repository.glyphSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GlyphSettings(onboardingCompleted = true)
    )

    private val _isInitialPrefsLoaded = MutableStateFlow(false)
    val isInitialPrefsLoaded: StateFlow<Boolean> = _isInitialPrefsLoaded.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var playTrackJob: Job? = null
    private var moodFetchJob: Job? = null
    private var feedbackJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var activeUserPlaylistObserverJob: Job? = null
    private var lyricsResyncJob: Job? = null
    private var streamRetryAttempt: Int = 0
    private var lastLyricsTrackId: String = ""
    private var lastLyricsDurationMs: Long = 0L

    init {
        audioEngine.onTrackEnded = { handleTrackCompleted() }
        audioEngine.onSkipPrevious = { skipPrevious() }
        audioEngine.onStreamError = { failedTrack ->
            retryWithAlternateEngine(failedTrack)
        }
        viewModelScope.launch {
            val initialSettings = repository.glyphSettingsFlow.first()
            audioEngine.setPlaybackSpeed(initialSettings.playbackSpeed)
            _isInitialPrefsLoaded.value = true
        }
        observePreparedDurationForLyricsSync()
        loadInitialDiscoverFeeds()
        observeSpotifyTokenStartup()
        observeScrobblerStartup()
    }

    private fun observePreparedDurationForLyricsSync() {
        viewModelScope.launch {
            audioState.collect { engine ->
                val tr = engine.currentTrack ?: return@collect
                val preparedDur = engine.durationMs
                if (!engine.isBuffering &&
                    preparedDur > 35_000L &&
                    tr.id == lastLyricsTrackId &&
                    kotlin.math.abs(preparedDur - lastLyricsDurationMs) > 4_000L
                ) {
                    lastLyricsDurationMs = preparedDur
                    lyricsResyncJob?.cancel()
                    lyricsResyncJob = viewModelScope.launch {
                        val refined = repository.fetchSyncedLyrics(
                            trackTitle = tr.title,
                            artistName = tr.artist,
                            durationMs = preparedDur
                        )
                        if (refined.isNotEmpty() && audioState.value.currentTrack?.id == tr.id) {
                            _uiState.update { it.copy(lyrics = refined, isLyricsLoading = false) }
                        }
                    }
                }
            }
        }
    }

    private fun observeSpotifyTokenStartup() {
        viewModelScope.launch {
            val existingToken = repository.spotifyAccessTokenFlow.first()
            if (!existingToken.isNullOrBlank()) {
                syncSpotifyData()
            }
        }
    }

    private fun observeScrobblerStartup() {
        viewModelScope.launch {
            val creds = repository.scrobblerCredsFlow.first()
            if (creds.lastFmUsername.isNotBlank() || creds.listenBrainzUsername.isNotBlank()) {
                syncScrobblerNow(silent = true)
            }
        }
    }

    fun emitFeedback(message: String) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(feedbackMessage = message) }
        feedbackJob = viewModelScope.launch {
            delay(1900L)
            _uiState.update { state ->
                if (state.feedbackMessage == message) state.copy(feedbackMessage = null) else state
            }
        }
    }

    fun completeOnboarding() {
        triggerConfirmHaptic()
        viewModelScope.launch {
            repository.completeOnboarding()
            loadInitialDiscoverFeeds()
        }
    }

    fun reopenOnboardingGuide() {
        triggerHaptic()
        viewModelScope.launch {
            repository.reopenOnboarding()
        }
    }

    fun updateUserSpotifyClientId(clientId: String) {
        viewModelScope.launch {
            repository.saveUserSpotifyClientId(clientId)
        }
    }

    fun updatePipedInstance(url: String) {
        triggerConfirmHaptic()
        emitFeedback("Relay updated")
        viewModelScope.launch {
            repository.setCustomPipedInstance(url)
            loadInitialDiscoverFeeds()
        }
    }

    fun setYtmEngineEnabled(enabled: Boolean) {
        triggerHaptic()
        viewModelScope.launch {
            repository.setYtmEngineEnabled(enabled)
            loadInitialDiscoverFeeds()
        }
    }

    // =========================================================================
    // PERSONALIZED HOME DISCOVERY & GENRE / STYLE CHIPS
    // =========================================================================

    private fun computePersonalizedArtistSeeds(fallbackSeeds: List<String>): List<String> {
        val spArtists = _uiState.value.spotifyProfile.topArtists.take(3)
        val savedArtists = savedTracks.value
            .map { it.artist.substringBefore(",").trim() }
            .filter { it.isNotBlank() && !it.equals("Device Storage", true) }
            .distinct()
            .take(3)
        val recentArtists = recentScrobbles.value
            .map { it.artist.substringBefore(",").trim() }
            .filter { it.isNotBlank() && !it.equals("Device Storage", true) }
            .distinct()
            .take(2)
        val personal = (spArtists + savedArtists + recentArtists).distinct()
        return (personal + fallbackSeeds.map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    private fun interleaveTrackLists(buckets: List<List<TrackItem>>, maxTotal: Int): List<TrackItem> {
        val seenKeys = HashSet<String>()
        val result = ArrayList<TrackItem>(maxTotal)
        var round = 0
        while (result.size < maxTotal) {
            var addedInRound = false
            for (bucket in buckets) {
                if (round < bucket.size) {
                    val candidate = bucket[round]
                    val key = "${candidate.title.lowercase().substringBefore("(").trim()}|${candidate.artist.lowercase().substringBefore(",").trim()}"
                    if (seenKeys.add(key)) {
                        result.add(candidate)
                        addedInRound = true
                        if (result.size >= maxTotal) break
                    }
                }
            }
            if (!addedInRound) break
            round++
        }
        return result
    }

    fun loadInitialDiscoverFeeds() {
        val currentMood = DEFAULT_MOOD_CHANNELS.firstOrNull { it.id == _uiState.value.selectedMoodId }
            ?: DEFAULT_MOOD_CHANNELS.first()
        fetchDiscoverForChannel(currentMood, isInitialLoad = true)
    }

    fun selectMoodChannel(channel: MoodChannel) {
        triggerHaptic()
        val isRefresh = _uiState.value.selectedMoodId == channel.id && _uiState.value.moodTracks.isNotEmpty()
        if (isRefresh) {
            emitFeedback("Refreshing ${channel.label}…")
        }
        fetchDiscoverForChannel(channel, isInitialLoad = false, shuffleOrder = isRefresh)
    }

    fun setHomeFeedFilter(filter: HomeFeedFilter) {
        triggerHaptic()
        _uiState.update { it.copy(homeFeedFilter = filter) }
    }

    private fun fetchDiscoverForChannel(
        channel: MoodChannel,
        isInitialLoad: Boolean,
        shuffleOrder: Boolean = false
    ) {
        moodFetchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedMoodId = channel.id,
                activeMoodLabel = channel.label,
                isDiscoverLoading = isInitialLoad && it.moodTracks.isEmpty(),
                isMoodRefreshing = !isInitialLoad,
                discoverError = null
            )
        }

        moodFetchJob = viewModelScope.launch {
            val baseSeeds = if (channel.id == "foryou") {
                computePersonalizedArtistSeeds(channel.artistSeeds)
            } else {
                channel.artistSeeds.map { it.trim() }
            }
            val orderedArtistSeeds = if (shuffleOrder) baseSeeds.shuffled() else baseSeeds
            val orderedReleaseSeeds = if (shuffleOrder) channel.releaseSeeds.shuffled() else channel.releaseSeeds

            val spotifyCrateSeeds = listOf(
                "Melancholy Night Mix::Frank Ocean|Joji|Cigarettes After Sex|Radiohead" to "SPOTIFY MIX",
                "Good Mood Vinyl::Daft Punk|Kaytranada|Parcels|Jungle" to "SPOTIFY MIX",
                "Downtown Sweater Weather::The Neighbourhood|Arctic Monkeys|Tame Impala|Beach House" to "SPOTIFY MIX",
                "Late Night Analog::The Weeknd|Kavinsky|SZA|Fred again.." to "SPOTIFY MIX"
            )
            val ytmSessionSeeds = listOf(
                "Ultra Slowed & Reverb Radio::Travis Scott|The Weeknd|Don Toliver|Metro Boomin" to "YTM RADIO",
                "Tokyo Vinyl Listening Bar::Nujabes|Khruangbin|Ryuichi Sakamoto|Tom Misch" to "YTM SESSION",
                "Boiler Room Crate::Fred again..|Bicep|Disclosure|Four Tet" to "YTM LIVE",
                "Warm Tape Study::J Dilla|Men I Trust|Mac DeMarco|Clairo" to "YTM MIX"
            )
            val vinylPressingSeeds = orderedReleaseSeeds.map { rel ->
                "$rel::$rel" to "LP PRESSING"
            }

            coroutineScope {
                val playlistsDeferred = async {
                    repository.fetchHomeSuggestedPlaylists(channel.playlistSeeds)
                }
                val spCratesDeferred = async {
                    repository.fetchHomeSuggestedPlaylists(spotifyCrateSeeds)
                }
                val ytmSessionsDeferred = async {
                    repository.fetchHomeSuggestedPlaylists(ytmSessionSeeds)
                }
                val vinylDeferred = async {
                    repository.fetchHomeSuggestedPlaylists(vinylPressingSeeds)
                }
                val moodBucketsDeferred = orderedArtistSeeds.map { artistQuery ->
                    async {
                        repository.searchStreamingCatalog(artistQuery, limit = 6).getOrDefault(emptyList())
                    }
                }
                val releaseBucketsDeferred = orderedReleaseSeeds.map { releaseQuery ->
                    async {
                        repository.searchStreamingCatalog(releaseQuery, limit = 4).getOrDefault(emptyList())
                    }
                }

                val playlists = playlistsDeferred.await()
                val spCratesRaw = spCratesDeferred.await()
                val ytmSessionsRaw = ytmSessionsDeferred.await()
                val vinylRaw = vinylDeferred.await()
                val moodBuckets = moodBucketsDeferred.map { it.await() }
                val releaseBuckets = releaseBucketsDeferred.map { it.await() }

                val spTopForYou = if (channel.id == "foryou") {
                    _uiState.value.spotifyTopTracks.take(6)
                } else emptyList()

                val moodList = interleaveTrackLists(
                    buckets = if (spTopForYou.isNotEmpty()) listOf(spTopForYou) + moodBuckets else moodBuckets,
                    maxTotal = 18
                )
                val featured = interleaveTrackLists(releaseBuckets, maxTotal = 12)

                val paletteAccents = listOf(0xFF1ED760, 0xFFD71921, 0xFFA855F7, 0xFF38BDF8, 0xFFF59E0B)
                val nchordMixes = playlists.mapIndexed { idx, pl ->
                    pl.copy(
                        badge = "NCHORD",
                        accentHex = paletteAccents[(idx + 1) % paletteAccents.size]
                    )
                }
                val ytmSessions = ytmSessionsRaw.mapIndexed { idx, pl ->
                    pl.copy(
                        badge = "YTM",
                        isYoutubePlaylist = true,
                        accentHex = paletteAccents[(idx + 2) % paletteAccents.size]
                    )
                }
                val vinylPressings = vinylRaw.mapIndexed { idx, pl ->
                    pl.copy(
                        badge = "VINYL LP",
                        accentHex = paletteAccents[(idx + 3) % paletteAccents.size]
                    )
                }

                _uiState.update { state ->
                    val mergedSpotifyCrates = mergeSpotifyIntoCarousel(
                        state.spotifyPlaylists,
                        spCratesRaw.mapIndexed { idx, pl ->
                            pl.copy(
                                badge = "SPOTIFY",
                                accentHex = paletteAccents[idx % paletteAccents.size]
                            )
                        }
                    )
                    state.copy(
                        suggestedPlaylists = nchordMixes.ifEmpty { state.suggestedPlaylists },
                        spotifyCratePlaylists = mergedSpotifyCrates.ifEmpty { state.spotifyCratePlaylists },
                        ytmSessionPlaylists = ytmSessions.ifEmpty { state.ytmSessionPlaylists },
                        vinylPressingPlaylists = vinylPressings.ifEmpty { state.vinylPressingPlaylists },
                        featuredTracks = featured.ifEmpty { moodList.take(8) },
                        moodTracks = moodList,
                        isDiscoverLoading = false,
                        isMoodRefreshing = false,
                        discoverError = if (featured.isEmpty() && moodList.isEmpty()) {
                            "Offline. Check your network connection or play from Offline Vault."
                        } else null
                    )
                }
            }
        }
    }

    private fun mergeSpotifyIntoCarousel(
        spotifyLists: List<SpotifyPlaylistSummary>,
        curatedLists: List<SuggestedPlaylist>
    ): List<SuggestedPlaylist> {
        val spMapped = spotifyLists.take(6).map { sp ->
            SuggestedPlaylist(
                id = "sp_carousel_${sp.id}",
                title = sp.name,
                subtitle = sp.description.ifBlank { "${sp.trackCount} tracks • ${sp.ownerName}" },
                badge = "SPOTIFY",
                artworkUrl = sp.imageUrl.orEmpty(),
                trackCount = sp.trackCount,
                externalPlaylistId = sp.id,
                isSpotifyPlaylist = true,
                accentHex = 0xFF1ED760
            )
        }
        return (spMapped + curatedLists).distinctBy { it.id }
    }

    // =========================================================================
    // UNIVERSAL COLLECTION NAVIGATION (PLAYLISTS, ALBUMS, ARTISTS)
    // =========================================================================

    private fun pushCollectionPage(page: CollectionPageState) {
        _uiState.update { state ->
            state.copy(
                isFullPlayerOpen = false,
                isAccountPageOpen = false,
                collectionStack = (state.collectionStack.filterNot { it.id == page.id } + page).takeLast(5)
            )
        }
    }

    private fun updateCollectionPageById(pageId: String, transform: (CollectionPageState) -> CollectionPageState) {
        _uiState.update { state ->
            state.copy(
                collectionStack = state.collectionStack.map { p ->
                    if (p.id == pageId) transform(p) else p
                }
            )
        }
    }

    fun popCollectionPage() {
        triggerHaptic()
        activeUserPlaylistObserverJob?.cancel()
        _uiState.update { state ->
            state.copy(collectionStack = state.collectionStack.dropLast(1))
        }
    }

    fun openCarouselPlaylist(playlist: SuggestedPlaylist) {
        triggerHaptic()
        val pageId = "col_${playlist.id}"
        val type = when {
            playlist.isSpotifyPlaylist -> CollectionPageType.SPOTIFY_PLAYLIST
            playlist.isYoutubePlaylist -> CollectionPageType.YOUTUBE_PLAYLIST
            else -> CollectionPageType.CURATED_PLAYLIST
        }
        pushCollectionPage(
            CollectionPageState(
                id = pageId,
                type = type,
                title = playlist.title,
                subtitle = playlist.subtitle,
                badge = playlist.badge.uppercase(),
                artworkUrl = playlist.artworkUrl,
                tracks = playlist.preloadedTracks,
                isLoading = playlist.preloadedTracks.isEmpty(),
                spotifyPlaylistId = if (playlist.isSpotifyPlaylist) playlist.externalPlaylistId else null
            )
        )
        if (playlist.preloadedTracks.isEmpty()) {
            viewModelScope.launch {
                val tracks = repository.fetchCarouselPlaylistTracks(playlist)
                updateCollectionPageById(pageId) {
                    it.copy(
                        tracks = tracks,
                        artworkUrl = it.artworkUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun openUserPlaylist(playlist: UserPlaylist) {
        triggerHaptic()
        activeUserPlaylistObserverJob?.cancel()
        val pageId = "user_pl_${playlist.id}"
        pushCollectionPage(
            CollectionPageState(
                id = pageId,
                type = CollectionPageType.USER_PLAYLIST,
                title = playlist.name,
                subtitle = playlist.description.ifBlank { "${playlist.trackCount} songs" },
                badge = when {
                    !playlist.spotifyPlaylistId.isNullOrBlank() -> "SPOTIFY SYNCED"
                    !playlist.youtubePlaylistId.isNullOrBlank() -> "YTM SYNCED"
                    else -> "YOUR PLAYLIST"
                },
                artworkUrl = playlist.coverArtworkUrl,
                mosaicUrls = playlist.mosaicUrls,
                isLoading = true,
                userPlaylistId = playlist.id,
                spotifyPlaylistId = playlist.spotifyPlaylistId
            )
        )
        activeUserPlaylistObserverJob = viewModelScope.launch {
            repository.observeUserPlaylistTracks(playlist.id).collect { tracks ->
                val latestMeta = userPlaylists.value.firstOrNull { it.id == playlist.id }
                val mosaic = tracks.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4)
                updateCollectionPageById(pageId) { page ->
                    page.copy(
                        title = latestMeta?.name ?: page.title,
                        subtitle = latestMeta?.description?.takeIf { it.isNotBlank() } ?: "${tracks.size} songs",
                        artworkUrl = latestMeta?.coverArtworkUrl?.ifBlank { mosaic.firstOrNull().orEmpty() }
                            ?: page.artworkUrl,
                        mosaicUrls = mosaic,
                        tracks = tracks,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun openSpotifyPlaylist(playlist: SpotifyPlaylistSummary) {
        triggerHaptic()
        val pageId = "sp_pl_${playlist.id}"
        pushCollectionPage(
            CollectionPageState(
                id = pageId,
                type = CollectionPageType.SPOTIFY_PLAYLIST,
                title = playlist.name,
                subtitle = playlist.description.ifBlank { "${playlist.trackCount} songs • ${playlist.ownerName}" },
                badge = "SPOTIFY PLAYLIST",
                artworkUrl = playlist.imageUrl.orEmpty(),
                isLoading = true,
                spotifyPlaylistId = playlist.id
            )
        )
        viewModelScope.launch {
            val tracks = repository.fetchSpotifyPlaylistTracks(playlist.id).getOrDefault(emptyList())
            updateCollectionPageById(pageId) {
                it.copy(
                    tracks = tracks,
                    artworkUrl = it.artworkUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() },
                    isLoading = false
                )
            }
        }
    }

    fun openAlbumForTrack(track: TrackItem) {
        triggerHaptic()
        val pageId = "album_lookup_${track.id}"
        pushCollectionPage(
            CollectionPageState(
                id = pageId,
                type = CollectionPageType.ALBUM,
                title = track.album.ifBlank { track.title },
                subtitle = track.artist,
                badge = "ALBUM",
                artworkUrl = track.artworkUrl,
                isLoading = true,
                artistNameForNav = track.artist.substringBefore(",").trim()
            )
        )
        viewModelScope.launch {
            val resolvedPage = repository.fetchAlbumPage(track)
            updateCollectionPageById(pageId) {
                resolvedPage.copy(id = pageId)
            }
        }
    }

    fun openArtistPage(
        artistName: String,
        artistIdHint: String? = null,
        fallbackArtwork: String = ""
    ) {
        triggerHaptic()
        val cleanName = artistName.substringBefore(",").trim().ifBlank { artistName }
        val pageId = "artist_lookup_${cleanName.lowercase().hashCode()}"
        pushCollectionPage(
            CollectionPageState(
                id = pageId,
                type = CollectionPageType.ARTIST,
                title = cleanName,
                subtitle = "Loading artist discography…",
                badge = "ARTIST",
                artworkUrl = fallbackArtwork,
                isLoading = true,
                artistNameForNav = cleanName
            )
        )
        viewModelScope.launch {
            val resolved = repository.fetchArtistPage(cleanName, artistIdHint, fallbackArtwork)
            updateCollectionPageById(pageId) {
                resolved.copy(id = pageId)
            }
        }
    }

    fun openRecentGridTile(tile: RecentGridTile) {
        triggerHaptic()
        when (tile.kind) {
            RecentTileKind.LIKED_SONGS -> {
                val list = savedTracks.value
                pushCollectionPage(
                    CollectionPageState(
                        id = "col_liked_songs",
                        type = CollectionPageType.CURATED_PLAYLIST,
                        title = "Liked Songs",
                        subtitle = "${list.size} saved tracks",
                        badge = "SAVED VAULT",
                        artworkUrl = list.firstOrNull()?.artworkUrl.orEmpty(),
                        mosaicUrls = list.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
                        tracks = list,
                        isLoading = false
                    )
                )
            }
            RecentTileKind.DOWNLOADS -> {
                val list = downloadedTracks.value
                pushCollectionPage(
                    CollectionPageState(
                        id = "col_offline_downloads",
                        type = CollectionPageType.CURATED_PLAYLIST,
                        title = "Offline Vault",
                        subtitle = "${list.size} in-app offline songs",
                        badge = "OFFLINE VAULT",
                        artworkUrl = list.firstOrNull()?.artworkUrl.orEmpty(),
                        mosaicUrls = list.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
                        tracks = list,
                        isLoading = false
                    )
                )
            }
            RecentTileKind.ON_REPEAT -> {
                val list = (recentScrobbles.value + _uiState.value.moodTracks).distinctBy { it.id }.take(25)
                pushCollectionPage(
                    CollectionPageState(
                        id = "col_on_repeat",
                        type = CollectionPageType.CURATED_PLAYLIST,
                        title = "On Repeat",
                        subtitle = "Songs you have been spinning non-stop",
                        badge = "ROTATION",
                        artworkUrl = list.firstOrNull()?.artworkUrl.orEmpty(),
                        mosaicUrls = list.mapNotNull { it.artworkUrl.takeIf(String::isNotBlank) }.distinct().take(4),
                        tracks = list,
                        isLoading = false
                    )
                )
            }
            RecentTileKind.USER_PLAYLIST -> {
                val found = userPlaylists.value.firstOrNull { it.id == tile.referenceId }
                if (found != null) {
                    openUserPlaylist(found)
                }
            }
            RecentTileKind.SPOTIFY_PLAYLIST -> {
                val sp = _uiState.value.spotifyPlaylists.firstOrNull { it.id == tile.referenceId }
                if (sp != null) {
                    openSpotifyPlaylist(sp)
                }
            }
            RecentTileKind.CURATED_PLAYLIST -> {
                val all = _uiState.value.suggestedPlaylists +
                    _uiState.value.spotifyCratePlaylists +
                    _uiState.value.ytmSessionPlaylists +
                    _uiState.value.vinylPressingPlaylists
                val cur = all.firstOrNull { it.id == tile.referenceId }
                if (cur != null) {
                    openCarouselPlaylist(cur)
                }
            }
        }
    }

    fun playCarouselPlaylistImmediately(playlist: SuggestedPlaylist, shuffle: Boolean = false) {
        triggerConfirmHaptic()
        emitFeedback(if (shuffle) "Shuffling ${playlist.title}" else "Playing ${playlist.title}")
        viewModelScope.launch {
            repository.recordRecentCollectionPlay(
                id = "cur_${playlist.id}",
                title = playlist.title,
                subtitle = playlist.badge,
                artworkUrl = playlist.artworkUrl,
                kind = RecentTileKind.CURATED_PLAYLIST,
                referenceId = playlist.id
            )
            val rawTracks = if (playlist.preloadedTracks.isNotEmpty()) {
                playlist.preloadedTracks
            } else {
                repository.fetchCarouselPlaylistTracks(playlist)
            }
            if (rawTracks.isNotEmpty()) {
                val list = if (shuffle) rawTracks.shuffled() else rawTracks
                playTrackFromContext(list.first(), list, silentFeedback = true)
            }
        }
    }

    // =========================================================================
    // EXTENDED SONG MENU, QUEUE ACTIONS, & PLAYLIST MANAGEMENT
    // =========================================================================

    fun openTrackOptions(track: TrackItem, playlistContextId: String? = null) {
        triggerHaptic()
        _uiState.update {
            it.copy(
                contextMenuTrack = track,
                contextMenuPlaylistId = playlistContextId
            )
        }
    }

    fun dismissTrackOptions() {
        _uiState.update {
            it.copy(
                contextMenuTrack = null,
                contextMenuPlaylistId = null
            )
        }
    }

    fun openAddToPlaylistSheet(track: TrackItem) {
        triggerHaptic()
        _uiState.update { it.copy(addToPlaylistTargetTrack = track) }
    }

    fun dismissAddToPlaylistSheet() {
        _uiState.update { it.copy(addToPlaylistTargetTrack = null) }
    }

    fun addTrackToQueueEnd(track: TrackItem) {
        triggerConfirmHaptic()
        val currentQ = _uiState.value.queue
        if (currentQ.isEmpty() || audioState.value.currentTrack == null) {
            playTrackFromContext(track, listOf(track), silentFeedback = false)
            return
        }
        _uiState.update { state ->
            state.copy(queue = state.queue + track)
        }
        emitFeedback("Added to Queue • ${track.title}")
        viewModelScope.launch { repository.prewarmTrackStream(track) }
    }

    fun playTrackNext(track: TrackItem) {
        triggerConfirmHaptic()
        val state = _uiState.value
        if (state.queue.isEmpty() || audioState.value.currentTrack == null) {
            playTrackFromContext(track, listOf(track), silentFeedback = false)
            return
        }
        val insertIdx = (state.currentQueueIndex + 1).coerceIn(0, state.queue.size)
        val updated = state.queue.toMutableList().apply { add(insertIdx, track) }
        _uiState.update { it.copy(queue = updated) }
        emitFeedback("Playing Next • ${track.title}")
        viewModelScope.launch { repository.prewarmTrackStream(track) }
    }

    fun startTrackRadio(seedTrack: TrackItem) {
        triggerConfirmHaptic()
        emitFeedback("Starting Radio • ${seedTrack.title}")
        viewModelScope.launch {
            val res = repository.searchStreamingCatalog(
                "${seedTrack.artist.substringBefore(",")} ${seedTrack.title}",
                limit = 18
            )
            val radioQueue = (listOf(seedTrack) + res.getOrDefault(emptyList())).distinctBy { it.id }
            playTrackFromContext(seedTrack, radioQueue, silentFeedback = true)
        }
    }

    fun createNewUserPlaylist(name: String, description: String, initialTrack: TrackItem? = null) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            val created = repository.createUserPlaylist(name, description, syncToSpotify = true)
            if (initialTrack != null) {
                repository.addTrackToUserPlaylist(created.id, initialTrack)
                emitFeedback("Added to ${created.name}")
            } else {
                emitFeedback("Created ${created.name}")
            }
        }
    }

    fun renameUserPlaylist(playlistId: String, name: String, description: String) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            repository.renameUserPlaylist(playlistId, name, description)
            emitFeedback("Playlist updated")
        }
    }

    fun deleteUserPlaylist(playlistId: String) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            repository.deleteUserPlaylist(playlistId)
            emitFeedback("Playlist deleted")
        }
    }

    fun addTrackToExistingUserPlaylist(playlist: UserPlaylist, track: TrackItem) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            repository.addTrackToUserPlaylist(playlist.id, track)
            emitFeedback("Saved to ${playlist.name}")
        }
    }

    fun addTrackToConnectedSpotifyPlaylist(spPlaylist: SpotifyPlaylistSummary, track: TrackItem) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            val ok = repository.addTrackToSpotifyPlaylist(spPlaylist.id, track)
            emitFeedback(if (ok) "Synced to ${spPlaylist.name}" else "Unable to sync track to Spotify")
        }
    }

    fun removeTrackFromUserPlaylist(playlistId: String, trackId: String) {
        triggerHaptic()
        viewModelScope.launch {
            repository.removeTrackFromUserPlaylist(playlistId, trackId)
            emitFeedback("Removed from playlist")
        }
    }

    fun moveTrackInUserPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        triggerHaptic()
        viewModelScope.launch {
            repository.moveTrackInUserPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun saveCollectionToLibrary(page: CollectionPageState) {
        if (page.tracks.isEmpty()) return
        triggerConfirmHaptic()
        viewModelScope.launch {
            val saved = repository.cloneCollectionAsUserPlaylist(
                name = page.title,
                description = page.subtitle,
                artworkUrl = page.artworkUrl,
                tracks = page.tracks,
                spotifyPlaylistId = page.spotifyPlaylistId
            )
            emitFeedback("Saved ${saved.name} to Library")
        }
    }

    fun togglePinLibraryItem(itemKey: String, label: String = "Item") {
        viewModelScope.launch {
            when (repository.togglePinLibraryItem(itemKey)) {
                PinToggleOutcome.PINNED -> {
                    triggerConfirmHaptic()
                    emitFeedback("Pinned • $label")
                }
                PinToggleOutcome.UNPINNED -> {
                    triggerHaptic()
                    emitFeedback("Unpinned • $label")
                }
                PinToggleOutcome.LIMIT_REACHED -> {
                    triggerHaptic()
                    emitFeedback("Max 4 items can be pinned")
                }
            }
        }
    }

    fun downloadTrackOffline(track: TrackItem) {
        if (downloadProgress.value.containsKey(track.id)) return
        triggerConfirmHaptic()
        emitFeedback("Downloading • ${track.title}")
        viewModelScope.launch {
            val res = repository.downloadTrackForOffline(track)
            res.fold(
                onSuccess = {
                    emitFeedback("Downloaded for offline • ${track.title}")
                },
                onFailure = { err ->
                    emitFeedback(err.localizedMessage ?: "Download failed")
                }
            )
        }
    }

    fun removeOfflineDownload(track: TrackItem) {
        triggerHaptic()
        viewModelScope.launch {
            repository.removeOfflineDownload(track.id)
            emitFeedback("Removed offline copy • ${track.title}")
        }
    }

    fun downloadCollectionOffline(tracks: List<TrackItem>, collectionTitle: String) {
        val pending = tracks.filter { it.id !in downloadedTrackIds.value }
        if (pending.isEmpty()) {
            emitFeedback("All songs already downloaded")
            return
        }
        triggerConfirmHaptic()
        emitFeedback("Downloading ${pending.size} songs • $collectionTitle")
        viewModelScope.launch {
            var completed = 0
            pending.forEach { t ->
                if (repository.downloadTrackForOffline(t).isSuccess) {
                    completed++
                }
            }
            emitFeedback("Offline Vault ready • $completed/${pending.size} saved")
        }
    }

    fun clearPlaybackQueue() {
        triggerHaptic()
        val current = audioState.value.currentTrack
        _uiState.update {
            it.copy(
                queue = if (current != null) listOf(current) else emptyList(),
                currentQueueIndex = if (current != null) 0 else -1
            )
        }
        emitFeedback("Up Next cleared")
    }

    fun setSleepTimer(minutes: Int?) {
        triggerConfirmHaptic()
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerRemainingMinutes = minutes) }
        when {
            minutes == null -> emitFeedback("Sleep timer off")
            minutes == -1 -> emitFeedback("Sleep at end of track")
            minutes > 0 -> {
                emitFeedback("Sleep timer • ${minutes}m")
                sleepTimerJob = viewModelScope.launch {
                    var rem = minutes
                    while (rem > 0) {
                        delay(60_000L)
                        rem -= 1
                        _uiState.update { it.copy(sleepTimerRemainingMinutes = rem.takeIf { r -> r > 0 }) }
                    }
                    if (audioState.value.isPlaying) {
                        audioEngine.togglePlayPause()
                    }
                    _uiState.update { it.copy(sleepTimerRemainingMinutes = null) }
                    emitFeedback("Sleep timer paused playback")
                }
            }
        }
    }

    fun connectLastFm(username: String, passwordOptional: String) {
        triggerConfirmHaptic()
        _remoteScrobblerMeta.update { it.copy(isSyncing = true, statusMessage = "Connecting Last.fm…") }
        viewModelScope.launch {
            val res = repository.connectLastFmAccount(username, passwordOptional)
            res.fold(
                onSuccess = { snap ->
                    _remoteScrobblerMeta.update {
                        it.copy(
                            remotePlayCount = snap.totalPlayCount,
                            remoteTopArtists = snap.topArtists,
                            remoteRecentTracks = snap.recentTracks,
                            isSyncing = false,
                            statusMessage = snap.statusSummary
                        )
                    }
                    emitFeedback("Last.fm Connected • ${username.trim()}")
                },
                onFailure = { err ->
                    _remoteScrobblerMeta.update {
                        it.copy(isSyncing = false, statusMessage = err.localizedMessage ?: "Last.fm error")
                    }
                    emitFeedback(err.localizedMessage ?: "Last.fm connection failed")
                }
            )
        }
    }

    fun connectListenBrainz(username: String, tokenOptional: String) {
        triggerConfirmHaptic()
        _remoteScrobblerMeta.update { it.copy(isSyncing = true, statusMessage = "Connecting ListenBrainz…") }
        viewModelScope.launch {
            val res = repository.connectListenBrainzAccount(username, tokenOptional)
            res.fold(
                onSuccess = { snap ->
                    _remoteScrobblerMeta.update {
                        it.copy(
                            remotePlayCount = snap.totalPlayCount,
                            remoteTopArtists = snap.topArtists,
                            remoteRecentTracks = snap.recentTracks,
                            isSyncing = false,
                            statusMessage = snap.statusSummary
                        )
                    }
                    emitFeedback("ListenBrainz Connected • ${username.trim()}")
                },
                onFailure = { err ->
                    _remoteScrobblerMeta.update {
                        it.copy(isSyncing = false, statusMessage = err.localizedMessage ?: "ListenBrainz error")
                    }
                    emitFeedback(err.localizedMessage ?: "ListenBrainz connection failed")
                }
            )
        }
    }

    fun syncScrobblerNow(silent: Boolean = false) {
        if (!silent) triggerHaptic()
        _remoteScrobblerMeta.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val snap = repository.refreshScrobblerRemoteSnapshot()
            _remoteScrobblerMeta.update {
                it.copy(
                    remotePlayCount = snap.totalPlayCount,
                    remoteTopArtists = snap.topArtists,
                    remoteRecentTracks = snap.recentTracks,
                    isSyncing = false,
                    statusMessage = snap.statusSummary.ifBlank { "Synced" }
                )
            }
            if (!silent) emitFeedback("Scrobbler & Insights synced")
        }
    }

    fun disconnectScrobbler() {
        triggerHaptic()
        viewModelScope.launch {
            repository.disconnectScrobbler()
            _remoteScrobblerMeta.value = ScrobblerConfigAndState()
            emitFeedback("Scrobbler disconnected")
        }
    }

    fun importPlaylistFromUrl(url: String) {
        triggerConfirmHaptic()
        emitFeedback("Syncing playlist…")
        viewModelScope.launch {
            val res = repository.importExternalPlaylistUrl(url)
            res.fold(
                onSuccess = { pl ->
                    emitFeedback("Synced • ${pl.name}")
                    openUserPlaylist(pl)
                },
                onFailure = { err ->
                    emitFeedback(err.localizedMessage ?: "Playlist import failed")
                }
            )
        }
    }

    // =========================================================================
    // NAVIGATION & SEARCH (ALL / SONGS / PLAYLISTS / SPOTIFY)
    // =========================================================================

    fun selectTab(tab: NothingTab) {
        if (_uiState.value.isAccountPageOpen) {
            _uiState.update { it.copy(isAccountPageOpen = false) }
        }
        if (_uiState.value.activeCollectionPage != null) {
            activeUserPlaylistObserverJob?.cancel()
            _uiState.update { it.copy(collectionStack = emptyList()) }
            if (_uiState.value.activeTab == tab) return
        }
        if (_uiState.value.activeTab == tab) return
        triggerHaptic()
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setAccountPageOpen(open: Boolean) {
        triggerHaptic()
        _uiState.update {
            it.copy(
                isAccountPageOpen = open,
                isFullPlayerOpen = if (open) false else it.isFullPlayerOpen
            )
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        triggerHaptic()
        val updated = (_uiState.value.lyricsOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        _uiState.update { it.copy(lyricsOffsetMs = updated) }
        val sec = updated / 1000.0
        val sign = if (sec > 0) "+" else ""
        emitFeedback("Lyrics sync • ${sign}${"%.1f".format(sec)}s")
    }

    fun resetLyricsOffset() {
        triggerHaptic()
        _uiState.update { it.copy(lyricsOffsetMs = 0L) }
        emitFeedback("Lyrics sync reset")
    }

    fun setFullPlayerOpen(open: Boolean) {
        triggerHaptic()
        _uiState.update { it.copy(isFullPlayerOpen = open) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null, searchNotice = null) }
        searchDebounceJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    searchPlaylistsResults = emptyList(),
                    isSearching = false
                )
            }
            return
        }
        searchDebounceJob = viewModelScope.launch {
            delay(260L)
            executeSearch(query.trim())
        }
    }

    fun setSearchFilter(filter: SearchFilter) {
        triggerHaptic()
        _uiState.update { it.copy(searchFilter = filter) }
    }

    fun toggleSearchSource(useSpotify: Boolean) {
        triggerHaptic()
        _uiState.update { it.copy(searchUseSpotify = useSpotify, searchError = null, searchNotice = null) }
        val q = _uiState.value.searchQuery.trim()
        if (q.length >= 2) {
            viewModelScope.launch { executeSearch(q) }
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, searchError = null, searchNotice = null) }
        val useSp = _uiState.value.searchUseSpotify

        if (useSp) {
            val spRes = repository.searchSpotifyCatalogRich(query)
            spRes.fold(
                onSuccess = { payload ->
                    _uiState.update {
                        it.copy(
                            searchResults = payload.tracks,
                            searchPlaylistsResults = payload.playlists,
                            isSearching = false,
                            searchNotice = if (!payload.usedLiveSpotifyApi && !it.spotifyProfile.isConnected) {
                                "Showing open catalog • Connect your Spotify Client ID in Studio for personal Spotify sync"
                            } else null,
                            searchError = if (payload.tracks.isEmpty() && payload.playlists.isEmpty()) {
                                "No matches for \"$query\""
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            searchPlaylistsResults = emptyList(),
                            isSearching = false,
                            searchError = err.localizedMessage ?: "Search unavailable"
                        )
                    }
                }
            )
        } else {
            coroutineScope {
                val tracksDeferred = async { repository.searchStreamingCatalog(query, limit = 24) }
                val playlistsDeferred = async { repository.searchPlaylists(query, limit = 8) }
                val tracksRes = tracksDeferred.await()
                val playlists = playlistsDeferred.await()

                tracksRes.fold(
                    onSuccess = { tracks ->
                        _uiState.update {
                            it.copy(
                                searchResults = tracks,
                                searchPlaylistsResults = playlists,
                                isSearching = false,
                                searchError = if (tracks.isEmpty() && playlists.isEmpty()) {
                                    "No matches for \"$query\""
                                } else null
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                searchResults = emptyList(),
                                searchPlaylistsResults = playlists,
                                isSearching = false,
                                searchError = if (playlists.isEmpty()) {
                                    err.localizedMessage ?: "Search unavailable"
                                } else null
                            )
                        }
                    }
                )
            }
        }
    }

    // =========================================================================
    // SPOTIFY PKCE OAUTH & TWO-WAY ACCOUNT SYNC
    // =========================================================================

    fun launchSpotifyOAuth(clientIdInput: String, onLaunchBrowser: (Uri) -> Unit) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            val uri = repository.prepareSpotifyAuthUri(clientIdInput)
            if (uri != null) {
                onLaunchBrowser(uri)
            } else {
                _uiState.update {
                    it.copy(spotifyStatusBanner = "Enter a valid Spotify Client ID first.")
                }
            }
        }
    }

    fun handleIncomingOAuthIntent(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == "nothingchord" && uri.host == "callback") {
            val error = uri.getQueryParameter("error")
            val code = uri.getQueryParameter("code")
            if (!error.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isAccountPageOpen = true,
                        spotifyStatusBanner = "Authorization cancelled ($error)"
                    )
                }
                return
            }
            if (!code.isNullOrBlank()) {
                viewModelScope.launch {
                    repository.completeOnboarding()
                    _uiState.update {
                        it.copy(
                            isAccountPageOpen = true,
                            isSpotifyLoading = true,
                            spotifyStatusBanner = "Connecting Spotify PKCE…"
                        )
                    }
                    val exchange = repository.handleSpotifyOAuthCode(code)
                    if (exchange.isSuccess) {
                        emitFeedback("Spotify Connected & Syncing")
                        syncSpotifyData()
                    } else {
                        _uiState.update {
                            it.copy(
                                isSpotifyLoading = false,
                                spotifyStatusBanner = "Token exchange failed. Verify Redirect URI nothingchord://callback"
                            )
                        }
                    }
                }
            }
        }
    }

    fun syncSpotifyData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSpotifyLoading = true, spotifyStatusBanner = null) }
            val result = repository.fetchSpotifyDashboard()
            result.fold(
                onSuccess = { (profile, playlists, topTracks) ->
                    _uiState.update { state ->
                        val pureCrates = state.spotifyCratePlaylists.filterNot { it.isSpotifyPlaylist }
                        state.copy(
                            spotifyProfile = profile,
                            spotifyPlaylists = playlists,
                            spotifyTopTracks = topTracks,
                            spotifyCratePlaylists = mergeSpotifyIntoCarousel(playlists, pureCrates),
                            isSpotifyLoading = false,
                            spotifyStatusBanner = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSpotifyLoading = false,
                            spotifyProfile = SpotifyProfileState(isConnected = false),
                            spotifyStatusBanner = err.localizedMessage ?: "Spotify sync failed"
                        )
                    }
                }
            )
        }
    }

    fun disconnectSpotifyAccount() {
        triggerConfirmHaptic()
        emitFeedback("Spotify Disconnected")
        viewModelScope.launch {
            repository.disconnectSpotify()
            _uiState.update { state ->
                state.copy(
                    spotifyProfile = SpotifyProfileState(isConnected = false),
                    spotifyPlaylists = emptyList(),
                    spotifyTopTracks = emptyList(),
                    suggestedPlaylists = state.suggestedPlaylists.filterNot { it.isSpotifyPlaylist },
                    spotifyStatusBanner = "Disconnected"
                )
            }
        }
    }

    fun triggerSpotifyRemoteAction(action: String) {
        triggerHaptic()
        viewModelScope.launch {
            val ok = repository.sendSpotifyRemoteTransport(action)
            if (ok) {
                delay(350L)
                syncSpotifyData()
            } else {
                _uiState.update {
                    it.copy(spotifyStatusBanner = "Requires an active Spotify Premium device")
                }
            }
        }
    }

    // =========================================================================
    // AUDIO ENGINE PLAYBACK & BITCHORD QUALITY TIERS
    // =========================================================================

    fun playTrackFromContext(
        track: TrackItem,
        contextQueue: List<TrackItem> = listOf(track),
        silentFeedback: Boolean = false
    ) {
        triggerConfirmHaptic()
        playTrackJob?.cancel()
        lyricsResyncJob?.cancel()
        streamRetryAttempt = 0
        lastLyricsTrackId = track.id
        lastLyricsDurationMs = track.durationMs

        val effectiveQueue = if (contextQueue.isEmpty()) listOf(track) else contextQueue
        val idx = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val currentSettings = glyphSettings.value

        audioEngine.prepareImmediateFeedback(track, currentSettings.audioQualityTier)
        if (!silentFeedback) {
            emitFeedback("Playing • ${track.title}")
        }

        _uiState.update {
            it.copy(
                queue = effectiveQueue,
                currentQueueIndex = idx,
                lyrics = emptyList(),
                isLyricsLoading = true,
                lyricsOffsetMs = 0L
            )
        }

        playTrackJob = viewModelScope.launch {
            // Fetch synced lyrics immediately in parallel with stream resolution
            val lyricsJob = launch {
                val fetchedLyrics = repository.fetchSyncedLyrics(
                    trackTitle = track.title,
                    artistName = track.artist,
                    durationMs = track.durationMs
                )
                _uiState.update {
                    it.copy(
                        lyrics = fetchedLyrics,
                        isLyricsLoading = false
                    )
                }
            }

            var resolved = kotlinx.coroutines.withTimeoutOrNull(8_500L) {
                repository.resolvePlayableStream(track, forceAlternate = false, attemptIndex = 0)
            }
            if (resolved == null || resolved.url.isNullOrBlank()) {
                streamRetryAttempt = 1
                resolved = kotlinx.coroutines.withTimeoutOrNull(8_500L) {
                    repository.resolvePlayableStream(track, forceAlternate = true, attemptIndex = 1)
                }
            }
            if (resolved == null || resolved.url.isNullOrBlank()) {
                streamRetryAttempt = 2
                resolved = repository.resolvePlayableStream(track, forceAlternate = true, attemptIndex = 2)
            }

            val isOfflineVaultPlay = resolved.containerLabel == "IN-APP VAULT" ||
                track.id in downloadedTrackIds.value
            val enrichedTrack = track.copy(
                audioStreamUrl = resolved.url ?: track.audioStreamUrl,
                durationMs = resolved.durationMs.coerceAtLeast(30_000L),
                streamQualityBadge = if (isOfflineVaultPlay) {
                    "OFFLINE • ${resolved.codecLabel}"
                } else {
                    "${resolved.codecLabel} • ${resolved.bitrateKbps}k"
                },
                isDownloaded = isOfflineVaultPlay
            )
            audioEngine.playTrack(
                track = enrichedTrack,
                resolved = resolved,
                qualityTier = currentSettings.audioQualityTier,
                bitPerfectUsb = currentSettings.bitPerfectUsbDac,
                allowRetryCallback = true
            )

            // Record recent playlist/collection tile when playing inside a collection page
            _uiState.value.activeCollectionPage?.let { activePage ->
                val (tileId, kind, refId) = when (activePage.type) {
                    CollectionPageType.USER_PLAYLIST -> Triple(
                        "upl_${activePage.userPlaylistId}",
                        RecentTileKind.USER_PLAYLIST,
                        activePage.userPlaylistId.orEmpty()
                    )
                    CollectionPageType.SPOTIFY_PLAYLIST -> Triple(
                        "sp_${activePage.spotifyPlaylistId}",
                        RecentTileKind.SPOTIFY_PLAYLIST,
                        activePage.spotifyPlaylistId.orEmpty()
                    )
                    else -> Triple(
                        "cur_${activePage.id.removePrefix("col_")}",
                        RecentTileKind.CURATED_PLAYLIST,
                        activePage.id.removePrefix("col_")
                    )
                }
                if (refId.isNotBlank()) {
                    launch {
                        repository.recordRecentCollectionPlay(
                            id = tileId,
                            title = activePage.title,
                            subtitle = activePage.badge,
                            artworkUrl = activePage.artworkUrl.ifBlank { enrichedTrack.artworkUrl },
                            kind = kind,
                            referenceId = refId
                        )
                    }
                }
            }

            // Unified play tracking: records in Nchord Insights DB + Last.fm/ListenBrainz + Spotify stats sync
            launch {
                repository.recordScrobbleAndSync(enrichedTrack, isOfflinePlay = isOfflineVaultPlay)
                if (_uiState.value.spotifyProfile.isConnected) {
                    repository.fetchSpotifyDashboard().onSuccess { (profile, _, topTracks) ->
                        _uiState.update { s ->
                            s.copy(spotifyProfile = profile, spotifyTopTracks = topTracks)
                        }
                    }
                }
            }

            effectiveQueue.getOrNull(idx + 1)?.let { nextTrack ->
                launch { repository.prewarmTrackStream(nextTrack) }
            }

            lyricsJob.join()
        }
    }

    private fun retryWithAlternateEngine(failedTrack: TrackItem) {
        viewModelScope.launch {
            val nextAttempt = streamRetryAttempt + 1
            if (nextAttempt > 3) return@launch
            streamRetryAttempt = nextAttempt
            val currentSettings = glyphSettings.value
            val altResolved = repository.resolvePlayableStream(
                track = failedTrack,
                forceAlternate = true,
                attemptIndex = nextAttempt
            )
            if (!altResolved.url.isNullOrBlank()) {
                val updated = failedTrack.copy(
                    audioStreamUrl = altResolved.url,
                    durationMs = altResolved.durationMs.coerceAtLeast(30_000L),
                    streamQualityBadge = "${altResolved.codecLabel} • ${altResolved.bitrateKbps}k"
                )
                audioEngine.playTrack(
                    track = updated,
                    resolved = altResolved,
                    qualityTier = currentSettings.audioQualityTier,
                    bitPerfectUsb = currentSettings.bitPerfectUsbDac,
                    allowRetryCallback = nextAttempt < 3
                )
            }
        }
    }

    fun setAudioQualityTier(tier: AudioQualityTier) {
        triggerConfirmHaptic()
        emitFeedback("Quality • ${tier.title}")
        viewModelScope.launch {
            repository.setAudioQualityTier(tier)
        }
    }

    fun setMobileAudioQualityTier(tier: AudioQualityTier) {
        triggerHaptic()
        viewModelScope.launch { repository.setMobileAudioQualityTier(tier) }
    }

    fun setBitPerfectUsbDac(enabled: Boolean) {
        triggerHaptic()
        emitFeedback(if (enabled) "Bit-Perfect USB DAC On" else "USB Direct Off")
        viewModelScope.launch { repository.setBitPerfectUsbDac(enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        triggerHaptic()
        val clamped = speed.coerceIn(0.5f, 2.0f)
        audioEngine.setPlaybackSpeed(clamped)
        viewModelScope.launch { repository.setPlaybackSpeed(clamped) }
    }

    fun togglePlayPause() {
        triggerHaptic()
        audioEngine.togglePlayPause()
    }

    fun seekToPosition(positionMs: Long) {
        triggerHaptic()
        audioEngine.seekTo(positionMs)
    }

    fun skipNext() {
        triggerHaptic()
        val state = _uiState.value
        val q = state.queue
        if (q.isEmpty()) return

        val nextIdx = if (state.shuffleEnabled && q.size > 1) {
            q.indices.filter { it != state.currentQueueIndex }.random()
        } else {
            val candidate = state.currentQueueIndex + 1
            if (candidate < q.size) {
                candidate
            } else if (state.repeatMode == RepeatModeState.ALL) {
                0
            } else {
                if (glyphSettings.value.automixEnabled) {
                    triggerAutomixExtension(q.last())
                }
                return
            }
        }
        playTrackFromContext(q[nextIdx], q, silentFeedback = true)
    }

    fun skipPrevious() {
        triggerHaptic()
        if (audioState.value.currentPositionMs > 3_500L) {
            audioEngine.seekTo(0L)
            return
        }
        val state = _uiState.value
        val q = state.queue
        if (q.isEmpty()) return
        val prevIdx = (state.currentQueueIndex - 1).coerceAtLeast(0)
        playTrackFromContext(q[prevIdx], q, silentFeedback = true)
    }

    private fun handleTrackCompleted() {
        val state = _uiState.value
        if (state.sleepTimerRemainingMinutes == -1) {
            _uiState.update { it.copy(sleepTimerRemainingMinutes = null) }
            emitFeedback("Sleep timer ended playback")
            return
        }
        if (state.repeatMode == RepeatModeState.ONE) {
            val current = audioState.value.currentTrack ?: return
            playTrackFromContext(current, state.queue, silentFeedback = true)
            return
        }
        skipNext()
    }

    private fun triggerAutomixExtension(seedTrack: TrackItem) {
        viewModelScope.launch {
            val relatedRes = repository.searchStreamingCatalog(seedTrack.artist, limit = 10)
            val existingIds = _uiState.value.queue.map { it.id }.toSet()
            val freshTracks = relatedRes.getOrDefault(emptyList()).filter { it.id !in existingIds }
            if (freshTracks.isNotEmpty()) {
                val expandedQueue = _uiState.value.queue + freshTracks
                playTrackFromContext(freshTracks.first(), expandedQueue, silentFeedback = true)
            }
        }
    }

    fun toggleShuffle() {
        triggerHaptic()
        val nextVal = !_uiState.value.shuffleEnabled
        _uiState.update { it.copy(shuffleEnabled = nextVal) }
        emitFeedback(if (nextVal) "Shuffle On" else "Shuffle Off")
    }

    fun cycleRepeatMode() {
        triggerHaptic()
        val next = when (_uiState.value.repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        _uiState.update { it.copy(repeatMode = next) }
        emitFeedback(
            when (next) {
                RepeatModeState.OFF -> "Repeat Off"
                RepeatModeState.ALL -> "Repeat Queue"
                RepeatModeState.ONE -> "Repeat One"
            }
        )
    }

    fun toggleSaveTrack(track: TrackItem) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            val nowSaved = repository.toggleSaveTrack(track)
            val syncSuffix = if (_uiState.value.spotifyProfile.isConnected) " & Spotify" else ""
            emitFeedback(if (nowSaved) "Saved to Library$syncSuffix" else "Removed from Saved")
        }
    }

    fun importLocalAudioUri(uri: Uri) {
        triggerConfirmHaptic()
        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            var displayName = "Local Track"
            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx)
                            ?.substringBeforeLast(".")
                            ?: displayName
                    }
                }
            }

            val importedTrack = TrackItem(
                id = "local_${uri.toString().hashCode()}",
                title = displayName,
                artist = "Device Storage",
                album = "Local",
                artworkUrl = "",
                audioStreamUrl = uri.toString(),
                durationMs = 180_000L,
                sourceType = AudioSourceType.LOCAL_FILE
            )

            _uiState.update { state ->
                val updated = listOf(importedTrack) + state.localImportedTracks.filter { it.id != importedTrack.id }
                state.copy(localImportedTracks = updated)
            }
            emitFeedback("Imported • $displayName")
            playTrackFromContext(
                importedTrack,
                listOf(importedTrack) + _uiState.value.localImportedTracks,
                silentFeedback = true
            )
        }
    }

    fun setOledDarkMode(enabled: Boolean) {
        triggerHaptic()
        viewModelScope.launch { repository.setOledDarkMode(enabled) }
    }

    fun setGlyphReactive(enabled: Boolean) {
        triggerHaptic()
        viewModelScope.launch { repository.setGlyphReactive(enabled) }
    }

    fun setAutomixEnabled(enabled: Boolean) {
        triggerHaptic()
        viewModelScope.launch { repository.setAutomixEnabled(enabled) }
    }

    fun setTactileHaptics(enabled: Boolean) {
        audioEngine.triggerDetentHaptic(true)
        viewModelScope.launch { repository.setTactileHaptics(enabled) }
    }

    fun triggerHaptic() {
        audioEngine.triggerDetentHaptic(glyphSettings.value.tactileHaptics)
    }

    fun triggerConfirmHaptic() {
        audioEngine.triggerConfirmHaptic(glyphSettings.value.tactileHaptics)
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    val db = NothingChordDatabase.getInstance(appCtx)
                    val prefs = NothingPreferencesStore(appCtx)
                    val dlManager = InAppDownloadManager(appCtx)
                    val repo = NothingMusicRepository(db.musicDao(), prefs, dlManager)
                    val engine = NothingAudioEngine(appCtx)
                    return NothingChordViewModel(appCtx, repo, engine) as T
                }
            }
    }
}
