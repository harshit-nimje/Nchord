package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ArtistInsightStat
import com.example.data.model.InsightsSnapshot
import com.example.data.model.ScrobblerConfigAndState
import com.example.data.model.SpotifyProfileState
import com.example.data.model.TrackItem
import com.example.ui.components.NothingSlowMarqueeText
import com.example.ui.theme.MonoFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class StudioCounterMode(val label: String, val unitSuffix: String) {
    MINUTES("MINUTES", "M"),
    SPINS("SPINS", ""),
    ARTISTS("ARTISTS", "")
}

/**
 * Clean-Room Nothing OS Acoustic Atelier — Minimalist Sonic Telemetry & Taste Geometry.
 *
 * Crafted specifically for NChord in Nothing's monochromatic hardware aesthetic:
 * - Obsidian `#101013` / `#151519` modules with razor-thin `0.85.dp` `#25252C` hairlines,
 *   corner registration marks, dot-matrix numerals, and restrained `#D71921` Nothing Red telemetry.
 * - Playback speed controls are intentionally absent from Studio to keep this page focused purely
 *   on acoustic telemetry, listening cadence, taste DNA, and scrobbler synchronization.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun InsightsScreen(
    snapshot: InsightsSnapshot,
    scrobblerState: ScrobblerConfigAndState,
    spotifyProfile: SpotifyProfileState,
    isPlaying: Boolean,
    onOpenEqualizer: () -> Unit = {},
    onPlayTopTrack: (TrackItem) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    onConnectLastFm: (username: String, passwordOptional: String) -> Unit,
    onConnectListenBrainz: (username: String, tokenOptional: String) -> Unit,
    onSyncScrobblerNow: () -> Unit,
    onDisconnectScrobbler: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showScrobblerSheet by rememberSaveable { mutableStateOf(false) }
    var counterMode by rememberSaveable { mutableStateOf(StudioCounterMode.MINUTES) }
    var inspectedHour by rememberSaveable(snapshot.peakHour) {
        mutableIntStateOf(snapshot.peakHour.coerceIn(0, 23))
    }

    if (showScrobblerSheet) {
        ScrobblerConnectionSheet(
            scrobblerState = scrobblerState,
            onDismiss = { showScrobblerSheet = false },
            onConnectLastFm = { user, pass ->
                onConnectLastFm(user, pass)
                showScrobblerSheet = false
            },
            onConnectListenBrainz = { user, token ->
                onConnectListenBrainz(user, token)
                showScrobblerSheet = false
            },
            onDisconnect = {
                onDisconnectScrobbler()
                showScrobblerSheet = false
            }
        )
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF101013) else Color(0xFFF2F2F5)
    val cardElevatedBg = if (isDark) Color(0xFF17171C) else Color(0xFFE7E7EC)
    val hairline = if (isDark) Color(0xFF25252D) else Color(0xFFD5D5DD)
    val highContrastPillBg = if (isDark) Color(0xFFF5F5F7) else Color(0xFF101013)
    val highContrastPillFg = if (isDark) Color(0xFF0C0C0F) else Color(0xFFF7F7F9)
    val onPrimaryText = MaterialTheme.colorScheme.onBackground
    val onMutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val nothingRed = Color(0xFFD71921)

    val cardShape = RoundedCornerShape(26.dp)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_telemetry_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = 196.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. MASTER ACOUSTIC COUNTER + 270° VINYL GROOVE STYLUS GAUGE
        item(key = "studio_master_counter") {
            val counterString = remember(counterMode, snapshot) {
                when (counterMode) {
                    StudioCounterMode.MINUTES -> {
                        if (snapshot.totalMinutes >= 600L) {
                            "${"%.1f".format(snapshot.totalMinutes / 60f)}H"
                        } else {
                            "${snapshot.totalMinutes}M"
                        }
                    }
                    StudioCounterMode.SPINS -> "${snapshot.totalPlays}"
                    StudioCounterMode.ARTISTS -> "${snapshot.uniqueArtistsCount}"
                }
            }

            val gaugeSweepFraction = remember(counterMode, snapshot) {
                when (counterMode) {
                    StudioCounterMode.MINUTES -> (snapshot.totalMinutes / 360f).coerceIn(0.14f, 1f)
                    StudioCounterMode.SPINS -> (snapshot.totalPlays / 120f).coerceIn(0.14f, 1f)
                    StudioCounterMode.ARTISTS -> (snapshot.uniqueArtistsCount / 36f).coerceIn(0.14f, 1f)
                }
            }
            val animatedSweep by animateFloatAsState(
                targetValue = gaugeSweepFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "studio_gauge_sweep"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBg)
                    .border(0.85.dp, hairline, cardShape)
                    .padding(20.dp)
                    .testTag("studio_master_counter_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // Top Instrument Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ledColor by animateColorAsState(
                                targetValue = if (isPlaying) nothingRed else onMutedText.copy(alpha = 0.42f),
                                animationSpec = tween(280),
                                label = "studio_led_color"
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                            Text(
                                text = if (isPlaying) "SIGNAL LOCKED" else "ATELIER TELEMETRY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    letterSpacing = 1.4.sp
                                ),
                                color = if (isPlaying) onPrimaryText else onMutedText
                            )
                        }

                        Text(
                            text = if (snapshot.streakDays > 0) {
                                "${"%02d".format(snapshot.streakDays)}D STREAK"
                            } else {
                                "SESSION 01"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.1.sp
                            ),
                            color = onMutedText
                        )
                    }

                    // Asymmetric Split: Dot-Matrix Readout (Left) + 270° Vinyl Groove Gauge (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioDotMatrixGlyphText(
                                text = counterString.take(6),
                                dotColor = onPrimaryText,
                                dotStepDp = 6.6.dp
                            )

                            Text(
                                text = formatArchetypeTitleCase(snapshot.collectorArchetype),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = onPrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val subReadout = remember(counterMode, snapshot, spotifyProfile.isConnected) {
                                val srcBadge = if (spotifyProfile.isConnected) "SPOTIFY + LOCAL" else "LOCAL VAULT"
                                when (counterMode) {
                                    StudioCounterMode.MINUTES -> "${snapshot.totalPlays} spins · $srcBadge"
                                    StudioCounterMode.SPINS -> "${snapshot.offlinePlays} offline · ${snapshot.totalMinutes}m total"
                                    StudioCounterMode.ARTISTS -> "Peak ${"%02d:00".format(snapshot.peakHour)} · $srcBadge"
                                }
                            }
                            Text(
                                text = subReadout,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    letterSpacing = 0.6.sp
                                ),
                                color = onMutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        StudioVinylStylusGauge(
                            progress = animatedSweep,
                            isPlaying = isPlaying,
                            primaryColor = onPrimaryText,
                            mutedColor = onMutedText,
                            accentRed = nothingRed,
                            centerCode = when (counterMode) {
                                StudioCounterMode.MINUTES -> "MIN"
                                StudioCounterMode.SPINS -> "RPM"
                                StudioCounterMode.ARTISTS -> "CAT"
                            },
                            modifier = Modifier.size(104.dp)
                        )
                    }

                    // Minimalist Segmented Mode Capsule
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(cardElevatedBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StudioCounterMode.entries.forEach { mode ->
                            val selected = counterMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(if (selected) highContrastPillBg else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true),
                                        onClick = {
                                            if (counterMode != mode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                counterMode = mode
                                            }
                                        }
                                    )
                                    .padding(vertical = 8.dp)
                                    .testTag("studio_counter_mode_${mode.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = 1.0.sp
                                    ),
                                    color = if (selected) highContrastPillFg else onMutedText
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 24H MIRRORED ACOUSTIC HORIZON (TOUCH-SCRUBBABLE OSCILLOSCOPE WAVEFORM)
        item(key = "studio_circadian_horizon") {
            val safeHour = inspectedHour.coerceIn(0, 23)
            val rawDensity = snapshot.hourlyMatrix.getOrElse(safeHour) { 0f }
            val hourDensityPct = (rawDensity * 100f).toInt().coerceIn(0, 100)
            val exactHourPlays = snapshot.hourlyPlayCounts.getOrElse(safeHour) { 0 }
            val phaseLabel = remember(safeHour) {
                when (safeHour) {
                    in 0..4 -> "AFTER HOURS"
                    in 5..10 -> "MORNING DRIFT"
                    in 11..16 -> "DAYLIGHT FOCUS"
                    in 17..20 -> "DUSK WARMUP"
                    else -> "NOCTURNAL PEAK"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBg)
                    .border(0.85.dp, hairline, cardShape)
                    .padding(20.dp)
                    .testTag("studio_circadian_card"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "24H ACOUSTIC HORIZON",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.3.sp
                            ),
                            color = onMutedText
                        )
                        Text(
                            text = "${"%02d:00".format(safeHour)} · $phaseLabel",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = onPrimaryText
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (exactHourPlays > 0) "$exactHourPlays SPINS" else "$hourDensityPct% LOAD",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp
                            ),
                            color = if (safeHour == snapshot.peakHour) nothingRed else onPrimaryText
                        )
                        Text(
                            text = "PEAK ${"%02d:00".format(snapshot.peakHour)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = onMutedText.copy(alpha = 0.72f)
                        )
                    }
                }

                // Symmetrical Mirrored Dot-Matrix Soundwave Scrub Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardElevatedBg.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val w = size.width.coerceAtLeast(1)
                                var currentBucket = ((down.position.x / w) * 24f).toInt().coerceIn(0, 23)
                                if (currentBucket != inspectedHour) {
                                    inspectedHour = currentBucket
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val ptr = event.changes.firstOrNull() ?: break
                                    if (ptr.pressed) {
                                        ptr.consume()
                                        val nextBucket = ((ptr.position.x / w) * 24f).toInt().coerceIn(0, 23)
                                        if (nextBucket != currentBucket) {
                                            currentBucket = nextBucket
                                            inspectedHour = nextBucket
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .testTag("studio_circadian_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cols = 24
                        val halfPairs = 4 // 1 center dot + 4 mirrored pairs = 9 vertical dots max
                        val colStep = size.width / cols
                        val midY = size.height * 0.5f
                        val rowSpacing = (size.height * 0.44f) / halfPairs

                        // Subtle center horizon hairline
                        drawLine(
                            color = onMutedText.copy(alpha = 0.14f),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 0.8.dp.toPx()
                        )

                        for (h in 0 until cols) {
                            val cx = h * colStep + colStep * 0.5f
                            val rawVal = snapshot.hourlyMatrix.getOrElse(h) { 0f }
                            // Subtle rhythmic baseline when user has zero plays yet so Studio still looks alive
                            val effectiveVal = if (snapshot.totalPlays == 0) {
                                val synth = 0.16f + 0.42f * sin((h / 23f) * PI.toFloat()).coerceAtLeast(0f)
                                if (h in 20..23) (synth + 0.28f).coerceAtMost(0.92f) else synth
                            } else {
                                rawVal
                            }
                            val activePairs = (effectiveVal * halfPairs).toInt().coerceIn(0, halfPairs)
                            val isSelected = h == safeHour
                            val isPeak = h == snapshot.peakHour

                            if (isSelected) {
                                drawLine(
                                    color = nothingRed.copy(alpha = 0.42f),
                                    start = Offset(cx, 0f),
                                    end = Offset(cx, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            for (pair in -halfPairs..halfPairs) {
                                val dist = kotlin.math.abs(pair)
                                val isLit = dist <= activePairs && (dist == 0 || effectiveVal > 0.05f)
                                val cy = midY + pair * rowSpacing
                                val dotColor = when {
                                    isLit && isSelected -> nothingRed
                                    isLit && isPeak -> nothingRed.copy(alpha = 0.86f)
                                    isLit -> onPrimaryText.copy(alpha = 0.88f - dist * 0.08f)
                                    isSelected -> onPrimaryText.copy(alpha = 0.26f)
                                    else -> onMutedText.copy(alpha = 0.12f)
                                }
                                val r = when {
                                    isLit && isSelected -> 2.75.dp.toPx()
                                    isLit -> 2.25.dp.toPx()
                                    else -> 1.35.dp.toPx()
                                }
                                drawCircle(
                                    color = dotColor,
                                    radius = r,
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("00", "06", "12", "18", "23").forEach { tick ->
                        Text(
                            text = "$tick:00",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 0.6.sp
                            ),
                            color = onMutedText.copy(alpha = 0.68f)
                        )
                    }
                }
            }
        }

        // 3. 5-AXIS SONIC DNA POLAR MATRIX + HARDWARE TELEMETRY STRIP (NO SPEED CONTROLS)
        item(key = "studio_sonic_dna") {
            val axes = remember(snapshot) {
                listOf(
                    "NOCTURNAL" to (snapshot.nightSharePercent / 100f).coerceIn(0.15f, 1f),
                    "LOYALTY" to (snapshot.replayLoyaltyPercent / 100f).coerceIn(0.15f, 1f),
                    "IMMERSION" to (snapshot.immersionScorePercent / 100f).coerceIn(0.15f, 1f),
                    "VAULT" to (snapshot.vaultAutonomyPercent / 100f).coerceIn(0.15f, 1f),
                    "CADENCE" to (snapshot.cadenceConsistencyPercent / 100f).coerceIn(0.15f, 1f)
                )
            }
            val avgMin = snapshot.avgTrackDurationSec / 60
            val avgRemSec = snapshot.avgTrackDurationSec % 60

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBg)
                    .border(0.85.dp, hairline, cardShape)
                    .padding(20.dp)
                    .testTag("studio_sonic_dna_card"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "SONIC DNA GEOMETRY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.3.sp
                            ),
                            color = onMutedText
                        )
                        Text(
                            text = "Taste Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = onPrimaryText
                        )
                    }

                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(cardElevatedBg)
                            .border(0.75.dp, hairline, CircleShape)
                            .clickable(onClick = onOpenEqualizer)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("studio_open_dsp_eq_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Equalizer,
                            contentDescription = "Open System Equalizer",
                            tint = onPrimaryText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "SYSTEM EQ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = onPrimaryText
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SonicDnaPolarRadar(
                        axes = axes,
                        primaryColor = onPrimaryText,
                        mutedColor = onMutedText,
                        accentRed = nothingRed,
                        modifier = Modifier.size(138.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        axes.forEachIndexed { idx, (label, fraction) ->
                            val pct = (fraction * 100f).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (idx == 0) nothingRed else onPrimaryText.copy(alpha = 0.68f))
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = MonoFontFamily,
                                            letterSpacing = 0.9.sp
                                        ),
                                        color = onMutedText
                                    )
                                }
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = onPrimaryText
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = hairline)

                // Clean 3-column hardware telemetry footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioMiniStatCell(
                        label = "AVG CUT",
                        value = "%d:%02d".format(avgMin, avgRemSec),
                        primaryColor = onPrimaryText,
                        mutedColor = onMutedText,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(modifier = Modifier.height(26.dp), thickness = 0.75.dp, color = hairline)
                    StudioMiniStatCell(
                        label = "SAVED CRATE",
                        value = "${snapshot.savedTracksCount + snapshot.downloadedTracksCount}",
                        primaryColor = onPrimaryText,
                        mutedColor = onMutedText,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(modifier = Modifier.height(26.dp), thickness = 0.75.dp, color = hairline)
                    StudioMiniStatCell(
                        label = "OFFLINE VAULT",
                        value = "${snapshot.downloadedTracksCount} LP",
                        primaryColor = onPrimaryText,
                        mutedColor = onMutedText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. HEAVY ROTATION PRESSINGS + #1 CROWN CUT
        item(key = "studio_heavy_rotation_rack") {
            val maxArtistPlays = (snapshot.topArtists.maxOfOrNull { it.playCount } ?: 1).coerceAtLeast(1)
            val totalTopPlays = snapshot.topArtists.sumOf { it.playCount }.coerceAtLeast(1)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBg)
                    .border(0.85.dp, hairline, cardShape)
                    .padding(20.dp)
                    .testTag("studio_top_artists_card"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HEAVY ROTATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            letterSpacing = 1.3.sp
                        ),
                        color = onMutedText
                    )
                    Text(
                        text = "INDEX 01—0${snapshot.topArtists.size.coerceIn(1, 5)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = onMutedText
                    )
                }

                if (snapshot.topArtists.isNotEmpty()) {
                    snapshot.topArtists.take(5).forEachIndexed { idx, artist ->
                        val sharePct = ((artist.playCount.toFloat() / totalTopPlays.toFloat()) * 100f)
                            .toInt()
                            .coerceIn(1, 100)
                        ArtistTelemetryBarRow(
                            rank = idx + 1,
                            artist = artist,
                            sharePct = sharePct,
                            fraction = (artist.playCount.toFloat() / maxArtistPlays.toFloat()).coerceIn(0.12f, 1f),
                            isLead = idx == 0,
                            primaryColor = onPrimaryText,
                            mutedColor = onMutedText,
                            surfaceBg = cardElevatedBg,
                            accentRed = nothingRed,
                            onClick = { onOpenArtist(artist.name) }
                        )
                    }
                } else {
                    Text(
                        text = "Spin records or link Spotify / Last.fm to calibrate your rotation index.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onMutedText
                    )
                }

                snapshot.topTrack?.let { crown ->
                    val context = LocalContext.current
                    val artReq = remember(crown.artworkUrl) {
                        ImageRequest.Builder(context)
                            .data(crown.artworkUrl)
                            .crossfade(140)
                            .size(120)
                            .build()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardElevatedBg)
                            .border(0.75.dp, hairline, RoundedCornerShape(18.dp))
                            .clickable { onPlayTopTrack(crown) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("studio_crown_track_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (crown.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = artReq,
                                    contentDescription = crown.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = nothingRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CROWN CUT · ${snapshot.topTrackPlays} SPINS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = MonoFontFamily,
                                    letterSpacing = 1.0.sp
                                ),
                                color = nothingRed
                            )
                            NothingSlowMarqueeText(
                                text = "${crown.title} — ${crown.artist}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = onPrimaryText,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(highContrastPillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play most spun track",
                                tint = highContrastPillFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. SCROBBLER TELEMETRY BRIDGE (LAST.FM & LISTENBRAINZ)
        item(key = "studio_scrobbler_patchbay") {
            val isLinked = scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected
            val providerTitle = when {
                scrobblerState.lastFmConnected -> "Last.fm · @${scrobblerState.lastFmUsername}"
                scrobblerState.listenBrainzConnected -> "ListenBrainz · @${scrobblerState.listenBrainzUsername}"
                else -> "Last.fm / ListenBrainz Standby"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(cardBg)
                    .border(0.85.dp, hairline, cardShape)
                    .padding(20.dp)
                    .testTag("studio_scrobbler_patchbay_card"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isLinked) nothingRed else onMutedText.copy(alpha = 0.38f))
                        )
                        Text(
                            text = "SCROBBLER BRIDGE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.3.sp
                            ),
                            color = onMutedText
                        )
                    }

                    Text(
                        text = if (scrobblerState.pendingOfflineScrobbles > 0) {
                            "${scrobblerState.pendingOfflineScrobbles} QUEUED"
                        } else if (isLinked) {
                            "LIVE 2-WAY"
                        } else {
                            "OFFLINE"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = if (isLinked) onPrimaryText else onMutedText
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = providerTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = onPrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLinked) {
                            "${scrobblerState.remotePlayCount} remote scrobbles · Offline queue enabled"
                        } else {
                            "Connect Last.fm or ListenBrainz for automatic cross-device scrobbling"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onMutedText
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(highContrastPillBg)
                            .clickable { showScrobblerSheet = true }
                            .padding(vertical = 11.dp)
                            .testTag("studio_scrobbler_dial_circle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLinked) "CONFIGURE SCROBBLER" else "CONNECT SCROBBLER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = highContrastPillFg
                        )
                    }

                    if (isLinked) {
                        Row(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(cardElevatedBg)
                                .border(0.85.dp, hairline, CircleShape)
                                .clickable { onSyncScrobblerNow() }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                                .testTag("studio_scrobbler_sync_now"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Sync scrobbles now",
                                tint = onPrimaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (scrobblerState.isSyncing) "SYNCING…" else "SYNC",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = onPrimaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioVinylStylusGauge(
    progress: Float,
    isPlaying: Boolean,
    primaryColor: Color,
    mutedColor: Color,
    accentRed: Color,
    centerCode: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val outerR = size.minDimension * 0.46f
            val innerGrooveR = outerR * 0.72f
            val totalTicks = 28
            val startDeg = 135f
            val sweepDeg = 270f
            val litTicks = (progress * totalTicks).toInt().coerceIn(2, totalTicks)

            // Outer 270-degree dot-matrix calibration ring
            for (i in 0 until totalTicks) {
                val deg = startDeg + (i.toFloat() / (totalTicks - 1).toFloat()) * sweepDeg
                val rad = (deg * PI / 180.0).toFloat()
                val px = center.x + cos(rad) * outerR
                val py = center.y + sin(rad) * outerR
                val isLit = i < litTicks
                val isTip = i == litTicks - 1
                drawCircle(
                    color = when {
                        isTip -> accentRed
                        isLit -> primaryColor.copy(alpha = 0.88f)
                        else -> mutedColor.copy(alpha = 0.16f)
                    },
                    radius = if (isTip) 2.8.dp.toPx() else 1.75.dp.toPx(),
                    center = Offset(px, py)
                )
            }

            // Inner micro-groove ring
            drawCircle(
                color = mutedColor.copy(alpha = 0.16f),
                radius = innerGrooveR,
                center = center,
                style = Stroke(width = 0.9.dp.toPx())
            )

            // Precision stylus needle pointing to active tick
            val needleDeg = startDeg + progress.coerceIn(0f, 1f) * sweepDeg
            val needleRad = (needleDeg * PI / 180.0).toFloat()
            val needleStart = Offset(
                center.x + cos(needleRad) * (innerGrooveR * 0.48f),
                center.y + sin(needleRad) * (innerGrooveR * 0.48f)
            )
            val needleEnd = Offset(
                center.x + cos(needleRad) * (outerR * 0.88f),
                center.y + sin(needleRad) * (outerR * 0.88f)
            )
            drawLine(
                color = if (isPlaying) accentRed else primaryColor.copy(alpha = 0.72f),
                start = needleStart,
                end = needleEnd,
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Text(
            text = centerCode,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            ),
            color = mutedColor
        )
    }
}

@Composable
private fun SonicDnaPolarRadar(
    axes: List<Pair<String, Float>>,
    primaryColor: Color,
    mutedColor: Color,
    accentRed: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = axes.size
        if (count < 3) return@Canvas
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxR = size.minDimension * 0.44f

        // 3 concentric pentagonal calibration rings
        for (ring in 1..3) {
            val r = maxR * (ring / 3f)
            val ringPath = Path()
            for (i in 0 until count) {
                val angle = (-PI / 2.0 + (2.0 * PI * i) / count).toFloat()
                val px = center.x + cos(angle) * r
                val py = center.y + sin(angle) * r
                if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
            }
            ringPath.close()
            drawPath(
                path = ringPath,
                color = mutedColor.copy(alpha = if (ring == 3) 0.24f else 0.12f),
                style = Stroke(width = 0.8.dp.toPx())
            )
        }

        // Axis spokes
        for (i in 0 until count) {
            val angle = (-PI / 2.0 + (2.0 * PI * i) / count).toFloat()
            drawLine(
                color = mutedColor.copy(alpha = 0.16f),
                start = center,
                end = Offset(center.x + cos(angle) * maxR, center.y + sin(angle) * maxR),
                strokeWidth = 0.8.dp.toPx()
            )
        }

        // User Taste DNA Polygon
        val dataPath = Path()
        val vertices = ArrayList<Offset>(count)
        axes.forEachIndexed { i, (_, value) ->
            val clamped = value.coerceIn(0.15f, 1f)
            val angle = (-PI / 2.0 + (2.0 * PI * i) / count).toFloat()
            val pt = Offset(
                center.x + cos(angle) * (maxR * clamped),
                center.y + sin(angle) * (maxR * clamped)
            )
            vertices.add(pt)
            if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
        }
        dataPath.close()

        drawPath(
            path = dataPath,
            color = accentRed.copy(alpha = 0.16f)
        )
        drawPath(
            path = dataPath,
            color = primaryColor.copy(alpha = 0.86f),
            style = Stroke(width = 1.4.dp.toPx())
        )

        vertices.forEachIndexed { idx, pt ->
            drawCircle(
                color = if (idx == 0) accentRed else primaryColor,
                radius = if (idx == 0) 3.2.dp.toPx() else 2.4.dp.toPx(),
                center = pt
            )
        }
    }
}

@Composable
private fun StudioMiniStatCell(
    label: String,
    value: String,
    primaryColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                letterSpacing = 0.9.sp
            ),
            color = mutedColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = primaryColor
        )
    }
}

@Composable
private fun ArtistTelemetryBarRow(
    rank: Int,
    artist: ArtistInsightStat,
    sharePct: Int,
    fraction: Float,
    isLead: Boolean,
    primaryColor: Color,
    mutedColor: Color,
    surfaceBg: Color,
    accentRed: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "%02d".format(rank),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isLead) accentRed else mutedColor
                )

                // Micro-vinyl artist disc with center spindle hole
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(surfaceBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (artist.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF101013))
                    )
                }

                NothingSlowMarqueeText(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isLead) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = primaryColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = mutedColor.copy(alpha = 0.55f),
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${artist.playCount} SPINS · $sharePct%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                color = if (isLead) primaryColor else mutedColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Proportional 24-dot Nothing LED bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            val totalDots = 24
            val litDots = (fraction * totalDots).toInt().coerceIn(1, totalDots)
            val step = size.width / totalDots
            for (i in 0 until totalDots) {
                val lit = i < litDots
                drawCircle(
                    color = when {
                        lit && isLead -> accentRed
                        lit -> primaryColor.copy(alpha = 0.78f)
                        else -> mutedColor.copy(alpha = 0.14f)
                    },
                    radius = 2.0.dp.toPx(),
                    center = Offset(i * step + step / 2f, size.height / 2f)
                )
            }
        }
    }
}

/**
 * Clean-room Circular Dot-Matrix Glyph Renderer for Nothing OS Studio numerals (`148M`, `42`, `9`).
 */
