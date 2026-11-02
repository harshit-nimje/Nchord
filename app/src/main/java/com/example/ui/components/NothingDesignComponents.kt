package com.example.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AudioQualityTier
import com.example.data.model.TrackItem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Shared live backdrop state carrying an opaque full-screen [blurredLayer] (`TileMode.Clamp`
 * 44.dp Gaussian hardware `BlurEffect`) so [FrostedGlassCapsule] renders 100% opaque heavily-blurred
 * underlying content with zero crisp bleed-through and live scroll/frame invalidation.
 */
@Immutable
data class GlassBackdropState(
    val blurredLayer: GraphicsLayer,
    val rootOffset: Offset,
    val invalidationTick: Int = 0
)

val LocalGlassBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/**
 * Guards against RenderNode display-list cycles: when `true`, the current composition subtree
 * is rendered *inside* [recordGlassBackdrop]'s recorded layer, so [FrostedGlassCapsule] must not
 * draw that ancestor [GraphicsLayer] back into itself.
 */
val LocalInsideBackdropRecording = compositionLocalOf { false }

@Stable
class GlassBackdropRig(
    val sharpLayer: GraphicsLayer,
    val blurredLayer: GraphicsLayer
) {
    var rootOffset by mutableStateOf(Offset.Zero)
    var scrollTick by mutableIntStateOf(0)
    var isCurrentlyRecording: Boolean = false

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (consumed.x != 0f || consumed.y != 0f) {
                scrollTick++
            }
            return Offset.Zero
        }
    }

    fun toState(extraTick: Int = 0): GlassBackdropState = GlassBackdropState(
        blurredLayer = blurredLayer,
        rootOffset = rootOffset,
        invalidationTick = scrollTick + extraTick
    )
}

@Composable
fun rememberGlassBackdropRig(blurRadius: Dp = 44.dp): GlassBackdropRig {
    val sharpLayer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }

    val rig = remember(sharpLayer, blurredLayer) {
        GlassBackdropRig(sharpLayer = sharpLayer, blurredLayer = blurredLayer)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blurredLayer.renderEffect = remember(blurPx) {
            BlurEffect(
                radiusX = blurPx,
                radiusY = blurPx,
                edgeTreatment = TileMode.Clamp
            )
        }
    }
    return rig
}

/**
 * Attaches live scroll invalidation + dual sharp/heavy-blur GraphicsLayer recording to any backdrop container.
 */
fun Modifier.recordGlassBackdrop(
    rig: GlassBackdropRig,
    backgroundColor: Color
): Modifier = this
    .nestedScroll(rig.nestedScrollConnection)
    .onGloballyPositioned { coords ->
        rig.rootOffset = coords.positionInRoot()
    }
    .drawWithContent {
        if (size.width <= 0f || size.height <= 0f) {
            drawContent()
            return@drawWithContent
        }
        rig.isCurrentlyRecording = true
        try {
            rig.sharpLayer.record {
                drawRect(color = backgroundColor)
                this@drawWithContent.drawContent()
            }
            rig.blurredLayer.record {
                drawRect(color = backgroundColor)
                drawLayer(rig.sharpLayer)
            }
        } finally {
            rig.isCurrentlyRecording = false
        }
        drawLayer(rig.sharpLayer)
    }

/**
 * Refined 96x96 monochrome pure-white optical silica micrograin texture tiled on the GPU.
 * Kept intentionally delicate so heavy blur is the hero and grain adds subtle tactile depth.
 */
