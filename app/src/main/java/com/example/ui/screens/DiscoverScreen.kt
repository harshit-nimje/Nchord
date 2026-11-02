package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.HomeFeedFilter
import com.example.data.model.RecentGridTile
import com.example.data.model.RecentTileKind
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.ui.DEFAULT_MOOD_CHANNELS
import com.example.ui.MoodChannel
import com.example.ui.NothingUiState
import com.example.ui.components.NothingPlaylistMosaicCover
import com.example.ui.components.NothingTrackRow
import com.example.ui.theme.MonoFontFamily

@Composable
fun DiscoverScreen(
    uiState: NothingUiState,
    recentGridTiles: List<RecentGridTile>,
    downloadedTrackIds: Set<String>,
    downloadProgress: Map<String, Float>,
    offlineTracks: List<TrackItem>,
    currentTrackId: String?,
    isPlaying: Boolean,
    savedIds: Set<String>,
    onSelectHomeFilter: (HomeFeedFilter) -> Unit,
    onSelectMood: (MoodChannel) -> Unit,
    onOpenRecentTile: (RecentGridTile) -> Unit,
    onOpenCarouselPlaylist: (SuggestedPlaylist) -> Unit,
    onPlayCarouselPlaylist: (SuggestedPlaylist, Boolean) -> Unit,
    onOpenAlbumForTrack: (TrackItem) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleSave: (TrackItem) -> Unit,
    onSwipeAddToQueue: (TrackItem) -> Unit,
    onLongClickTrack: (TrackItem) -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeFilter = uiState.homeFeedFilter

    val nchordMixes = remember(uiState.suggestedPlaylists, uiState.featuredTracks, uiState.activeMoodLabel) {
        if (uiState.suggestedPlaylists.isNotEmpty()) {
            uiState.suggestedPlaylists
        } else if (uiState.featuredTracks.isNotEmpty()) {
            uiState.featuredTracks.chunked(4).mapIndexed { index, chunk ->
                val first = chunk.first()
                SuggestedPlaylist(
                    id = "fallback_mix_${uiState.selectedMoodId}_$index",
                    title = if (index == 0) "${uiState.activeMoodLabel} Mix" else "Night Rotation ${index + 1}",
                    subtitle = chunk.map { it.artist.substringBefore(",").trim() }.distinct().joinToString(", "),
                    badge = "NCHORD",
                    artworkUrl = first.artworkUrl,
                    trackCount = chunk.size,
                    preloadedTracks = chunk,
                    accentHex = if (index % 2 == 0) 0xFFD71921 else 0xFF1ED760
                )
            }
        } else {
            emptyList()
        }
    }

    val pickedSpotlightTrack = remember(uiState.featuredTracks, uiState.moodTracks, offlineTracks, activeFilter) {
        when (activeFilter) {
            HomeFeedFilter.OFFLINE -> offlineTracks.firstOrNull()
            else -> uiState.featuredTracks.firstOrNull() ?: uiState.moodTracks.firstOrNull()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("discover_screen_list"),
        contentPadding = PaddingValues(bottom = 196.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Minimal Collector Filter Strip (All • Playlists • Spotify • YouTube • Offline) + Genre Pills
        item(key = "home_collector_filter_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HomeFeedFilter.entries, key = { it.name }) { filter ->
                        val selected = filter == activeFilter
                        val bg by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            },
                            animationSpec = tween(180),
                            label = "home_filter_bg"
                        )
                        val fg by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.background
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = tween(180),
                            label = "home_filter_fg"
                        )

                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(bg)
                                .border(
                                    width = 0.8.dp,
                                    color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                    shape = CircleShape
                                )
                                .clickable { onSelectHomeFilter(filter) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("home_filter_${filter.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = fg
                            )
                        }
                    }
                }

                // Subtle Genre / Crate Selector Row
                if (activeFilter != HomeFeedFilter.OFFLINE) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(DEFAULT_MOOD_CHANNELS, key = { it.id }) { channel ->
                            val selected = channel.id == uiState.selectedMoodId
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = 0.75.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectMood(channel) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("mood_chip_${channel.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channel.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = MonoFontFamily,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                if (uiState.isMoodRefreshing) {
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(1.5.dp)
                    )
                }
            }
        }

        // 2. Top 2-Column × (2–4 Row) Recently Played Playlists Grid (replaces big container)
        if (recentGridTiles.isNotEmpty()) {
            item(key = "home_recent_playlists_grid") {
                RecentPlaylistsCompactGrid(
                    tiles = recentGridTiles,
                    onClickTile = onOpenRecentTile,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // 3. Offline Filter Dedicated View
        if (activeFilter == HomeFeedFilter.OFFLINE) {
            item(key = "offline_vault_header") {
                SectionShelfHeader(
                    title = "Offline Vinyl Vault",
                    tag = "${offlineTracks.size} IN-APP"
                )
            }
            if (offlineTracks.isEmpty()) {
                item(key = "offline_empty_state") {
                    Text(
                        text = stringResource(R.string.lib_empty_downloads),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                    )
                }
            } else {
                items(offlineTracks, key = { "home_dl_${it.id}" }) { track ->
                    NothingTrackRow(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        isSaved = track.id in savedIds,
                        isDownloaded = true,
                        downloadProgress = downloadProgress[track.id],
                        onPlayClick = { onPlayTrack(track, offlineTracks) },
                        onToggleSave = { onToggleSave(track) },
                        onSwipeAddToQueue = { onSwipeAddToQueue(track) },
                        onLongClick = { onLongClickTrack(track) },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
            return@LazyColumn
        }

        // 4. "Picked for you" Collector Spotlight Card (Spotify Reference + Nothing Vinyl Language)
        if (pickedSpotlightTrack != null) {
            item(key = "picked_for_you_spotlight") {
                PickedForYouVinylCard(
                    track = pickedSpotlightTrack,
                    isSaved = pickedSpotlightTrack.id in savedIds,
                    onOpenRelease = { onOpenAlbumForTrack(pickedSpotlightTrack) },
                    onPlayNow = {
                        val queue = (listOf(pickedSpotlightTrack) + uiState.featuredTracks).distinctBy { it.id }
                        onPlayTrack(pickedSpotlightTrack, queue)
                    },
                    onToggleSave = { onToggleSave(pickedSpotlightTrack) },
                    onMoreOptions = { onLongClickTrack(pickedSpotlightTrack) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // 5. Loading / Error States
        if (uiState.isDiscoverLoading && nchordMixes.isEmpty() && uiState.moodTracks.isEmpty()) {
            item(key = "discover_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else if (uiState.discoverError != null && uiState.moodTracks.isEmpty()) {
            item(key = "discover_error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.discoverError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRetryLoad,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("discover_retry_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // 6. Square-Tile Playlist Carousels (Nchord Engine, Spotify Crates, YouTube Music Sessions, Vinyl Pressings)
        val showNchord = activeFilter == HomeFeedFilter.ALL || activeFilter == HomeFeedFilter.PLAYLISTS
        val showSpotify = activeFilter == HomeFeedFilter.ALL ||
            activeFilter == HomeFeedFilter.PLAYLISTS ||
            activeFilter == HomeFeedFilter.SPOTIFY
        val showYoutube = activeFilter == HomeFeedFilter.ALL ||
            activeFilter == HomeFeedFilter.PLAYLISTS ||
            activeFilter == HomeFeedFilter.YOUTUBE

        if (showNchord && nchordMixes.isNotEmpty()) {
            item(key = "carousel_nchord_soundtrack") {
                SquarePlaylistCarouselSection(
                    title = stringResource(R.string.section_soundtrack),
                    tag = "NCHORD ENGINE",
                    playlists = nchordMixes,
                    onOpenPlaylist = onOpenCarouselPlaylist,
                    onQuickPlayPlaylist = { pl -> onPlayCarouselPlaylist(pl, false) }
                )
            }
        }

        if (showSpotify && uiState.spotifyCratePlaylists.isNotEmpty()) {
            item(key = "carousel_spotify_crates") {
                SquarePlaylistCarouselSection(
                    title = stringResource(R.string.section_spotify_crates),
                    tag = if (uiState.spotifyProfile.isConnected) "SPOTIFY SYNCED" else "SPOTIFY CURATED",
                    playlists = uiState.spotifyCratePlaylists,
                    onOpenPlaylist = onOpenCarouselPlaylist,
                    onQuickPlayPlaylist = { pl -> onPlayCarouselPlaylist(pl, false) }
                )
            }
        }

        if (showYoutube && uiState.ytmSessionPlaylists.isNotEmpty()) {
            item(key = "carousel_ytm_sessions") {
                SquarePlaylistCarouselSection(
                    title = stringResource(R.string.section_ytm_sessions),
                    tag = "YOUTUBE MUSIC",
                    playlists = uiState.ytmSessionPlaylists,
                    onOpenPlaylist = onOpenCarouselPlaylist,
                    onQuickPlayPlaylist = { pl -> onPlayCarouselPlaylist(pl, false) }
                )
            }
        }

        if (showNchord && uiState.vinylPressingPlaylists.isNotEmpty()) {
            item(key = "carousel_vinyl_pressings") {
                SquarePlaylistCarouselSection(
                    title = stringResource(R.string.section_vinyl_pressings),
                    tag = "33⅓ RPM SLEEVES",
                    playlists = uiState.vinylPressingPlaylists,
                    onOpenPlaylist = onOpenCarouselPlaylist,
                    onQuickPlayPlaylist = { pl -> onPlayCarouselPlaylist(pl, false) }
                )
            }
        }

        // 7. Concise Needle-Drop Track Selection at Bottom of Collector Shelf
        if (activeFilter == HomeFeedFilter.ALL && uiState.moodTracks.isNotEmpty()) {
            item(key = "needle_drops_header") {
                SectionShelfHeader(
                    title = stringResource(R.string.section_featured),
                    tag = uiState.activeMoodLabel.uppercase()
                )
            }
            val tracksSubset = uiState.moodTracks.take(8)
            items(tracksSubset, key = { "home_track_${it.id}" }) { track ->
                NothingTrackRow(
                    track = track,
                    isCurrentTrack = track.id == currentTrackId,
                    isPlaying = isPlaying && track.id == currentTrackId,
                    isSaved = track.id in savedIds,
                    isDownloaded = track.id in downloadedTrackIds,
                    downloadProgress = downloadProgress[track.id],
                    onPlayClick = { onPlayTrack(track, uiState.moodTracks) },
                    onToggleSave = { onToggleSave(track) },
                    onSwipeAddToQueue = { onSwipeAddToQueue(track) },
                    onLongClick = { onLongClickTrack(track) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    }
}

/**
 * Compact 2-Column × (2–4 Row) Recently Played Playlists Grid.
 * Inspired by Spotify's quick-resume shelf, distilled into crisp Nothing OS monochrome + red hardware accents.
 */
@Composable
private fun RecentPlaylistsCompactGrid(
    tiles: List<RecentGridTile>,
    onClickTile: (RecentGridTile) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(tiles) { tiles.take(8).chunked(2) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_recent_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { rowIdx, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { tile ->
                    RecentCompactPlaylistTile(
                        tile = tile,
                        onClick = { onClickTile(tile) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentCompactPlaylistTile(
    tile: RecentGridTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "recent_tile_scale"
    )
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .testTag("recent_grid_tile_${tile.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Square Cover Art (56dp flush)
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    when (tile.kind) {
                        RecentTileKind.LIKED_SONGS -> Brush.linearGradient(
                            listOf(Color(0xFFD71921), Color(0xFF431116))
                        )
                        RecentTileKind.ON_REPEAT -> Brush.linearGradient(
                            listOf(Color(0xFF1F2430), Color(0xFF0E1015))
                        )
                        RecentTileKind.DOWNLOADS -> Brush.linearGradient(
                            listOf(Color(0xFF182920), Color(0xFF0C120F))
                        )
                        else -> Brush.linearGradient(
                            listOf(Color(0xFF1E2026), Color(0xFF121318))
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                tile.kind == RecentTileKind.LIKED_SONGS && tile.artworkUrl.isBlank() -> {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                tile.kind == RecentTileKind.ON_REPEAT && tile.artworkUrl.isBlank() -> {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                tile.kind == RecentTileKind.DOWNLOADS && tile.artworkUrl.isBlank() -> {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    NothingPlaylistMosaicCover(
                        artworkUrl = tile.artworkUrl,
                        mosaicUrls = tile.mosaicUrls,
                        sizeDp = 56.dp,
                        cornerRadiusDp = 0.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        )

        if (tile.isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(12.dp)
            )
        }
    }
}

/**
 * "Picked for you" Vinyl Collector Spotlight Card.
 * Directly inspired by the reference screenshot: bold section heading, square sleeve on left with subtle
 * vinyl disc edge peek, release type + title + artist, `+` save button, `⋮` menu, and crisp white circular Play button.
 */
@Composable
private fun PickedForYouVinylCard(
    track: TrackItem,
    isSaved: Boolean,
    onOpenRelease: () -> Unit,
    onPlayNow: () -> Unit,
    onToggleSave: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artReq = remember(track.artworkUrl) {
        ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .crossfade(180)
            .size(360)
            .build()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.section_picked_for_you),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .border(
                    width = 0.85.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.11f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onOpenRelease)
                .testTag("home_picked_for_you_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Square Sleeve + Minimal Vinyl Rim Peek
            Box(
                modifier = Modifier
                    .width(154.dp)
                    .height(148.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Vinyl disc rim peeking 12dp out from behind the square sleeve
                Canvas(
                    modifier = Modifier
                        .size(132.dp)
                        .offset(x = 20.dp)
                ) {
                    val r = size.minDimension / 2f
                    drawCircle(color = Color(0xFF090A0D), radius = r)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = r * 0.86f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFD71921),
                        radius = r * 0.24f
                    )
                    drawCircle(color = Color.Black, radius = r * 0.06f)
                }

                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (track.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = artReq,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(34.dp)
                        )
                    }
                }
            }

            // Right Collector Metadata & Tactile Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (track.album.isNotBlank()) "ALBUM • VINYL CUT" else "SINGLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                letterSpacing = 1.1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.album.ifBlank { track.title },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onMoreOptions,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp)
                            .testTag("picked_card_more")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.cd_track_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (isSaved) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                                shape = CircleShape
                            )
                            .testTag("picked_card_save")
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_like_track),
                            tint = if (isSaved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground)
                            .clickable(onClick = onPlayNow)
                            .testTag("picked_card_play"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Horizontal Square-Tile Playlist Carousel Shelf (Spotify / YouTube Music / Nchord Engine / Vinyl Pressings).
 */
@Composable
private fun SquarePlaylistCarouselSection(
    title: String,
    tag: String,
    playlists: List<SuggestedPlaylist>,
    onOpenPlaylist: (SuggestedPlaylist) -> Unit,
    onQuickPlayPlaylist: (SuggestedPlaylist) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionShelfHeader(title = title, tag = tag)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(playlists, key = { "${tag}_${it.id}" }) { playlist ->
                SquareVinylPlaylistTile(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                    onLongPressPlay = { onQuickPlayPlaylist(playlist) }
                )
            }
        }
    }
}

@Composable
private fun SectionShelfHeader(
    title: String,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Practical Minimal Square Sleeve Playlist Tile (`154.dp × 154.dp` square cover):
 * - Clean source badge (`SPOTIFY`, `YTM`, `NCHORD`, `VINYL LP`) top-left
 * - Signature color bar + title overlay at bottom of sleeve (inspired by Spotify Mixes in Nothing aesthetic)
 * - Descriptive 2-line artist/curator caption below
 * - Tap opens playlist; long-press starts instant playback
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareVinylPlaylistTile(
    playlist: SuggestedPlaylist,
    onClick: () -> Unit,
    onLongPressPlay: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "square_tile_scale"
    )
    val accentColor = remember(playlist.accentHex) { Color(playlist.accentHex) }

    val imageReq = remember(playlist.artworkUrl) {
        ImageRequest.Builder(context)
            .data(playlist.artworkUrl)
            .crossfade(160)
            .size(360)
            .build()
    }

    Column(
        modifier = Modifier
            .width(154.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressPlay()
                }
            )
            .testTag("square_playlist_tile_${playlist.id}")
    ) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            if (playlist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = imageReq,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Architectural dot-matrix fallback sleeve
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF15171D))
                    val step = size.width / 8f
                    for (r in 1..6) {
                        for (c in 1..6) {
                            drawCircle(
                                color = accentColor.copy(alpha = 0.28f),
                                radius = 2.dp.toPx(),
                                center = Offset(c * step, r * step)
                            )
                        }
                    }
                }
            }

            // Bottom gradient scrim for crisp sleeve title legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.18f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            )

            // Source Micro-Tag (Top Left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.68f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = playlist.badge.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 9.sp,
                            letterSpacing = 0.7.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Sleeve Title + Signature Left Bar + Bottom Accent Strip
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 0.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(accentColor)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = playlist.subtitle.ifBlank { "${playlist.trackCount} tracks" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
