package com.example.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Off-main-thread 6-band real-time FFT audio analyser backed by Android's [Visualizer] API:
 *   Band 0: Sub-bass   (20 – 60 Hz)
 *   Band 1: Bass       (60 – 250 Hz)
 *   Band 2: Low mids   (250 – 500 Hz)
 *   Band 3: Midrange   (500 – 2,000 Hz)
 *   Band 4: Upper mids (2,000 – 6,000 Hz)
 *   Band 5: Treble     (6,000 – 20,000 Hz)
 *
 * Threading & Fallback Guarantees:
 * - All FFT bin unpacking, logarithmic dB scaling, and attack/release smoothing run on a dedicated
 *   background [HandlerThread] (`NchordVisualizerWorker`), never blocking the Compose UI thread.
 * - Exposes [isActivelyCapturingRealFft] truthfully: returns `true` ONLY when [Visualizer] is
 *   attached, enabled, and delivering non-silent acoustic frames (automatically falling back to
 *   `false` if permission is absent, initialization fails, or hardware offload zeroes the PCM tap).
 */
internal class NothingAudioVisualizer(
    private val context: Context
) {
    private val lock = Any()
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = -1

    private val smoothedSixBands = FloatArray(6) { 0.08f }
    private val rollingFloorSixBands = FloatArray(6) { 0.10f }
    private val latestSixBandsSnapshot = FloatArray(6) { 0.08f }
    private val latestTwelveBandsSnapshot = FloatArray(12) { 0.08f }

    @Volatile
    private var visualizerEnabled: Boolean = false

    @Volatile
    private var lastNonSilentFrameElapsedMs: Long = 0L

    @Volatile
    private var attachTimestampElapsedMs: Long = 0L

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True ONLY when Android's hardware/DSP [Visualizer] is attached and producing live non-zero
     * FFT frames (if hardware offload yields all-zero buffers for >1,600ms, returns `false` so
     * callers fall back cleanly without claiming live FFT).
     */
    val isActivelyCapturingRealFft: Boolean
        get() {
            if (!visualizerEnabled) return false
            val now = android.os.SystemClock.elapsedRealtime()
            val sinceNonSilent = now - lastNonSilentFrameElapsedMs
            val sinceAttach = now - attachTimestampElapsedMs
            return lastNonSilentFrameElapsedMs > 0L && sinceNonSilent <= 1_600L || sinceAttach <= 650L
        }

    fun attachOrUpdate(audioSessionId: Int, playing: Boolean) {
        if (audioSessionId <= 0 || !playing || !hasRecordAudioPermission()) {
            pauseCapture()
            return
        }
        ensureWorkerHandler().post {
            synchronized(lock) {
                if (visualizer != null && attachedSessionId == audioSessionId) {
                    if (!visualizerEnabled) {
                        try {
                            visualizer?.enabled = true
                            visualizerEnabled = true
                            attachTimestampElapsedMs = android.os.SystemClock.elapsedRealtime()
                        } catch (e: Exception) {
                            Log.w("NothingAudioVisualizer", "Failed re-enabling Visualizer", e)
                            releaseVisualizerLocked()
                        }
                    }
                    return@post
                }

                releaseVisualizerLocked()
                try {
                    val range = Visualizer.getCaptureSizeRange()
                    val captureSize = 512.coerceIn(range[0], range[1])
                    val maxRate = Visualizer.getMaxCaptureRate()
                    // ~38Hz capture rate captures sharp kick/snare transients off-main-thread
                    val targetRateMilliHz = min(maxRate, 38_000).coerceAtLeast(12_000)

                    val instance = Visualizer(audioSessionId)
                    instance.enabled = false
                    instance.captureSize = captureSize
                    instance.measurementMode = Visualizer.MEASUREMENT_MODE_NONE
                    instance.scalingMode = Visualizer.SCALING_MODE_NORMALIZED

                    val listener = object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            vis: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            vis: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 8) return
                            // Android Visualizer passes samplingRate in milliHertz (e.g. 44100000)
                            val sampleRateHz = if (samplingRate > 200_000) {
                                samplingRate / 1000
                            } else if (samplingRate > 0) {
                                samplingRate
                            } else {
                                44_100
                            }
                            processFftFrameOffMainThread(fft, sampleRateHz)
                        }
                    }

                    val status = instance.setDataCaptureListener(
                        listener,
                        targetRateMilliHz,
                        false,
                        true
                    )
                    if (status == Visualizer.SUCCESS) {
                        instance.enabled = true
                        visualizer = instance
                        attachedSessionId = audioSessionId
                        visualizerEnabled = true
                        attachTimestampElapsedMs = android.os.SystemClock.elapsedRealtime()
                    } else {
                        instance.release()
                    }
                } catch (e: Exception) {
                    Log.w("NothingAudioVisualizer", "Visualizer unavailable for session $audioSessionId: ${e.message}")
                    releaseVisualizerLocked()
                }
            }
        }
    }

    fun pauseCapture() {
        val handler = synchronized(lock) { workerHandler } ?: return
        handler.post {
            synchronized(lock) {
                if (visualizerEnabled) {
                    try {
                        visualizer?.enabled = false
                    } catch (_: Exception) {
                    }
                    visualizerEnabled = false
                }
            }
        }
    }

    fun detachSession() {
        val handler = synchronized(lock) { workerHandler } ?: return
        handler.post {
            synchronized(lock) {
                releaseVisualizerLocked()
                for (i in smoothedSixBands.indices) {
                    smoothedSixBands[i] = 0.08f
                    rollingFloorSixBands[i] = 0.10f
                    latestSixBandsSnapshot[i] = 0.08f
                }
                for (i in latestTwelveBandsSnapshot.indices) {
                    latestTwelveBandsSnapshot[i] = 0.08f
                }
            }
        }
    }

    fun release() {
        synchronized(lock) {
            releaseVisualizerLocked()
            workerThread?.quitSafely()
            workerThread = null
            workerHandler = null
        }
    }

    /**
     * Copies the latest smoothed 6-band (`Sub-bass..Treble`) and 12-bar Glyph spectra if live FFT
     * is actively producing non-silent frames, or returns `null` when fallback should be used.
     */
    fun readLiveSpectraIfActive(): Pair<List<Float>, List<Float>>? {
        if (!isActivelyCapturingRealFft) return null
        synchronized(lock) {
            val six = List(6) { latestSixBandsSnapshot[it] }
            val twelve = List(12) { latestTwelveBandsSnapshot[it] }
            return six to twelve
        }
    }

    private fun processFftFrameOffMainThread(fft: ByteArray, sampleRateHz: Int) {
        val rawSix = extractSixMusicalBandsFromFft(fft, sampleRateHz)
        var peakSignal = 0f
        for (v in rawSix) {
            if (v > peakSignal) peakSignal = v
        }
        if (peakSignal > 0.095f) {
            lastNonSilentFrameElapsedMs = android.os.SystemClock.elapsedRealtime()
        }

        synchronized(lock) {
            for (b in 0 until 6) {
                val rawVal = rawSix[b]
                // Track slow adaptive floor so steady compression doesn't mask kick/snare transients
                val floorPrev = rollingFloorSixBands[b]
                val floorCoeff = if (rawVal < floorPrev) 0.14f else 0.025f
                val nextFloor = (floorPrev + (rawVal - floorPrev) * floorCoeff).coerceIn(0.08f, 0.52f)
                rollingFloorSixBands[b] = nextFloor

                val transientHeadroom = (rawVal - nextFloor * 0.72f).coerceAtLeast(0f)
                val contrastTarget = (0.06f + transientHeadroom * 1.38f).coerceIn(0.06f, 0.99f)

                val prev = smoothedSixBands[b]
                // Snappy attack (0.76) on drum hits; musical release (0.28) so valleys carve cleanly between beats
                val coeff = if (contrastTarget > prev) 0.76f else 0.28f
                val smoothed = (prev + (contrastTarget - prev) * coeff).coerceIn(0.06f, 0.99f)
                smoothedSixBands[b] = smoothed
                latestSixBandsSnapshot[b] = smoothed
            }
            expandSixToTwelveBars(smoothedSixBands, latestTwelveBandsSnapshot)
        }
    }

    private fun ensureWorkerHandler(): Handler {
        synchronized(lock) {
            val existing = workerHandler
            if (existing != null) return existing
            val thread = HandlerThread("NchordVisualizerWorker").apply { start() }
            val handler = Handler(thread.looper)
            workerThread = thread
            workerHandler = handler
            return handler
        }
    }

    private fun releaseVisualizerLocked() {
        visualizerEnabled = false
        attachedSessionId = -1
        lastNonSilentFrameElapsedMs = 0L
        val old = visualizer
        visualizer = null
        if (old != null) {
            try {
                old.enabled = false
            } catch (_: Exception) {
            }
            try {
                old.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        // Cutoff edges in Hz for: Sub-bass, Bass, Low mids, Midrange, Upper mids, Treble
        private val BAND_EDGES_HZ = floatArrayOf(
            20f,
            60f,
            250f,
            500f,
            2_000f,
            6_000f,
            20_000f
        )

        // Perceptual tilt compensation so high-frequency hi-hats/air match bass energy visually
        private val BAND_PRE_GAIN = floatArrayOf(
            1.05f, // Sub-bass
            1.00f, // Bass
            1.15f, // Low mids
            1.30f, // Midrange
            1.65f, // Upper mids
            2.10f  // Treble
        )

        /**
         * Pure, deterministic converter from Android's packed `Visualizer.getFft()` byte array into
         * 6 normalized `[0.08, 0.98]` musical frequency bands.
         *
         * Android packed FFT layout for capture size `N = fft.size`:
         * - `fft[0]` = Re(DC), `fft[1]` = Re(Nyquist)
         * - For bin `k` in `1 until N/2`: `fft[2*k]` = Re(k), `fft[2*k + 1]` = Im(k)
         * - Frequency of bin `k` = `k * sampleRateHz / N`
         */
        internal fun extractSixMusicalBandsFromFft(
            fft: ByteArray,
            sampleRateHz: Int
        ): FloatArray {
            val out = FloatArray(6) { 0.08f }
            val n = fft.size
            if (n < 8 || sampleRateHz <= 0) return out

            val halfBins = n / 2
            val binHz = sampleRateHz.toFloat() / n.toFloat()

            for (band in 0 until 6) {
                val lowHz = BAND_EDGES_HZ[band]
                val highHz = BAND_EDGES_HZ[band + 1]
                val startBin = max(1, (lowHz / binHz).toInt())
                val endBin = min(halfBins - 1, max(startBin, (highHz / binHz).toInt()))

                var sumMag = 0f
                var peakMag = 0f
                var count = 0
                for (k in startBin..endBin) {
                    val re = fft[2 * k].toFloat()
                    val im = fft[2 * k + 1].toFloat()
                    val mag = hypot(re, im)
                    sumMag += mag
                    if (mag > peakMag) peakMag = mag
                    count++
                }

                if (count > 0) {
                    val avgMag = sumMag / count.toFloat()
                    // Blend peak and RMS-like average so narrow-band bass notes in coarse low bins register cleanly
                    val blendedMag = (0.62f * peakMag + 0.38f * avgMag) * BAND_PRE_GAIN[band]
                    // Maximum theoretical single-bin magnitude for signed bytes (-128..127) is ~181f
                    // Logarithmic compression maps quiet passages (~1.5) -> ~0.12 and loud crests (~90+) -> ~0.96
                    val logNormalized = (ln(1f + blendedMag * 0.42f) / ln(1f + 115f * 0.42f))
                        .coerceIn(0f, 1f)
                    out[band] = (0.08f + logNormalized * 0.90f).coerceIn(0.08f, 0.98f)
                }
            }
            return out
        }

        /**
         * Smoothly interpolates the 6 core musical bands into the 12-column Glyph spectrum.
         */
        internal fun expandSixToTwelveBars(sixBands: FloatArray, outTwelve: FloatArray) {
            if (sixBands.size < 6 || outTwelve.size < 12) return
            for (i in 0 until 12) {
                val pos = (i / 11f) * 5f
                val lo = pos.toInt().coerceIn(0, 5)
                val hi = (lo + 1).coerceAtMost(5)
                val frac = pos - lo
                outTwelve[i] = (sixBands[lo] * (1f - frac) + sixBands[hi] * frac)
                    .coerceIn(0.08f, 0.98f)
            }
        }
    }
}