private val OpticalMicrograinBrush: ShaderBrush by lazy {
    val size = 96
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val u = h and 0xFF
            val alpha = when {
                u > 224 -> 210
                u > 168 -> 110
                u > 110 -> 45
                else -> 0
            }
            pixels[y * size + x] = (alpha shl 24) or 0x00FFFFFF
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

@Suppress("UNUSED_PARAMETER")
fun Modifier.nothingSubtleGrainBackground(
    dotColor: Color,
    spacingDp: Dp = 28.dp,
    dotRadiusDp: Dp = 0.75.dp
): Modifier = this

/**
 * Native Pure-White Translucent Frosted Glass with Active Heavy Backdrop Blur:
 * - Draws the full-screen `TileMode.Clamp` 44dp [GlassBackdropState.blurredLayer] at 100% opacity
 *   clipped to [shape] for true floating overlays (Dock, Mini-Player, Player controls), guaranteeing
 *   zero unblurred bleed-through at capsule edges and zero RenderNode display-list cycles!
 * - Subscribes to `backdrop.invalidationTick` inside `drawBehind` so scrolling or background waves
 *   update the refraction at 120fps with zero Compose recomposition.
 * - Layers flat pure-white translucency + delicate silica micrograin + crisp pure-white border.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun FrostedGlassCapsule(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val insideRecording = LocalInsideBackdropRecording.current
    val backdrop = if (insideRecording) null else LocalGlassBackdrop.current
    val canRefractBackdrop = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var capsuleRootOffset by remember { mutableStateOf(Offset.Zero) }

    val pureWhiteTint = if (isDark) {
        if (canRefractBackdrop) {
            Color.White.copy(alpha = 0.16f)
        } else {
            // Pure-white frosted translucency over solid backdrop when inside a recorded stage
            Color.White.copy(alpha = 0.08f)
        }
    } else {
        if (canRefractBackdrop) {
            Color.White.copy(alpha = 0.78f)
        } else {
            Color.White.copy(alpha = 0.90f)
        }
    }
    val grainAlpha = if (isDark) 0.036f else 0.028f
    val rimColor = if (isDark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.11f)
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                capsuleRootOffset = coords.positionInRoot()
            }
            .clip(shape)
    ) {
        // Layer 1: Active 100% opaque 44dp hardware-blurred backdrop refraction (only outside recorded subtree)
        if (canRefractBackdrop && backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        @Suppress("UNUSED_VARIABLE")
                        val liveTick = backdrop.invalidationTick
                        val dx = backdrop.rootOffset.x - capsuleRootOffset.x
                        val dy = backdrop.rootOffset.y - capsuleRootOffset.y
                        translate(left = dx, top = dy) {
                            drawLayer(backdrop.blurredLayer)
                        }
                    }
            )
        }

        // Layer 2: Pure flat white native translucency + delicate silica micrograin + crisp rim
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    onDrawBehind {
                        drawRect(color = pureWhiteTint)
                        drawRect(brush = OpticalMicrograinBrush, alpha = grainAlpha)
                    }
                }
                .border(width = 0.85.dp, color = rimColor, shape = shape)
        )

        // Layer 3: Foreground interactive content
        content()
    }
}

/**
 * Nothing OS Top Header: Circular Dot-Matrix bold header ("Nchord", "Search", "Library", "Studio")
 * paired with a clean status/audio-tier action on the right.
 */
/**
 * Nothing OS Top Header:
 * - Top-left Account button positioned BEFORE "Nchord", pushing the header title rightward
 * - Right-hand live audio quality / studio pill
 */
@Composable
fun NothingCleanHeader(
    title: String,
    isPlaying: Boolean,
    isSpotifyLinked: Boolean,
    isYtmEnabled: Boolean,
    audioQualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
    spectrum: List<Float>,
    glyphReactive: Boolean,
    onStatusClick: () -> Unit,
    avatarUrl: String? = null,
    isScrobblerLinked: Boolean = false,
    onAccountClick: () -> Unit = onStatusClick,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasAccountLinked = isSpotifyLinked || isScrobblerLinked

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    .border(
                        width = 0.85.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onAccountClick
                    )
                    .testTag("account_button"),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    val req = remember(avatarUrl) {
                        ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(140)
                            .size(120)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = stringResource(R.string.header_account),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = stringResource(R.string.header_account),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (hasAccountLinked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onStatusClick
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("header_engine_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPlaying) {
                DotMatrixEqualizer(
                    spectrum = spectrum.take(4),
                    isActive = true,
                    glyphEnabled = glyphReactive,
                    modifier = Modifier
                        .width(18.dp)
                        .height(12.dp)
                )
            }
            Text(
                text = when {
                    isSpotifyLinked -> "Spotify • ${audioQualityTier.shortBadge}"
                    isYtmEnabled -> audioQualityTier.shortBadge
                    else -> "Account"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Account and settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Nothing OS Segmented Progress / Carousel Indicator Bars.
 */
@Composable
fun NothingSegmentedIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pageCount) {
            val active = i == currentPage
            val barColor by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f)
                },
                animationSpec = tween(220),
                label = "seg_bar_$i"
            )
            Box(
                modifier = Modifier
                    .weight(if (active) 1.35f else 1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

/**
 * Floating Feedback Toast Pill using pure-white native frosted glass.
 */
@Composable
fun NothingFeedbackPill(
    message: String,
    modifier: Modifier = Modifier
) {
    FrostedGlassCapsule(
        shape = CircleShape,
        modifier = modifier.testTag("nothing_feedback_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Authentic Circular Dot-Matrix Timecode Renderer.
 */
private val NDOT_DIGITS: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111),
    '1' to intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111),
    '2' to intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111),
    '3' to intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111),
    '4' to intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001),
    '5' to intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111),
    '6' to intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111),
    '7' to intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010),
    '8' to intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111),
    '9' to intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111),
    ':' to intArrayOf(0b000, 0b010, 0b000, 0b010, 0b000)
)

