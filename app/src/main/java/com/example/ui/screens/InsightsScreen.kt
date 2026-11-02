package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArtistInsightStat
import com.example.data.model.InsightsSnapshot
import com.example.data.model.ScrobblerConfigAndState
import com.example.data.model.SpotifyProfileState
import com.example.data.model.TrackItem
import com.example.ui.theme.MonoFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Studio Screen — Merged Insights + Studio Telemetry & Taste Profiling.
 *
 * Strictly pure stats, data, and taste profiling — ZERO song suggestions and ZERO playlists.
 * Modeled directly after the Nothing OS Weather architectural bento grid:
 * - Giant circular dot-matrix primary readout + concise arrow telemetry + dark capsule divider bar
 * - 2-column `aspectRatio(1f)` bento grid combining a pure-white `CircleShape` Taste Index card,
 *   dark `#141416` `RoundedCornerShape(26.dp)` wireframe geometry cards (3D dome ellipses,
 *   8x6 dot-matrix density field, radar cone with `#D71921` red arc, compass taste vector with red `N`,
 *   3D wireframe cylinder vault gauge, circadian arc + top artists), and a dark `CircleShape` scrobbler dial.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun InsightsScreen(
    snapshot: InsightsSnapshot,
    scrobblerState: ScrobblerConfigAndState,
    spotifyProfile: SpotifyProfileState,
    isPlaying: Boolean,
    onPlayTopTrack: (TrackItem) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    onConnectLastFm: (username: String, passwordOptional: String) -> Unit,
    onConnectListenBrainz: (username: String, tokenOptional: String) -> Unit,
    onSyncScrobblerNow: () -> Unit,
    onDisconnectScrobbler: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showScrobblerSheet by rememberSaveable { mutableStateOf(false) }

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
    val cardBg = if (isDark) Color(0xFF141416) else Color(0xFFEDEDEF)
    val whiteCircleBg = if (isDark) Color(0xFFF2F2F4) else Color(0xFF121214)
    val whiteCircleFg = if (isDark) Color(0xFF111113) else Color(0xFFF6F6F8)
    val dividerPillColor = if (isDark) Color(0xFF161618) else Color(0xFFE2E2E6)
    val onCardPrimary = MaterialTheme.colorScheme.onBackground
    val onCardSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val nothingRed = Color(0xFFD71921)
    val coolBlue = Color(0xFF6B8AFF)

    // Compute pure Taste Profiling Index (0..99) from artist diversity, streak consistency, and spin depth
    val rawTasteScore = if (snapshot.totalPlays == 0 && snapshot.uniqueArtistsCount == 0) {
        72
    } else {
        (38 + minOf(snapshot.uniqueArtistsCount * 4, 34) +
            minOf(snapshot.streakDays * 3, 15) +
            minOf(snapshot.totalPlays / 4, 12)).coerceIn(18, 99)
    }

    val peakPhaseLabel = when (snapshot.peakHour) {
        in 0..4 -> "Afterhours"
        in 5..10 -> "Morning"
        in 11..16 -> "Daylight"
        in 17..20 -> "Dusk"
        else -> "Nocturnal"
    }

    val tasteCharacter = when {
        snapshot.uniqueArtistsCount >= 12 -> "Eclectic"
        snapshot.uniqueArtistsCount >= 5 -> "Focused"
        snapshot.totalPlays > 0 -> "Deep Cut"
        else -> "Calibrating"
    }

    val dominantDirectionDeg = ((snapshot.peakHour / 24f) * 360f + snapshot.uniqueArtistsCount * 17f) % 360f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_telemetry_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Nothing Weather-Style Hero Readout + Concise High/Low Status + Capsule Divider
        item(key = "studio_hero_header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Giant circular dot-matrix primary readout (Minutes Spun)
                    StudioDotMatrixGlyphText(
                        text = "${snapshot.totalMinutes}M",
                        dotColor = onCardPrimary,
                        dotStepDp = 8.2.dp
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatArchetypeTitleCase(snapshot.collectorArchetype),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp
                            ),
                            color = onCardPrimary.copy(alpha = 0.88f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "↓ ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = coolBlue
                                )
                                Text(
                                    text = "${snapshot.offlinePlays}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onCardPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "↑ ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = nothingRed
                                )
                                Text(
                                    text = "${snapshot.streakDays}d",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onCardPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sleek Nothing OS dark capsule divider bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(11.dp)
                        .clip(CircleShape)
                        .background(dividerPillColor)
                )
            }
        }

        // 2. Row 1: Pure-White Contrast Circle (TASTE INDEX) + Stacked 3D Wireframe Dome (Peak Rhythm)
        item(key = "studio_bento_row_1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Pure White Contrast Circle Card ("TASTE INDEX" + Dot-Matrix Score°)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(whiteCircleBg)
                        .testTag("studio_taste_index_circle"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TASTE INDEX",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.1.sp,
                                fontSize = 11.sp
                            ),
                            color = whiteCircleFg.copy(alpha = 0.78f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        StudioDotMatrixGlyphText(
                            text = "${rawTasteScore}°",
                            dotColor = whiteCircleFg,
                            dotStepDp = 7.2.dp
                        )
                    }
                }

                // Right: Dark Square Card — Peak Rhythm + 3D Concentric Wireframe Dome
                StudioSquareBentoCard(
                    title = "Peak rhythm",
                    subtitle = "%02d:00 · %s".format(snapshot.peakHour, peakPhaseLabel),
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    WireframeDomeGraphic(
                        activeRatio = ((snapshot.hourlyMatrix.maxOrNull() ?: 0.35f)).coerceIn(0.2f, 1f),
                        strokeColor = onCardPrimary
                    )
                }
            }
        }

        // 3. Row 2: 24h Spin Density (Dot-Matrix Grid) + Sonic Breadth (Radar Cone with Red Arc)
        item(key = "studio_bento_row_2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioSquareBentoCard(
                    title = "Spin density",
                    subtitle = "${snapshot.totalPlays} · ${if (snapshot.totalPlays >= 25) "Heavy" else "Active"}",
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    DotDensityFieldGraphic(
                        hourlyMatrix = snapshot.hourlyMatrix,
                        totalPlays = snapshot.totalPlays,
                        dotColor = onCardPrimary
                    )
                }

                StudioSquareBentoCard(
                    title = "Sonic breadth",
                    subtitle = "${snapshot.uniqueArtistsCount} · $tasteCharacter",
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    RadarBreadthConeGraphic(
                        artistCount = snapshot.uniqueArtistsCount,
                        lineColor = onCardPrimary,
                        accentRed = nothingRed
                    )
                }
            }
        }

        // 4. Row 3: Taste Vector (Compass Dial with Red N) + Offline Vault (3D Wireframe Cylinder)
        item(key = "studio_bento_row_3") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val primaryArtistName = snapshot.topArtists.firstOrNull()?.name ?: tasteCharacter
                StudioSquareBentoCard(
                    title = "Taste vector",
                    subtitle = primaryArtistName,
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    onClick = snapshot.topArtists.firstOrNull()?.let { art -> { onOpenArtist(art.name) } },
                    modifier = Modifier.weight(1f)
                ) {
                    CompassVectorDialGraphic(
                        angleDeg = dominantDirectionDeg,
                        ringColor = onCardPrimary,
                        accentRed = nothingRed
                    )
                }

                val offlinePct = if (snapshot.totalPlays > 0) {
                    ((snapshot.offlinePlays * 100) / snapshot.totalPlays).coerceIn(0, 100)
                } else if (snapshot.downloadedTracksCount > 0) {
                    100
                } else {
                    0
                }
                StudioSquareBentoCard(
                    title = "Offline vault",
                    subtitle = "${snapshot.downloadedTracksCount} saved · $offlinePct%",
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    WireframeCylinderGaugeGraphic(
                        fillFraction = (snapshot.downloadedTracksCount / 20f).coerceIn(0.18f, 0.88f),
                        strokeColor = onCardPrimary
                    )
                }
            }
        }

        // 5. Row 4: Dark Scrobbler Dial Circle (1013.0 Barometer Style) + Top Artists & Circadian Arc
        item(key = "studio_bento_row_4") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isScrobblerLinked = scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected
                val dialNumeric = maxOf(snapshot.totalPlays, scrobblerState.remotePlayCount).toString()

                // Left: Dark Circle Dial Card (tappable to manage or sync Last.fm / ListenBrainz)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(cardBg)
                        .clickable {
                            if (isScrobblerLinked) onSyncScrobblerNow() else showScrobblerSheet = true
                        }
                        .padding(18.dp)
                        .testTag("studio_scrobbler_dial_circle")
                ) {
                    // Perimeter calibration dots + top white beacon circle
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension * 0.42f
                        for (tick in 0 until 12) {
                            val rad = (tick * 30.0 - 90.0) * (PI / 180.0)
                            val px = cx + cos(rad).toFloat() * r
                            val py = cy + sin(rad).toFloat() * r
                            if (tick == 0) {
                                drawCircle(
                                    color = if (isScrobblerLinked) nothingRed else onCardPrimary,
                                    radius = 9.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            } else {
                                drawCircle(
                                    color = onCardPrimary.copy(alpha = 0.20f),
                                    radius = 1.8.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StudioDotMatrixGlyphText(
                            text = dialNumeric,
                            dotColor = onCardPrimary,
                            dotStepDp = 3.8.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                scrobblerState.lastFmConnected -> "LAST.FM SYNC"
                                scrobblerState.listenBrainzConnected -> "LBRAINZ SYNC"
                                spotifyProfile.isConnected -> "SPOTIFY SYNC"
                                else -> "TAP TO SYNC"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            ),
                            color = onCardSecondary,
                            modifier = Modifier.clickable { showScrobblerSheet = true }
                        )
                    }
                }

                // Right: Circadian Horizon & Top Artists Profile Card (pure stats, zero songs/playlists)
                val hoursListened = snapshot.totalMinutes / 60L
                val remMins = snapshot.totalMinutes % 60L
                val spanText = if (hoursListened > 0L) {
                    "${hoursListened} hr ${remMins} min · $peakPhaseLabel"
                } else {
                    "${remMins} min · $peakPhaseLabel"
                }
                StudioSquareBentoCard(
                    title = "Top artists",
                    subtitle = spanText,
                    containerColor = cardBg,
                    titleColor = onCardPrimary,
                    subtitleColor = onCardSecondary,
                    modifier = Modifier.weight(1f)
                ) {
                    TopArtistsAndHorizonGraphic(
                        topArtists = snapshot.topArtists.take(3),
                        onOpenArtist = onOpenArtist,
                        primaryColor = onCardPrimary,
                        secondaryColor = onCardSecondary,
                        accentRed = nothingRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioSquareBentoCard(
    title: String,
    subtitle: String,
    containerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    graphicContent: @Composable () -> Unit
) {
    val clickMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(containerColor)
            .then(clickMod)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            graphicContent()
        }
    }
}

/**
 * Stacked 3D concentric dome wireframe ellipses (inspired by Nothing OS UV Index card).
 */
@Composable
private fun WireframeDomeGraphic(
    activeRatio: Float,
    strokeColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val baseW = size.width * 0.64f
        val baseH = baseW * 0.16f
        val bottomY = size.height * 0.86f
        val layers = 6

        // Crisp bright base ellipse ring
        drawOval(
            color = strokeColor.copy(alpha = 0.88f),
            topLeft = Offset(cx - baseW / 2f, bottomY - baseH / 2f),
            size = Size(baseW, baseH),
            style = Stroke(width = 1.4.dp.toPx())
        )

        // Concentric 3D dome arches rising above the base ring
        for (i in 1..layers) {
            val frac = i.toFloat() / layers.toFloat()
            val archHeight = size.height * (0.20f + frac * 0.56f)
            val alpha = (0.14f + (1f - frac) * 0.16f + activeRatio * 0.08f).coerceIn(0.10f, 0.42f)
            drawOval(
                color = strokeColor.copy(alpha = alpha),
                topLeft = Offset(cx - baseW / 2f, bottomY - archHeight),
                size = Size(baseW, archHeight + baseH * 0.45f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Staggered 10x6 dot-matrix density field (bright left columns transitioning to dim right columns,
 * directly modeled on Nothing OS Air Quality dot field).
 */
@Composable
private fun DotDensityFieldGraphic(
    hourlyMatrix: List<Float>,
    totalPlays: Int,
    dotColor: Color
) {
    val activeHoursCount = hourlyMatrix.count { it > 0.05f }
    val litColumns = when {
        totalPlays == 0 -> 4
        else -> (2 + (activeHoursCount * 10) / 16).coerceIn(2, 10)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cols = 10
        val rows = 6
        val padX = 4.dp.toPx()
        val padY = 6.dp.toPx()
        val stepX = (size.width - padX * 2f) / (cols - 1).coerceAtLeast(1)
        val stepY = (size.height - padY * 2f) / (rows - 1).coerceAtLeast(1)
        val r = 2.1.dp.toPx()

        for (c in 0 until cols) {
            val isLit = c < litColumns
            val staggerY = if (c % 2 == 1) stepY * 0.28f else 0f
            for (row in 0 until rows) {
                val py = (padY + row * stepY + staggerY).coerceAtMost(size.height - r)
                val px = padX + c * stepX
                drawCircle(
                    color = dotColor.copy(alpha = if (isLit) 0.92f else 0.16f),
                    radius = r,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * Angular radar cone beam with concentric range arcs and surgical Nothing Red active arc
 * (modeled directly on Nothing OS Visibility card).
 */
@Composable
private fun RadarBreadthConeGraphic(
    artistCount: Int,
    lineColor: Color,
    accentRed: Color
) {
    val activeArcIdx = when {
        artistCount >= 14 -> 3
        artistCount >= 6 -> 2
        else -> 1
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val apex = Offset(size.width * 0.08f, size.height * 0.56f)
        val maxLen = size.width * 0.84f
        val halfSpreadDeg = 17f
        val topRad = (-halfSpreadDeg * PI / 180.0).toFloat()
        val botRad = (halfSpreadDeg * PI / 180.0).toFloat()

        val topEnd = Offset(apex.x + cos(topRad) * maxLen, apex.y + sin(topRad) * maxLen)
        val botEnd = Offset(apex.x + cos(botRad) * maxLen, apex.y + sin(botRad) * maxLen)

        val conePath = Path().apply {
            moveTo(botEnd.x, botEnd.y)
            lineTo(apex.x, apex.y)
            lineTo(topEnd.x, topEnd.y)
        }
        drawPath(
            path = conePath,
            color = lineColor.copy(alpha = 0.88f),
            style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)
        )

        for (arcIdx in 1..3) {
            val radius = maxLen * (0.48f + (arcIdx - 1) * 0.21f)
            drawArc(
                color = if (arcIdx == activeArcIdx) accentRed else lineColor.copy(alpha = 0.72f),
                startAngle = -halfSpreadDeg,
                sweepAngle = halfSpreadDeg * 2f,
                useCenter = false,
                topLeft = Offset(apex.x - radius, apex.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Minimalist circular compass dial with Nothing Red `N` marker and directional needle tick
 * (modeled directly on Nothing OS Wind Speed card).
 */
@Composable
private fun CompassVectorDialGraphic(
    angleDeg: Float,
    ringColor: Color,
    accentRed: Color
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "N",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = accentRed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp, bottom = 4.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(size.width, size.height) * 0.44f

            drawCircle(
                color = ringColor.copy(alpha = 0.88f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.35.dp.toPx())
            )

            // Red North reference tick inside top rim
            drawLine(
                color = accentRed,
                start = Offset(cx, cy - r),
                end = Offset(cx, cy - r + 7.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Active taste vector pointer tick
            val rad = ((angleDeg - 90f) * PI / 180.0).toFloat()
            drawLine(
                color = ringColor,
                start = Offset(cx + cos(rad) * (r * 0.68f), cy + sin(rad) * (r * 0.68f)),
                end = Offset(cx + cos(rad) * r, cy + sin(rad) * r),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 3D wireframe cylinder level gauge (modeled directly on Nothing OS Humidity card).
 */
@Composable
private fun WireframeCylinderGaugeGraphic(
    fillFraction: Float,
    strokeColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cylW = size.width * 0.56f
        val ellipseH = cylW * 0.24f
        val left = (size.width - cylW) / 2f
        val right = left + cylW
        val topCenterY = size.height * 0.18f
        val botCenterY = size.height * 0.82f

        // Vertical cylinder walls
        drawLine(
            color = strokeColor.copy(alpha = 0.86f),
            start = Offset(left, topCenterY),
            end = Offset(left, botCenterY),
            strokeWidth = 1.25.dp.toPx()
        )
        drawLine(
            color = strokeColor.copy(alpha = 0.86f),
            start = Offset(right, topCenterY),
            end = Offset(right, botCenterY),
            strokeWidth = 1.25.dp.toPx()
        )

        // Top & bottom ellipses
        drawOval(
            color = strokeColor.copy(alpha = 0.88f),
            topLeft = Offset(left, topCenterY - ellipseH / 2f),
            size = Size(cylW, ellipseH),
            style = Stroke(width = 1.25.dp.toPx())
        )
        drawOval(
            color = strokeColor.copy(alpha = 0.88f),
            topLeft = Offset(left, botCenterY - ellipseH / 2f),
            size = Size(cylW, ellipseH),
            style = Stroke(width = 1.25.dp.toPx())
        )

        // Internal fluid level ellipse
        val levelY = botCenterY - (botCenterY - topCenterY) * fillFraction.coerceIn(0.15f, 0.85f)
        drawOval(
            color = strokeColor.copy(alpha = 0.45f),
            topLeft = Offset(left, levelY - ellipseH / 2f),
            size = Size(cylW, ellipseH),
            style = Stroke(width = 1.15.dp.toPx())
        )
    }
}

/**
 * Pure Artist Taste Profile + Nothing OS Horizon Arc (zero song suggestions, zero playlists).
 */
@Composable
private fun TopArtistsAndHorizonGraphic(
    topArtists: List<ArtistInsightStat>,
    onOpenArtist: (String) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    accentRed: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (topArtists.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                topArtists.take(3).forEachIndexed { idx, art ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(art.name) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${art.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (idx == 0) primaryColor else primaryColor.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${art.playCount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = if (idx == 0) accentRed else secondaryColor
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Spin music to map artist affinity",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }

        // Clean horizon parabola arc at bottom matching Nothing OS Sunrise & Sunset card
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            val arcW = size.width * 0.68f
            val arcLeft = (size.width - arcW) / 2f
            drawArc(
                color = primaryColor.copy(alpha = 0.86f),
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(arcLeft, 4.dp.toPx()),
                size = Size(arcW, size.height * 1.9f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Custom Circular Dot-Matrix Glyph Renderer for giant Nothing OS numerals (`32°`, `1013.0`, `148M`).
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
    '.' to intArrayOf(0b000, 0b000, 0b000, 0b000, 0b000, 0b010, 0b010),
    '°' to intArrayOf(0b010, 0b101, 0b010, 0b000, 0b000, 0b000, 0b000)
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
            '.', '°' -> 4
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
            val charWidth = if (ch == '.' || ch == '°') 3 else 5
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
    if (raw.isBlank()) return "Analog"
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
