package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic per-song geometric & rhythmic wave signature so each track has its own
 * recognizable wave choreography, wavelength density, and tempo-locked pulse cadence.
 */
data class SongGrooveProfile(
    val signatureKey: String,
    val bpm: Float,
    val choreographyMode: Int, // 0 = Radial Shockwave, 1 = Twin-Driver Interference, 2 = Chladni Cymatics, 3 = Spiral Strobe
    val ringFrequency: Float,
    val lobeCount: Int,
    val latticeFreqX: Float,
    val latticeFreqY: Float,
    val spiralTwist: Float
) {
    companion object {
        private const val TWO_PI_F = (PI * 2.0).toFloat()

        fun fromTrack(trackKey: String, titleHint: String = ""): SongGrooveProfile {
            val cleanKey = trackKey.ifBlank { "default_nchord_groove" }
            val seed = (cleanKey.hashCode() * 31) xor titleHint.hashCode()
            val absSeed = abs(seed.toLong()).toInt()
            val lower = titleHint.lowercase()

            val bpm = when {
                lower.contains("slow") || lower.contains("acoustic") || lower.contains("nocturn") ||
                    lower.contains("lullaby") || lower.contains("ballad") -> 84f + (absSeed % 14)
                lower.contains("club") || lower.contains("techno") || lower.contains("house") ||
                    lower.contains("synth") || lower.contains("dance") || lower.contains("upbeat") -> 120f + (absSeed % 18)
                else -> 92f + (absSeed % 40)
            }

            val mode = (absSeed shr 2) and 3
            val ringFreq = 13.5f + ((absSeed shr 4) % 9) * 1.15f // 13.5 .. 22.7
            val lobes = 2 + ((absSeed shr 7) % 4) // 2..5 harmonic lobes
            val latX = 9.4f + ((absSeed shr 9) % 7) * 1.35f
            val latY = 10.2f + ((absSeed shr 11) % 7) * 1.30f
            val twist = (if ((absSeed and 1) == 0) 1f else -1f) * (6.8f + ((absSeed shr 13) % 6) * 1.25f)

            return SongGrooveProfile(
                signatureKey = "$cleanKey|$titleHint",
                bpm = bpm,
                choreographyMode = mode,
                ringFrequency = ringFreq,
                lobeCount = lobes,
                latticeFreqX = latX,
                latticeFreqY = latY,
                spiralTwist = twist + TWO_PI_F * 0f
            )
        }
    }
}

/**
 * Stationary-Coordinate Zero-Allocation 2D Dot-Matrix Groove Wave Field.
 *
 * - Every lattice dot stays strictly anchored at its exact grid coordinate `(restNx, restNy)` —
 *   zero X/Y positional displacement, zero wobbling out of alignment.
 * - Song-specific wave geometry ([SongGrooveProfile]) is precomputed into lookup tables once per
 *   track switch rather than recalculated per frame.
 * - Real-time spectral onset detection (`beatKick` & `snareSnap`) combined with tempo-locked beat
 *   wavefronts drives wide-range dot diameter sculpting (`0.50dp` dark valleys up to `5.35dp`
 *   luminous beat crests) and expanding kick shockwave rings across the fixed grid.
 * - Dots at or below [staticCutoffYPx] (song title and transport region) remain strictly stitched
 *   in their calm static baseline state with zero animation and skip per-frame wave integration.
 */
