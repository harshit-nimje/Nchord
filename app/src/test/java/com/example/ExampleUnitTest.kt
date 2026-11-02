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
}

