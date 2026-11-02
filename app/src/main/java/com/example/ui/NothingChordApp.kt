package com.example.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.GlyphSettings
import com.example.data.model.InsightsSnapshot
import com.example.data.model.RecentGridTile
import com.example.data.model.ScrobblerConfigAndState
import com.example.data.model.TrackItem
import com.example.data.model.UserPlaylist
import com.example.player.AudioEngineState
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.FrostedGlassCapsule
import com.example.ui.components.LocalGlassBackdrop
import com.example.ui.components.LocalInsideBackdropRecording
import com.example.ui.components.NothingCleanHeader
import com.example.ui.components.NothingFeedbackPill
import com.example.ui.components.NothingTrackOptionsSheet
import com.example.ui.components.PlaylistEditorDialog
import com.example.ui.components.recordGlassBackdrop
import com.example.ui.components.rememberGlassBackdropRig
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FullScreenNothingPlayer
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NothingMiniPlayerPill
import com.example.ui.screens.NothingOnboardingScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SourcesAndGlyphScreen

private data class DockItemSpec(
    val tab: NothingTab,
    val headerRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val contentDesc: String,
    val testTag: String
)

private val DOCK_ITEMS = listOf(
    DockItemSpec(
        NothingTab.HOME,
        R.string.header_home,
        Icons.Filled.Home,
        Icons.Outlined.Home,
        "Home",
        "nav_tab_discover"
    ),
    DockItemSpec(
        NothingTab.SEARCH,
        R.string.header_search,
        Icons.Filled.Search,
        Icons.Outlined.Search,
        "Search",
        "nav_tab_search"
    ),
    DockItemSpec(
        NothingTab.LIBRARY,
        R.string.header_library,
        Icons.Filled.LibraryMusic,
        Icons.Outlined.LibraryMusic,
        "Library",
        "nav_tab_library"
    ),
    DockItemSpec(
        NothingTab.SOURCES,
        R.string.header_sources,
        Icons.Filled.GraphicEq,
        Icons.Outlined.GraphicEq,
        "Studio",
        "nav_tab_sources"
    )
)