class ParticleSystem(
    val cols: Int = 19,
    val rows: Int = 33,
    private val stiffness: Float = 78f,
    private val damping: Float = 14.8f
) {
    val count: Int = cols * rows

    val restNx = FloatArray(count)
    val restNy = FloatArray(count)
    val dispX = FloatArray(count) // Strictly 0f: dots never displace from grid coordinates
    val dispY = FloatArray(count) // Strictly 0f: dots never displace from grid coordinates
    val energy = FloatArray(count)
    val grooveRelief = FloatArray(count) // -1f (carved valley) .. +1f (sculpted ridge)
    val colorWave = FloatArray(count)
    private val velEnergy = FloatArray(count)

    // Precomputed static per-dot polar/Cartesian geometry (immutable for grid dimensions)
    private val radialDistLut = FloatArray(count)
    private val polarThetaLut = FloatArray(count)
    private val twinSourceDistALut = FloatArray(count)
    private val twinSourceDistBLut = FloatArray(count)
    private val haloRingLut = FloatArray(count)
    private val outerFieldLut = FloatArray(count)
    internal val topEdgeFadeLut = FloatArray(count)

    // Song-seeded spatial phase LUTs (recomputed only when track changes)
    private val bassDistPhase = FloatArray(count)
    private val twinInterferenceBase = FloatArray(count)
    private val diamondPhaseX = FloatArray(count)
    private val diamondPhaseY = FloatArray(count)
    private val spiralBasePhase = FloatArray(count)
    private val trebleBasePhase = FloatArray(count)

    // Cached pixel coordinates updated only when Canvas geometry changes
    private val cachedPx = FloatArray(count)
    private val cachedPy = FloatArray(count)
    private var lastPadX = Float.NaN
    private var lastPadY = Float.NaN
    private var lastUsableW = Float.NaN
    private var lastUsableH = Float.NaN

    var activeProfile: SongGrooveProfile = SongGrooveProfile.fromTrack("default_nchord_groove")
        private set

    var liveBlend: Float = 0f
        private set
    private var liveBlendVel: Float = 0f
    private var internalClockSec: Float = 0f
    private var prevLowEnergy: Float = 0f
    private var prevMidHighEnergy: Float = 0f
    private var beatKick: Float = 0f
    private var snareSnap: Float = 0f
    private var shockwaveRadius: Float = 0.14f

    // Normalized center of the circular vinyl platter in the upper-middle stage
    private val centerNx = 0.50f
    private val centerNy = 0.36f

    init {
        var idx = 0
        for (r in 0 until rows) {
            val ny = if (rows > 1) r.toFloat() / (rows - 1).toFloat() else 0.5f
            val ry = (ny - centerNy) * 1.40f
            val topFade = if (ny < 0.07f) (ny / 0.07f).coerceIn(0.32f, 1f) else 1f
            for (c in 0 until cols) {
                val nx = if (cols > 1) c.toFloat() / (cols - 1).toFloat() else 0.5f
                val rx = nx - centerNx
                val dist = sqrt(rx * rx + ry * ry).coerceAtLeast(0.001f)
                val theta = atan2(ry, rx)
                val distFromHalo = dist - 0.33f

                // Dual stereo driver focal points for Twin-Interference choreography
                val ax = nx - 0.24f
                val ay = (ny - 0.30f) * 1.32f
                val bx = nx - 0.76f
                val by = (ny - 0.42f) * 1.32f

                restNx[idx] = nx
                restNy[idx] = ny
                radialDistLut[idx] = dist
                polarThetaLut[idx] = theta
                twinSourceDistALut[idx] = sqrt(ax * ax + ay * ay)
                twinSourceDistBLut[idx] = sqrt(bx * bx + by * by)
                haloRingLut[idx] = exp(-(distFromHalo * distFromHalo) / 0.125f)
                outerFieldLut[idx] = (1.08f - dist * 0.44f).coerceIn(0.28f, 1f)
                topEdgeFadeLut[idx] = topFade
                idx++
            }
        }
        rebuildSongPhaseLuts(activeProfile)
    }

    /**
     * Reconfigures the dot-matrix spatial wave LUTs when a new song loads. No-op if unchanged.
     */
    fun configureTrackSignature(trackKey: String, titleHint: String = "") {
        val candidateKey = "${trackKey.ifBlank { "default_nchord_groove" }}|$titleHint"
        if (candidateKey == activeProfile.signatureKey) return
        val nextProfile = SongGrooveProfile.fromTrack(trackKey, titleHint)
        activeProfile = nextProfile
        rebuildSongPhaseLuts(nextProfile)
    }

    private fun rebuildSongPhaseLuts(profile: SongGrooveProfile) {
        val lobesF = profile.lobeCount.toFloat()
        for (i in 0 until count) {
            val nx = restNx[i]
            val ny = restNy[i]
            val dist = radialDistLut[i]
            val theta = polarThetaLut[i]

            bassDistPhase[i] = dist * profile.ringFrequency
            twinInterferenceBase[i] = (twinSourceDistALut[i] - twinSourceDistBLut[i]) * (profile.ringFrequency * 0.85f)
            diamondPhaseX[i] = (nx - centerNx) * profile.latticeFreqX
            diamondPhaseY[i] = (ny - centerNy) * profile.latticeFreqY
            spiralBasePhase[i] = theta * lobesF + dist * profile.spiralTwist
            trebleBasePhase[i] = nx * (14f + lobesF * 2.1f) - ny * (11f + lobesF * 1.6f)
        }
    }

    internal fun ensurePixelGrid(padX: Float, padY: Float, usableW: Float, usableH: Float) {
        if (padX == lastPadX && padY == lastPadY && usableW == lastUsableW && usableH == lastUsableH) {
            return
        }
        lastPadX = padX
        lastPadY = padY
        lastUsableW = usableW
        lastUsableH = usableH
        for (i in 0 until count) {
            cachedPx[i] = padX + restNx[i] * usableW
            cachedPy[i] = padY + restNy[i] * usableH
        }
    }

    internal fun pxAt(index: Int): Float = cachedPx[index]
    internal fun pyAt(index: Int): Float = cachedPy[index]

    /**
     * Advances spring-damped luminance, groove relief & dot-size wave integration by [rawDtSeconds].
     * Dot positions `(restNx, restNy)` never move. Dots strictly below [activeNyCutoff] skip wave
     * synthesis and cleanly reset any residual energy if the cutoff shifted dynamically.
     */
    fun step(
        rawDtSeconds: Float,
        isPlaying: Boolean,
        bass: Float,
        mid: Float,
        treble: Float,
        activeNyCutoff: Float = 1f,
        subBass: Float = bass,
        lowMid: Float = (bass + mid) * 0.5f,
        upperMid: Float = (mid + treble) * 0.5f,
        songPositionSeconds: Float = -1f,
        playbackSpeed: Float = 1f
    ): Boolean {
        val dt = rawDtSeconds.coerceIn(0.001f, 0.050f)
        val sb = subBass.coerceIn(0f, 1f)
        val b = bass.coerceIn(0f, 1f)
        val lm = lowMid.coerceIn(0f, 1f)
        val m = mid.coerceIn(0f, 1f)
        val um = upperMid.coerceIn(0f, 1f)
        val t = treble.coerceIn(0f, 1f)
        val clampedCutoffNy = activeNyCutoff.coerceIn(0f, 1.05f)
        val profile = activeProfile
        val speed = playbackSpeed.coerceIn(0.5f, 2.0f)

        val effectiveSongSec: Float
        if (isPlaying) {
            internalClockSec += dt * speed
            effectiveSongSec = if (songPositionSeconds >= 0f) songPositionSeconds else internalClockSec

            // 1. Sub-bass & Kick drum transient onset detector -> launches expanding shockwave ring
            val lowPunch = sb * 0.62f + b * 0.38f
            val bassRise = (lowPunch - prevLowEnergy).coerceAtLeast(0f)
            prevLowEnergy += (lowPunch - prevLowEnergy) * (dt * 15f).coerceIn(0f, 1f)

            val prevKick = beatKick
            beatKick = (beatKick * exp(-dt * 8.2f) + bassRise * 3.1f + (lowPunch - 0.42f).coerceAtLeast(0f) * dt * 5.2f)
                .coerceIn(0f, 1f)
            // Re-trigger expanding shockwave ring from vinyl edge when a sharp kick hits
            if (bassRise > 0.085f && prevKick < 0.58f) {
                shockwaveRadius = 0.16f
            } else {
                shockwaveRadius += dt * (0.95f + beatKick * 1.35f) * speed
                if (shockwaveRadius > 1.18f) {
                    shockwaveRadius = 0.16f + (shockwaveRadius - 1.18f)
                }
            }

            // 2. Snare / Clap / Vocal transient detector -> crisp geometric harmonic accent
            val highPunch = m * 0.38f + um * 0.38f + t * 0.24f
            val midHighRise = (highPunch - prevMidHighEnergy).coerceAtLeast(0f)
            prevMidHighEnergy += (highPunch - prevMidHighEnergy) * (dt * 16f).coerceIn(0f, 1f)
            snareSnap = (snareSnap * exp(-dt * 10.5f) + midHighRise * 2.9f + (highPunch - 0.48f).coerceAtLeast(0f) * dt * 3.6f)
                .coerceIn(0f, 1f)
        } else {
            effectiveSongSec = if (songPositionSeconds >= 0f) songPositionSeconds else internalClockSec
            prevLowEnergy = 0f
            prevMidHighEnergy = 0f
            beatKick *= exp(-dt * 11f)
            snareSnap *= exp(-dt * 12f)
        }

        val targetBlend = if (isPlaying) 1f else 0f
        val blendAcc = (targetBlend - liveBlend) * 36f - 11.5f * liveBlendVel
        liveBlendVel += blendAcc * dt
        liveBlend = (liveBlend + liveBlendVel * dt).coerceIn(0f, 1f)

        var maxResidual = abs(targetBlend - liveBlend) + abs(liveBlendVel) * 0.1f

        // Tempo-locked beat & bar phases synchronized directly to the song's BPM & timeline!
        val beatsElapsed = effectiveSongSec * (profile.bpm / 60f)
        val beatFloor = floor(beatsElapsed)
        val beatFrac = (beatsElapsed - beatFloor).coerceIn(0f, 1f)
        val barBeat = beatFloor.toInt() and 3
        val tempoPulse = exp(-beatFrac * 5.2f) // Crisp rhythmic pulse on every quarter-note beat

        // Beat-locked phase shifts (exactly 1 full wave cycle per 2 beats -> locked to song rhythm!)
        val radialWaveTravel = beatsElapsed * PI.toFloat()
        val harmonicShiftX = beatsElapsed * (PI.toFloat() * 0.5f) + if (barBeat == 1 || barBeat == 3) 0.55f else 0f
        val harmonicShiftY = beatsElapsed * (PI.toFloat() * 0.5f)
        val spiralRotation = beatsElapsed * (PI.toFloat() * 0.65f)
        val trebleTravel = beatsElapsed * (PI.toFloat() * 1.25f)

        // Overall acoustic energy envelope: deep calm in quiet passages, explosive punch on loud drops
        val overallAcousticPower = (
            sb * 0.30f + b * 0.24f + lm * 0.12f + m * 0.16f + um * 0.10f + t * 0.08f
            ).coerceIn(0f, 1f)
        val rhythmicPulseBoost = (beatKick * 0.58f + snareSnap * 0.28f + tempoPulse * overallAcousticPower * 0.34f)
            .coerceIn(0f, 1f)

        // Mode-specific choreography weights so songs visually feel distinct from one another
        val mode = profile.choreographyMode
        val radialWeight: Float
        val twinWeight: Float
        val latticeWeight: Float
        val spiralWeight: Float
        when (mode) {
            0 -> { // Concentric Platter Shockwave + Chladni Cross
                radialWeight = 0.52f + beatKick * 0.34f
                twinWeight = 0.08f
                latticeWeight = 0.24f + snareSnap * 0.26f
                spiralWeight = 0.16f + um * 0.14f
            }
            1 -> { // Twin-Driver Stereo Interference
                radialWeight = 0.24f + beatKick * 0.26f
                twinWeight = 0.44f + lm * 0.22f + m * 0.20f
                latticeWeight = 0.18f + snareSnap * 0.20f
                spiralWeight = 0.14f + t * 0.16f
            }
            2 -> { // Chladni Cymatic Standing Plate (morphs cleanly on backbeats)
                radialWeight = 0.26f + beatKick * 0.28f
                twinWeight = 0.14f
                latticeWeight = 0.46f + m * 0.24f + snareSnap * 0.28f
                spiralWeight = 0.14f + um * 0.16f
            }
            else -> { // Harmonic Multi-Lobe Spiral Strobe
                radialWeight = 0.28f + beatKick * 0.28f
                twinWeight = 0.12f + lm * 0.14f
                latticeWeight = 0.18f + snareSnap * 0.18f
                spiralWeight = 0.44f + m * 0.20f + um * 0.22f
            }
        }
        val trebleWeight = 0.12f + t * 0.28f + snareSnap * 0.14f
        val weightNorm = 1f / (radialWeight + twinWeight + latticeWeight + spiralWeight + trebleWeight).coerceAtLeast(0.5f)

        // Dynamic drive scales near zero in silence (~0.06) and surges to ~1.28 on beat hits
        val haloDrive = (0.08f + overallAcousticPower * 0.66f + rhythmicPulseBoost * 0.62f).coerceIn(0f, 1.32f)
        val outerDrive = (0.05f + overallAcousticPower * 0.52f + rhythmicPulseBoost * 0.56f).coerceIn(0f, 1.18f)

        val reliefLerp = (dt * 18.0f).coerceIn(0f, 1f)
        val colorLerp = (dt * 12.0f).coerceIn(0f, 1f)
        val currentShockR = shockwaveRadius

        for (i in 0 until count) {
            if (restNy[i] > clampedCutoffNy) {
                if (energy[i] != 0f || velEnergy[i] != 0f || grooveRelief[i] != 0f || colorWave[i] != 0f) {
                    energy[i] = 0f
                    velEnergy[i] = 0f
                    grooveRelief[i] = 0f
                    colorWave[i] = 0f
                }
                continue
            }

            var targetE = 0f
            var targetRelief = 0f
            var targetColorShift = 0f

            if (isPlaying) {
                // 1. Tempo-locked radial bass wave
                val bassRingRaw = sin(bassDistPhase[i] - radialWaveTravel)
                val halfRing = 0.5f + 0.5f * bassRingRaw
                val bassCrest = halfRing * halfRing

                // 2. Stereo twin-source wave interference
                val twinWave = cos(twinInterferenceBase[i] + harmonicShiftX * 1.4f)

                // 3. Chladni standing/travelling cymatic plate
                val dxPhase = diamondPhaseX[i]
                val dyPhase = diamondPhaseY[i]
                val chladniWave = if (mode == 2) {
                    (cos(dxPhase + harmonicShiftX) * cos(dyPhase - harmonicShiftY) -
                        sin(dyPhase * 0.85f + harmonicShiftX) * sin(dxPhase * 0.85f - harmonicShiftY))
                        .coerceIn(-1f, 1f)
                } else {
                    sin(dxPhase + harmonicShiftX) * cos(dyPhase - harmonicShiftY)
                }

                // 4. Multi-lobe vinyl groove spiral
                val spiralGroove = cos(spiralBasePhase[i] - spiralRotation)

                // 5. Treble shimmer ribbon
                val trebleRibbon = sin(trebleBasePhase[i] + trebleTravel)

                // Physical expanding kick-drum shockwave ring across the stationary grid
                val shockDelta = radialDistLut[i] - currentShockR
                val kickShockRing = exp(-(shockDelta * shockDelta) / 0.014f) * (0.35f + beatKick * 0.65f)

                val combinedWave = (
                    (bassRingRaw * radialWeight +
                        twinWave * twinWeight +
                        chladniWave * latticeWeight +
                        spiralGroove * spiralWeight +
                        trebleRibbon * trebleWeight) * weightNorm
                    ).coerceIn(-1f, 1f)

                // Amplify relief contrast with live acoustic power so quiet sections flatten calmly and beats carve deeply
                val grooveContrast = (0.34f + overallAcousticPower * 0.46f + rhythmicPulseBoost * 0.42f).coerceIn(0.25f, 1.22f)
                targetRelief = (combinedWave * grooveContrast + kickShockRing * 0.45f).coerceIn(-1f, 1f)

                // Sharp crest shaping: suppresses muddy mid-tones so wave ridges read as crisp geometric patterns
                val positiveCrest = ((combinedWave + 0.18f) * (1f / 1.18f)).coerceIn(0f, 1f)
                val sculptedCrest = positiveCrest * positiveCrest

                targetE = (
                    haloRingLut[i] * ((0.44f * bassCrest + 0.56f * sculptedCrest) * haloDrive + kickShockRing * 0.46f) +
                        outerFieldLut[i] * (sculptedCrest * outerDrive + kickShockRing * 0.38f)
                    ).coerceIn(0f, 1f)

                targetColorShift = (
                    0.46f * bassCrest +
                        0.34f * kickShockRing.coerceIn(0f, 1f) +
                        0.20f * (0.5f + 0.5f * spiralGroove)
                    ).coerceIn(0f, 1f)
            }

            // Fast spring-damped thickness & luminance energy (strictly zero positional displacement)
            val curE = energy[i]
            val curVe = velEnergy[i]
            val ae = (targetE - curE) * stiffness - damping * curVe
            val nve = curVe + ae * dt
            val ne = (curE + nve * dt).coerceIn(0f, 1f)
            velEnergy[i] = nve
            energy[i] = ne

            val nextRelief = (grooveRelief[i] + (targetRelief - grooveRelief[i]) * reliefLerp)
                .coerceIn(-1f, 1f)
            grooveRelief[i] = nextRelief
            colorWave[i] = colorWave[i] + (targetColorShift - colorWave[i]) * colorLerp

            val residual = ne + abs(nve) * 0.05f + abs(nextRelief) * 0.15f
            if (residual > maxResidual) {
                maxResidual = residual
            }
        }

        if (!isPlaying && maxResidual < 0.008f) {
            for (i in 0 until count) {
                energy[i] = 0f
                velEnergy[i] = 0f
                grooveRelief[i] = 0f
                colorWave[i] = 0f
            }
            liveBlend = 0f
            liveBlendVel = 0f
            beatKick = 0f
            snareSnap = 0f
            return false
        }

        return true
    }

    fun maxAbsDisplacement(): Float = 0f

    fun maxEnergy(): Float {
        var maxVal = 0f
        for (i in 0 until count) {
            if (energy[i] > maxVal) maxVal = energy[i]
        }
        return maxVal
    }

    fun reliefSpan(): Float {
        var minVal = 1f
        var maxVal = -1f
        for (i in 0 until count) {
            val r = grooveRelief[i]
            if (r < minVal) minVal = r
            if (r > maxVal) maxVal = r
        }
        return (maxVal - minVal).coerceAtLeast(0f)
    }

    companion object {
        /**
         * Fast branchless polynomial approximation of `x.pow(1.06f)` on `[0, 1]`.
         * Exact at `x = 0` and `x = 1`, monotonically increasing, max absolute error < 0.009,
         * avoiding `Math.pow` inside 120/144Hz per-dot Canvas draw loops.
         */
        internal fun fastCrestPower(x: Float): Float {
            val clamped = x.coerceIn(0f, 1f)
            return clamped * (0.94f + 0.06f * clamped)
        }
    }
}

