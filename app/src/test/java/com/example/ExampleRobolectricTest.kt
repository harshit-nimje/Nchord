package com.example

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NothingChordDatabase
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.UserPlaylistEntity
import com.example.data.model.AudioQualityTier
import com.example.data.model.AudioSourceType
import com.example.data.model.GlyphSettings
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.model.SpotifyProfileState
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.data.remote.FullStreamEngine
import com.example.data.remote.SpotifyPkceHelper
import com.example.ui.NothingUiState
import com.example.ui.SearchFilter
import com.example.ui.components.FrostedGlassCapsule
import com.example.ui.components.LocalGlassBackdrop
import com.example.ui.components.LocalInsideBackdropRecording
import com.example.ui.components.recordGlassBackdrop
import com.example.ui.components.rememberGlassBackdropRig
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SourcesAndGlyphScreen
import com.example.ui.theme.NothingChordTheme
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app name from context matches Nchord`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nchord", appName)
    }

    @Test
    fun `spotify pkce s256 challenge state nonce and redirect uri generation enforces least privilege scopes`() {
        val verifier = SpotifyPkceHelper.generateCodeVerifier()
        assertTrue(verifier.length >= 43)
        val state = SpotifyPkceHelper.generateStateNonce()
        assertTrue(state.length >= 24)
        val challenge = SpotifyPkceHelper.generateCodeChallenge(verifier)
        assertTrue(challenge.isNotBlank())
        val authUri = SpotifyPkceHelper.buildAuthorizationUri("test_client_123", challenge, state)
        assertEquals("https", authUri.scheme)
        assertEquals("accounts.spotify.com", authUri.host)
        assertEquals("nothingchord://callback", authUri.getQueryParameter("redirect_uri"))
        assertEquals("S256", authUri.getQueryParameter("code_challenge_method"))
        assertEquals(state, authUri.getQueryParameter("state"))
        val scopes = authUri.getQueryParameter("scope").orEmpty()
        assertTrue(scopes.contains("user-library-read"))
        assertTrue(scopes.contains("user-modify-playback-state"))
        org.junit.Assert.assertFalse(scopes.contains("user-library-modify"))
        org.junit.Assert.assertFalse(scopes.contains("playlist-modify-private"))
    }

    @Test
    fun `des ecb stream decryption upgrades http 96 to https 320 mp4`() {
        val sampleRawUrl = "http://aac.saavncdn.com/420/sample_full_song_96.mp4"
        val keySpec = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(sampleRawUrl.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

        val master320 = FullStreamEngine.decryptSaavnMediaUrl(encrypted, prefer320 = true)
        assertEquals("https://aac.saavncdn.com/420/sample_full_song_320.mp4", master320)

        val standard = FullStreamEngine.decryptSaavnMediaUrl(encrypted, prefer320 = false)
        assertEquals("https://aac.saavncdn.com/420/sample_full_song_160.mp4", standard)
    }

    @Test
    fun `canonical track matcher chooses original studio album version over acoustic cover live slowed and yt video`() {
        val candidates = listOf(
            TrackItem(
                id = "acoustic_ep",
                title = "Creep (Acoustic)",
                artist = "Radiohead",
                album = "My Iron Lung",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/acoustic.mp4",
                durationMs = 259_000L
            ),
            TrackItem(
                id = "cover_band",
                title = "Creep",
                artist = "Boyce Avenue",
                album = "Cover Sessions, Vol. 2",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/cover.mp4",
                durationMs = 240_000L
            ),
            TrackItem(
                id = "live_session",
                title = "Creep (Live at Reading)",
                artist = "Radiohead",
                album = "Live 2009",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/live.mp4",
                durationMs = 264_000L
            ),
            TrackItem(
                id = "slowed_edit",
                title = "Creep (Slowed + Reverb)",
                artist = "Radiohead",
                album = "Single",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/slowed.mp4",
                durationMs = 285_000L
            ),
            TrackItem(
                id = "ytm_video_upload",
                title = "Radiohead - Creep (Official Music Video)",
                artist = "Radiohead",
                album = "YouTube Video",
                artworkUrl = "",
                audioStreamUrl = null,
                durationMs = 276_000L,
                sourceType = AudioSourceType.YTM_ADFREE
            ),
            TrackItem(
                id = "studio_original",
                title = "Creep",
                artist = "Radiohead",
                album = "Pablo Honey",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/pablo_honey_creep_320.mp4",
                durationMs = 238_000L
            )
        )

        val chosenOriginal = FullStreamEngine.pickBestCanonicalTrack(
            candidates = candidates,
            targetTitle = "Creep",
            targetArtist = "Radiohead",
            targetAlbum = "Pablo Honey",
            targetDurationMs = 239_000L,
            minimumScore = 35
        )
        assertNotNull(chosenOriginal)
        assertEquals("studio_original", chosenOriginal?.id)

        // When the user explicitly asks for the Acoustic version, it SHOULD match the Acoustic version
        val chosenAcoustic = FullStreamEngine.pickBestCanonicalTrack(
            candidates = candidates,
            targetTitle = "Creep (Acoustic)",
            targetArtist = "Radiohead",
            targetAlbum = "",
            targetDurationMs = 259_000L,
            minimumScore = 35
        )
        assertNotNull(chosenAcoustic)
        assertEquals("acoustic_ep", chosenAcoustic?.id)

        // Search ranking puts studio album master #1 for normal query, and acoustic #1 for acoustic query
        val normalRanked = FullStreamEngine.rankSearchResultsForCanonicalOriginals(
            query = "Creep Radiohead",
            candidates = candidates
        )
        assertEquals("studio_original", normalRanked.first().id)

        val acousticRanked = FullStreamEngine.rankSearchResultsForCanonicalOriginals(
            query = "Creep Acoustic Radiohead",
            candidates = candidates
        )
        assertEquals("acoustic_ep", acousticRanked.first().id)
    }

    @Test
    fun `search screen renders inside recorded glass backdrop and handles query input without crashing`() {
        val sampleTracks = listOf(
            TrackItem(
                id = "dup_id_1",
                title = "Starboy",
                artist = "The Weeknd, Daft Punk",
                album = "Starboy",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/starboy.mp4",
                durationMs = 230_000L
            ),
            TrackItem(
                id = "dup_id_1",
                title = "Starboy",
                artist = "The Weeknd",
                album = "Starboy",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/starboy2.mp4",
                durationMs = 230_000L
            )
        )
        val samplePlaylists = listOf(
            SuggestedPlaylist(
                id = "pl_dup",
                title = "Midnight Synth",
                subtitle = "Curated",
                badge = "MIX",
                artworkUrl = "",
                trackCount = 2,
                preloadedTracks = sampleTracks
            ),
            SuggestedPlaylist(
                id = "pl_dup",
                title = "Midnight Synth II",
                subtitle = "Curated",
                badge = "MIX",
                artworkUrl = "",
                trackCount = 2,
                preloadedTracks = sampleTracks
            )
        )

        var queryState by mutableStateOf("Starboy")
        var activeFilter by mutableStateOf(SearchFilter.ALL)

        composeTestRule.setContent {
            NothingChordTheme(darkTheme = true) {
                val rig = rememberGlassBackdropRig()
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalInsideBackdropRecording provides true,
                        LocalGlassBackdrop provides null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .recordGlassBackdrop(rig, Color.Black)
                        ) {
                            SearchScreen(
                                uiState = NothingUiState(
                                    searchQuery = queryState,
                                    searchFilter = activeFilter,
                                    searchResults = sampleTracks,
                                    searchPlaylistsResults = samplePlaylists,
                                    suggestedPlaylists = samplePlaylists
                                ),
                                currentTrackId = null,
                                isPlaying = false,
                                savedIds = emptySet(),
                                onQueryChange = { queryState = it },
                                onToggleSource = {},
                                onSelectFilter = { activeFilter = it },
                                onOpenPlaylist = {},
                                onImportPlaylistUrl = {},
                                onPlayTrack = { _, _ -> },
                                onToggleSave = {},
                                onSwipeAddToQueue = {},
                                onLongClickTrack = {}
                            )
                        }
                    }

                    CompositionLocalProvider(
                        LocalGlassBackdrop provides rig.toState()
                    ) {
                        FrostedGlassCapsule(
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Text("Dock")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clear_search_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", queryState)

        composeTestRule.onNodeWithTag("search_query_input").performTextInput("Daft Punk")
        composeTestRule.waitForIdle()
        assertEquals("Daft Punk", queryState)
    }

    @Test
    fun `settings studio screen renders inside recorded glass backdrop and scrolls cleanly without crashing`() {
        val state = NothingUiState(
            spotifyStatusBanner = "Spotify PKCE ready",
            spotifyProfile = SpotifyProfileState(
                isConnected = true,
                userId = "u1",
                displayName = "Listener",
                productTier = "premium",
                savedTracksCount = 42,
                topArtists = listOf("Daft Punk", "Daft Punk", "Radiohead")
            ),
            spotifyPlaylists = listOf(
                SpotifyPlaylistSummary("sp_1", "Late Night", "Synth", null, 18, "Listener"),
                SpotifyPlaylistSummary("sp_1", "Late Night Copy", "Synth", null, 12, "Listener")
            )
        )

        composeTestRule.setContent {
            NothingChordTheme(darkTheme = true) {
                val rig = rememberGlassBackdropRig()
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalInsideBackdropRecording provides true,
                        LocalGlassBackdrop provides null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .recordGlassBackdrop(rig, Color.Black)
                        ) {
                            SourcesAndGlyphScreen(
                                uiState = state,
                                settings = GlyphSettings(onboardingCompleted = true),
                                totalScrobbles = 24,
                                totalStreamedMs = 3_600_000L,
                                savedCount = 12,
                                onSaveClientId = {},
                                onConnectSpotify = {},
                                onOpenSpotifyDashboard = {},
                                onSyncSpotify = {},
                                onDisconnectSpotify = {},
                                onOpenPlaylist = {},
                                onOpenArtist = {},
                                onRemoteAction = {},
                                onSelectAudioQuality = {},
                                onToggleBitPerfectUsb = {},
                                onOpenSystemEqualizer = {},
                                onImportPlaylistUrl = {},
                                onToggleYtmEngine = {},
                                onUpdatePipedInstance = {},
                                onToggleOled = {},
                                onToggleGlyphReactive = {},
                                onToggleAutomix = {},
                                onToggleHaptics = {},
                                onReopenOnboarding = {}
                            )
                        }
                    }

                    CompositionLocalProvider(
                        LocalGlassBackdrop provides rig.toState()
                    ) {
                        FrostedGlassCapsule(
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Text("Dock")
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // Scroll through lazy sections in Account & Settings to ensure every card & glass capsule composes cleanly
        for (idx in 0..4) {
            composeTestRule.onNodeWithTag("sources_hub_list").performScrollToIndex(idx)
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun `top left account button precedes header title and merged studio telemetry renders cleanly`() {
        var accountClicked = false
        val snapshot = com.example.data.model.InsightsSnapshot(
            totalMinutes = 172L,
            totalPlays = 48,
            offlinePlays = 6,
            downloadedTracksCount = 4,
            uniqueArtistsCount = 14,
            streakDays = 4,
            peakHour = 22,
            collectorArchetype = "NOCTURNAL SYNTH",
            hourlyMatrix = List(24) { if (it == 22) 1f else 0.2f },
            topArtists = listOf(com.example.data.model.ArtistInsightStat("Daft Punk", 18, 64, "")),
            topTrack = TrackItem(
                id = "t1",
                title = "Veridis Quo",
                artist = "Daft Punk",
                album = "Discovery",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/veridis.mp4",
                durationMs = 345_000L
            ),
            topTrackPlays = 9
        )

        composeTestRule.setContent {
            NothingChordTheme(darkTheme = true) {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.components.NothingCleanHeader(
                        title = "Nchord",
                        isPlaying = true,
                        isSpotifyLinked = true,
                        isYtmEnabled = true,
                        spectrum = List(5) { 0.35f },
                        glyphReactive = true,
                        onAccountClick = { accountClicked = true },
                        onStatusClick = {}
                    )
                    com.example.ui.screens.InsightsScreen(
                        snapshot = snapshot,
                        scrobblerState = com.example.data.model.ScrobblerConfigAndState(),
                        spotifyProfile = SpotifyProfileState(isConnected = true, displayName = "Listener"),
                        isPlaying = true,
                        onOpenArtist = {},
                        onConnectLastFm = { _, _ -> },
                        onConnectListenBrainz = { _, _ -> },
                        onSyncScrobblerNow = {},
                        onDisconnectScrobbler = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("header_engine_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("account_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Expected top-left Account button click to trigger Account page", accountClicked)
        composeTestRule.onNodeWithTag("studio_master_counter_card").assertExists()
        composeTestRule.onNodeWithTag("studio_circadian_card").assertExists()
        // Verify playback speed controls were removed from Studio page per user request
        composeTestRule.onNodeWithTag("studio_pitch_deck_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("studio_telemetry_list").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_sonic_dna_card").assertExists()
        composeTestRule.onNodeWithTag("studio_telemetry_list").performScrollToIndex(4)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_scrobbler_dial_circle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scrobbler_connection_sheet").assertExists()
    }

    @Test
    fun `search separates original top match above more results and dims or hides unplayable songs`() {
        val playable = TrackItem(
            id = "ok_track",
            title = "Digital Love",
            artist = "Daft Punk",
            album = "Discovery",
            artworkUrl = "",
            audioStreamUrl = "https://example.com/ok.mp4",
            durationMs = 301_000L,
            isPlayable = true
        )
        val slowedCopy = TrackItem(
            id = "slowed_copy",
            title = "Digital Love (Slowed + Reverb)",
            artist = "TikTok Edits",
            album = "Slowed Bootleg",
            artworkUrl = "",
            audioStreamUrl = "https://example.com/slow.mp4",
            durationMs = 340_000L,
            isPlayable = true
        )
        val unplayable = TrackItem(
            id = "dead_track",
            title = "Region Locked Cut",
            artist = "Unknown Vault",
            album = "Vault",
            artworkUrl = "",
            audioStreamUrl = null,
            durationMs = 210_000L,
            isPlayable = false
        )
        var hideUnplayable by mutableStateOf(false)

        composeTestRule.setContent {
            NothingChordTheme(darkTheme = true) {
                SearchScreen(
                    uiState = NothingUiState(
                        searchQuery = "Digital Love",
                        searchFilter = SearchFilter.SONGS,
                        searchResults = listOf(slowedCopy, playable, unplayable)
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    savedIds = emptySet(),
                    unplayableTrackIds = setOf("dead_track"),
                    hideUnplayableSongs = hideUnplayable,
                    onQueryChange = {},
                    onToggleSource = {},
                    onSelectFilter = {},
                    onOpenPlaylist = {},
                    onImportPlaylistUrl = {},
                    onPlayTrack = { _, _ -> },
                    onToggleSave = {},
                    onSwipeAddToQueue = {},
                    onLongClickTrack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Original playable song is separated into the dedicated Top Match card above MORE RESULTS
        composeTestRule.onNodeWithTag("search_top_match_card").assertExists()
        composeTestRule.onNodeWithTag("search_more_results_header").assertExists()
        composeTestRule.onNodeWithTag("track_item_card_slowed_copy").assertExists()
        composeTestRule.onNodeWithTag("search_results_list")
            .performScrollToNode(hasTestTag("track_item_card_dead_track"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_item_card_dead_track").assertExists()
        composeTestRule.onNodeWithTag("unplayable_badge_dead_track", useUnmergedTree = true).assertExists()

        hideUnplayable = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_item_card_dead_track").assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_results_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_top_match_card").assertExists()
    }

    @Test
    fun `room v2 user playlists support create add and remove`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NothingChordDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.musicDao()

        val pl = UserPlaylistEntity(
            id = "pl_test_1",
            name = "Midnight Cassette",
            description = "Synth & Indie",
            coverArtworkUrl = "",
            spotifyPlaylistId = null,
            youtubePlaylistId = null
        )
        dao.upsertUserPlaylist(pl)

        dao.upsertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = "pl_test_1",
                trackId = "t1",
                position = 0,
                title = "Nightcall",
                artist = "Kavinsky",
                album = "OutRun",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/t1.mp4",
                durationMs = 258_000L,
                sourceTypeName = "YTM_ADFREE"
            )
        )
        dao.upsertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = "pl_test_1",
                trackId = "t2",
                position = 1,
                title = "Instant Crush",
                artist = "Daft Punk",
                album = "RAM",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/t2.mp4",
                durationMs = 337_000L,
                sourceTypeName = "YTM_ADFREE"
            )
        )

        val tracks = dao.observeTracksForPlaylist("pl_test_1").first()
        assertEquals(2, tracks.size)
        assertEquals("Nightcall", tracks[0].title)
        assertEquals("Instant Crush", tracks[1].title)

        dao.removeTrackFromPlaylist("pl_test_1", "t1")
        val afterRemove = dao.observeTracksForPlaylist("pl_test_1").first()
        assertEquals(1, afterRemove.size)
        assertEquals("Instant Crush", afterRemove[0].title)

        db.close()
    }

    @Test
    fun `live full stream engine returns full length songs and playable stream telemetry`() = runBlocking {
        val tracks = FullStreamEngine.searchFullTracks(
            query = "Daft Punk Starboy",
            limit = 6,
            qualityTier = AudioQualityTier.STUDIO_320
        )
        assertTrue("Expected non-empty full tracks from live engine", tracks.isNotEmpty())
        val first = tracks.first()
        assertTrue(
            "Expected full-length song (> 90s), got ${first.durationMs}ms",
            first.durationMs > 90_000L
        )
        val resolved = FullStreamEngine.resolveFullLengthStream(
            track = first,
            qualityTier = AudioQualityTier.STUDIO_320
        )
        assertNotNull(
            "Expected playable full-length stream URL for id=${first.id} title='${first.title}' artist='${first.artist}' album='${first.album}' dur=${first.durationMs} stream=${first.audioStreamUrl} all=${tracks.map { "${it.id}:${it.title}|${it.artist}|hasUrl=${it.audioStreamUrl != null}" }}",
            resolved.url
        )
        assertTrue("Expected HTTPS stream URL, got ${resolved.url}", resolved.url!!.startsWith("https://"))
        assertTrue("Expected full song duration (> 90s), got ${resolved.durationMs}", resolved.durationMs > 90_000L)

        val playlists = FullStreamEngine.fetchSuggestedPlaylists(
            listOf("After Hours Rotation::The Weeknd|Kavinsky|Daft Punk" to "Late Night")
        )
        assertTrue("Expected suggested playlist for Open Carousel", playlists.isNotEmpty())
    }

    @Test
    fun `lyrics title sanitizer preserves hyphenated titles and parses multi tag lrc lines`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NothingChordDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.repository.NothingMusicRepository(
            musicDao = db.musicDao(),
            prefsStore = com.example.data.local.NothingPreferencesStore(context),
            downloadManager = com.example.data.local.InAppDownloadManager(context)
        )

        assertEquals("Anti-Hero", repo.sanitizeTitleForLyrics("Anti-Hero (feat. Bleachers)"))
        assertEquals("Instant Crush", repo.sanitizeTitleForLyrics("Instant Crush (feat. Julian Casablancas) - 2013 Remaster"))
        assertEquals("Non-Stop", repo.sanitizeTitleForLyrics("Non-Stop [Official Audio]"))

        val parsed = repo.parseLrcTimestamps(
            """
            [00:10.50][01:10.50] <00:10.60>Around <00:11.00>the world
            [00:24.05] Digital love tonight
            """.trimIndent()
        )
        assertEquals(3, parsed.size)
        assertEquals("Around the world", parsed[0].text)
        assertEquals(10_500L, parsed[0].timestampMs)
        assertEquals("Digital love tonight", parsed[1].text)
        assertEquals(24_050L, parsed[1].timestampMs)
        assertEquals("Around the world", parsed[2].text)
        assertEquals(70_500L, parsed[2].timestampMs)
        db.close()
    }

    @Test
    fun `library enforces max four pinned items and deletes playlist with pinned entry cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NothingChordDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.repository.NothingMusicRepository(
            musicDao = db.musicDao(),
            prefsStore = com.example.data.local.NothingPreferencesStore(context),
            downloadManager = com.example.data.local.InAppDownloadManager(context)
        )

        val pl1 = repo.createUserPlaylist("Crate 1", "")
        val pl2 = repo.createUserPlaylist("Crate 2", "")
        val pl3 = repo.createUserPlaylist("Crate 3", "")
        val pl4 = repo.createUserPlaylist("Crate 4", "")
        val pl5 = repo.createUserPlaylist("Crate 5", "")

        assertEquals(com.example.data.repository.PinToggleOutcome.PINNED, repo.togglePinLibraryItem("upl_${pl1.id}"))
        assertEquals(com.example.data.repository.PinToggleOutcome.PINNED, repo.togglePinLibraryItem("upl_${pl2.id}"))
        assertEquals(com.example.data.repository.PinToggleOutcome.PINNED, repo.togglePinLibraryItem("upl_${pl3.id}"))
        assertEquals(com.example.data.repository.PinToggleOutcome.PINNED, repo.togglePinLibraryItem("upl_${pl4.id}"))
        assertEquals(com.example.data.repository.PinToggleOutcome.LIMIT_REACHED, repo.togglePinLibraryItem("upl_${pl5.id}"))

        assertEquals(4, repo.pinnedItemKeysFlow.first().size)

        repo.deleteUserPlaylist(pl1.id)
        assertEquals(3, repo.pinnedItemKeysFlow.first().size)
        assertEquals(4, repo.userPlaylistsFlow.first().size)
        db.close()
    }

    @Test
    fun `full screen player overlay blocks touch pass through and switches to lyrics page`() {
        var underlyingTileClicked = false
        var adjustedOffsetDelta = 0L
        val activeTrack = TrackItem(
            id = "tr_1",
            title = "Veridis Quo",
            artist = "Daft Punk",
            album = "Discovery",
            artworkUrl = "",
            audioStreamUrl = "https://example.com/veridis.mp4",
            durationMs = 345_000L
        )
        val sampleLyrics = listOf(
            com.example.data.model.LyricLine(5_000L, "Around the world", isSynced = true),
            com.example.data.model.LyricLine(10_000L, "Digital love", isSynced = true)
        )

        composeTestRule.setContent {
            NothingChordTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Underlying Home tile occupying the entire screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { underlyingTileClicked = true }
                            .testTag("underlying_home_tile")
                    )

                    // Full-screen player overlay on top
                    com.example.ui.screens.FullScreenNothingPlayer(
                        uiState = NothingUiState(
                            isFullPlayerOpen = true,
                            lyrics = sampleLyrics
                        ),
                        audioState = com.example.player.AudioEngineState(
                            currentTrack = activeTrack,
                            isPlaying = false,
                            durationMs = 345_000L
                        ),
                        isSaved = false,
                        savedIds = emptySet(),
                        glyphReactive = true,
                        onCollapse = {},
                        onTogglePlay = {},
                        onSkipPrev = {},
                        onSkipNext = {},
                        onSeekMs = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleSave = {},
                        onPlayQueueItem = {},
                        onOpenTrackOptions = {},
                        onOpenArtist = {},
                        onChangePlaybackSpeed = {},
                        onAdjustLyricsOffset = { adjustedOffsetDelta += it },
                        onResetLyricsOffset = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_player_screen").performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertFalse(
            "Expected full-screen player overlay to consume touches and block clicks to underlying home tile",
            underlyingTileClicked
        )

        composeTestRule.onNodeWithTag("player_pane_tab_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lyrics_offset_plus").performClick()
        composeTestRule.waitForIdle()
        assertEquals(500L, adjustedOffsetDelta)
    }

    @Test
    fun `room migration 3 to 4 preserves user rows and builds query indices non destructively`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("nchord_migration_3_4_test.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        try {
            // 1. Bootstrap a real SQLite v3 database and insert user data across tables
            val rawV3 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_tracks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `audioStreamUrl` TEXT, `durationMs` INTEGER NOT NULL, `sourceTypeName` TEXT NOT NULL, `externalSpotifyUri` TEXT, `albumId` TEXT, `artistId` TEXT, `spotifyTrackId` TEXT, `youtubeVideoId` TEXT, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `scrobble_history` (`scrobbleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `audioStreamUrl` TEXT, `durationMs` INTEGER NOT NULL, `sourceTypeName` TEXT NOT NULL, `albumId` TEXT, `artistId` TEXT, `spotifyTrackId` TEXT, `isOfflinePlay` INTEGER NOT NULL, `syncedToScrobbler` INTEGER NOT NULL, `playedAt` INTEGER NOT NULL)"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `user_playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `coverArtworkUrl` TEXT NOT NULL, `spotifyPlaylistId` TEXT, `youtubePlaylistId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `audioStreamUrl` TEXT, `durationMs` INTEGER NOT NULL, `sourceTypeName` TEXT NOT NULL, `youtubeVideoId` TEXT, `externalSpotifyUri` TEXT, `albumId` TEXT, `artistId` TEXT, `spotifyTrackId` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackId`))"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloaded_tracks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `localFilePath` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `codecLabel` TEXT NOT NULL, `bitrateKbps` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `sourceTypeName` TEXT NOT NULL, `youtubeVideoId` TEXT, `externalSpotifyUri` TEXT, `spotifyTrackId` TEXT, `albumId` TEXT, `artistId` TEXT, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `pinned_library_items` (`itemKey` TEXT NOT NULL, `pinnedAt` INTEGER NOT NULL, PRIMARY KEY(`itemKey`))"
            )
            rawV3.execSQL(
                "CREATE TABLE IF NOT EXISTS `recent_collections` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `kindName` TEXT NOT NULL, `referenceId` TEXT NOT NULL, `playedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            rawV3.execSQL(
                "INSERT INTO `saved_tracks` (`id`, `title`, `artist`, `album`, `artworkUrl`, `audioStreamUrl`, `durationMs`, `sourceTypeName`, `externalSpotifyUri`, `savedAt`) VALUES ('saved_v3_1', 'Something About Us', 'Daft Punk', 'Discovery', '', 'https://example.com/sau.mp4', 231000, 'YTM_ADFREE', NULL, 1700000000)"
            )
            rawV3.version = 3
            rawV3.close()

            // 2. Open with Room v4 + MIGRATION_3_4 (NO global destructive fallback!)
            val migratedDb = Room.databaseBuilder(
                context,
                NothingChordDatabase::class.java,
                dbFile.name
            ).addMigrations(NothingChordDatabase.MIGRATION_3_4)
                .allowMainThreadQueries()
                .build()

            val preservedSaved = runBlocking { migratedDb.musicDao().observeSavedTracks().first() }
            assertEquals("Expected v3 saved track to survive non-destructive v4 migration", 1, preservedSaved.size)
            assertEquals("Something About Us", preservedSaved.first().title)
            migratedDb.close()
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun `runtime caches configure 32mb okhttp cache and moshi ksp adapters parse dtos without kotlin reflection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.data.remote.NetworkModule.initializeRuntimeCaches(context)
        assertEquals(
            32L * 1024L * 1024L,
            com.example.data.remote.NetworkModule.activeHttpCacheSizeMaxBytes()
        )

        // Verify zero-reflection Moshi KSP generated adapters deserialize Spotify & LrcLib DTOs cleanly
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val tokenJson = """{"access_token":"tok_abc","token_type":"Bearer","expires_in":3600,"refresh_token":"ref_xyz"}"""
        val tokenDto = moshi.adapter(com.example.data.remote.SpotifyTokenResponseDto::class.java).fromJson(tokenJson)
        assertNotNull(tokenDto)
        assertEquals("tok_abc", tokenDto?.accessToken)
        assertEquals("ref_xyz", tokenDto?.refreshToken)

        val lrcJson = """{"id":99,"trackName":"Veridis Quo","artistName":"Daft Punk","syncedLyrics":"[00:01.00] Tune"}"""
        val lrcDto = moshi.adapter(com.example.data.remote.LrcLibResponseDto::class.java).fromJson(lrcJson)
        assertNotNull(lrcDto)
        assertEquals("Veridis Quo", lrcDto?.trackName)
    }

    @Test
    fun `home playlists support remove from home and delete actions and dock search re-tap requests keyboard`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NothingChordDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.repository.NothingMusicRepository(
            musicDao = db.musicDao(),
            prefsStore = com.example.data.local.NothingPreferencesStore(context),
            downloadManager = com.example.data.local.InAppDownloadManager(context)
        )
        val audioEngine = com.example.player.NothingAudioEngine(context)
        val vm = com.example.ui.NothingChordViewModel(context, repo, audioEngine)

        try {
            assertEquals(0, vm.uiState.value.searchKeyboardRequestNonce)
            // First tap switches active tab from HOME -> SEARCH without forcing keyboard
            vm.selectTab(com.example.ui.NothingTab.SEARCH)
            assertEquals(com.example.ui.NothingTab.SEARCH, vm.uiState.value.activeTab)
            assertEquals(0, vm.uiState.value.searchKeyboardRequestNonce)

            // Pressing Search on the dock AGAIN while already on Search increments keyboard request nonce
            vm.selectTab(com.example.ui.NothingTab.SEARCH)
            assertEquals(1, vm.uiState.value.searchKeyboardRequestNonce)

            var removedPlaylistId: String? = null
            var deletedPlaylistId: String? = null
            val garbagePl = com.example.data.model.SuggestedPlaylist(
                id = "garbage_mix_1",
                title = "Unwanted Garbage Mix",
                subtitle = "Noise",
                badge = "NCHORD",
                artworkUrl = "",
                trackCount = 8
            )
            val spotlight = TrackItem(
                id = "spot_1",
                title = "Instant Crush",
                artist = "Daft Punk",
                album = "Random Access Memories",
                artworkUrl = "",
                audioStreamUrl = "https://example.com/ic.mp4",
                durationMs = 337_000L
            )

            composeTestRule.setContent {
                NothingChordTheme(darkTheme = true) {
                    com.example.ui.screens.DiscoverScreen(
                        uiState = NothingUiState(
                            isDiscoverLoading = false,
                            featuredTracks = listOf(spotlight),
                            suggestedPlaylists = listOf(garbagePl)
                        ),
                        recentGridTiles = emptyList(),
                        downloadedTrackIds = emptySet(),
                        downloadProgress = emptyMap(),
                        offlineTracks = emptyList(),
                        currentTrackId = null,
                        isPlaying = false,
                        savedIds = emptySet(),
                        onSelectHomeFilter = {},
                        onSelectMood = {},
                        onOpenRecentTile = {},
                        onOpenCarouselPlaylist = {},
                        onPlayCarouselPlaylist = { _, _ -> },
                        onRemoveCarouselPlaylistFromHome = { removedPlaylistId = it.id },
                        onDeleteCarouselPlaylist = { deletedPlaylistId = it.id },
                        onOpenAlbumForTrack = {},
                        onPlayTrack = { _, _ -> },
                        onToggleSave = {},
                        onSwipeAddToQueue = {},
                        onLongClickTrack = {},
                        onRetryLoad = {}
                    )
                }
            }

            composeTestRule.waitForIdle()
            // Picked for you renders both save (+) and play side-by-side cleanly
            composeTestRule.onNodeWithTag("picked_card_save").assertExists()
            composeTestRule.onNodeWithTag("picked_card_play").assertExists()

            // Open playlist manage sheet from Home tile and remove from Home
            composeTestRule.onNodeWithTag("square_playlist_more_garbage_mix_1").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_playlist_manage_sheet").assertExists()
            composeTestRule.onNodeWithTag("home_playlist_action_remove_home").performClick()
            composeTestRule.waitForIdle()
            assertEquals("garbage_mix_1", removedPlaylistId)

            // Re-open playlist manage sheet and delete playlist permanently
            composeTestRule.onNodeWithTag("square_playlist_more_garbage_mix_1").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_playlist_action_delete")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            assertEquals("garbage_mix_1", deletedPlaylistId)
        } finally {
            audioEngine.release()
            db.close()
        }
    }

    @Test
    fun `display refresh rate card reflects 144hz peak panel capability and toggles locked 144hz policy`() {
        var selectedPolicy = com.example.data.model.DisplayRefreshPolicy.HIGH
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.ui.components.LocalPeakDisplayRefreshHz provides 144
            ) {
                NothingChordTheme(darkTheme = true) {
                    com.example.ui.screens.SourcesAndGlyphScreen(
                        uiState = NothingUiState(),
                        settings = com.example.data.model.GlyphSettings(
                            displayRefreshPolicy = selectedPolicy
                        ),
                        scrobblerState = com.example.data.model.ScrobblerConfigAndState(),
                        totalScrobbles = 12,
                        totalStreamedMs = 3_600_000L,
                        savedCount = 4,
                        onSaveClientId = {},
                        onConnectSpotify = {},
                        onOpenSpotifyDashboard = {},
                        onSyncSpotify = {},
                        onDisconnectSpotify = {},
                        onConnectLastFm = { _, _ -> },
                        onConnectListenBrainz = { _, _ -> },
                        onSyncScrobblerNow = {},
                        onDisconnectScrobbler = {},
                        onOpenPlaylist = {},
                        onOpenArtist = {},
                        onRemoteAction = {},
                        onSelectAudioQuality = {},
                        onToggleBitPerfectUsb = {},
                        onOpenSystemEqualizer = {},
                        onImportPlaylistUrl = {},
                        onToggleYtmEngine = {},
                        onUpdatePipedInstance = {},
                        onToggleOled = {},
                        onToggleGlyphReactive = {},
                        onToggleAutomix = {},
                        onToggleHideUnplayable = {},
                        onSelectDisplayRefreshPolicy = { selectedPolicy = it },
                        onToggleHaptics = {},
                        onReopenOnboarding = {},
                        onBack = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sources_hub_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("display_refresh_active_badge"))
        composeTestRule.onNodeWithTag("display_refresh_active_badge").assertExists()
        val batteryTag = "refresh_policy_${com.example.data.model.DisplayRefreshPolicy.BATTERY_SAVER.id}"
        composeTestRule.onNodeWithTag("sources_hub_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(batteryTag))
        composeTestRule.onNodeWithTag(batteryTag).performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.model.DisplayRefreshPolicy.BATTERY_SAVER, selectedPolicy)
    }
}