private val STUDIO_DOT_GLYPHS: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
    '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
    '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111),
    '3' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b00001, 0b10001, 0b01110),
    '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
    '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
    '6' to intArrayOf(0b01110, 0b10000, 0b11110, 0b10001, 0b10001, 0b10001, 0b01110),
    '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
    '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
    '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110),
    'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10001, 0b10001, 0b10001, 0b10001),
    'H' to intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
    '.' to intArrayOf(0b000, 0b000, 0b000, 0b000, 0b000, 0b010, 0b010)
)

@Composable
private fun StudioDotMatrixGlyphText(
    text: String,
    dotColor: Color,
    dotStepDp: Dp = 7.dp,
    modifier: Modifier = Modifier
) {
    val upper = text.uppercase()
    val totalCols = upper.sumOf { ch ->
        when (ch) {
            '.' -> 4
            else -> 6
        }
    }.coerceAtLeast(1) - 1

    Canvas(
        modifier = modifier
            .width(dotStepDp * totalCols)
            .height(dotStepDp * 7)
    ) {
        val step = size.height / 7f
        val radius = step * 0.37f
        var cursorCol = 0

        for (ch in upper) {
            val rowsMask = STUDIO_DOT_GLYPHS[ch]
            val charWidth = if (ch == '.') 3 else 5
            if (rowsMask != null) {
                for (r in 0 until 7) {
                    val mask = rowsMask[r]
                    for (c in 0 until charWidth) {
                        val bit = (mask shr (charWidth - 1 - c)) and 1
                        if (bit == 1) {
                            drawCircle(
                                color = dotColor,
                                radius = radius,
                                center = Offset((cursorCol + c + 0.5f) * step, (r + 0.5f) * step)
                            )
                        }
                    }
                }
            }
            cursorCol += charWidth + 1
        }
    }
}