@Composable
fun NdotCircularTimecode(
    text: String,
    dotColor: Color,
    dotSizeDp: Dp = 2.6.dp,
    modifier: Modifier = Modifier
) {
    val charCols = text.sumOf { if (it == ':') 2 else 4 } - 1
    val heightDp = dotSizeDp * 6.5f
    val widthDp = dotSizeDp * (charCols.coerceAtLeast(1) * 1.35f)

    Canvas(
        modifier = modifier
            .width(widthDp)
            .height(heightDp)
    ) {
        val step = size.height / 5f
        val radius = step * 0.34f
        var cursorCol = 0f

        for (ch in text) {
            val glyph = NDOT_DIGITS[ch]
            val cols = if (ch == ':') 1 else 3
            if (glyph != null) {
                for (row in 0 until 5) {
                    val rowMask = glyph[row]
                    if (ch == ':') {
                        if ((rowMask and 0b010) != 0) {
                            drawCircle(
                                color = dotColor,
                                radius = radius,
                                center = Offset((cursorCol + 0.5f) * step, (row + 0.5f) * step)
                            )
                        }
                    } else {
                        for (col in 0 until 3) {
                            val bit = (rowMask shr (2 - col)) and 1
                            if (bit == 1) {
                                drawCircle(
                                    color = dotColor,
                                    radius = radius,
                                    center = Offset((cursorCol + col + 0.5f) * step, (row + 0.5f) * step)
                                )
                            }
                        }
                    }
                }
            }
            cursorCol += cols + 1f
        }
    }
}

/**
 * Monochrome Glyph Dot-Matrix Equalizer.
 */
@Composable
fun DotMatrixEqualizer(
    spectrum: List<Float>,
    isActive: Boolean,
    glyphEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val activeDotColor = MaterialTheme.colorScheme.onSurface
    val dimDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Canvas(modifier = modifier) {
        val columns = spectrum.size.coerceAtLeast(1)
        val rows = 5
        val colWidth = size.width / columns
        val rowHeight = size.height / rows
        val dotRadius = minOf(colWidth, rowHeight) * 0.28f

        for (c in 0 until columns) {
            val amplitude = if (isActive && glyphEnabled) spectrum[c] else 0.16f
            val litRows = (amplitude * rows).toInt().coerceIn(1, rows)

            for (r in 0 until rows) {
                val invertedRow = rows - 1 - r
                val isLit = invertedRow < litRows
                drawCircle(
                    color = if (isLit) activeDotColor else dimDotColor,
                    radius = dotRadius,
                    center = Offset(
                        x = c * colWidth + colWidth / 2f,
                        y = r * rowHeight + rowHeight / 2f
                    )
                )
            }
        }
    }
}

