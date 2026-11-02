package com.example.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.LyricLine
import com.example.data.model.RepeatModeState
import com.example.data.model.TrackItem
import com.example.player.AudioEngineState
import com.example.ui.NothingUiState
import com.example.ui.components.FrostedGlassCapsule
import com.example.ui.components.GlyphVinylTurntable
import com.example.ui.components.LocalGlassBackdrop
import com.example.ui.components.NdotCircularTimecode
import com.example.ui.components.NothingDotScrubber
import com.example.ui.components.NothingParticleMatrixBackdrop
import com.example.ui.components.NothingTrackRow
import com.example.ui.components.formatTimeCode
import com.example.ui.components.recordGlassBackdrop
import com.example.ui.components.rememberGlassBackdropRig
import com.example.ui.theme.LyricsFontFamily
import com.example.ui.theme.MonoFontFamily
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure-White Translucent Frosted Glass Mini-Player Pill with Active Swipe Continuity:
 * - Dragging horizontally slides the current song text in the drag direction while the incoming
 *   song text (`previousTrack` from left when swiping right, `nextTrack` from right when swiping left)
 *   glides continuously into the viewport.
 * - Crossing ~46% swipe threshold swaps the circular cover art to the incoming track with a tactile
 *   detent haptic and subtle spring pulse.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun NothingMiniPlayerPill(
    audioState: AudioEngineState,
    glyphReactive: Boolean,
    isSaved: Boolean = false,
    previousTrack: TrackItem? = null,
    nextTrack: TrackItem? = null,
    onToggleSave: (() -> Unit)? = null,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = audioState.currentTrack ?: return
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val latestSkipNext by rememberUpdatedState(onSkipNext)
    val latestSkipPrev by rememberUpdatedState(onSkipPrevious)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pillScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "mini_pill_scale"
    )

    val progress = if (audioState.durationMs > 0L) {
        (audioState.currentPositionMs.toFloat() / audioState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var pillWidthPx by remember { mutableFloatStateOf(1f) }
    var textViewportWidthPx by remember { mutableFloatStateOf(1f) }
    val dragOffsetX = remember { Animatable(0f) }
    var crossedMidDetent by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        crossedMidDetent = false
        dragOffsetX.snapTo(0f)
    }

    val swipeFrac = (dragOffsetX.value / (pillWidthPx * 0.58f).coerceAtLeast(1f)).coerceIn(-1f, 1f)
    val absSwipe = abs(swipeFrac)

    // Cover art switches to incoming track past ~46% swipe progress
    val displayedArtTrack = when {
        swipeFrac >= 0.46f && previousTrack != null -> previousTrack
        swipeFrac <= -0.46f && nextTrack != null -> nextTrack
        else -> track
    }
    val coverPulseScale = 1f - (0.12f * (1f - (abs(absSwipe - 0.46f) / 0.22f).coerceIn(0f, 1f)) * if (absSwipe > 0.04f) 1f else 0f)

    val displayedArtReq = remember(displayedArtTrack.artworkUrl) {
        ImageRequest.Builder(context)
            .data(displayedArtTrack.artworkUrl)
            .crossfade(140)
            .size(144)
            .build()
    }

    val incomingTrack = when {
        swipeFrac > 0.01f -> previousTrack ?: track
        swipeFrac < -0.01f -> nextTrack ?: track
        else -> null
    }
    val incomingSubtitle = when {
        swipeFrac > 0.01f && previousTrack == null -> "Restart Song"
        swipeFrac < -0.01f && nextTrack == null -> "Next in Automix"
        else -> incomingTrack?.artist.orEmpty()
    }

    FrostedGlassCapsule(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { sz ->
                if (sz.width > 0) pillWidthPx = sz.width.toFloat()
            }
            .scale(pillScale)
            .pointerInput(track.id, previousTrack?.id, nextTrack?.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        crossedMidDetent = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val maxDrag = pillWidthPx * 0.64f
                        val nextVal = (dragOffsetX.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                        val nextFrac = abs(nextVal / (pillWidthPx * 0.58f).coerceAtLeast(1f))
                        if (!crossedMidDetent && nextFrac >= 0.46f) {
                            crossedMidDetent = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (crossedMidDetent && nextFrac < 0.42f) {
                            crossedMidDetent = false
                        }
                        coroutineScope.launch {
                            dragOffsetX.snapTo(nextVal)
                        }
                    },
                    onDragEnd = {
                        val finalFrac = (dragOffsetX.value / (pillWidthPx * 0.58f).coerceAtLeast(1f))
                        coroutineScope.launch {
                            when {
                                finalFrac <= -0.35f -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragOffsetX.animateTo(
                                        targetValue = -pillWidthPx * 0.58f,
                                        animationSpec = tween(durationMillis = 120)
                                    )
                                    latestSkipNext()
                                    dragOffsetX.snapTo(0f)
                                }
                                finalFrac >= 0.35f -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragOffsetX.animateTo(
                                        targetValue = pillWidthPx * 0.58f,
                                        animationSpec = tween(durationMillis = 120)
                                    )
                                    latestSkipPrev?.invoke()
                                    dragOffsetX.snapTo(0f)
                                }
                                else -> {
                                    dragOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        crossedMidDetent = false
                        coroutineScope.launch {
                            dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onExpand
            )
            .testTag("mini_player_pill"),
        shape = CircleShape
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            scaleX = coverPulseScale
                            scaleY = coverPulseScale
                            rotationZ = swipeFrac * 18f
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 0.85.dp,
                            color = if (absSwipe >= 0.46f) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                            } else {
                                Color.White.copy(alpha = 0.22f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (displayedArtTrack.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = displayedArtReq,
                            contentDescription = stringResource(R.string.cd_album_art),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Clipped continuous swipe text stage: outgoing text slides out as incoming text slides in
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .onSizeChanged { sz ->
                            if (sz.width > 0) textViewportWidthPx = sz.width.toFloat()
                        }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val textSlidePx = swipeFrac * textViewportWidthPx
                    // Outgoing current track text
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = textSlidePx
                                alpha = (1f - absSwipe * 1.15f).coerceIn(0f, 1f)
                            }
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (audioState.isBuffering) {
                                "Resolving ${audioState.streamTelemetry.tier.shortBadge}…"
                            } else {
                                track.artist
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Incoming previous (from left) or next (from right) song text
                    if (incomingTrack != null && absSwipe > 0.02f) {
                        val enterOffset = if (swipeFrac > 0f) {
                            textSlidePx - textViewportWidthPx
                        } else {
                            textSlidePx + textViewportWidthPx
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = enterOffset
                                    alpha = (absSwipe * 1.25f).coerceIn(0f, 1f)
                                }
                        ) {
                            Text(
                                text = incomingTrack.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = incomingSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (onToggleSave != null) {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mini_player_like_button")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_play_pause")
                ) {
                    if (audioState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (audioState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .height(1.5.dp)
            )
        }
    }
}

/**
 * Full-Screen Nchord Player Deck:
 * - Interactive vertical pull-down / swipe-down gesture smoothly translates and dismisses the player
 * - HorizontalPager swipe navigation:
 *     * Swipe Left from Artwork -> Lyrics (page 2)
 *     * Swipe Right from Artwork -> Up Next (page 0)
 * - Stationary-Coordinate Dot-Matrix Backdrop:
 *     * Dots stay strictly in place and serenely modulate color & thickness above the song title
 *     * Dots at and below the song title remain strictly static ("stitched as is")
 * - Synced Lyrics Stage with live variable-weight word bolding + manual sync calibration (`-0.5s / +0.5s`)
 */
@Composable
fun FullScreenNothingPlayer(
    uiState: NothingUiState,
    audioState: AudioEngineState,
    isSaved: Boolean,
    savedIds: Set<String>,
    downloadedTrackIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    glyphReactive: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleSave: (TrackItem) -> Unit,
    onToggleDownloadTrack: ((TrackItem) -> Unit)? = null,
    onClearQueue: (() -> Unit)? = null,
    onPlayQueueItem: (TrackItem) -> Unit,
    onOpenTrackOptions: (TrackItem) -> Unit,
    onOpenArtist: (String) -> Unit,
    onChangePlaybackSpeed: (Float) -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit = {},
    onResetLyricsOffset: () -> Unit = {}
) {
    BackHandler(onBack = onCollapse)

    val track = audioState.currentTrack ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val latestCollapse by rememberUpdatedState(onCollapse)

    val bgColor = MaterialTheme.colorScheme.background
    // Page 0 = Up Next (swipe right), Page 1 = Artwork (center default), Page 2 = Lyrics (swipe left)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    var scrubPreviewFraction by remember { mutableStateOf<Float?>(null) }
    var showAudioStatsHud by remember { mutableStateOf(false) }

    // Measure exact Y coordinate of the song title row so dots at/below it never animate
    var backdropRootY by remember { mutableFloatStateOf(0f) }
    var songTitleTopRelativeYPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    // Interactive pull-down / swipe-down to collapse
    val pullDownOffsetY = remember { Animatable(0f) }
    val collapseThresholdPx = with(density) { 132.dp.toPx() }
    val pullProgress = (pullDownOffsetY.value / (collapseThresholdPx * 2.4f).coerceAtLeast(1f)).coerceIn(0f, 1f)

    // Extract vibrant album accent color off-main-thread for serene dot waves
    var extractedAccent by remember { mutableStateOf(Color(0xFFD71921)) }
    LaunchedEffect(track.artworkUrl) {
        if (track.artworkUrl.isBlank()) {
            extractedAccent = Color(0xFFD71921)
            return@LaunchedEffect
        }
        val color = withContext(Dispatchers.Default) {
            extractVibrantArtworkAccent(context, track.artworkUrl)
        }
        if (color != null) {
            extractedAccent = color
        }
    }
    val smoothAccent by animateColorAsState(
        targetValue = extractedAccent,
        animationSpec = tween(durationMillis = 850),
        label = "player_ambient_accent"
    )

    val playBtnInteraction = remember { MutableInteractionSource() }
    val playBtnPressed by playBtnInteraction.collectIsPressedAsState()
    val playBtnScale by animateFloatAsState(
        targetValue = if (playBtnPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "play_btn_scale"
    )

    // 120Hz frame-interpolated playback clock phase-locked to MediaPlayer position
    var livePositionMs by remember(track.id) { mutableLongStateOf(audioState.currentPositionMs) }
    val latestAnchorPos by rememberUpdatedState(audioState.currentPositionMs)
    val latestDuration by rememberUpdatedState(audioState.durationMs)
    val latestSpeed by rememberUpdatedState(audioState.playbackSpeed)
    val latestIsBuffering by rememberUpdatedState(audioState.isBuffering)

    LaunchedEffect(audioState.currentPositionMs, audioState.isPlaying) {
        if (!audioState.isPlaying || abs(livePositionMs - audioState.currentPositionMs) > 140L) {
            livePositionMs = audioState.currentPositionMs
        }
    }

    LaunchedEffect(audioState.isPlaying, track.id) {
        if (!audioState.isPlaying) {
            livePositionMs = latestAnchorPos
            return@LaunchedEffect
        }
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val elapsedMs = (((nowNanos - lastNanos) / 1_000_000.0) * latestSpeed)
                .toLong()
                .coerceIn(0L, 48L)
            lastNanos = nowNanos
            if (!latestIsBuffering) {
                val maxDur = latestDuration.coerceAtLeast(1L)
                val stepped = (livePositionMs + elapsedMs).coerceAtMost(maxDur)
                val drift = latestAnchorPos - stepped
                livePositionMs = when {
                    abs(drift) > 180L -> latestAnchorPos
                    drift > 28L -> (stepped + 2L).coerceAtMost(maxDur)
                    drift < -95L -> (stepped - 1L).coerceAtLeast(0L)
                    else -> stepped
                }
            }
        }
    }

    val playerBackdropRig = rememberGlassBackdropRig(blurRadius = 46.dp)
    val livePlayerTick = if (audioState.isPlaying) {
        (livePositionMs / 90L).toInt()
    } else {
        pagerState.currentPage
    }

    val onVerticalPullGesture: Modifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0f || pullDownOffsetY.value > 0f) {
                    change.consume()
                    val nextY = (pullDownOffsetY.value + dragAmount).coerceAtLeast(0f)
                    coroutineScope.launch { pullDownOffsetY.snapTo(nextY) }
                }
            },
            onDragEnd = {
                if (pullDownOffsetY.value >= collapseThresholdPx) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestCollapse()
                    coroutineScope.launch { pullDownOffsetY.snapTo(0f) }
                } else {
                    coroutineScope.launch {
                        pullDownOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    pullDownOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        )
    }

    val isArtworkFocused = pagerState.currentPage == 1

    // Outer scrim + touch blocker so touches never leak through to Home tiles
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - pullProgress * 0.65f).coerceIn(0f, 1f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("full_player_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = pullDownOffsetY.value
                    val s = 1f - (pullProgress * 0.045f)
                    scaleX = s
                    scaleY = s
                }
                .clip(
                    RoundedCornerShape(
                        topStart = if (pullDownOffsetY.value > 3f) 28.dp else 0.dp,
                        topEnd = if (pullDownOffsetY.value > 3f) 28.dp else 0.dp
                    )
                )
                .background(bgColor)
        ) {
            // Recorded backdrop layer: Stationary-Grid Dot-Matrix Wave + Soft Artwork Aura
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        backdropRootY = coords.positionInRoot().y
                    }
                    .recordGlassBackdrop(playerBackdropRig, bgColor)
            ) {
                AmbientArtworkAura(
                    isPlaying = audioState.isPlaying && !audioState.isBuffering,
                    isLyricsOrQueue = !isArtworkFocused,
                    spectrum = audioState.glyphSpectrum,
                    accentColor = smoothAccent,
                    modifier = Modifier.fillMaxSize()
                )
                NothingParticleMatrixBackdrop(
                    isPlaying = audioState.isPlaying && !audioState.isBuffering && glyphReactive,
                    visible = isArtworkFocused,
                    spectrum = if (glyphReactive) audioState.glyphSpectrum else emptyList(),
                    accentColor = smoothAccent,
                    staticCutoffYPx = songTitleTopRelativeYPx,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Foreground UI with live heavy backdrop blur wired to frosted capsules
            CompositionLocalProvider(
                LocalGlassBackdrop provides playerBackdropRig.toState(extraTick = livePlayerTick)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Subtle top pull handle + Top Bar: Collapse + Mode Switcher (Up Next | Artwork | Lyrics) + Song Menu
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(onVerticalPullGesture),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FrostedGlassCapsule(
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = onCollapse,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("collapse_player_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.cd_close_player),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            // Ordered left-to-right to match horizontal swipe: [Up Next (0), Artwork (1), Lyrics (2)]
                            val panes = listOf(
                                0 to stringResource(R.string.player_tab_queue),
                                1 to stringResource(R.string.player_tab_disc),
                                2 to stringResource(R.string.player_tab_lyrics)
                            )
                            FrostedGlassCapsule(shape = CircleShape) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    panes.forEach { (pageIdx, label) ->
                                        val selected = pagerState.currentPage == pageIdx
                                        val tabBg by animateColorAsState(
                                            targetValue = if (selected) {
                                                MaterialTheme.colorScheme.onBackground
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(200),
                                            label = "player_tab_bg_$pageIdx"
                                        )
                                        val tabFg by animateColorAsState(
                                            targetValue = if (selected) {
                                                MaterialTheme.colorScheme.background
                                            } else {
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f)
                                            },
                                            animationSpec = tween(200),
                                            label = "player_tab_fg_$pageIdx"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(tabBg)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = ripple(),
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(pageIdx)
                                                        }
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                .testTag("player_pane_tab_$pageIdx"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = tabFg
                                            )
                                        }
                                    }
                                }
                            }

                            FrostedGlassCapsule(
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = { onOpenTrackOptions(track) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("full_player_more_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreHoriz,
                                        contentDescription = stringResource(R.string.cd_track_options),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    // Center HorizontalPager Stage: Swipe Right -> Up Next (0) | Artwork (1) | Swipe Left -> Lyrics (2)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val stageMax = minOf(maxWidth, maxHeight) * 0.86f
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("player_horizontal_pager")
                        ) { page ->
                            when (page) {
                                0 -> {
                                    // Up Next Stage (swiped right from Artwork)
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (uiState.queue.size > 1 && onClearQueue != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "UP NEXT • ${uiState.queue.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                                        .clickable(onClick = onClearQueue)
                                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                                        .testTag("player_clear_queue_button")
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Filled.ClearAll,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onBackground,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Clear",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            itemsIndexed(
                                                uiState.queue,
                                                key = { idx, item -> "${idx}_${item.id}" }
                                            ) { _, qTrack ->
                                                NothingTrackRow(
                                                    track = qTrack,
                                                    isCurrentTrack = qTrack.id == track.id,
                                                    isPlaying = audioState.isPlaying && qTrack.id == track.id,
                                                    isSaved = qTrack.id in savedIds,
                                                    isDownloaded = qTrack.id in downloadedTrackIds || qTrack.isDownloaded,
                                                    downloadProgress = downloadProgress[qTrack.id],
                                                    onPlayClick = { onPlayQueueItem(qTrack) },
                                                    onToggleSave = { onToggleSave(qTrack) },
                                                    onLongClick = { onOpenTrackOptions(qTrack) }
                                                )
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    // Center Artwork Platter Stage (also supports pull-down to dismiss)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(onVerticalPullGesture),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        GlyphVinylTurntable(
                                            artworkUrl = track.artworkUrl,
                                            isPlaying = audioState.isPlaying,
                                            glyphReactive = glyphReactive,
                                            spectrum = audioState.glyphSpectrum,
                                            modifier = Modifier.size(stageMax.coerceAtMost(306.dp))
                                        )
                                    }
                                }

                                else -> {
                                    // Synced Lyrics Stage (swiped left from Artwork)
                                    SyncedLyricsStage(
                                        lyrics = uiState.lyrics,
                                        isLoading = uiState.isLyricsLoading,
                                        currentPosMs = livePositionMs,
                                        lyricsOffsetMs = uiState.lyricsOffsetMs,
                                        accentColor = smoothAccent,
                                        onSeekLine = onSeekMs,
                                        onAdjustOffset = onAdjustLyricsOffset,
                                        onResetOffset = onResetLyricsOffset
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Metadata + Audio Quality Telemetry + Dramatic Thickening Scrubber + Transport
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(visible = showAudioStatsHud) {
                            FrostedGlassCapsule(
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .testTag("player_stats_for_nerds_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val tel = audioState.streamTelemetry
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${tel.codecLabel} • ${tel.bitDepth}-bit / ${tel.sampleRateHz / 1000.0}kHz",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "${tel.bitrateKbps} kbps • ${tel.outputRoute}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Speed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        listOf(0.85f, 1.0f, 1.15f, 1.25f, 1.5f).forEach { spd ->
                                            val activeSpd = abs(audioState.playbackSpeed - spd) < 0.03f
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (activeSpd) {
                                                            MaterialTheme.colorScheme.onBackground
                                                        } else {
                                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                                        }
                                                    )
                                                    .clickable { onChangePlaybackSpeed(spd) }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "${spd}x",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (activeSpd) {
                                                        MaterialTheme.colorScheme.background
                                                    } else {
                                                        MaterialTheme.colorScheme.onBackground
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Song Title Row — establishes staticCutoffYPx so dots at/below this line stay stitched as-is
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    songTitleTopRelativeYPx = (coords.positionInRoot().y - backdropRootY)
                                        .coerceAtLeast(0f)
                                }
                                .then(onVerticalPullGesture),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { onOpenArtist(track.artist) }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.09f))
                                            .clickable { showAudioStatsHud = !showAudioStatsHud }
                                            .padding(horizontal = 9.dp, vertical = 3.dp)
                                            .testTag("player_quality_badge_pill")
                                    ) {
                                        val exactQualityText = remember(audioState.streamTelemetry) {
                                            val tel = audioState.streamTelemetry
                                            if (tel.bitrateKbps > 0 && !tel.codecLabel.startsWith("Resolving")) {
                                                "${tel.codecLabel} • ${tel.bitrateKbps}k"
                                            } else {
                                                "Resolving…"
                                            }
                                        }
                                        Text(
                                            text = exactQualityText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    if (uiState.sleepTimerRemainingMinutes != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bedtime,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (uiState.sleepTimerRemainingMinutes == -1) "EOT" else "${uiState.sleepTimerRemainingMinutes}m",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (onToggleDownloadTrack != null) {
                                val isTrackDownloaded = track.id in downloadedTrackIds || track.isDownloaded
                                val isTrackDownloading = downloadProgress.containsKey(track.id)
                                IconButton(
                                    onClick = { onToggleDownloadTrack(track) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("full_player_download_button")
                                ) {
                                    if (isTrackDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isTrackDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                            contentDescription = if (isTrackDownloaded) "Remove offline copy" else "Download song in app",
                                            tint = if (isTrackDownloaded) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onToggleSave(track) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("full_player_like_button")
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = stringResource(R.string.cd_like_track),
                                    tint = if (isSaved) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    }
                                )
                            }
                        }

                        if (!audioState.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = audioState.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val fraction = if (audioState.durationMs > 0L) {
                            (livePositionMs.toFloat() / audioState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        NothingDotScrubber(
                            progressFraction = fraction,
                            trackSeed = track.id.hashCode(),
                            onScrubPreviewFraction = { preview ->
                                scrubPreviewFraction = preview
                            },
                            onSeekFraction = { frac ->
                                val targetMs = (frac * audioState.durationMs).toLong()
                                livePositionMs = targetMs
                                onSeekMs(targetMs)
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val displayedPositionMs = scrubPreviewFraction?.let {
                            (it * audioState.durationMs).toLong()
                        } ?: livePositionMs

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NdotCircularTimecode(
                                text = formatTimeCode(displayedPositionMs),
                                dotColor = MaterialTheme.colorScheme.onBackground,
                                dotSizeDp = 2.4.dp
                            )
                            NdotCircularTimecode(
                                text = formatTimeCode(audioState.durationMs),
                                dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                dotSizeDp = 2.4.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onToggleShuffle,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_shuffle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    tint = if (uiState.shuffleEnabled) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    }
                                )
                            }

                            FrostedGlassCapsule(
                                shape = CircleShape,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = onSkipPrev
                                        )
                                        .testTag("player_prev_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = stringResource(R.string.cd_prev_track),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .size(70.dp)
                                    .scale(playBtnScale)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = playBtnInteraction,
                                        indication = ripple(),
                                        onClick = onTogglePlay
                                    )
                                    .testTag("player_play_pause_button"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 10.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (audioState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (audioState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.cd_play_pause),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            FrostedGlassCapsule(
                                shape = CircleShape,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = onSkipNext
                                        )
                                        .testTag("player_next_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = stringResource(R.string.cd_next_track),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onCycleRepeat,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_repeat_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.repeatMode == RepeatModeState.ONE) {
                                        Icons.Filled.RepeatOne
                                    } else {
                                        Icons.Filled.Repeat
                                    },
                                    contentDescription = stringResource(R.string.cd_repeat),
                                    tint = if (uiState.repeatMode != RepeatModeState.OFF) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Subtle radial artwork atmosphere behind the particle lattice.
 * Dims smoothly when paused or on Lyrics/Queue panes so text contrast stays razor-sharp.
 */
@Composable
private fun AmbientArtworkAura(
    isPlaying: Boolean,
    isLyricsOrQueue: Boolean,
    spectrum: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val rawBass = if (isPlaying && spectrum.isNotEmpty()) {
        spectrum.take(3).average().toFloat()
    } else 0f
    val bassGlow by animateFloatAsState(
        targetValue = rawBass.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aura_bass_glow"
    )
    val auraAlpha by animateFloatAsState(
        targetValue = when {
            isLyricsOrQueue -> 0.06f
            isPlaying -> 0.16f + bassGlow * 0.11f
            else -> 0.03f
        },
        animationSpec = tween(durationMillis = 480),
        label = "aura_stage_alpha"
    )

    if (auraAlpha <= 0.005f) return

    Canvas(modifier = modifier) {
        val focalCenter = Offset(size.width * 0.5f, size.height * 0.36f)
        val radius = minOf(size.width, size.height) * (0.62f + bassGlow * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = auraAlpha),
                    accentColor.copy(alpha = auraAlpha * 0.30f),
                    Color.Transparent
                ),
                center = focalCenter,
                radius = radius.coerceAtLeast(1f)
            ),
            radius = radius,
            center = focalCenter
        )
    }
}

/**
 * Dramatic Live On-The-Spot Variable-Weight Synced Lyrics Stage:
 * - Driven at 120Hz by `livePositionMs` + `lyricsOffsetMs`
 * - Includes compact Nothing OS `-0.5s / +0.5s` sync calibration pill bar
 * - Renders plain unsynced lyrics cleanly without fake drifting karaoke word-bolding
 */
@Composable
private fun SyncedLyricsStage(
    lyrics: List<LyricLine>,
    isLoading: Boolean,
    currentPosMs: Long,
    lyricsOffsetMs: Long,
    accentColor: Color,
    onSeekLine: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onBackground,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.player_lyrics_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.player_lyrics_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(28.dp)
            )
        }
        return
    }

    val isTimeSynced = remember(lyrics) { lyrics.first().isSynced }
    val effectivePosMs = (currentPosMs + lyricsOffsetMs).coerceAtLeast(0L)

    val listState = rememberLazyListState()
    val activeIdx by remember(lyrics, effectivePosMs, isTimeSynced) {
        derivedStateOf {
            if (!isTimeSynced) -1
            else lyrics.indexOfLast { it.timestampMs <= effectivePosMs }.coerceAtLeast(0)
        }
    }

    val intraLineProgress = remember(lyrics, activeIdx, effectivePosMs, isTimeSynced) {
        if (!isTimeSynced || activeIdx < 0) {
            1f
        } else {
            val currentLine = lyrics.getOrNull(activeIdx)
            if (currentLine == null) {
                1f
            } else {
                val startMs = currentLine.timestampMs
                val nextStartMs = lyrics.getOrNull(activeIdx + 1)?.timestampMs ?: (startMs + 3_800L)
                val vocalSpanMs = ((nextStartMs - startMs) * 0.88f).toLong().coerceIn(420L, 6_500L)
                ((effectivePosMs - startMs).toFloat() / vocalSpanMs.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(activeIdx, isTimeSynced) {
        if (isTimeSynced && activeIdx in lyrics.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 3) else -150
            listState.animateScrollToItem(
                index = activeIdx,
                scrollOffset = centerOffset
            )
        }
    }

    val baseOnBg = MaterialTheme.colorScheme.onBackground
    val glowColor = remember(accentColor, baseOnBg) {
        lerp(baseOnBg, accentColor, 0.32f)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sync Calibration & Mode Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTimeSynced) "TIME-SYNCED LRC" else "UNSYNCED LYRICS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MonoFontFamily,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isTimeSynced) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(baseOnBg.copy(alpha = 0.08f))
                            .clickable { onAdjustOffset(-500L) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("lyrics_offset_minus")
                    ) {
                        Text(
                            text = "-0.5s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = baseOnBg
                        )
                    }

                    val offsetSec = lyricsOffsetMs / 1000.0
                    val offsetLabel = if (lyricsOffsetMs == 0L) {
                        "SYNC 0.0s"
                    } else {
                        val sign = if (offsetSec > 0) "+" else ""
                        "${sign}${"%.1f".format(offsetSec)}s"
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (lyricsOffsetMs != 0L) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    baseOnBg.copy(alpha = 0.05f)
                                }
                            )
                            .clickable { onResetOffset() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("lyrics_offset_reset")
                    ) {
                        Text(
                            text = offsetLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = if (lyricsOffsetMs != 0L) MaterialTheme.colorScheme.primary else baseOnBg.copy(alpha = 0.72f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(baseOnBg.copy(alpha = 0.08f))
                            .clickable { onAdjustOffset(500L) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("lyrics_offset_plus")
                    ) {
                        Text(
                            text = "+0.5s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                            color = baseOnBg
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 36.dp, bottom = 92.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("synced_lyrics_list")
        ) {
            itemsIndexed(
                items = lyrics,
                key = { idx, line -> "${idx}_${line.timestampMs}" }
            ) { idx, line ->
                if (!isTimeSynced) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = LyricsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp,
                            lineHeight = 32.sp
                        ),
                        color = baseOnBg.copy(alpha = 0.86f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                } else {
                    val distance = abs(idx - activeIdx)
                    val isCurrent = distance == 0
                    val isPast = idx < activeIdx

                    val targetAlpha = when {
                        isCurrent -> 1f
                        distance == 1 -> if (isPast) 0.28f else 0.44f
                        distance == 2 -> 0.20f
                        else -> 0.11f
                    }
                    val lineAlpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "lyric_depth_alpha"
                    )
                    val lineScale by animateFloatAsState(
                        targetValue = if (isCurrent) 1.04f else 0.96f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "lyric_focus_scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = lineScale
                                scaleY = lineScale
                                alpha = lineAlpha
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    val seekTarget = (line.timestampMs - lyricsOffsetMs).coerceAtLeast(0L)
                                    onSeekLine(seekTarget)
                                }
                            )
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                    ) {
                        if (isCurrent) {
                            val wordWindows = remember(line.text) {
                                val split = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                                val weights = split.map { (it.length + 2).toFloat() }
                                val totalW = weights.sum().coerceAtLeast(1f)
                                var cursor = 0f
                                split.mapIndexed { wIdx, w ->
                                    val span = weights[wIdx] / totalW
                                    val start = cursor
                                    val end = (cursor + span).coerceAtMost(1f)
                                    cursor = end
                                    Triple(w, start, end)
                                }
                            }

                            val liveBoldAnnotated = buildAnnotatedString {
                                wordWindows.forEachIndexed { wIdx, (word, startF, endF) ->
                                    val span = (endF - startF).coerceAtLeast(0.02f)
                                    val rawWordFrac = ((intraLineProgress - startF) / span).coerceIn(0f, 1f)
                                    val eased = rawWordFrac * rawWordFrac * (3f - 2f * rawWordFrac)

                                    val dynamicWeight = when {
                                        eased >= 0.90f -> FontWeight.Black
                                        eased >= 0.72f -> FontWeight.ExtraBold
                                        eased >= 0.52f -> FontWeight.Bold
                                        eased >= 0.32f -> FontWeight.SemiBold
                                        eased >= 0.14f -> FontWeight.Medium
                                        eased > 0.02f -> FontWeight.Normal
                                        else -> FontWeight.Light
                                    }

                                    val wordColor = lerp(
                                        baseOnBg.copy(alpha = 0.34f),
                                        baseOnBg,
                                        eased
                                    )
                                    val activePulse = if (rawWordFrac in 0.05f..0.98f) {
                                        0.58f
                                    } else if (rawWordFrac >= 0.98f) {
                                        0.30f
                                    } else {
                                        0f
                                    }

                                    withStyle(
                                        SpanStyle(
                                            fontFamily = LyricsFontFamily,
                                            fontWeight = dynamicWeight,
                                            color = wordColor,
                                            shadow = if (activePulse > 0f) {
                                                Shadow(
                                                    color = glowColor.copy(alpha = activePulse * eased),
                                                    offset = Offset.Zero,
                                                    blurRadius = 22f
                                                )
                                            } else null
                                        )
                                    ) {
                                        append(word)
                                    }
                                    if (wIdx < wordWindows.lastIndex) append(" ")
                                }
                            }

                            Text(
                                text = liveBoldAnnotated,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = LyricsFontFamily,
                                    fontSize = 27.sp,
                                    lineHeight = 36.sp,
                                    letterSpacing = (-0.3).sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = LyricsFontFamily,
                                    fontWeight = if (isPast) FontWeight.Normal else FontWeight.Light,
                                    fontSize = 22.sp,
                                    lineHeight = 30.sp
                                ),
                                color = baseOnBg.copy(alpha = if (isPast) 0.42f else 0.38f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Samples a downscaled 40x40 software bitmap to find the richest vibrant hue from the album cover.
 */
private suspend fun extractVibrantArtworkAccent(
    context: android.content.Context,
    artworkUrl: String
): Color? {
    return runCatching {
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(40)
            .allowHardware(false)
            .build()
        val drawable = context.imageLoader.execute(request).drawable as? BitmapDrawable
            ?: return null
        val bitmap = drawable.bitmap ?: return null

        var bestR = 215f
        var bestG = 25f
        var bestB = 33f
        var bestScore = -1f

        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 10).coerceAtLeast(1)
        val stepY = (h / 10).coerceAtLeast(1)

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val chroma = maxC - minC
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

                if (lum in 0.12f..0.88f) {
                    val score = chroma * 1.8f + (1f - abs(lum - 0.52f)) * 0.35f
                    if (score > bestScore) {
                        bestScore = score
                        bestR = r
                        bestG = g
                        bestB = b
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (bestScore < 0.12f) {
            Color(0xFFDCE2F0)
        } else {
            Color(red = bestR, green = bestG, blue = bestB, alpha = 1f)
        }
    }.getOrNull()
}