private class CutoffRatioHolder(var activeNyCutoff: Float = 1f)

private class PlaybackClockInterpolator {
    var lastAnchorPosMs: Long = -1L
    var lastAnchorFrameNanos: Long = 0L

    fun sampleSeconds(anchorPosMs: Long, nowNanos: Long, isPlaying: Boolean, speed: Float): Float {
        if (anchorPosMs < 0L) return -1f
        if (anchorPosMs != lastAnchorPosMs || lastAnchorFrameNanos == 0L || !isPlaying) {
            lastAnchorPosMs = anchorPosMs
            lastAnchorFrameNanos = nowNanos
            return anchorPosMs / 1000f
        }
        val elapsedSec = ((nowNanos - lastAnchorFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.15f)
        return (anchorPosMs / 1000f) + elapsedSec * speed.coerceIn(0.5f, 2.0f)
    }
}

@Composable
fun NothingParticleMatrixBackdrop(
    isPlaying: Boolean,
    visible: Boolean,
    spectrum: List<Float>,
    sixBandSpectrum: List<Float> = emptyList(),
    trackSignatureKey: String = "",
    trackTitleHint: String = "",
    playbackPositionMs: Long = -1L,
    playbackSpeed: Float = 1f,
    accentColor: Color,
    staticCutoffYPx: Float = Float.MAX_VALUE,
    onFrameRendered: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val particleSystem = remember { ParticleSystem(cols = 19, rows = 33) }
    val cutoffHolder = remember { CutoffRatioHolder() }
    val clockInterpolator = remember { PlaybackClockInterpolator() }

    LaunchedEffect(trackSignatureKey, trackTitleHint) {
        particleSystem.configureTrackSignature(trackSignatureKey, trackTitleHint)
    }

    val lowPowerMode = LocalLowPowerAnimationMode.current
    val currentLowPower by rememberUpdatedState(lowPowerMode)
    val currentPlaying by rememberUpdatedState(isPlaying)
    val currentSpectrum by rememberUpdatedState(spectrum)
    val currentSixBands by rememberUpdatedState(sixBandSpectrum)
    val currentPosMs by rememberUpdatedState(playbackPositionMs)
    val currentSpeed by rememberUpdatedState(playbackSpeed)
    val currentOnFrameRendered by rememberUpdatedState(onFrameRendered)

    val stageAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "particle_stage_alpha"
    )

    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPlaying, visible) {
        if (!visible && !isPlaying && particleSystem.liveBlend <= 0.005f) return@LaunchedEffect
        var lastFrameNanos = 0L
        var keepStepping = true
        while (keepStepping) {
            withFrameNanos { nowNanos ->
                if (lastFrameNanos != 0L && currentLowPower && (nowNanos - lastFrameNanos) in 0L until 32_000_000L) {
                    return@withFrameNanos
                }
                val dtSec = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.050f)
                }
                lastFrameNanos = nowNanos

                val six = currentSixBands
                val spec = currentSpectrum
                val subBass: Float
                val bass: Float
                val lowMid: Float
                val mid: Float
                val upperMid: Float
                val treble: Float
                if (six.size >= 6) {
                    subBass = six[0]
                    bass = six[1]
                    lowMid = six[2]
                    mid = six[3]
                    upperMid = six[4]
                    treble = six[5]
                } else {
                    subBass = if (spec.size >= 2) spec[0] * 0.65f + spec[1] * 0.35f else 0.22f
                    bass = if (spec.size >= 4) spec[2] * 0.55f + spec[3] * 0.45f else 0.22f
                    lowMid = if (spec.size >= 6) (spec[4] + spec[5]) * 0.5f else 0.20f
                    mid = if (spec.size >= 8) (spec[6] + spec[7]) * 0.5f else 0.20f
                    upperMid = if (spec.size >= 10) (spec[8] + spec[9]) * 0.5f else 0.18f
                    treble = if (spec.size >= 12) (spec[10] + spec[11]) * 0.5f else 0.16f
                }

                val playingNow = currentPlaying && visible
                val songSec = clockInterpolator.sampleSeconds(
                    anchorPosMs = currentPosMs,
                    nowNanos = nowNanos,
                    isPlaying = playingNow,
                    speed = currentSpeed
                )

                val activeMotion = particleSystem.step(
                    rawDtSeconds = dtSec,
                    isPlaying = playingNow,
                    bass = bass,
                    mid = mid,
                    treble = treble,
                    activeNyCutoff = cutoffHolder.activeNyCutoff,
                    subBass = subBass,
                    lowMid = lowMid,
                    upperMid = upperMid,
                    songPositionSeconds = songSec,
                    playbackSpeed = currentSpeed
                )
                frameTick++
                // Refresh 46dp frosted button refraction on alternating frames while keeping Canvas at native 144Hz
                if ((frameTick and 1) == 0 || !playingNow) {
                    currentOnFrameRendered?.invoke()
                }
                keepStepping = playingNow || activeMotion
            }
        }
    }

    if (stageAlpha <= 0.005f) return

    val idleDotColor = Color(0xFF8C9099)
    val valleyTone = Color(0xFF434750)
    val deepTone = remember(accentColor) { lerp(idleDotColor, accentColor, 0.85f) }
    val crestTone = remember(accentColor) { lerp(accentColor, Color.White, 0.62f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tickRead = frameTick
        if (tickRead < 0) return@Canvas

        val padX = 20f * density
        val padY = 24f * density
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        particleSystem.ensurePixelGrid(padX, padY, usableW, usableH)

        val baseRadiusPx = 1.25f * density
        val minRadiusPx = 0.48f * density
        val maxExtraCrestPx = 4.10f * density
        val maxClampedRadiusPx = baseRadiusPx + maxExtraCrestPx + 0.65f * density
        val blend = particleSystem.liveBlend
        val effectiveCutoffY = if (staticCutoffYPx < Float.MAX_VALUE * 0.5f) {
            staticCutoffYPx
        } else {
            size.height * 0.68f
        }
        // Update normalized cutoff for next frame's step() with a small safety margin
        cutoffHolder.activeNyCutoff = (((effectiveCutoffY - padY) / usableH) + (1f / particleSystem.rows))
            .coerceIn(0f, 1.05f)

        val invFeatherZone = 1f / (22f * density).coerceAtLeast(1f)
        val lowerStaticColor = idleDotColor.copy(alpha = (0.165f * stageAlpha).coerceIn(0f, 0.45f))

        // Pre-extract RGB channels once per frame to avoid ~180,000 Oklab cbrt/pow conversions/sec at 144Hz
        val idleR = idleDotColor.red
        val idleG = idleDotColor.green
        val idleB = idleDotColor.blue
        val valDeltaR = valleyTone.red - idleR
        val valDeltaG = valleyTone.green - idleG
        val valDeltaB = valleyTone.blue - idleB
        val deepR = deepTone.red
        val deepG = deepTone.green
        val deepB = deepTone.blue
        val crestDeltaR = crestTone.red - deepR
        val crestDeltaG = crestTone.green - deepG
        val crestDeltaB = crestTone.blue - deepB

        for (i in 0 until particleSystem.count) {
            // Fixed grid coordinates — dots NEVER translate or drift out of place
            val px = particleSystem.pxAt(i)
            val py = particleSystem.pyAt(i)

            // Strictly stitched static dots at and below the song title & transport area
            if (py >= effectiveCutoffY) {
                drawCircle(
                    color = lowerStaticColor,
                    radius = baseRadiusPx,
                    center = Offset(px, py)
                )
                continue
            }

            val zoneActivity = ((effectiveCutoffY - py) * invFeatherZone).coerceIn(0f, 1f)
            val topEdgeFade = particleSystem.topEdgeFadeLut[i]
            val localBlend = blend * zoneActivity

            // Fast path when player is idle/paused at rest
            if (localBlend <= 0.002f) {
                val restAlpha = (0.165f * topEdgeFade * stageAlpha).coerceIn(0.06f, 0.92f)
                drawCircle(
                    color = Color(red = idleR, green = idleG, blue = idleB, alpha = restAlpha),
                    radius = baseRadiusPx,
                    center = Offset(px, py)
                )
                continue
            }

            val e = particleSystem.energy[i] * zoneActivity
            val relief = particleSystem.grooveRelief[i] * zoneActivity

            // Wide dynamic dot diameter: carved ~0.48dp troughs up to ~5.35dp punchy beat crests
            val valleyCarvePx = if (relief < 0f) (-relief) * 0.78f * density * localBlend else 0f
            val crestSwellingPx = ParticleSystem.fastCrestPower(e) * maxExtraCrestPx * localBlend +
                (relief.coerceAtLeast(0f) * 0.66f * density * localBlend)
            val dotRadius = (baseRadiusPx - valleyCarvePx + crestSwellingPx)
                .coerceIn(minRadiusPx, maxClampedRadiusPx)

            val idleAlpha = 0.165f * topEdgeFade
            val valleyDim = if (relief < 0f) (-relief) * 0.09f else 0f
            val liveAlpha = (0.11f - valleyDim + e * 0.82f) * topEdgeFade
            val finalAlpha = ((idleAlpha * (1f - localBlend) + liveAlpha * localBlend) * stageAlpha)
                .coerceIn(0.05f, 0.95f)

            val hueMix = (particleSystem.colorWave[i] * 0.46f + e * 0.54f).coerceIn(0f, 1f)
            val waveR = deepR + crestDeltaR * hueMix
            val waveG = deepG + crestDeltaG * hueMix
            val waveB = deepB + crestDeltaB * hueMix

            val baseR: Float
            val baseG: Float
            val baseB: Float
            if (relief < -0.08f) {
                val vt = (-relief * localBlend).coerceIn(0f, 0.90f)
                baseR = idleR + valDeltaR * vt
                baseG = idleG + valDeltaG * vt
                baseB = idleB + valDeltaB * vt
            } else {
                baseR = idleR
                baseG = idleG
                baseB = idleB
            }

            val colorMix = (localBlend * (0.18f + e * 0.82f)).coerceIn(0f, 1f)
            val finalR = (baseR + (waveR - baseR) * colorMix).coerceIn(0f, 1f)
            val finalG = (baseG + (waveG - baseG) * colorMix).coerceIn(0f, 1f)
            val finalB = (baseB + (waveB - baseB) * colorMix).coerceIn(0f, 1f)

            drawCircle(
                color = Color(red = finalR, green = finalG, blue = finalB, alpha = finalAlpha),
                radius = dotRadius,
                center = Offset(px, py)
            )
        }
    }
}