/**
 * Sleek Minimal Cassette-Reel Circular Album Turntable:
 * - Precision metallic hairline outer rim (`1.5.dp`) + `4.dp` recessed obsidian lip + inner hairline
 *   around the rotating circular artwork so it feels like a tactile cassette reel.
 * - Minimal precision center cassette spindle hub (`28.dp`) with 3 drive teeth that rotate with the reel.
 * - Accumulates rotation smoothly at 120fps and holds its exact angle gracefully when paused.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun GlyphVinylTurntable(
    artworkUrl: String,
    isPlaying: Boolean,
    glyphReactive: Boolean,
    spectrum: List<Float>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var accumulatedAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastFrameMs = withFrameMillis { it }
            while (true) {
                val nowMs = withFrameMillis { it }
                val deltaMs = (nowMs - lastFrameMs).coerceIn(0L, 64L)
                lastFrameMs = nowMs
                accumulatedAngle = (accumulatedAngle + deltaMs * (360f / 22_000f)) % 360f
            }
        }
    }

    val platterScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.945f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "platter_scale"
    )

    val imageRequest = remember(artworkUrl) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .crossfade(240)
            .size(720)
            .build()
    }

    // Stationary outer sleek minimal cassette bezel + rotating inner reel & drive spindle
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = platterScale
                scaleY = platterScale
            }
            .clip(CircleShape)
            .background(Color(0xFF090A0D))
            .border(
                width = 1.dp,
                color = Color(0xFF262830),
                shape = CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = accumulatedAngle
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 0.5.dp,
                    color = Color(0xFF1E2028),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = stringResource(R.string.cd_album_art),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Minimal Cassette-Reel Center Spindle Hub
            Canvas(modifier = Modifier.size(30.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerR = size.minDimension / 2f
                val innerBoreR = outerR * 0.36f

                drawCircle(
                    color = Color(0xFF0E0F13).copy(alpha = 0.92f),
                    radius = outerR,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.36f),
                    radius = outerR - 0.6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.1.dp.toPx())
                )

                // 3 precision cassette drive splines at 120° intervals
                for (tooth in 0 until 3) {
                    val rad = (tooth * 120.0 - 90.0) * (PI / 180.0)
                    val p1 = Offset(
                        x = center.x + cos(rad).toFloat() * (innerBoreR + 0.5.dp.toPx()),
                        y = center.y + sin(rad).toFloat() * (innerBoreR + 0.5.dp.toPx())
                    )
                    val p2 = Offset(
                        x = center.x + cos(rad).toFloat() * (outerR * 0.68f),
                        y = center.y + sin(rad).toFloat() * (outerR * 0.68f)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.6.dp.toPx()
                    )
                }

                drawCircle(
                    color = Color(0xFF030305),
                    radius = innerBoreR,
                    center = center
                )
            }
        }
    }
}

/**
 * Dramatic Nothing OS Volume-Bar Mechanic Dotted Playback Scrubber:
 * - At rest: `6.dp` sleek linear white pill + crisp circular timeline dots ahead of it.
 * - On hold / drag: Dramatically thickens to `38.dp` (`6.3x` expansion!) with tactile spring physics,
 *   live timestamp preview, and subtle detent haptics — matching the Nothing OS volume slider pill.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun NothingDotScrubber(
    progressFraction: Float,
    onSeekFraction: (Float) -> Unit,
    trackSeed: Int = 42,
    onScrubPreviewFraction: ((Float?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val latestOnSeek by rememberUpdatedState(onSeekFraction)
    val latestOnPreview by rememberUpdatedState(onScrubPreviewFraction)

    var isInteracting by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction.coerceIn(0f, 1f)) }

    val effectiveFraction = if (isInteracting) {
        dragFraction
    } else {
        progressFraction.coerceIn(0f, 1f)
    }

    // Dramatic 6.dp -> 38.dp pill expansion when held or scrubbed
    val pillThickness by animateDpAsState(
        targetValue = if (isInteracting) 38.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrubber_pill_thickness"
    )

    val pillColor = MaterialTheme.colorScheme.onBackground
    val dotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.26f)
    val activeTrackBedColor = MaterialTheme.colorScheme.onBackground.copy(
        alpha = if (isInteracting) 0.10f else 0f
    )
    val animatedBedColor by animateColorAsState(
        targetValue = activeTrackBedColor,
        animationSpec = tween(180),
        label = "scrubber_bed_tint"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    var currentFrac = (down.position.x / widthPx).coerceIn(0f, 1f)
                    var lastHapticFrac = currentFrac

                    isInteracting = true
                    dragFraction = currentFrac
                    latestOnPreview?.invoke(currentFrac)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    do {
                        val event = awaitPointerEvent()
                        val activeChange = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (activeChange != null && activeChange.pressed) {
                            val nextFrac = (activeChange.position.x / widthPx).coerceIn(0f, 1f)
                            if (abs(nextFrac - currentFrac) > 0.001f) {
                                activeChange.consume()
                                currentFrac = nextFrac
                                dragFraction = currentFrac
                                latestOnPreview?.invoke(currentFrac)
                                if (abs(currentFrac - lastHapticFrac) >= 0.04f) {
                                    lastHapticFrac = currentFrac
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    isInteracting = false
                    latestOnPreview?.invoke(null)
                    latestOnSeek(currentFrac)
                }
            }
            .testTag("player_dot_scrubber")
    ) {
        val centerY = size.height / 2f
        val pillHeightPx = pillThickness.toPx().coerceAtMost(size.height)
        val pillRadiusPx = pillHeightPx / 2f

        // Subtle expanded capsule bed while scrubbing so full track width feels grounded
        if (animatedBedColor.alpha > 0.01f) {
            drawRoundRect(
                color = animatedBedColor,
                topLeft = Offset(0f, centerY - pillRadiusPx),
                size = Size(width = size.width, height = pillHeightPx),
                cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx)
            )
        }

        // Dotted timeline ahead of the live pill
        val dotRadiusPx = 2.1.dp.toPx()
        val dotSpacingPx = 10.dp.toPx()
        val startDotX = pillRadiusPx
        val endDotX = (size.width - dotRadiusPx * 1.5f).coerceAtLeast(startDotX)

        val availableWidth = (size.width - pillHeightPx).coerceAtLeast(1f)
        val targetPillRight = (pillHeightPx + effectiveFraction * availableWidth)
            .coerceIn(pillHeightPx, size.width.coerceAtLeast(pillHeightPx))

        var dotX = startDotX
        val clearancePx = 6.dp.toPx()
        while (dotX <= endDotX) {
            if (dotX > targetPillRight + clearancePx) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadiusPx,
                    center = Offset(dotX, centerY)
                )
            }
            dotX += dotSpacingPx
        }

        // Solid crisp linear rounded pill filling live playback
        drawRoundRect(
            color = pillColor,
            topLeft = Offset(0f, centerY - pillRadiusPx),
            size = Size(width = targetPillRight, height = pillHeightPx),
            cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx)
        )
    }
}

/**
 * 2x2 Mosaic Cover for User & Synced Playlists.
 */
