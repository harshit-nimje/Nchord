package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SuggestedPlaylist
import com.example.data.model.TrackItem
import com.example.ui.NothingUiState
import com.example.ui.SearchFilter
import com.example.ui.components.FrostedGlassCapsule
import com.example.ui.components.ImportPlaylistUrlDialog
import com.example.ui.components.NothingTrackRow

private val QUICK_SEARCH_PROMPTS = listOf(
    "The Weeknd",
    "Daft Punk",
    "Tame Impala",
    "Fred again..",
    "Frank Ocean",
    "Synthwave Night"
)

@Composable
fun SearchScreen(
    uiState: NothingUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    savedIds: Set<String>,
    downloadedTrackIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onQueryChange: (String) -> Unit,
    onToggleSource: (Boolean) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onOpenPlaylist: (SuggestedPlaylist) -> Unit,
    onImportPlaylistUrl: (String) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleSave: (TrackItem) -> Unit,
    onSwipeAddToQueue: (TrackItem) -> Unit,
    onLongClickTrack: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showImportUrlDialog by remember { mutableStateOf(false) }

    if (showImportUrlDialog) {
        ImportPlaylistUrlDialog(
            onDismiss = { showImportUrlDialog = false },
            onImportUrl = onImportPlaylistUrl
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            FrostedGlassCapsule(
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_query_input")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Always-accessible Source & Content Type Filter Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "src_open") {
                SoftSearchPill(
                    label = "YTM / Master",
                    selected = !uiState.searchUseSpotify,
                    onClick = { onToggleSource(false) },
                    tag = "search_source_open_chip"
                )
            }
            item(key = "src_spotify") {
                SoftSearchPill(
                    label = if (uiState.spotifyProfile.isConnected) "Spotify • Synced" else "Spotify",
                    selected = uiState.searchUseSpotify,
                    onClick = { onToggleSource(true) },
                    tag = "search_source_spotify_chip"
                )
            }
            item(key = "divider_filter") {
                VerticalDivider(
                    modifier = Modifier
                        .height(18.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            }
            items(SearchFilter.entries, key = { it.name }) { filter ->
                SoftSearchPill(
                    label = filter.label,
                    selected = uiState.searchFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    tag = "search_filter_${filter.name}"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val showSongs = uiState.searchFilter != SearchFilter.PLAYLISTS
        val showPlaylists = uiState.searchFilter != SearchFilter.SONGS
        val hasDisplayedResults =
            (showSongs && uiState.searchResults.isNotEmpty()) ||
                (showPlaylists && uiState.searchPlaylistsResults.isNotEmpty())

        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            uiState.searchQuery.trim().length < 2 -> {
                SearchIdleDiscoveryView(
                    onPickPrompt = onQueryChange,
                    onOpenImportUrl = { showImportUrlDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            uiState.searchError != null && !hasDisplayedResults -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.searchError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            !hasDisplayedResults -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results in ${uiState.searchFilter.label.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("search_results_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 192.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!uiState.searchNotice.isNullOrBlank()) {
                        item(key = "search_notice") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = uiState.searchNotice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Playlists Results Section
                    if (showPlaylists && uiState.searchPlaylistsResults.isNotEmpty()) {
                        item(key = "playlists_header") {
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                            )
                        }

                        if (uiState.searchFilter == SearchFilter.PLAYLISTS) {
                            itemsIndexed(
                                items = uiState.searchPlaylistsResults,
                                key = { idx, pl -> "pl_row_${pl.id}_$idx" }
                            ) { _, playlist ->
                                SearchPlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist) },
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        } else {
                            item(key = "playlists_shelf") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 22.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = uiState.searchPlaylistsResults,
                                        key = { idx, pl -> "pl_card_${pl.id}_$idx" }
                                    ) { _, playlist ->
                                        SearchPlaylistCard(
                                            playlist = playlist,
                                            onClick = { onOpenPlaylist(playlist) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Songs Results Section
                    if (showSongs && uiState.searchResults.isNotEmpty()) {
                        if (uiState.searchFilter == SearchFilter.ALL && uiState.searchPlaylistsResults.isNotEmpty()) {
                            item(key = "songs_header") {
                                Text(
                                    text = "Songs",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                                )
                            }
                        }

                        itemsIndexed(
                            items = uiState.searchResults,
                            key = { idx, t -> "song_${t.id}_$idx" }
                        ) { _, track ->
                            NothingTrackRow(
                                track = track,
                                isCurrentTrack = track.id == currentTrackId,
                                isPlaying = isPlaying && track.id == currentTrackId,
                                isSaved = track.id in savedIds,
                                isDownloaded = track.id in downloadedTrackIds || track.isDownloaded,
                                downloadProgress = downloadProgress[track.id],
                                onPlayClick = { onPlayTrack(track, uiState.searchResults) },
                                onToggleSave = { onToggleSave(track) },
                                onSwipeAddToQueue = { onSwipeAddToQueue(track) },
                                onLongClick = { onLongClickTrack(track) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchIdleDiscoveryView(
    onPickPrompt: (String) -> Unit,
    onOpenImportUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                .border(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(onClick = onOpenImportUrl)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("search_import_url_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import Spotify or YouTube Music URL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Sync any public playlist link for full ad-free playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Try searching",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            QUICK_SEARCH_PROMPTS.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { term ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                .clickable { onPickPrompt(term) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = term,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: SuggestedPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("search_playlist_card_${playlist.id}")
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchPlaylistRow(
    playlist: SuggestedPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.badge} • ${playlist.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SoftSearchPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
        },
        animationSpec = tween(200),
        label = "search_pill_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "search_pill_fg"
    )

    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = fg
        )
    }
}
