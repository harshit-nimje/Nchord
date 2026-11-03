package com.example

import com.example.data.model.AudioQualityTier
import com.example.data.remote.FullStreamEngine
import com.example.data.remote.ScrobblerEngine
import com.example.data.repository.NothingMusicRepository
import com.example.ui.components.ParticleSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    @Test
    fun `particle system keeps dots anchored at zero displacement while modulating serene color and thickness energy`() {
        val system = ParticleSystem(cols = 19, rows = 33)
        assertEquals(627, system.count)
        assertEquals(0f, system.maxAbsDisplacement(), 0.0001f)
        assertEquals(0f, system.maxEnergy(), 0.0001f)

        var hasMotion = false
        repeat(30) {
            hasMotion = system.step(
                rawDtSeconds = 1f / 60f,
                isPlaying = true,
                bass = 0.85f,
                mid = 0.62f,
                treble = 0.45f
            )
        }
        assertTrue("Expected active serene energy wave while playing", hasMotion)
        assertEquals("Expected zero positional displacement so dots stay strictly in place", 0f, system.maxAbsDisplacement(), 0.0001f)
        assertTrue("Expected non-zero luminance/thickness energy while playing, got ${system.maxEnergy()}", system.maxEnergy() > 0.25f)
        assertTrue("Expected wide groove relief span across crests and troughs, got ${system.reliefSpan()}", system.reliefSpan() > 0.35f)
        assertTrue("Expected liveBlend to ramp toward 1f while playing, got ${system.liveBlend}", system.liveBlend > 0.7f)

        var stillMoving = true
        var settleSteps = 0
        while (stillMoving && settleSteps < 240) {
            stillMoving = system.step(
                rawDtSeconds = 1f / 60f,
                isPlaying = false,
                bass = 0f,
                mid = 0f,
                treble = 0f
            )
            settleSteps++
        }

        assertFalse("Expected particle system to converge and idle once damped", stillMoving)
        assertEquals("Expected zero displacement at rest", 0f, system.maxAbsDisplacement(), 0.0001f)
        assertEquals("Expected zero energy once settled at rest", 0f, system.maxEnergy(), 0.0001f)
        assertEquals("Expected zero liveBlend once settled at rest", 0f, system.liveBlend, 0.0001f)
    }

    @Test
    fun `particle system short-circuits static cutoff rows cleanly and fastCrestPower matches pow curve`() {
        val system = ParticleSystem(cols = 19, rows = 33)
        // Step with full height active first
        repeat(15) {
            system.step(
                rawDtSeconds = 1f / 60f,
                isPlaying = true,
                bass = 0.9f,
                mid = 0.7f,
                treble = 0.6f,
                activeNyCutoff = 1f
            )
        }
        val bottomRowIndex = system.count - 1
        assertTrue("Bottom row should energize when cutoff is 1f", system.energy[bottomRowIndex] > 0f)

        // Dynamically tighten cutoff to upper 65% of screen (simulating song title layout pass)
        system.step(
            rawDtSeconds = 1f / 60f,
            isPlaying = true,
            bass = 0.9f,
            mid = 0.7f,
            treble = 0.6f,
            activeNyCutoff = 0.65f
        )
        for (i in 0 until system.count) {
            if (system.restNy[i] > 0.65f) {
                assertEquals("Dot below cutoff must zero energy cleanly", 0f, system.energy[i], 0.0001f)
                assertEquals("Dot below cutoff must zero relief cleanly", 0f, system.grooveRelief[i], 0.0001f)
            }
        }

        // Verify polynomial crest approximation stays within 0.009 of x.pow(1.06f) across [0, 1]
        assertEquals(0f, ParticleSystem.fastCrestPower(0f), 0.00001f)
        assertEquals(1f, ParticleSystem.fastCrestPower(1f), 0.00001f)
        for (step in 0..100) {
            val x = step / 100f
            val exact = Math.pow(x.toDouble(), 1.06).toFloat()
            val approx = ParticleSystem.fastCrestPower(x)
            assertTrue(
                "fastCrestPower($x) error too high: exact=$exact approx=$approx",
                kotlin.math.abs(exact - approx) < 0.01f
            )
        }
    }

    @Test
    fun `saavn url tier adaptation handles query parameters safely without upward 404 rewrites`() {
        val signed320 = "https://aac.saavncdn.com/396/b4e570050007b056c662f2a98c9f28ec_320.mp4?expires=1790000000"
        val signed160 = "https://aac.saavncdn.com/396/b4e570050007b056c662f2a98c9f28ec_160.mp4?expires=1790000000"

        assertEquals(
            "https://aac.saavncdn.com/396/b4e570050007b056c662f2a98c9f28ec_96.mp4?expires=1790000000",
            FullStreamEngine.adaptSaavnUrlForTier(signed320, AudioQualityTier.DATA_SAVER)
        )
        assertEquals(
            "https://aac.saavncdn.com/396/b4e570050007b056c662f2a98c9f28ec_160.mp4?expires=1790000000",
            FullStreamEngine.adaptSaavnUrlForTier(signed320, AudioQualityTier.YTM_NORMAL)
        )
        // Must NOT blindly rewrite _160.mp4 upward to _320.mp4
        assertEquals(
            signed160,
            FullStreamEngine.adaptSaavnUrlForTier(signed160, AudioQualityTier.STUDIO_320)
        )
    }

    @Test
    fun `lastfm signature sorts keys alphabetically and excludes format and callback`() {
        val params = mapOf(
            "method" to "track.scrobble",
            "artist" to "Daft Punk",
            "track" to "Veridis Quo",
            "api_key" to "demo_key",
            "format" to "json",
            "callback" to "jsonp"
        )
        val sigWithFormat = ScrobblerEngine.computeLastFmSignature(params, "shared_secret_123")
        val sigClean = ScrobblerEngine.computeLastFmSignature(
            params.filterKeys { it != "format" && it != "callback" },
            "shared_secret_123"
        )
        assertEquals(32, sigClean.length)
        assertEquals(sigClean, sigWithFormat)
    }

    @Test
    fun `library max pinned items constant is strictly four and vault sanitizes traversal attempts`() {
        assertEquals(4, NothingMusicRepository.MAX_PINNED_ITEMS)

        val tempDir = Files.createTempDirectory("nchord_vault_test").toFile()
        try {
            val rawId = "../../external/song:id?weird*chars"
            val sanitized = rawId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(84)
            val target = File(tempDir, "$sanitized.m4a")
            assertTrue(target.canonicalPath.startsWith(tempDir.canonicalPath))
            assertFalse(target.name.contains("/"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `display refresh selector preserves active physical resolution and honors auto high and battery policies`() {
        val modes = listOf(
            // Active resolution: 1080x2400 @ 60Hz and 120Hz
            DisplayModeCandidate(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeCandidate(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
            // Different resolution: 1440x3200 @ 144Hz (must NEVER be picked when active is 1080x2400)
            DisplayModeCandidate(modeId = 3, physicalWidth = 1440, physicalHeight = 3200, refreshRate = 144f)
        )

        // AUTO + normal power -> 0 (delegate adaptive LTPO to OS)
        assertEquals(
            0,
            selectPreferredDisplayModeId(
                policy = com.example.data.model.DisplayRefreshPolicy.AUTO,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2400,
                modes = modes
            )
        )

        // AUTO + OS Battery Saver -> 60Hz mode at 1080x2400 (modeId = 1)
        assertEquals(
            1,
            selectPreferredDisplayModeId(
                policy = com.example.data.model.DisplayRefreshPolicy.AUTO,
                isOsPowerSaveMode = true,
                activeWidth = 1080,
                activeHeight = 2400,
                modes = modes
            )
        )

        // HIGH -> 120Hz at 1080x2400 (modeId = 2), NOT 144Hz at 1440x3200 (modeId = 3)
        assertEquals(
            2,
            selectPreferredDisplayModeId(
                policy = com.example.data.model.DisplayRefreshPolicy.HIGH,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2400,
                modes = modes
            )
        )

        // BATTERY_SAVER -> 60Hz at 1080x2400 (modeId = 1)
        assertEquals(
            1,
            selectPreferredDisplayModeId(
                policy = com.example.data.model.DisplayRefreshPolicy.BATTERY_SAVER,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2400,
                modes = modes
            )
        )
    }

    @Test
    fun `loopback audio stream proxy binds strictly to 127_0_0_1 and enforces secret token and path sanitization`() {
        assertEquals(
            "tok_1_42" to "abc123secret",
            com.example.player.LocalAudioStreamProxy.parseStreamRequestPath("/stream/tok_1_42?token=abc123secret")
        )
        // Reject missing token or path traversal
        assertEquals(
            null,
            com.example.player.LocalAudioStreamProxy.parseStreamRequestPath("/stream/tok_1_42")
        )
        assertEquals(
            null,
            com.example.player.LocalAudioStreamProxy.parseStreamRequestPath("/stream/../../etc?token=abc")
        )

        val proxy = com.example.player.LocalAudioStreamProxy(okhttp3.OkHttpClient())
        try {
            val url = proxy.registerStream(
                streamKey = "track:99/test",
                upstreamUrl = "https://example.com/audio.m4a",
                headers = mapOf("User-Agent" to "NchordTest")
            )
            assertTrue("Expected loopback URL on 127.0.0.1, got $url", url != null && url.startsWith("http://127.0.0.1:"))
            assertTrue("Expected secret token query parameter", url!!.contains("?token="))

            // Verify unauthenticated request to loopback port is rejected with HTTP 403
            val baseNoToken = url.substringBefore("?token=")
            val conn = (java.net.URL("$baseNoToken?token=wrong_token").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
                requestMethod = "GET"
            }
            assertEquals(403, conn.responseCode)
            conn.disconnect()
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `visualizer 6-band FFT extractor isolates sub-bass midrange and treble bins accurately`() {
        val captureSize = 512
        val sampleRateHz = 44_100
        // Bin spacing = 44100 / 512 ≈ 86.13 Hz per bin

        // 1. Silent FFT buffer -> all 6 bands sit cleanly at baseline 0.08f
        val silentFft = ByteArray(captureSize)
        val silentBands = com.example.player.NothingAudioVisualizer.extractSixMusicalBandsFromFft(
            fft = silentFft,
            sampleRateHz = sampleRateHz
        )
        assertEquals(6, silentBands.size)
        silentBands.forEach { bandVal ->
            assertEquals(0.08f, bandVal, 0.001f)
        }

        // 2. Inject strong Bass energy at bin 2 (~172 Hz -> Band 1: 60..250 Hz)
        val bassFft = ByteArray(captureSize)
        bassFft[2 * 2] = 110.toByte()
        bassFft[2 * 2 + 1] = 60.toByte()
        val bassBands = com.example.player.NothingAudioVisualizer.extractSixMusicalBandsFromFft(
            fft = bassFft,
            sampleRateHz = sampleRateHz
        )
        assertTrue(
            "Expected Bass band (index 1) to dominate when 172Hz bin is energized, got ${bassBands.toList()}",
            bassBands[1] > 0.70f && bassBands[1] > bassBands[3] && bassBands[1] > bassBands[5]
        )

        // 3. Inject strong Treble energy at bin 116 (~10,000 Hz -> Band 5: 6,000..20,000 Hz)
        val trebleFft = ByteArray(captureSize)
        trebleFft[2 * 116] = 95.toByte()
        trebleFft[2 * 116 + 1] = 45.toByte()
        val trebleBands = com.example.player.NothingAudioVisualizer.extractSixMusicalBandsFromFft(
            fft = trebleFft,
            sampleRateHz = sampleRateHz
        )
        assertTrue(
            "Expected Treble band (index 5) to dominate when 10kHz bin is energized, got ${trebleBands.toList()}",
            trebleBands[5] > 0.75f && trebleBands[5] > trebleBands[1] && trebleBands[5] > trebleBands[3]
        )

        // 4. Verify 6 -> 12 Glyph bar interpolation preserves bounds and endpoints
        val twelve = FloatArray(12)
        com.example.player.NothingAudioVisualizer.expandSixToTwelveBars(trebleBands, twelve)
        assertEquals(trebleBands[0], twelve[0], 0.0001f)
        assertEquals(trebleBands[5], twelve[11], 0.0001f)
        assertTrue(twelve.all { it in 0.08f..0.98f })
    }

    @Test
    fun `spotify pkce state verification rejects missing short or mismatched callback nonces and enforces least-privilege scopes`() {
        val validState = "xK9_pQ2mLw8nVz4rYt7bHc1jDf6sGa3u"
        assertTrue(com.example.data.remote.SpotifyPkceHelper.verifyCallbackState(validState, validState))
        assertFalse(com.example.data.remote.SpotifyPkceHelper.verifyCallbackState(validState, null))
        assertFalse(com.example.data.remote.SpotifyPkceHelper.verifyCallbackState(validState, ""))
        assertFalse(com.example.data.remote.SpotifyPkceHelper.verifyCallbackState(null, validState))
        assertFalse(com.example.data.remote.SpotifyPkceHelper.verifyCallbackState("too_short", "too_short"))
        assertFalse(
            com.example.data.remote.SpotifyPkceHelper.verifyCallbackState(
                validState,
                "xK9_pQ2mLw8nVz4rYt7bHc1jDf6sGa3X"
            )
        )

        val scopes = com.example.data.remote.NetworkModule.SPOTIFY_SCOPES.split(" ")
        assertFalse("Unused write scope user-library-modify must be removed", scopes.contains("user-library-modify"))
        assertFalse("Unused write scope playlist-modify-public must be removed", scopes.contains("playlist-modify-public"))
        assertFalse("Unused write scope playlist-modify-private must be removed", scopes.contains("playlist-modify-private"))
        assertTrue("Required read scope user-library-read must remain", scopes.contains("user-library-read"))
        assertTrue("Required playback scope user-modify-playback-state must remain", scopes.contains("user-modify-playback-state"))
    }

    @Test
    fun `song groove profile locks deterministic choreography per track while keeping particle coordinates strictly stationary`() {
        val daftProfileA = com.example.ui.components.SongGrooveProfile.fromTrack("tr_veridis", "Veridis Quo Discovery")
        val daftProfileB = com.example.ui.components.SongGrooveProfile.fromTrack("tr_veridis", "Veridis Quo Discovery")
        val clubProfile = com.example.ui.components.SongGrooveProfile.fromTrack("tr_club_techno", "Boiler Room Club Rave")

        assertEquals(daftProfileA, daftProfileB)
        assertTrue(daftProfileA.choreographyMode in 0..3)
        assertTrue(daftProfileA.bpm in 84f..138f)
        assertTrue(clubProfile.bpm >= 120f)

        val system = ParticleSystem(cols = 19, rows = 33)
        for (seedIdx in 0..7) {
            system.configureTrackSignature("track_seed_$seedIdx", "Song Title $seedIdx")
            repeat(20) { tick ->
                system.step(
                    rawDtSeconds = 1f / 60f,
                    isPlaying = true,
                    bass = if (tick % 4 == 0) 0.92f else 0.18f,
                    mid = 0.58f,
                    treble = if (tick % 2 == 0) 0.78f else 0.20f,
                    activeNyCutoff = 0.70f,
                    songPositionSeconds = tick * (1f / 60f)
                )
            }
            assertEquals("Dots must never shift X/Y coordinates for seed $seedIdx", 0f, system.maxAbsDisplacement(), 0.0001f)
            assertTrue("Expected dynamic groove relief for seed $seedIdx", system.reliefSpan() > 0.30f)
        }
    }

    @Test
    fun `taste engine evolves discovery ranking over time and separates original master song from copies in search`() {
        val now = System.currentTimeMillis()
        val saved = listOf(
            com.example.data.model.TrackItem(
                id = "sv_1",
                title = "Veridis Quo",
                artist = "Daft Punk",
                album = "Discovery",
                artworkUrl = "https://img/dp.jpg",
                audioStreamUrl = "https://stream/1.m4a",
                durationMs = 345_000L
            )
        )
        val scrobbles = (1..5).map { idx ->
            com.example.data.local.ScrobbleEntity(
                trackId = "sc_dp_$idx",
                title = "Digital Love",
                artist = "Daft Punk",
                album = "Discovery",
                artworkUrl = "https://img/dp.jpg",
                audioStreamUrl = "https://stream/dl.m4a",
                durationMs = 301_000L,
                sourceTypeName = "YTM_ADFREE",
                playedAt = now - idx * 60_000L
            )
        }

        val profile = com.example.domain.TasteAndSearchRankingEngine.buildTasteProfile(
            savedTracks = saved,
            scrobbles = scrobbles,
            downloadedTracks = emptyList(),
            userPlaylists = emptyList(),
            spotifyTopArtists = emptyList(),
            spotifyTopTracks = emptyList(),
            nowMs = now
        )
        assertTrue(profile.hasPersonalSignal)
        assertEquals("Daft Punk", profile.topAffinityArtists.first())

        // Sonic neighbor (Justice) and core favorite (Daft Punk) should rank ahead of unrelated filler
        val discoveryPool = listOf(
            com.example.data.model.TrackItem(
                id = "unaligned",
                title = "Generic Acoustic Cover",
                artist = "Random Cover Band",
                album = "Tribute Vol 4",
                artworkUrl = "",
                audioStreamUrl = "https://stream/u.m4a",
                durationMs = 190_000L
            ),
            com.example.data.model.TrackItem(
                id = "neighbor",
                title = "Genesis",
                artist = "Justice",
                album = "Cross",
                artworkUrl = "https://img/justice.jpg",
                audioStreamUrl = "https://stream/j.m4a",
                durationMs = 234_000L
            ),
            com.example.data.model.TrackItem(
                id = "favorite",
                title = "Something About Us",
                artist = "Daft Punk",
                album = "Discovery",
                artworkUrl = "https://img/dp.jpg",
                audioStreamUrl = "https://stream/sau.m4a",
                durationMs = 231_000L
            )
        )
        val ranked = com.example.domain.TasteAndSearchRankingEngine.rankDiscoveryTracks(discoveryPool, profile)
        assertEquals("favorite", ranked[0].id)
        assertEquals("neighbor", ranked[1].id)
        assertEquals("unaligned", ranked[2].id)

        // Search partitioning: separate canonical Original Master from slowed/karaoke/tribute noise
        val noisySearchPool = listOf(
            com.example.data.model.TrackItem(
                id = "copy_slowed",
                title = "Starboy (Ultra Slowed + Reverb TikTok Version)",
                artist = "DJ TikTok Edits",
                album = "Slowed Hits 2025",
                artworkUrl = "https://img/s1.jpg",
                audioStreamUrl = "https://stream/s1.m4a",
                durationMs = 260_000L
            ),
            com.example.data.model.TrackItem(
                id = "copy_karaoke",
                title = "Starboy (Karaoke Instrumental)",
                artist = "Karaoke Stars",
                album = "Sing Along",
                artworkUrl = "https://img/s2.jpg",
                audioStreamUrl = "https://stream/s2.m4a",
                durationMs = 228_000L
            ),
            com.example.data.model.TrackItem(
                id = "orig_master",
                title = "Starboy",
                artist = "The Weeknd, Daft Punk",
                album = "Starboy",
                artworkUrl = "https://img/starboy.jpg",
                audioStreamUrl = "https://stream/orig.m4a",
                durationMs = 230_000L,
                spotifyTrackId = "7MXVkk9YMctZqd1Srtv4MB"
            )
        )
        val partitioned = com.example.domain.TasteAndSearchRankingEngine.partitionSearchResults(
            query = "Starboy",
            rawTracks = noisySearchPool,
            profile = profile
        )
        assertEquals("orig_master", partitioned.topMatch?.id)
        assertEquals(2, partitioned.remainingTracks.size)
        assertTrue(partitioned.remainingTracks.any { it.id == "copy_slowed" })
        assertTrue(partitioned.remainingTracks.any { it.id == "copy_karaoke" })
    }

    @Test
    fun `144hz display refresh selector locks peak refresh rate in high mode and formatTimeCode formats without regex`() {
        val modes144 = listOf(
            DisplayModeCandidate(modeId = 1, physicalWidth = 1080, physicalHeight = 2412, refreshRate = 60f),
            DisplayModeCandidate(modeId = 2, physicalWidth = 1080, physicalHeight = 2412, refreshRate = 120f),
            DisplayModeCandidate(modeId = 3, physicalWidth = 1080, physicalHeight = 2412, refreshRate = 144f)
        )
        assertEquals(
            3,
            selectPreferredDisplayModeId(
                policy = com.example.data.model.DisplayRefreshPolicy.HIGH,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2412,
                modes = modes144
            )
        )
        assertEquals(
            144f,
            selectPreferredRefreshRateHz(
                policy = com.example.data.model.DisplayRefreshPolicy.HIGH,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2412,
                modes = modes144
            ),
            0.01f
        )
        assertEquals(
            0f,
            selectPreferredRefreshRateHz(
                policy = com.example.data.model.DisplayRefreshPolicy.AUTO,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2412,
                modes = modes144
            ),
            0.01f
        )
        assertEquals(
            60f,
            selectPreferredRefreshRateHz(
                policy = com.example.data.model.DisplayRefreshPolicy.BATTERY_SAVER,
                isOsPowerSaveMode = false,
                activeWidth = 1080,
                activeHeight = 2412,
                modes = modes144
            ),
            0.01f
        )

        assertEquals("00:00", com.example.ui.components.formatTimeCode(-500L))
        assertEquals("00:00", com.example.ui.components.formatTimeCode(0L))
        assertEquals("03:07", com.example.ui.components.formatTimeCode(187_400L))
        assertEquals("12:59", com.example.ui.components.formatTimeCode(779_999L))
        assertEquals("125:09", com.example.ui.components.formatTimeCode(7_509_000L))
    }
}



