package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist

/**
 * Extended Long-Press Song Options Bottom Sheet:
 * Play Next, Add to Queue, Add to Playlist, Share, Start Track Radio, Go to Album, Go to Artist,
 * plus Playlist Reorder / Remove / Edit when opened inside a User Playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingTrackOptionsSheet(
    track: TrackItem,
    isSaved: Boolean,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    sleepTimerRemainingMinutes: Int? = null,
    playlistContextId: String? = null,
    trackIndexInPlaylist: Int = -1,
    playlistTotalCount: Int = 0,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onDownloadOffline: (() -> Unit)? = null,
    onRemoveOffline: (() -> Unit)? = null,
    onSetSleepTimer: ((Int?) -> Unit)? = null,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onShareTrack: () -> Unit,
    onStartTrackRadio: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onMoveTrackUp: (() -> Unit)? = null,
    onMoveTrackDown: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onEditPlaylistDetails: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121317),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("track_options_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            // Song Summary Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(track.artist)
                            if (track.album.isNotBlank()) {
                                append(" • ")
                                append(track.album)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sheet_like_button")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Save song",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4 Quick Action Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickSheetActionPill(
                    icon = Icons.Filled.QueuePlayNext,
                    label = "Play Next",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    },
                    tag = "sheet_action_play_next",
                    modifier = Modifier.weight(1f)
                )
                QuickSheetActionPill(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "+ Queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    },
                    tag = "sheet_action_add_queue",
                    modifier = Modifier.weight(1f)
                )
                QuickSheetActionPill(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Playlist",
                    onClick = {
                        onDismiss()
                        onOpenAddToPlaylist()
                    },
                    tag = "sheet_action_add_playlist",
                    modifier = Modifier.weight(1f)
                )
                QuickSheetActionPill(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = {
                        onShareTrack()
                        onDismiss()
                    },
                    tag = "sheet_action_share",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Spacer(modifier = Modifier.height(6.dp))

            if (onDownloadOffline != null || onRemoveOffline != null) {
                SheetOptionRow(
                    icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    title = when {
                        isDownloading -> "Downloading to In-App Vault…"
                        isDownloaded -> "Remove Offline Download"
                        else -> "Download for Offline Listening"
                    },
                    subtitle = if (isDownloaded) {
                        "Saved in private Nchord Vault • Plays track to Insights & Spotify"
                    } else {
                        "Save inside app for zero-lag offline playback"
                    },
                    onClick = {
                        if (isDownloaded) {
                            onRemoveOffline?.invoke()
                        } else if (!isDownloading) {
                            onDownloadOffline?.invoke()
                        }
                        onDismiss()
                    },
                    tag = "sheet_row_download_offline"
                )
            }

            SheetOptionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to Playlist",
                subtitle = "Save to your Nchord or synced Spotify playlist",
                onClick = {
                    onDismiss()
                    onOpenAddToPlaylist()
                },
                tag = "sheet_row_add_to_playlist"
            )

            SheetOptionRow(
                icon = Icons.Filled.Radio,
                title = "Start Track Radio",
                subtitle = "Continuous mix inspired by ${track.title}",
                onClick = {
                    onStartTrackRadio()
                    onDismiss()
                },
                tag = "sheet_row_track_radio"
            )

            SheetOptionRow(
                icon = Icons.Filled.Album,
                title = "Go to Album",
                subtitle = track.album.ifBlank { "View release tracks" },
                onClick = {
                    onDismiss()
                    onGoToAlbum()
                },
                tag = "sheet_row_go_to_album"
            )

            SheetOptionRow(
                icon = Icons.Filled.Person,
                title = "Go to Artist",
                subtitle = track.artist,
                onClick = {
                    onDismiss()
                    onGoToArtist()
                },
                tag = "sheet_row_go_to_artist"
            )

            if (onSetSleepTimer != null) {
                val nextTimer = when (sleepTimerRemainingMinutes) {
                    null -> 15
                    in 1..15 -> 30
                    in 16..30 -> 45
                    in 31..60 -> -1
                    else -> null
                }
                val currentTimerLabel = when (sleepTimerRemainingMinutes) {
                    null -> "Off • Tap to set 15m"
                    -1 -> "Active: End of Track • Tap to turn off"
                    else -> "Active: ${sleepTimerRemainingMinutes}m left • Tap to cycle"
                }
                SheetOptionRow(
                    icon = Icons.Filled.Bedtime,
                    title = "Sleep Timer",
                    subtitle = currentTimerLabel,
                    onClick = {
                        onSetSleepTimer(nextTimer)
                    },
                    tag = "sheet_row_sleep_timer"
                )
            }

            if (playlistContextId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Spacer(modifier = Modifier.height(6.dp))

                if (onMoveTrackUp != null && trackIndexInPlaylist > 0) {
                    SheetOptionRow(
                        icon = Icons.Filled.ArrowUpward,
                        title = "Move Up in Playlist",
                        subtitle = null,
                        onClick = {
                            onMoveTrackUp()
                            onDismiss()
                        },
                        tag = "sheet_row_move_up"
                    )
                }

                if (onMoveTrackDown != null && trackIndexInPlaylist in 0 until (playlistTotalCount - 1)) {
                    SheetOptionRow(
                        icon = Icons.Filled.ArrowDownward,
                        title = "Move Down in Playlist",
                        subtitle = null,
                        onClick = {
                            onMoveTrackDown()
                            onDismiss()
                        },
                        tag = "sheet_row_move_down"
                    )
                }

                if (onEditPlaylistDetails != null) {
                    SheetOptionRow(
                        icon = Icons.Filled.Edit,
                        title = "Edit Playlist Details",
                        subtitle = "Rename or update description",
                        onClick = {
                            onDismiss()
                            onEditPlaylistDetails()
                        },
                        tag = "sheet_row_edit_playlist"
                    )
                }

                if (onRemoveFromPlaylist != null) {
                    SheetOptionRow(
                        icon = Icons.Filled.DeleteOutline,
                        title = "Remove from Playlist",
                        subtitle = null,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onRemoveFromPlaylist()
                            onDismiss()
                        },
                        tag = "sheet_row_remove_from_playlist"
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSheetActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    tag: String,
    tint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.88f),
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tint
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Add Song to Playlist Bottom Sheet with inline New Playlist creator & Spotify Playlist sync targets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: TrackItem,
    userPlaylists: List<UserPlaylist>,
    spotifyPlaylists: List<SpotifyPlaylistSummary>,
    onDismiss: () -> Unit,
    onSelectUserPlaylist: (UserPlaylist) -> Unit,
    onSelectSpotifyPlaylist: (SpotifyPlaylistSummary) -> Unit,
    onCreateNewPlaylistWithTrack: (name: String, description: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showInlineCreator by remember { mutableStateOf(userPlaylists.isEmpty()) }
    var draftName by remember { mutableStateOf("") }
    var draftDesc by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121317),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("add_to_playlist_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "${track.title} • ${track.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { showInlineCreator = !showInlineCreator }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("toggle_inline_playlist_creator"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                }
            }

            if (showInlineCreator) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        singleLine = true,
                        placeholder = { Text("Playlist name (e.g. Night Drive)") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inline_new_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = draftDesc,
                        onValueChange = { draftDesc = it },
                        singleLine = true,
                        placeholder = { Text("Optional vibe / description") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val title = draftName.trim().ifBlank { "${track.artist} Mix" }
                            onCreateNewPlaylistWithTrack(title, draftDesc.trim())
                            onDismiss()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_create_playlist_with_track")
                    ) {
                        Text("Create & Add Song", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userPlaylists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable {
                                onSelectUserPlaylist(pl)
                                onDismiss()
                            }
                            .padding(10.dp)
                            .testTag("pick_user_playlist_${pl.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NothingPlaylistMosaicCover(
                            artworkUrl = pl.coverArtworkUrl,
                            mosaicUrls = pl.mosaicUrls,
                            sizeDp = 46.dp,
                            cornerRadiusDp = 12.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("${pl.trackCount} songs")
                                    if (!pl.spotifyPlaylistId.isNullOrBlank()) append(" • Spotify Sync")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                if (spotifyPlaylists.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connected Spotify Playlists",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    items(spotifyPlaylists, key = { "sp_${it.id}" }) { sp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    onSelectSpotifyPlaylist(sp)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NothingPlaylistMosaicCover(
                                artworkUrl = sp.imageUrl.orEmpty(),
                                mosaicUrls = emptyList(),
                                sizeDp = 44.dp,
                                cornerRadiusDp = 11.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sp.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Spotify • ${sp.trackCount} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Sync to Spotify playlist",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for Creating or Editing a User Playlist with crisp Nothing OS RoundedCornerShape(28.dp) card styling.
 */
