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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stationary-Coordinate Zero-Allocation 2D Dot-Matrix Harmonic Wave Field.
 *
 * - Every lattice dot stays strictly anchored at its exact grid coordinate `(restNx, restNy)` —
 *   zero X/Y positional displacement, zero wobbling out of alignment.
 * - Audio energy (sub-bass radial swells, mid-band spiral harmonics, and treble shimmer) smoothly
 *   drives spring-damped luminance (`energy`) and dot thickness (`radius`) across the fixed lattice.
 * - Dots at or below [staticCutoffYPx] (song title and transport region) remain strictly stitched
 *   in their calm static baseline state with zero animation.
 */
class ParticleSystem(
    val cols: Int = 19,
    val rows: Int = 33,
    private val stiffness: Float = 38f,
    private val damping: Float = 12.6f
) {
    val count: Int = cols * rows

    val restNx = FloatArray(count)
    val restNy = FloatArray(count)
    val dispX = FloatArray(count) // Strictly 0f: dots never displace from grid coordinates
    val dispY = FloatArray(count) // Strictly 0f: dots never displace from grid coordinates
    val energy = FloatArray(count)
    val colorWave = FloatArray(count)
    private val velEnergy = FloatArray(count)

    var liveBlend: Float = 0f
        private set
    private var liveBlendVel: Float = 0f
    private var phaseTime: Float = 0f

    // Normalized center of the circular vinyl platter in the upper-middle stage
    private val centerNx = 0.50f
    private val centerNy = 0.36f

    init {
        var idx = 0
        for (r in 0 until rows) {
            val ny = if (rows > 1) r.toFloat() / (rows - 1).toFloat() else 0.5f
            for (c in 0 until cols) {
                val nx = if (cols > 1) c.toFloat() / (cols - 1).toFloat() else 0.5f
                restNx[idx] = nx
                restNy[idx] = ny
                idx++
            }
        }
    }

    /**
     * Advances serene spring-damped luminance & thickness wave integration by [rawDtSeconds].
     * Dot positions `(restNx, restNy)` never move.
     */
    fun step(
        rawDtSeconds: Float,
        isPlaying: Boolean,
        bass: Float,
        mid: Float,
        treble: Float
    ): Boolean {
        val dt = rawDtSeconds.coerceIn(0.001f, 0.034f)
        val b = bass.coerceIn(0f, 1f)
        val m = mid.coerceIn(0f, 1f)
        val t = treble.coerceIn(0f, 1f)

        if (isPlaying) {
            // Serene, unhurried phase progression gently modulated by musical dynamics
            phaseTime += dt * (1.08f + b * 0.92f + m * 0.55f)
        }

        val targetBlend = if (isPlaying) 1f else 0f
        val blendAcc = (targetBlend - liveBlend) * 28f - 10.2f * liveBlendVel
        liveBlendVel += blendAcc * dt
        liveBlend = (liveBlend + liveBlendVel * dt).coerceIn(0f, 1f)

        var maxResidual = abs(targetBlend - liveBlend) + abs(liveBlendVel) * 0.1f

        for (i in 0 until count) {
            val rx = restNx[i] - centerNx
            val ry = (restNy[i] - centerNy) * 1.42f
            val dist = sqrt(rx * rx + ry * ry).coerceAtLeast(0.001f)
            val theta = atan2(ry, rx)

            var targetE = 0f
            var targetColorShift = 0f

            if (isPlaying) {
                // Smooth concentric acoustic ring + gentle two-lobe vinyl harmonic wave
                val concentric = 0.5f + 0.5f * sin(dist * 11.2f - phaseTime * 2.15f)
                val spiral = 0.5f + 0.5f * cos(theta * 2f + dist * 6.4f - phaseTime * 1.35f)
                val latticePlane = 0.5f + 0.5f * sin(restNx[i] * 5.6f + restNy[i] * 6.8f - phaseTime * 1.05f)

                // Peak serenely in the halo around the circular platter, calm inside center & far edges
                val haloRing = exp(-((dist - 0.35f) * (dist - 0.35f)) / 0.13f)
                val ambientField = (1f - dist * 0.54f).coerceIn(0.16f, 0.95f)

                targetE = (
                    haloRing * (0.20f + b * 0.52f * concentric + m * 0.24f * spiral) +
                        ambientField * (0.06f + t * 0.20f * latticePlane)
                    ).coerceIn(0f, 1f)

                targetColorShift = (0.55f * concentric + 0.45f * spiral).coerceIn(0f, 1f)
            }

            // Spring-damped thickness & luminance energy (zero positional displacement)
            val ae = (targetE - energy[i]) * stiffness - damping * velEnergy[i]
            val nve = velEnergy[i] + ae * dt
            val ne = (energy[i] + nve * dt).coerceIn(0f, 1f)
            velEnergy[i] = nve
            energy[i] = ne
            colorWave[i] = colorWave[i] + (targetColorShift - colorWave[i]) * (dt * 6.5f).coerceIn(0f, 1f)

            val residual = ne + abs(nve) * 0.05f
            if (residual > maxResidual) {
                maxResidual = residual
            }
        }

        if (!isPlaying && maxResidual < 0.006f) {
            for (i in 0 until count) {
                energy[i] = 0f
                velEnergy[i] = 0f
                colorWave[i] = 0f
            }
            liveBlend = 0f
            liveBlendVel = 0f
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
}

@Composable
fun NothingParticleMatrixBackdrop(
    isPlaying: Boolean,
    visible: Boolean,
    spectrum: List<Float>,
    accentColor: Color,
    staticCutoffYPx: Float = Float.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val particleSystem = remember { ParticleSystem(cols = 19, rows = 33) }
    val currentPlaying by rememberUpdatedState(isPlaying)
    val currentSpectrum by rememberUpdatedState(spectrum)

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
                val dtSec = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.034f)
                }
                lastFrameNanos = nowNanos

                val spec = currentSpectrum
                val bass = if (spec.size >= 4) {
                    spec[0] * 0.40f + spec[1] * 0.30f + spec[2] * 0.20f + spec[3] * 0.10f
                } else 0.22f
                val mid = if (spec.size >= 8) {
                    (spec[4] + spec[5] + spec[6] + spec[7]) * 0.25f
                } else 0.20f
                val treble = if (spec.size >= 12) {
                    (spec[8] + spec[9] + spec[10] + spec[11]) * 0.25f
                } else 0.16f

                val activeMotion = particleSystem.step(
                    rawDtSeconds = dtSec,
                    isPlaying = currentPlaying && visible,
                    bass = bass,
                    mid = mid,
                    treble = treble
                )
                frameTick++
                keepStepping = (currentPlaying && visible) || activeMotion
            }
        }
    }

    if (stageAlpha <= 0.005f) return

    val idleDotColor = Color(0xFF8C9099)
    val deepTone = lerp(idleDotColor, accentColor, 0.72f)
    val crestTone = lerp(accentColor, Color.White, 0.48f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val tickRead = frameTick
        if (tickRead < 0) return@Canvas

        val padX = 20f * density
        val padY = 24f * density
        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)

        val baseRadiusPx = 1.32f * density
        val activeExtraRadiusPx = 2.05f * density
        val blend = particleSystem.liveBlend
        // Default fallback cutoff at 68% height before first layout measurement arrives
        val effectiveCutoffY = if (staticCutoffYPx < Float.MAX_VALUE * 0.5f) {
            staticCutoffYPx
        } else {
            size.height * 0.68f
        }
        val featherZonePx = 20f * density

        for (i in 0 until particleSystem.count) {
            val ny = particleSystem.restNy[i]
            // Fixed grid coordinates — dots NEVER translate or drift out of place
            val px = padX + particleSystem.restNx[i] * usableW
            val py = padY + ny * usableH

            // Strictly stitched static dots at and below the song title & transport area
            if (py >= effectiveCutoffY) {
                val lowerAlpha = (0.165f * stageAlpha).coerceIn(0f, 0.45f)
                drawCircle(
                    color = idleDotColor.copy(alpha = lowerAlpha),
                    radius = baseRadiusPx,
                    center = Offset(px, py)
                )
                continue
            }

            // Gentle feather just above the song title so upper wave transitions cleanly into stitched lower grid
            val zoneActivity = ((effectiveCutoffY - py) / featherZonePx).coerceIn(0f, 1f)
            val topEdgeFade = if (ny < 0.07f) (ny / 0.07f).coerceIn(0.32f, 1f) else 1f

            val e = particleSystem.energy[i] * zoneActivity
            val localBlend = blend * zoneActivity

            val dotRadius = baseRadiusPx + (e * activeExtraRadiusPx * localBlend)
            val idleAlpha = 0.165f * topEdgeFade
            val liveAlpha = (0.165f + e * 0.58f) * topEdgeFade
            val finalAlpha = ((idleAlpha * (1f - localBlend) + liveAlpha * localBlend) * stageAlpha)
                .coerceIn(0f, 0.86f)

            val waveHue = lerp(deepTone, crestTone, (particleSystem.colorWave[i] * 0.65f + e * 0.35f).coerceIn(0f, 1f))
            val finalColor = lerp(idleDotColor, waveHue, (localBlend * (0.28f + e * 0.72f)).coerceIn(0f, 1f))

            drawCircle(
                color = finalColor.copy(alpha = finalAlpha),
                radius = dotRadius,
                center = Offset(px, py)
            )
        }
    }
}