@Composable
fun NothingChordApp(viewModel: NothingChordViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val savedTracks by viewModel.savedTracks.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedTrackIds.collectAsStateWithLifecycle()
    val recentScrobbles by viewModel.recentScrobbles.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val totalScrobbles by viewModel.totalScrobbles.collectAsStateWithLifecycle()
    val totalStreamedMs by viewModel.totalStreamedMs.collectAsStateWithLifecycle()
    val downloadedTracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val pinnedItemKeys by viewModel.pinnedItemKeys.collectAsStateWithLifecycle()
    val recentGridTiles by viewModel.homeRecentGridTiles.collectAsStateWithLifecycle()
    val scrobblerState by viewModel.scrobblerState.collectAsStateWithLifecycle()
    val insightsSnapshot by viewModel.insightsSnapshot.collectAsStateWithLifecycle()
    val glyphSettings by viewModel.glyphSettings.collectAsStateWithLifecycle()
    val isInitialPrefsLoaded by viewModel.isInitialPrefsLoaded.collectAsStateWithLifecycle()

    var editingPlaylistIdFromSheet by remember { mutableStateOf<String?>(null) }

    if (!isInitialPrefsLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val safeLaunchExternalUri: (Uri) -> Unit = { uri ->
        val launched = runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        }.isSuccess
        if (!launched) {
            viewModel.emitFeedback("No browser available on this device")
        }
    }

    if (!glyphSettings.onboardingCompleted) {
        NothingOnboardingScreen(
            settings = glyphSettings,
            onToggleYtm = { viewModel.setYtmEngineEnabled(it) },
            onSaveClientId = { viewModel.updateUserSpotifyClientId(it) },
            onConnectSpotifyNow = { clientId ->
                viewModel.launchSpotifyOAuth(clientId, safeLaunchExternalUri)
            },
            onOpenSpotifyDashboard = {
                safeLaunchExternalUri(Uri.parse("https://developer.spotify.com/dashboard"))
            },
            onFinishOnboarding = { viewModel.completeOnboarding() }
        )
        return
    }

    if (!uiState.isFullPlayerOpen &&
        !uiState.isAccountPageOpen &&
        uiState.activeCollectionPage == null &&
        uiState.activeTab != NothingTab.HOME
    ) {
        BackHandler {
            viewModel.selectTab(NothingTab.HOME)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importLocalAudioUri(uri)
        }
    }

    val openSystemEqualizer: () -> Unit = {
        val eqLaunched = runCatching {
            val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            }
            context.startActivity(eqIntent)
        }.isSuccess
        if (!eqLaunched) {
            viewModel.emitFeedback("System EQ panel unavailable on this ROM")
        }
    }

    val shareTrack: (TrackItem) -> Unit = { track ->
        val shareText = buildString {
            append("${track.title} — ${track.artist}")
            val spId = track.spotifyTrackId ?: track.externalSpotifyUri?.substringAfterLast(":")
            if (!spId.isNullOrBlank()) {
                append("\nhttps://open.spotify.com/track/$spId")
            } else if (!track.youtubeVideoId.isNullOrBlank()) {
                append("\nhttps://music.youtube.com/watch?v=${track.youtubeVideoId}")
            }
        }
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, track.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
        }
    }

    val currentTrackId by remember { derivedStateOf { audioState.currentTrack?.id } }
    val isPlaying by remember { derivedStateOf { audioState.isPlaying } }
    val hasActiveTrack by remember { derivedStateOf { audioState.currentTrack != null } }

    val previousQueueTrack = remember(uiState.queue, uiState.currentQueueIndex) {
        if (uiState.currentQueueIndex > 0) uiState.queue.getOrNull(uiState.currentQueueIndex - 1) else null
    }
    val nextQueueTrack = remember(uiState.queue, uiState.currentQueueIndex) {
        if (uiState.currentQueueIndex >= 0) uiState.queue.getOrNull(uiState.currentQueueIndex + 1) else null
    }

    val activeSpec = DOCK_ITEMS.firstOrNull { it.tab == uiState.activeTab } ?: DOCK_ITEMS.first()

    // Global Edit Playlist Dialog triggered from long-press sheet
    val editingPlaylist = editingPlaylistIdFromSheet?.let { targetId ->
        userPlaylists.firstOrNull { it.id == targetId }
    }
    if (editingPlaylist != null) {
        PlaylistEditorDialog(
            isEditMode = true,
            initialName = editingPlaylist.name,
            initialDescription = editingPlaylist.description,
            onDismiss = { editingPlaylistIdFromSheet = null },
            onConfirm = { newName, newDesc ->
                viewModel.renameUserPlaylist(editingPlaylist.id, newName, newDesc)
            },
            onDeletePlaylist = {
                viewModel.deleteUserPlaylist(editingPlaylist.id)
                if (uiState.activeCollectionPage?.userPlaylistId == editingPlaylist.id) {
                    viewModel.popCollectionPage()
                }
            }
        )
    }

    // Global Extended Long-Press Song Options Sheet (works on every screen + Full-Screen Player)
    uiState.contextMenuTrack?.let { menuTrack ->
        val ctxPid = uiState.contextMenuPlaylistId
        val activeCol = uiState.activeCollectionPage
        val idxInPlaylist = if (ctxPid != null && activeCol?.userPlaylistId == ctxPid) {
            activeCol.tracks.indexOfFirst { it.id == menuTrack.id }
        } else -1
        val totalInPlaylist = if (ctxPid != null && activeCol?.userPlaylistId == ctxPid) {
            activeCol.tracks.size
        } else 0

        NothingTrackOptionsSheet(
            track = menuTrack,
            isSaved = menuTrack.id in savedIds,
            isDownloaded = menuTrack.id in downloadedTrackIds || menuTrack.isDownloaded,
            isDownloading = downloadProgress.containsKey(menuTrack.id),
            sleepTimerRemainingMinutes = uiState.sleepTimerRemainingMinutes,
            playlistContextId = ctxPid,
            trackIndexInPlaylist = idxInPlaylist,
            playlistTotalCount = totalInPlaylist,
            onDismiss = { viewModel.dismissTrackOptions() },
            onToggleSave = { viewModel.toggleSaveTrack(menuTrack) },
            onDownloadOffline = { viewModel.downloadTrackOffline(menuTrack) },
            onRemoveOffline = { viewModel.removeOfflineDownload(menuTrack) },
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onPlayNext = { viewModel.playTrackNext(menuTrack) },
            onAddToQueue = { viewModel.addTrackToQueueEnd(menuTrack) },
            onOpenAddToPlaylist = { viewModel.openAddToPlaylistSheet(menuTrack) },
            onShareTrack = { shareTrack(menuTrack) },
            onStartTrackRadio = { viewModel.startTrackRadio(menuTrack) },
            onGoToAlbum = { viewModel.openAlbumForTrack(menuTrack) },
            onGoToArtist = {
                viewModel.openArtistPage(
                    artistName = menuTrack.artist,
                    artistIdHint = menuTrack.artistId,
                    fallbackArtwork = menuTrack.artworkUrl
                )
            },
            onMoveTrackUp = if (ctxPid != null && idxInPlaylist > 0) {
                { viewModel.moveTrackInUserPlaylist(ctxPid, idxInPlaylist, idxInPlaylist - 1) }
            } else null,
            onMoveTrackDown = if (ctxPid != null && idxInPlaylist in 0 until (totalInPlaylist - 1)) {
                { viewModel.moveTrackInUserPlaylist(ctxPid, idxInPlaylist, idxInPlaylist + 1) }
            } else null,
            onRemoveFromPlaylist = if (ctxPid != null) {
                { viewModel.removeTrackFromUserPlaylist(ctxPid, menuTrack.id) }
            } else null,
            onEditPlaylistDetails = if (ctxPid != null) {
                { editingPlaylistIdFromSheet = ctxPid }
            } else null
        )
    }

    // Global Add-to-Playlist Sheet
    uiState.addToPlaylistTargetTrack?.let { targetTrack ->
        AddToPlaylistSheet(
            track = targetTrack,
            userPlaylists = userPlaylists,
            spotifyPlaylists = uiState.spotifyPlaylists,
            onDismiss = { viewModel.dismissAddToPlaylistSheet() },
            onSelectUserPlaylist = { pl ->
                viewModel.addTrackToExistingUserPlaylist(pl, targetTrack)
            },
            onSelectSpotifyPlaylist = { sp ->
                viewModel.addTrackToConnectedSpotifyPlaylist(sp, targetTrack)
            },
            onCreateNewPlaylistWithTrack = { name, desc ->
                viewModel.createNewUserPlaylist(name, desc, initialTrack = targetTrack)
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpandedLayout = maxWidth >= 720.dp

        if (isExpandedLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    NavigationRailItem(
                        selected = uiState.isAccountPageOpen,
                        onClick = { viewModel.setAccountPageOpen(!uiState.isAccountPageOpen) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.isAccountPageOpen) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = stringResource(R.string.header_account)
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.background,
                            indicatorColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_rail_account")
                    )
                    DOCK_ITEMS.forEach { spec ->
                        val selected = !uiState.isAccountPageOpen &&
                            uiState.activeTab == spec.tab &&
                            uiState.activeCollectionPage == null
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(spec.tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) spec.activeIcon else spec.inactiveIcon,
                                    contentDescription = spec.contentDesc
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.background,
                                indicatorColor = MaterialTheme.colorScheme.onBackground,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(spec.testTag)
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 1.dp)

                ExpandedMainStage(
                    headerTitle = stringResource(activeSpec.headerRes),
                    uiState = uiState,
                    audioState = audioState,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    hasActiveTrack = hasActiveTrack,
                    recentGridTiles = recentGridTiles,
                    userPlaylists = userPlaylists,
                    downloadedTracks = downloadedTracks,
                    downloadedTrackIds = downloadedTrackIds,
                    downloadProgress = downloadProgress,
                    pinnedItemKeys = pinnedItemKeys,
                    savedTracks = savedTracks,
                    savedIds = savedIds,
                    recentScrobbles = recentScrobbles,
                    totalScrobbles = totalScrobbles,
                    totalStreamedMs = totalStreamedMs,
                    scrobblerState = scrobblerState,
                    insightsSnapshot = insightsSnapshot,
                    glyphSettings = glyphSettings,
                    viewModel = viewModel,
                    onLaunchUri = safeLaunchExternalUri,
                    onPickLocalAudio = {
                        runCatching { audioPickerLauncher.launch(arrayOf("audio/*")) }
                    },
                    onOpenSystemEqualizer = openSystemEqualizer,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            val mainBackdropRig = rememberGlassBackdropRig(blurRadius = 44.dp)
            val bgColor = MaterialTheme.colorScheme.background

            Box(modifier = Modifier.fillMaxSize()) {
                // Underlying stage recorded into dual sharp + 44dp Clamp-Blurred GraphicsLayers
                CompositionLocalProvider(
                    LocalInsideBackdropRecording provides true,
                    LocalGlassBackdrop provides null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor)
                            .recordGlassBackdrop(mainBackdropRig, bgColor)
                    ) {
                        // Static faint dot-matrix architectural depth layer (never moves on scroll or tab swipe)
                        NothingStaticDotMatrixBackdrop()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            val activeCollection = uiState.activeCollectionPage
                        if (activeCollection != null) {
                            CollectionDetailScreen(
                                page = activeCollection,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                savedIds = savedIds,
                                downloadedTrackIds = downloadedTrackIds,
                                downloadProgress = downloadProgress,
                                onBack = { viewModel.popCollectionPage() },
                                onPlayTrack = { track, list -> viewModel.playTrackFromContext(track, list) },
                                onToggleSave = { viewModel.toggleSaveTrack(it) },
                                onSwipeAddToQueue = { viewModel.addTrackToQueueEnd(it) },
                                onLongClickTrack = { track, pid -> viewModel.openTrackOptions(track, pid) },
                                onDownloadAll = {
                                    viewModel.downloadCollectionOffline(activeCollection.tracks, activeCollection.title)
                                },
                                onSaveCollectionToLibrary = { viewModel.saveCollectionToLibrary(it) },
                                onOpenRelatedPlaylist = { viewModel.openCarouselPlaylist(it) },
                                onOpenArtist = { artist -> viewModel.openArtistPage(artist) },
                                onRenameUserPlaylist = { pid, name, desc -> viewModel.renameUserPlaylist(pid, name, desc) },
                                onDeleteUserPlaylist = { pid -> viewModel.deleteUserPlaylist(pid) },
                                onMovePlaylistTrack = { pid, from, to -> viewModel.moveTrackInUserPlaylist(pid, from, to) },
                                onRemovePlaylistTrack = { pid, tid -> viewModel.removeTrackFromUserPlaylist(pid, tid) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            NothingCleanHeader(
                                title = stringResource(activeSpec.headerRes),
                                isPlaying = isPlaying,
                                isSpotifyLinked = uiState.spotifyProfile.isConnected,
                                isYtmEnabled = glyphSettings.ytmEngineEnabled,
                                audioQualityTier = glyphSettings.audioQualityTier,
                                spectrum = audioState.glyphSpectrum,
                                glyphReactive = glyphSettings.glyphReactiveLeds,
                                avatarUrl = uiState.spotifyProfile.avatarUrl,
                                isScrobblerLinked = scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected,
                                onAccountClick = { viewModel.setAccountPageOpen(true) },
                                onStatusClick = { viewModel.setAccountPageOpen(true) }
                            )
                            AnimatedTabContent(
                                uiState = uiState,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                recentGridTiles = recentGridTiles,
                                userPlaylists = userPlaylists,
                                downloadedTracks = downloadedTracks,
                                downloadedTrackIds = downloadedTrackIds,
                                downloadProgress = downloadProgress,
                                pinnedItemKeys = pinnedItemKeys,
                                savedTracks = savedTracks,
                                savedIds = savedIds,
                                recentScrobbles = recentScrobbles,
                                totalScrobbles = totalScrobbles,
                                totalStreamedMs = totalStreamedMs,
                                scrobblerState = scrobblerState,
                                insightsSnapshot = insightsSnapshot,
                                glyphSettings = glyphSettings,
                                viewModel = viewModel,
                                onLaunchUri = safeLaunchExternalUri,
                                onPickLocalAudio = {
                                    runCatching { audioPickerLauncher.launch(arrayOf("audio/*")) }
                                },
                                onOpenSystemEqualizer = openSystemEqualizer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        }
                    }
                }

                // Active 44dp Clamp-Blurred Pure-White Frosted Glass Floating Overlay (Dock + Mini-Player)
                CompositionLocalProvider(
                    LocalGlassBackdrop provides mainBackdropRig.toState(
                        extraTick = uiState.activeTab.ordinal * 37 +
                            uiState.collectionStack.size * 19 +
                            (if (isPlaying) (audioState.currentPositionMs / 250L).toInt() else 0)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedVisibility(
                            visible = uiState.feedbackMessage != null,
                            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
                            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NothingFeedbackPill(message = uiState.feedbackMessage.orEmpty())
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        AnimatedVisibility(
                            visible = hasActiveTrack,
                            enter = fadeIn(tween(220)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 },
                            exit = fadeOut(tween(160)) + slideOutVertically { it / 2 }
                        ) {
                            Column {
                                NothingMiniPlayerPill(
                                    audioState = audioState,
                                    glyphReactive = glyphSettings.glyphReactiveLeds,
                                    isSaved = audioState.currentTrack?.id in savedIds,
                                    previousTrack = previousQueueTrack,
                                    nextTrack = nextQueueTrack,
                                    onToggleSave = audioState.currentTrack?.let { tr -> { viewModel.toggleSaveTrack(tr) } },
                                    onExpand = { viewModel.setFullPlayerOpen(true) },
                                    onTogglePlay = { viewModel.togglePlayPause() },
                                    onSkipNext = { viewModel.skipNext() },
                                    onSkipPrevious = { viewModel.skipPrevious() }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        if (!uiState.isAccountPageOpen) {
                            FrostedGlassDock(
                                activeTab = uiState.activeTab,
                                onSelectTab = { viewModel.selectTab(it) }
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Full-Screen Account & Settings Page (triggered from top-left Account button)
        AnimatedVisibility(
            visible = uiState.isAccountPageOpen,
            enter = fadeIn(tween(180)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 10 }
            ),
            exit = fadeOut(tween(140)) + slideOutVertically(
                animationSpec = tween(180),
                targetOffsetY = { it / 12 }
            )
        ) {
            val accountBackdropRig = rememberGlassBackdropRig(blurRadius = 44.dp)
            val accountBg = MaterialTheme.colorScheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accountBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .testTag("account_settings_overlay")
            ) {
                CompositionLocalProvider(
                    LocalInsideBackdropRecording provides true,
                    LocalGlassBackdrop provides null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .recordGlassBackdrop(accountBackdropRig, accountBg)
                    ) {
                        NothingStaticDotMatrixBackdrop()
                        SourcesAndGlyphScreen(
                            uiState = uiState,
                            settings = glyphSettings,
                            scrobblerState = scrobblerState,
                            totalScrobbles = totalScrobbles,
                            totalStreamedMs = totalStreamedMs,
                            savedCount = savedTracks.size,
                            onSaveClientId = { viewModel.updateUserSpotifyClientId(it) },
                            onConnectSpotify = { clientId ->
                                viewModel.launchSpotifyOAuth(clientId, safeLaunchExternalUri)
                            },
                            onOpenSpotifyDashboard = {
                                safeLaunchExternalUri(Uri.parse("https://developer.spotify.com/dashboard"))
                            },
                            onSyncSpotify = { viewModel.syncSpotifyData() },
                            onDisconnectSpotify = { viewModel.disconnectSpotifyAccount() },
                            onConnectLastFm = { user, pass -> viewModel.connectLastFm(user, pass) },
                            onConnectListenBrainz = { user, token -> viewModel.connectListenBrainz(user, token) },
                            onSyncScrobblerNow = { viewModel.syncScrobblerNow(false) },
                            onDisconnectScrobbler = { viewModel.disconnectScrobbler() },
                            onOpenPlaylist = { viewModel.openSpotifyPlaylist(it) },
                            onOpenArtist = { artist ->
                                viewModel.setAccountPageOpen(false)
                                viewModel.openArtistPage(artist)
                            },
                            onRemoteAction = { viewModel.triggerSpotifyRemoteAction(it) },
                            onSelectAudioQuality = { viewModel.setAudioQualityTier(it) },
                            onToggleBitPerfectUsb = { viewModel.setBitPerfectUsbDac(it) },
                            onOpenSystemEqualizer = openSystemEqualizer,
                            onImportPlaylistUrl = { viewModel.importPlaylistFromUrl(it) },
                            onToggleYtmEngine = { viewModel.setYtmEngineEnabled(it) },
                            onUpdatePipedInstance = { viewModel.updatePipedInstance(it) },
                            onToggleOled = { viewModel.setOledDarkMode(it) },
                            onToggleGlyphReactive = { viewModel.setGlyphReactive(it) },
                            onToggleAutomix = { viewModel.setAutomixEnabled(it) },
                            onToggleHaptics = { viewModel.setTactileHaptics(it) },
                            onReopenOnboarding = { viewModel.reopenOnboardingGuide() },
                            onBack = { viewModel.setAccountPageOpen(false) },
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        )
                    }
                }
            }
        }

        // Full-Screen Immersive Nchord Deck
        AnimatedVisibility(
            visible = uiState.isFullPlayerOpen && hasActiveTrack,
            enter = fadeIn(tween(200)) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(160)) + slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { it }
            )
        ) {
            val activeTrack = audioState.currentTrack
            FullScreenNothingPlayer(
                uiState = uiState,
                audioState = audioState,
                isSaved = activeTrack != null && activeTrack.id in savedIds,
                savedIds = savedIds,
                downloadedTrackIds = downloadedTrackIds,
                downloadProgress = downloadProgress,
                glyphReactive = glyphSettings.glyphReactiveLeds,
                onCollapse = { viewModel.setFullPlayerOpen(false) },
                onTogglePlay = { viewModel.togglePlayPause() },
                onSkipPrev = { viewModel.skipPrevious() },
                onSkipNext = { viewModel.skipNext() },
                onSeekMs = { viewModel.seekToPosition(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleSave = { viewModel.toggleSaveTrack(it) },
                onToggleDownloadTrack = { tr ->
                    if (tr.id in downloadedTrackIds || tr.isDownloaded) {
                        viewModel.removeOfflineDownload(tr)
                    } else {
                        viewModel.downloadTrackOffline(tr)
                    }
                },
                onClearQueue = { viewModel.clearPlaybackQueue() },
                onPlayQueueItem = { qTrack ->
                    viewModel.playTrackFromContext(qTrack, uiState.queue)
                },
                onOpenTrackOptions = { track ->
                    viewModel.openTrackOptions(track)
                },
                onOpenArtist = { artistName ->
                    viewModel.openArtistPage(
                        artistName = artistName,
                        artistIdHint = activeTrack?.artistId,
                        fallbackArtwork = activeTrack?.artworkUrl.orEmpty()
                    )
                },
                onChangePlaybackSpeed = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                },
                onAdjustLyricsOffset = { deltaMs ->
                    viewModel.adjustLyricsOffset(deltaMs)
                },
                onResetLyricsOffset = {
                    viewModel.resetLyricsOffset()
                }
            )
        }
    }
}

@Composable
private fun ExpandedMainStage(
    headerTitle: String,
    uiState: NothingUiState,
    audioState: AudioEngineState,
    currentTrackId: String?,
    isPlaying: Boolean,
    hasActiveTrack: Boolean,
    recentGridTiles: List<RecentGridTile>,
    userPlaylists: List<UserPlaylist>,
    downloadedTracks: List<TrackItem>,
    downloadedTrackIds: Set<String>,
    downloadProgress: Map<String, Float>,
    pinnedItemKeys: List<String>,
    savedTracks: List<TrackItem>,
    savedIds: Set<String>,
    recentScrobbles: List<TrackItem>,
    totalScrobbles: Int,
    totalStreamedMs: Long,
    scrobblerState: ScrobblerConfigAndState,
    insightsSnapshot: InsightsSnapshot,
    glyphSettings: GlyphSettings,
    viewModel: NothingChordViewModel,
    onLaunchUri: (Uri) -> Unit,
    onPickLocalAudio: () -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedBackdropRig = rememberGlassBackdropRig(blurRadius = 44.dp)
    val bgColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier) {
        CompositionLocalProvider(
            LocalInsideBackdropRecording provides true,
            LocalGlassBackdrop provides null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .recordGlassBackdrop(expandedBackdropRig, bgColor)
            ) {
                NothingStaticDotMatrixBackdrop()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    val activeCollection = uiState.activeCollectionPage
                if (activeCollection != null) {
                    CollectionDetailScreen(
                        page = activeCollection,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        savedIds = savedIds,
                        downloadedTrackIds = downloadedTrackIds,
                        downloadProgress = downloadProgress,
                        onBack = { viewModel.popCollectionPage() },
                        onPlayTrack = { track, list -> viewModel.playTrackFromContext(track, list) },
                        onToggleSave = { viewModel.toggleSaveTrack(it) },
                        onSwipeAddToQueue = { viewModel.addTrackToQueueEnd(it) },
                        onLongClickTrack = { track, pid -> viewModel.openTrackOptions(track, pid) },
                        onDownloadAll = {
                            viewModel.downloadCollectionOffline(activeCollection.tracks, activeCollection.title)
                        },
                        onSaveCollectionToLibrary = { viewModel.saveCollectionToLibrary(it) },
                        onOpenRelatedPlaylist = { viewModel.openCarouselPlaylist(it) },
                        onOpenArtist = { artist -> viewModel.openArtistPage(artist) },
                        onRenameUserPlaylist = { pid, name, desc -> viewModel.renameUserPlaylist(pid, name, desc) },
                        onDeleteUserPlaylist = { pid -> viewModel.deleteUserPlaylist(pid) },
                        onMovePlaylistTrack = { pid, from, to -> viewModel.moveTrackInUserPlaylist(pid, from, to) },
                        onRemovePlaylistTrack = { pid, tid -> viewModel.removeTrackFromUserPlaylist(pid, tid) },
                        modifier = Modifier
                            .widthIn(max = 860.dp)
                            .align(Alignment.CenterHorizontally)
                            .fillMaxSize()
                    )
                } else {
                    NothingCleanHeader(
                        title = headerTitle,
                        isPlaying = isPlaying,
                        isSpotifyLinked = uiState.spotifyProfile.isConnected,
                        isYtmEnabled = glyphSettings.ytmEngineEnabled,
                        audioQualityTier = glyphSettings.audioQualityTier,
                        spectrum = audioState.glyphSpectrum,
                        glyphReactive = glyphSettings.glyphReactiveLeds,
                        avatarUrl = uiState.spotifyProfile.avatarUrl,
                        isScrobblerLinked = scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected,
                        onAccountClick = { viewModel.setAccountPageOpen(true) },
                        onStatusClick = { viewModel.setAccountPageOpen(true) }
                    )
                    AnimatedTabContent(
                        uiState = uiState,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        recentGridTiles = recentGridTiles,
                        userPlaylists = userPlaylists,
                        downloadedTracks = downloadedTracks,
                        downloadedTrackIds = downloadedTrackIds,
                        downloadProgress = downloadProgress,
                        pinnedItemKeys = pinnedItemKeys,
                        savedTracks = savedTracks,
                        savedIds = savedIds,
                        recentScrobbles = recentScrobbles,
                        totalScrobbles = totalScrobbles,
                        totalStreamedMs = totalStreamedMs,
                        scrobblerState = scrobblerState,
                        insightsSnapshot = insightsSnapshot,
                        glyphSettings = glyphSettings,
                        viewModel = viewModel,
                        onLaunchUri = onLaunchUri,
                        onPickLocalAudio = onPickLocalAudio,
                        onOpenSystemEqualizer = onOpenSystemEqualizer,
                        modifier = Modifier
                            .widthIn(max = 860.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                }
            }
        }

        CompositionLocalProvider(
            LocalGlassBackdrop provides expandedBackdropRig.toState(
                extraTick = uiState.activeTab.ordinal * 37 +
                    uiState.collectionStack.size * 19 +
                    (if (isPlaying) (audioState.currentPositionMs / 250L).toInt() else 0)
            )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = uiState.feedbackMessage != null,
                    enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingFeedbackPill(message = uiState.feedbackMessage.orEmpty())
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                AnimatedVisibility(
                    visible = hasActiveTrack,
                    enter = fadeIn(tween(220)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 },
                    exit = fadeOut(tween(160)) + slideOutVertically { it / 2 }
                ) {
                    val expPrevTrack = if (uiState.currentQueueIndex > 0) {
                        uiState.queue.getOrNull(uiState.currentQueueIndex - 1)
                    } else null
                    val expNextTrack = if (uiState.currentQueueIndex >= 0) {
                        uiState.queue.getOrNull(uiState.currentQueueIndex + 1)
                    } else null
                    NothingMiniPlayerPill(
                        audioState = audioState,
                        glyphReactive = glyphSettings.glyphReactiveLeds,
                        isSaved = audioState.currentTrack?.id in savedIds,
                        previousTrack = expPrevTrack,
                        nextTrack = expNextTrack,
                        onToggleSave = audioState.currentTrack?.let { tr -> { viewModel.toggleSaveTrack(tr) } },
                        onExpand = { viewModel.setFullPlayerOpen(true) },
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onSkipNext = { viewModel.skipNext() },
                        onSkipPrevious = { viewModel.skipPrevious() }
                    )
                }
            }
        }
    }
}

/**
 * Pure-White Native Translucent Heavy-Blur Floating Dock:
 * - Uses [FrostedGlassCapsule] (heavy 44dp Clamp-blurred backdrop + pure white translucency + subtle micrograin).
 * - Active tab glides inside a crisp pure-white circular pill with high-contrast dark icon.
 */
@Composable
private fun FrostedGlassDock(
    activeTab: NothingTab,
    onSelectTab: (NothingTab) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val activeIndex = DOCK_ITEMS.indexOfFirst { it.tab == activeTab }.coerceAtLeast(0)
    val itemSize = 48.dp
    val itemSpacing = 4.dp

    val pillOffsetX by animateDpAsState(
        targetValue = (itemSize + itemSpacing) * activeIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dock_pill_glide"
    )

    val activePillColor = if (isDark) Color.White else Color(0xFF0A0A0C)
    val activeIconColor = if (isDark) Color(0xFF09090B) else Color.White

    FrostedGlassCapsule(
        shape = CircleShape,
        modifier = Modifier.testTag("nothing_floating_dock")
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Box(
                modifier = Modifier
                    .offset(x = pillOffsetX)
                    .size(itemSize)
                    .clip(CircleShape)
                    .background(activePillColor)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DOCK_ITEMS.forEach { spec ->
                    val selected = activeTab == spec.tab
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) {
                            activeIconColor
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                        },
                        animationSpec = tween(180),
                        label = "dock_icon_tint"
                    )

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(itemSize)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onSelectTab(spec.tab) }
                            )
                            .testTag(spec.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) spec.activeIcon else spec.inactiveIcon,
                            contentDescription = spec.contentDesc,
                            tint = iconTint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedTabContent(
    uiState: NothingUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    recentGridTiles: List<RecentGridTile>,
    userPlaylists: List<UserPlaylist>,
    downloadedTracks: List<TrackItem>,
    downloadedTrackIds: Set<String>,
    downloadProgress: Map<String, Float>,
    pinnedItemKeys: List<String>,
    savedTracks: List<TrackItem>,
    savedIds: Set<String>,
    recentScrobbles: List<TrackItem>,
    totalScrobbles: Int,
    totalStreamedMs: Long,
    scrobblerState: ScrobblerConfigAndState,
    insightsSnapshot: InsightsSnapshot,
    glyphSettings: GlyphSettings,
    viewModel: NothingChordViewModel,
    onLaunchUri: (Uri) -> Unit,
    onPickLocalAudio: () -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = uiState.activeTab,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 32 }) togetherWith
                fadeOut(tween(140))
        },
        label = "tab_page_transition",
        modifier = modifier
    ) { tab ->
        when (tab) {
            NothingTab.HOME -> DiscoverScreen(
                uiState = uiState,
                recentGridTiles = recentGridTiles,
                downloadedTrackIds = downloadedTrackIds,
                downloadProgress = downloadProgress,
                offlineTracks = downloadedTracks,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                savedIds = savedIds,
                onSelectHomeFilter = { viewModel.setHomeFeedFilter(it) },
                onSelectMood = { viewModel.selectMoodChannel(it) },
                onOpenRecentTile = { viewModel.openRecentGridTile(it) },
                onOpenCarouselPlaylist = { viewModel.openCarouselPlaylist(it) },
                onPlayCarouselPlaylist = { pl, shuffle ->
                    viewModel.playCarouselPlaylistImmediately(pl, shuffle)
                },
                onOpenAlbumForTrack = { viewModel.openAlbumForTrack(it) },
                onPlayTrack = { track, list -> viewModel.playTrackFromContext(track, list) },
                onToggleSave = { viewModel.toggleSaveTrack(it) },
                onSwipeAddToQueue = { viewModel.addTrackToQueueEnd(it) },
                onLongClickTrack = { viewModel.openTrackOptions(it) },
                onRetryLoad = { viewModel.loadInitialDiscoverFeeds() },
                modifier = Modifier.fillMaxSize()
            )

            NothingTab.SEARCH -> SearchScreen(
                uiState = uiState,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                savedIds = savedIds,
                downloadedTrackIds = downloadedTrackIds,
                downloadProgress = downloadProgress,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onToggleSource = { viewModel.toggleSearchSource(it) },
                onSelectFilter = { viewModel.setSearchFilter(it) },
                onOpenPlaylist = { viewModel.openCarouselPlaylist(it) },
                onImportPlaylistUrl = { viewModel.importPlaylistFromUrl(it) },
                onPlayTrack = { track, list -> viewModel.playTrackFromContext(track, list) },
                onToggleSave = { viewModel.toggleSaveTrack(it) },
                onSwipeAddToQueue = { viewModel.addTrackToQueueEnd(it) },
                onLongClickTrack = { viewModel.openTrackOptions(it) },
                modifier = Modifier.fillMaxSize()
            )

            NothingTab.LIBRARY -> LibraryScreen(
                userPlaylists = userPlaylists,
                spotifyPlaylists = uiState.spotifyPlaylists,
                downloadedTracks = downloadedTracks,
                downloadedTrackIds = downloadedTrackIds,
                downloadProgress = downloadProgress,
                pinnedItemKeys = pinnedItemKeys,
                savedTracks = savedTracks,
                recentScrobbles = recentScrobbles,
                localImportedTracks = uiState.localImportedTracks,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                savedIds = savedIds,
                onTogglePinItem = { key, label -> viewModel.togglePinLibraryItem(key, label) },
                onDeleteUserPlaylist = { viewModel.deleteUserPlaylist(it.id) },
                onOpenUserPlaylist = { viewModel.openUserPlaylist(it) },
                onOpenSpotifyPlaylist = { viewModel.openSpotifyPlaylist(it) },
                onCreatePlaylist = { name, desc -> viewModel.createNewUserPlaylist(name, desc) },
                onImportPlaylistUrl = { viewModel.importPlaylistFromUrl(it) },
                onImportLocalAudio = onPickLocalAudio,
                onPlayTrack = { track, list -> viewModel.playTrackFromContext(track, list) },
                onToggleSave = { viewModel.toggleSaveTrack(it) },
                onSwipeAddToQueue = { viewModel.addTrackToQueueEnd(it) },
                onLongClickTrack = { viewModel.openTrackOptions(it) },
                modifier = Modifier.fillMaxSize()
            )

            // Studio Tab: Pure Stats, Data & Taste Profiling (Nothing OS Weather bento style)
            NothingTab.SOURCES -> InsightsScreen(
                snapshot = insightsSnapshot,
                scrobblerState = scrobblerState,
                spotifyProfile = uiState.spotifyProfile,
                isPlaying = isPlaying,
                onOpenArtist = { viewModel.openArtistPage(it) },
                onConnectLastFm = { user, pass -> viewModel.connectLastFm(user, pass) },
                onConnectListenBrainz = { user, token -> viewModel.connectListenBrainz(user, token) },
                onSyncScrobblerNow = { viewModel.syncScrobblerNow(false) },
                onDisconnectScrobbler = { viewModel.disconnectScrobbler() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Static faint dot-matrix architectural backdrop sitting directly above the theme surface.
 * Fixed in screen space — never scrolls or shifts during list scrolling or tab transitions.
 */
@Composable
fun NothingStaticDotMatrixBackdrop(modifier: Modifier = Modifier) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val dotColor = onBg.copy(alpha = if (isDark) 0.088f else 0.072f)
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val spacingPx = 18.dp.toPx().coerceAtLeast(8f)
        val radiusPx = 1.05.dp.toPx()
        val cols = (size.width / spacingPx).toInt() + 1
        val rows = (size.height / spacingPx).toInt() + 1
        val offsetX = (size.width - (cols - 1) * spacingPx) * 0.5f
        val offsetY = (size.height - (rows - 1) * spacingPx) * 0.5f

        for (r in 0 until rows) {
            val y = offsetY + r * spacingPx
            for (c in 0 until cols) {
                val x = offsetX + c * spacingPx
                drawCircle(
                    color = dotColor,
                    radius = radiusPx,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
    }
}
