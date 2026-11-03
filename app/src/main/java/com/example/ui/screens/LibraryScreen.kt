package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist
import com.example.data.repository.NothingMusicRepository
import com.example.ui.components.ImportPlaylistUrlDialog
import com.example.ui.components.NothingPlaylistMosaicCover
import com.example.ui.components.NothingTrackRow
import com.example.ui.components.PlaylistEditorDialog
import com.example.ui.theme.MonoFontFamily

@Composable
fun LibraryScreen(
    userPlaylists: List<UserPlaylist>,
    spotifyPlaylists: List<SpotifyPlaylistSummary>,
    downloadedTracks: List<TrackItem>,
    downloadedTrackIds: Set<String>,
    downloadProgress: Map<String, Float>,
    pinnedItemKeys: List<String>,
    savedTracks: List<TrackItem>,
    recentScrobbles: List<TrackItem>,
    localImportedTracks: List<TrackItem>,
    currentTrackId: String?,
    isPlaying: Boolean,
    savedIds: Set<String>,
    unplayableTrackIds: Set<String> = emptySet(),
    hideUnplayableSongs: Boolean = false,
    onTogglePinItem: (itemKey: String, label: String) -> Unit,
    onDeleteUserPlaylist: (UserPlaylist) -> Unit,
    onOpenUserPlaylist: (UserPlaylist) -> Unit,
    onOpenSpotifyPlaylist: (SpotifyPlaylistSummary) -> Unit,
    onCreatePlaylist: (name: String, description: String) -> Unit,
    onImportPlaylistUrl: (String) -> Unit,
    onImportLocalAudio: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleSave: (TrackItem) -> Unit,
    onSwipeAddToQueue: (TrackItem) -> Unit,
    onLongClickTrack: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSegment by rememberSaveable { mutableIntStateOf(0) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportUrlDialog by rememberSaveable { mutableStateOf(false) }
    var playlistPendingDeletion by remember { mutableStateOf<UserPlaylist?>(null) }

    val segments = listOf(
        stringResource(R.string.lib_playlists),
        stringResource(R.string.lib_downloads),
        stringResource(R.string.lib_saved),
        stringResource(R.string.lib_recent),
        stringResource(R.string.lib_local)
    )

    if (showCreatePlaylistDialog) {
        PlaylistEditorDialog(
            isEditMode = false,
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name, desc -> onCreatePlaylist(name, desc) }
        )
    }

    if (showImportUrlDialog) {
        ImportPlaylistUrlDialog(
            onDismiss = { showImportUrlDialog = false },
            onImportUrl = onImportPlaylistUrl
        )
    }

    playlistPendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistPendingDeletion = null },
            containerColor = Color(0xFF14151A),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.72f),
            title = {
                Text(
                    text = stringResource(R.string.lib_delete_playlist_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "\"${target.name}\" — ${stringResource(R.string.lib_delete_playlist_desc)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUserPlaylist(target)
                        playlistPendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_playlist_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistPendingDeletion = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Scrollable Segment Bar + Contextual Creation / Sync Pill
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(segments) { idx, label ->
                val selected = activeSegment == idx
                val bg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                    },
                    animationSpec = tween(200),
                    label = "lib_seg_bg"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(200),
                    label = "lib_seg_fg"
                )

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { activeSegment = idx }
                        .padding(horizontal = 15.dp, vertical = 9.dp)
                        .testTag("library_tab_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (idx == 1 && downloadedTracks.isNotEmpty()) {
                            "$label (${downloadedTracks.size})"
                        } else {
                            label
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = fg
                    )
                }
            }

            if (activeSegment == 0) {
                item(key = "new_playlist_action") {
                    LibraryActionPill(
                        icon = Icons.Filled.Add,
                        label = "New",
                        onClick = { showCreatePlaylistDialog = true },
                        tag = "create_playlist_button"
                    )
                }
                item(key = "sync_url_action") {
                    LibraryActionPill(
                        icon = Icons.Filled.Link,
                        label = "Sync URL",
                        onClick = { showImportUrlDialog = true },
                        tag = "import_url_playlist_button"
                    )
                }
            } else if (activeSegment == 4) {
                item(key = "import_audio_action") {
                    LibraryActionPill(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.lib_import),
                        onClick = onImportLocalAudio,
                        tag = "import_local_audio_button"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pin Status Bar on Playlists View (Max 4 Pins)
        if (activeSegment == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PINNED • ${pinnedItemKeys.size}/${NothingMusicRepository.MAX_PINNED_ITEMS}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        letterSpacing = 1.sp
                    ),
                    color = if (pinnedItemKeys.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "PIN UP TO 4 • DELETE ANYTIME",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        AnimatedContent(
            targetState = activeSegment,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "library_segment_fade",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { segment ->
            if (segment == 0) {
                PlaylistsLibraryView(
                    userPlaylists = userPlaylists,
                    spotifyPlaylists = spotifyPlaylists,
                    downloadedCount = downloadedTracks.size,
                    savedCount = savedTracks.size,
                    pinnedKeys = pinnedItemKeys.toSet(),
                    onTogglePinItem = onTogglePinItem,
                    onRequestDeletePlaylist = { pl -> playlistPendingDeletion = pl },
                    onJumpToDownloads = { activeSegment = 1 },
                    onJumpToSaved = { activeSegment = 2 },
                    onOpenUserPlaylist = onOpenUserPlaylist,
                    onOpenSpotifyPlaylist = onOpenSpotifyPlaylist,
                    onCreateClick = { showCreatePlaylistDialog = true },
                    onImportUrlClick = { showImportUrlDialog = true }
                )
            } else {
                val rawList = when (segment) {
                    1 -> downloadedTracks
                    2 -> savedTracks
                    3 -> recentScrobbles
                    else -> localImportedTracks
                }
                val displayedList = remember(
                    rawList,
                    segment,
                    unplayableTrackIds,
                    downloadedTrackIds,
                    hideUnplayableSongs
                ) {
                    if (!hideUnplayableSongs || segment == 1 || segment == 4) {
                        rawList
                    } else {
                        rawList.filter { track ->
                            (track.isPlayable && track.id !in unplayableTrackIds) ||
                                track.id in downloadedTrackIds ||
                                track.isDownloaded
                        }
                    }
                }

                if (displayedList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = if (segment == 1) Icons.Filled.DownloadDone else Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (segment) {
                                    1 -> stringResource(R.string.lib_empty_downloads)
                                    2 -> stringResource(R.string.lib_empty_saved)
                                    3 -> stringResource(R.string.lib_empty_recent)
                                    else -> stringResource(R.string.lib_empty_device)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_tracks_list"),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 192.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = displayedList,
                            key = { "${segment}_${it.id}" },
                            contentType = { "track_row" }
                        ) { track ->
                            val isDownloaded = track.id in downloadedTrackIds || track.isDownloaded
                            val isUnplayable = (!track.isPlayable || track.id in unplayableTrackIds) &&
                                !isDownloaded && segment != 4
                            NothingTrackRow(
                                track = track,
                                isCurrentTrack = track.id == currentTrackId,
                                isPlaying = isPlaying && track.id == currentTrackId,
                                isSaved = track.id in savedIds,
                                isDownloaded = isDownloaded,
                                isUnplayable = isUnplayable,
                                downloadProgress = downloadProgress[track.id],
                                onPlayClick = { onPlayTrack(track, displayedList) },
                                onToggleSave = { onToggleSave(track) },
                                onSwipeAddToQueue = { onSwipeAddToQueue(track) },
                                onLongClick = { onLongClickTrack(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsLibraryView(
    userPlaylists: List<UserPlaylist>,
    spotifyPlaylists: List<SpotifyPlaylistSummary>,
    downloadedCount: Int,
    savedCount: Int,
    pinnedKeys: Set<String>,
    onTogglePinItem: (itemKey: String, label: String) -> Unit,
    onRequestDeletePlaylist: (UserPlaylist) -> Unit,
    onJumpToDownloads: () -> Unit,
    onJumpToSaved: () -> Unit,
    onOpenUserPlaylist: (UserPlaylist) -> Unit,
    onOpenSpotifyPlaylist: (SpotifyPlaylistSummary) -> Unit,
    onCreateClick: () -> Unit,
    onImportUrlClick: () -> Unit
) {
    val sortedSpotify = remember(spotifyPlaylists, pinnedKeys) {
        spotifyPlaylists.sortedByDescending { "sp_${it.id}" in pinnedKeys }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_playlists_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 192.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Smart Pinnable Vaults (Liked Songs & Offline Downloads)
        val smartItems = buildList {
            add(
                Triple(
                    "smart_saved",
                    "Liked Songs",
                    "$savedCount saved songs"
                )
            )
            add(
                Triple(
                    "smart_downloads",
                    "Offline Downloads",
                    "$downloadedCount in-app offline songs"
                )
            )
        }.sortedByDescending { it.first in pinnedKeys }

        items(
            items = smartItems,
            key = { it.first },
            contentType = { "smart_vault_row" }
        ) { (key, title, subtitle) ->
            val isPinned = key in pinnedKeys
            val isDownloads = key == "smart_downloads"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                    .border(
                        width = if (isPinned) 1.dp else 0.75.dp,
                        color = if (isPinned) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        if (isDownloads) onJumpToDownloads() else onJumpToSaved()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("smart_library_row_$key"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDownloads) {
                                Brush.linearGradient(listOf(Color(0xFF1A2F24), Color(0xFF0E1512)))
                            } else {
                                Brush.linearGradient(listOf(Color(0xFFD71921), Color(0xFF471217)))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDownloads) Icons.Filled.DownloadDone else Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onTogglePinItem(key, title) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pin_button_$key")
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.cd_pin_item),
                        tint = if (isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (userPlaylists.isEmpty() && sortedSpotify.isEmpty()) {
            item(key = "empty_custom_playlists_cta") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = stringResource(R.string.lib_empty_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LibraryActionPill(
                            icon = Icons.Filled.Add,
                            label = "Create Playlist",
                            onClick = onCreateClick,
                            tag = "empty_create_playlist_button"
                        )
                        LibraryActionPill(
                            icon = Icons.Filled.Link,
                            label = "Import URL",
                            onClick = onImportUrlClick,
                            tag = "empty_import_url_button"
                        )
                    }
                }
            }
        }

        if (userPlaylists.isNotEmpty()) {
            items(
                items = userPlaylists,
                key = { "upl_${it.id}" },
                contentType = { "user_playlist_row" }
            ) { pl ->
                val pinKey = "upl_${pl.id}"
                val isPinned = pl.isPinned || pinKey in pinnedKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                        .border(
                            width = if (isPinned) 1.dp else 0.75.dp,
                            color = if (isPinned) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onOpenUserPlaylist(pl) }
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                        .testTag("user_playlist_row_${pl.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingPlaylistMosaicCover(
                        artworkUrl = pl.coverArtworkUrl,
                        mosaicUrls = pl.mosaicUrls,
                        sizeDp = 54.dp,
                        cornerRadiusDp = 12.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pl.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                if (isPinned) append("PINNED • ")
                                append("${pl.trackCount} songs")
                                when {
                                    !pl.spotifyPlaylistId.isNullOrBlank() -> append(" • Spotify")
                                    !pl.youtubePlaylistId.isNullOrBlank() -> append(" • YTM")
                                    pl.description.isNotBlank() -> append(" • ${pl.description}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onTogglePinItem(pinKey, pl.name) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pin_playlist_${pl.id}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.cd_pin_item),
                            tint = if (isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onRequestDeletePlaylist(pl) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_playlist_${pl.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_playlist),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        if (sortedSpotify.isNotEmpty()) {
            item(key = "sp_lib_header") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Spotify Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(
                items = sortedSpotify,
                key = { "sp_lib_${it.id}" },
                contentType = { "spotify_playlist_row" }
            ) { sp ->
                val pinKey = "sp_${sp.id}"
                val isPinned = pinKey in pinnedKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isPinned) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onOpenSpotifyPlaylist(sp) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("lib_spotify_playlist_${sp.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingPlaylistMosaicCover(
                        artworkUrl = sp.imageUrl.orEmpty(),
                        mosaicUrls = emptyList(),
                        sizeDp = 52.dp,
                        cornerRadiusDp = 12.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sp.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${sp.trackCount} songs • ${sp.ownerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onTogglePinItem(pinKey, sp.name) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pin_spotify_${sp.id}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(R.string.cd_pin_item),
                            tint = if (isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.09f))
            .border(
                0.75.dp,
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
