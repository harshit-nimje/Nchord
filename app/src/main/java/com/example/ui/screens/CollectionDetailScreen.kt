package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CollectionPageState
import com.example.data.model.CollectionPageType
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.ui.components.NothingPlaylistMosaicCover
import com.example.ui.components.NothingTrackRow
import com.example.ui.components.PlaylistEditorDialog

/**
 * Unified Nothing OS Detail Screen for User Playlists, Curated/Spotify/YTM Playlists, Albums, and Artists.
 */
@Composable
fun CollectionDetailScreen(
    page: CollectionPageState,
    currentTrackId: String?,
    isPlaying: Boolean,
    savedIds: Set<String>,
    downloadedTrackIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onBack: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleSave: (TrackItem) -> Unit,
    onSwipeAddToQueue: (TrackItem) -> Unit,
    onLongClickTrack: (TrackItem, String?) -> Unit,
    onDownloadAll: (() -> Unit)? = null,
    onSaveCollectionToLibrary: (CollectionPageState) -> Unit,
    onOpenRelatedPlaylist: (SuggestedPlaylist) -> Unit,
    onOpenArtist: (String) -> Unit,
    onRenameUserPlaylist: (playlistId: String, name: String, desc: String) -> Unit,
    onDeleteUserPlaylist: (playlistId: String) -> Unit,
    onMovePlaylistTrack: (playlistId: String, fromIdx: Int, toIdx: Int) -> Unit,
    onRemovePlaylistTrack: (playlistId: String, trackId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val isUserPlaylist = page.type == CollectionPageType.USER_PLAYLIST && page.userPlaylistId != null
    val isArtist = page.type == CollectionPageType.ARTIST
    var isReorderMode by remember(page.id) { mutableStateOf(false) }
    var showMetadataEditor by remember { mutableStateOf(false) }

    if (showMetadataEditor && isUserPlaylist) {
        val pid = page.userPlaylistId!!
        PlaylistEditorDialog(
            isEditMode = true,
            initialName = page.title,
            initialDescription = page.subtitle,
            onDismiss = { showMetadataEditor = false },
            onConfirm = { newName, newDesc ->
                onRenameUserPlaylist(pid, newName, newDesc)
            },
            onDeletePlaylist = {
                onDeleteUserPlaylist(pid)
                onBack()
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("collection_detail_screen"),
        contentPadding = PaddingValues(bottom = 192.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "collection_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("collection_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (isUserPlaylist) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(
                                        if (isReorderMode) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .clickable { isReorderMode = !isReorderMode }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("toggle_playlist_reorder_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isReorderMode) Icons.Filled.Check else Icons.Filled.Edit,
                                        contentDescription = null,
                                        tint = if (isReorderMode) {
                                            MaterialTheme.colorScheme.background
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isReorderMode) "Done" else "Reorder",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isReorderMode) {
                                            MaterialTheme.colorScheme.background
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { showMetadataEditor = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("edit_playlist_info_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Edit Info",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    } else if (!isArtist && page.tracks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onSaveCollectionToLibrary(page) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("save_collection_to_library_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save Playlist",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isArtist) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.2.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (page.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = page.artworkUrl,
                                    contentDescription = page.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                    } else {
                        NothingPlaylistMosaicCover(
                            artworkUrl = page.artworkUrl,
                            mosaicUrls = page.mosaicUrls,
                            sizeDp = 114.dp,
                            cornerRadiusDp = 22.dp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = page.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val clickableArtist = page.type == CollectionPageType.ALBUM &&
                            !page.artistNameForNav.isNullOrBlank()
                        Text(
                            text = page.subtitle.ifBlank { "${page.tracks.size} songs" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (clickableArtist) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (clickableArtist) {
                                Modifier.clickable { onOpenArtist(page.artistNameForNav!!) }
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (page.tracks.isNotEmpty()) {
                                onPlayTrack(page.tracks.first(), page.tracks)
                            }
                        },
                        enabled = page.tracks.isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("collection_play_all_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            if (page.tracks.isNotEmpty()) {
                                val shuffled = page.tracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        enabled = page.tracks.isNotEmpty(),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("collection_shuffle_button")
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                    }

                    if (onDownloadAll != null && page.tracks.isNotEmpty()) {
                        val allDownloaded = page.tracks.all { it.id in downloadedTrackIds }
                        val anyDownloading = page.tracks.any { downloadProgress.containsKey(it.id) }
                        IconButton(
                            onClick = onDownloadAll,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(
                                    if (allDownloaded) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .testTag("collection_download_all_button")
                        ) {
                            if (anyDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (allDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                    contentDescription = if (allDownloaded) "Downloaded in app" else "Download collection offline",
                                    tint = if (allDownloaded) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (page.isLoading && page.tracks.isEmpty()) {
            item(key = "collection_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else if (page.tracks.isEmpty()) {
            item(key = "collection_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No songs in this playlist yet. Long-press any song across Nchord to add it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = page.tracks,
                key = { idx, track -> "${page.id}_${idx}_${track.id}" }
            ) { idx, track ->
                if (isUserPlaylist && isReorderMode) {
                    val pid = page.userPlaylistId!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onMovePlaylistTrack(pid, idx, idx - 1) },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMovePlaylistTrack(pid, idx, idx + 1) },
                            enabled = idx < page.tracks.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(
                            onClick = { onRemovePlaylistTrack(pid, track.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Remove song",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    NothingTrackRow(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        isSaved = track.id in savedIds,
                        isDownloaded = track.id in downloadedTrackIds || track.isDownloaded,
                        downloadProgress = downloadProgress[track.id],
                        onPlayClick = { onPlayTrack(track, page.tracks) },
                        onToggleSave = { onToggleSave(track) },
                        onLongClick = { onLongClickTrack(track, page.userPlaylistId) },
                        onSwipeAddToQueue = { onSwipeAddToQueue(track) },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }

        if (page.relatedPlaylists.isNotEmpty()) {
            item(key = "related_playlists_section") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Artist Mixes & Playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(page.relatedPlaylists, key = { it.id }) { rel ->
                        Column(
                            modifier = Modifier
                                .width(146.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onOpenRelatedPlaylist(rel) }
                                .padding(bottom = 12.dp)
                        ) {
                            NothingPlaylistMosaicCover(
                                artworkUrl = rel.artworkUrl,
                                mosaicUrls = emptyList(),
                                sizeDp = 146.dp,
                                cornerRadiusDp = 18.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rel.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            Text(
                                text = rel.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