@Composable
fun PlaylistEditorDialog(
    isEditMode: Boolean,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    onDeletePlaylist: (() -> Unit)? = null
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121318))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("playlist_editor_dialog"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditMode) "Edit Playlist" else "New Playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Title") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_dialog_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    singleLine = true,
                    label = { Text("Description (optional)") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isEditMode && onDeletePlaylist != null) {
                    TextButton(
                        onClick = {
                            onDeletePlaylist()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("delete_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete Playlist",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.68f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            onConfirm(name.trim(), description.trim())
                            onDismiss()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("playlist_dialog_save_button")
                ) {
                    Text(if (isEditMode) "Save" else "Create")
                }
            }
        }
    }
}

/**
 * Dialog for Importing a Public YouTube Music or Spotify Playlist URL.
 * Uses an architectural RoundedCornerShape(28.dp) card (never CircleShape!) with 1-tap clipboard paste.
 */
@Composable
fun ImportPlaylistUrlDialog(
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121318))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("import_playlist_dialog_card"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Import Playlist",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "SPOTIFY • YOUTUBE MUSIC",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.09f))
                        .clickable {
                            val clipText = clipboard.getText()?.text?.trim().orEmpty()
                            if (clipText.isNotBlank()) {
                                urlInput = clipText
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("paste_playlist_url_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Paste link",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = "Paste any public Spotify (open.spotify.com/playlist/…) or YouTube Music (music.youtube.com/playlist?list=…) link to sync its full tracklist into your Library.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f)
            )

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "https://open.spotify.com/playlist/...",
                        color = Color.White.copy(alpha = 0.36f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_playlist_url_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.68f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (urlInput.trim().isNotEmpty()) {
                            onImportUrl(urlInput.trim())
                            onDismiss()
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("confirm_import_playlist_url")
                ) {
                    Text("Sync Playlist")
                }
            }
        }
    }
}