@Composable
fun NothingPlaylistMosaicCover(
    artworkUrl: String,
    mosaicUrls: List<String>,
    sizeDp: Dp = 56.dp,
    cornerRadiusDp: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadiusDp)

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (mosaicUrls.size >= 4) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0 until 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        for (col in 0 until 2) {
                            val url = mosaicUrls[row * 2 + col]
                            val req = remember(url) {
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(120)
                                    .size(180)
                                    .build()
                            }
                            AsyncImage(
                                model = req,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        } else if (artworkUrl.isNotBlank() || mosaicUrls.isNotEmpty()) {
            val single = artworkUrl.ifBlank { mosaicUrls.first() }
            val req = remember(single) {
                ImageRequest.Builder(context)
                    .data(single)
                    .crossfade(150)
                    .size(280)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(sizeDp * 0.42f)
            )
        }
    }
}

/**
 * Tactile Song Tile with:
 * - Right-Swipe to Add to Queue (`+ Queue` reveal capsule with threshold haptic & spring snap-back)
 * - Long-Press (or `•••` tap) to open Extended Song Menu (Add to Playlist, Share, Go to Album/Artist, etc.)
 * - Material 3 ripple + bouncy Save heart
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NothingTrackRow(
    track: TrackItem,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isSaved: Boolean,
    onPlayClick: () -> Unit,
    onToggleSave: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onSwipeAddToQueue: (() -> Unit)? = null,
    isDownloaded: Boolean = track.isDownloaded,
    downloadProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val latestSwipeAction by rememberUpdatedState(onSwipeAddToQueue)
    val latestLongClick by rememberUpdatedState(onLongClick)

    val swipeOffsetX = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val maxSwipePx = with(LocalDensity.current) { 96.dp.toPx() }
    var armedThreshold by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "row_press_scale"
    )
    val heartScale by animateFloatAsState(
        targetValue = if (isSaved) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_pop_scale"
    )
    val rowBgColor by animateColorAsState(
        targetValue = if (isCurrentTrack) {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(200),
        label = "row_bg_color"
    )

    val thumbRequest = remember(track.artworkUrl) {
        ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .crossfade(160)
            .size(168)
            .build()
    }

    val swipeModifier = if (onSwipeAddToQueue != null) {
        Modifier.pointerInput(track.id) {
            detectHorizontalDragGestures(
                onDragStart = { armedThreshold = false },
                onHorizontalDrag = { change, dragAmount ->
                    val next = (swipeOffsetX.value + dragAmount * 0.85f).coerceIn(0f, maxSwipePx)
                    if (next > 2f || swipeOffsetX.value > 0f) {
                        change.consume()
                        coroutineScope.launch { swipeOffsetX.snapTo(next) }
                        if (!armedThreshold && next >= thresholdPx) {
                            armedThreshold = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (armedThreshold && next < thresholdPx) {
                            armedThreshold = false
                        }
                    }
                },
                onDragEnd = {
                    val triggered = armedThreshold
                    armedThreshold = false
                    coroutineScope.launch {
                        swipeOffsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    if (triggered) {
                        latestSwipeAction?.invoke()
                    }
                },
                onDragCancel = {
                    armedThreshold = false
                    coroutineScope.launch {
                        swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(swipeModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        // Revealed Right-Swipe "+ Queue" Action Capsule behind the song tile
        if (swipeOffsetX.value > 4f) {
            val progress = (swipeOffsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .padding(start = 14.dp)
                    .scale(0.84f + 0.18f * progress)
                    .clip(CircleShape)
                    .background(
                        if (progress >= 1f) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = if (progress >= 1f) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress >= 1f) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .scale(scale)
                .clip(RoundedCornerShape(18.dp))
                .background(rowBgColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onPlayClick,
                    onLongClick = if (onLongClick != null) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestLongClick?.invoke()
                        }
                    } else null
                )
                .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp)
                .testTag("track_item_card_${track.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (track.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = stringResource(R.string.cd_album_art),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentTrack) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (downloadProgress != null) {
                        Icon(
                            imageVector = Icons.Filled.Downloading,
                            contentDescription = "Downloading",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    } else if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "Downloaded offline",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "• ${formatTimeCode(track.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onToggleSave,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .scale(heartScale)
                    .testTag("like_button_${track.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_like_track),
                    tint = if (isSaved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(19.dp)
                )
            }

            if (onLongClick != null) {
                IconButton(
                    onClick = { onLongClick.invoke() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_options_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_track_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

fun formatTimeCode(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