private fun formatArchetypeTitleCase(raw: String): String {
    if (raw.isBlank()) return "Nocturnal Analog"
    return raw.lowercase().split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblerConnectionSheet(
    scrobblerState: ScrobblerConfigAndState,
    onDismiss: () -> Unit,
    onConnectLastFm: (username: String, passwordOptional: String) -> Unit,
    onConnectListenBrainz: (username: String, tokenOptional: String) -> Unit,
    onDisconnect: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProvider by rememberSaveable {
        mutableStateOf(
            if (scrobblerState.listenBrainzConnected) "LISTENBRAINZ" else "LASTFM"
        )
    }
    var lastFmUser by rememberSaveable { mutableStateOf(scrobblerState.lastFmUsername) }
    var lastFmPassword by rememberSaveable { mutableStateOf("") }
    var lbUser by rememberSaveable { mutableStateOf(scrobblerState.listenBrainzUsername) }
    var lbToken by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121317),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("scrobbler_connection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Connect Scrobbler",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("LASTFM" to "Last.fm", "LISTENBRAINZ" to "ListenBrainz").forEach { (id, label) ->
                    val active = selectedProvider == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedProvider = id }
                            .padding(vertical = 10.dp)
                            .testTag("scrobbler_provider_$id"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) Color.Black else Color.White
                        )
                    }
                }
            }

            if (selectedProvider == "LASTFM") {
                Text(
                    text = "Enter your Last.fm username to fetch scrobbles & top artists. Optional: enter your Last.fm password when LASTFM_API_KEY & LASTFM_SHARED_SECRET are set in AI Studio Secrets for 2-way mobile scrobbling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )

                OutlinedTextField(
                    value = lastFmUser,
                    onValueChange = { lastFmUser = it },
                    label = { Text("Last.fm Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lastfm_username_input")
                )

                OutlinedTextField(
                    value = lastFmPassword,
                    onValueChange = { lastFmPassword = it },
                    label = { Text("Last.fm Password (optional for write sync)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lastfm_password_input")
                )

                Button(
                    onClick = { onConnectLastFm(lastFmUser, lastFmPassword) },
                    enabled = lastFmUser.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD71921),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_lastfm_submit")
                ) {
                    Text("Connect Last.fm")
                }
            } else {
                Text(
                    text = "ListenBrainz works immediately with zero developer keys. Paste your ListenBrainz User Token from listenbrainz.org/settings for live + offline 2-way scrobbling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )

                OutlinedTextField(
                    value = lbUser,
                    onValueChange = { lbUser = it },
                    label = { Text("ListenBrainz Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listenbrainz_username_input")
                )

                OutlinedTextField(
                    value = lbToken,
                    onValueChange = { lbToken = it },
                    label = { Text("User Token (optional for write scrobbling)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listenbrainz_token_input")
                )

                Button(
                    onClick = { onConnectListenBrainz(lbUser, lbToken) },
                    enabled = lbUser.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_listenbrainz_submit")
                ) {
                    Text("Connect ListenBrainz")
                }
            }

            if (scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected) {
                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Disconnect Scrobbler", color = Color(0xFFD71921))
                }
            }
        }
    }
}
