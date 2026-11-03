package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AudioQualityTier
import com.example.data.model.DisplayRefreshPolicy
import com.example.data.model.GlyphSettings
import com.example.data.model.ScrobblerConfigAndState
import com.example.data.model.SpotifyPlaylistSummary
import com.example.data.remote.NetworkModule
import com.example.ui.NothingUiState
import com.example.ui.components.FrostedGlassCapsule
import com.example.ui.components.ImportPlaylistUrlDialog
import com.example.ui.components.LocalPeakDisplayRefreshHz
import com.example.ui.theme.MonoFontFamily

@Composable
@Suppress("UNUSED_PARAMETER")
fun SourcesAndGlyphScreen(
    uiState: NothingUiState,
    settings: GlyphSettings,
    scrobblerState: ScrobblerConfigAndState = ScrobblerConfigAndState(),
    totalScrobbles: Int,
    totalStreamedMs: Long,
    savedCount: Int,
    onSaveClientId: (String) -> Unit,
    onConnectSpotify: (String) -> Unit,
    onOpenSpotifyDashboard: () -> Unit,
    onSyncSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onConnectLastFm: (String, String) -> Unit = { _, _ -> },
    onConnectListenBrainz: (String, String) -> Unit = { _, _ -> },
    onSyncScrobblerNow: () -> Unit = {},
    onDisconnectScrobbler: () -> Unit = {},
    onOpenPlaylist: (SpotifyPlaylistSummary) -> Unit = {},
    onOpenArtist: (String) -> Unit,
    onRemoteAction: (String) -> Unit,
    onSelectAudioQuality: (AudioQualityTier) -> Unit,
    onToggleBitPerfectUsb: (Boolean) -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    onImportPlaylistUrl: (String) -> Unit,
    onToggleYtmEngine: (Boolean) -> Unit,
    onUpdatePipedInstance: (String) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onToggleGlyphReactive: (Boolean) -> Unit,
    onToggleAutomix: (Boolean) -> Unit,
    onToggleHideUnplayable: (Boolean) -> Unit = {},
    onSelectDisplayRefreshPolicy: (DisplayRefreshPolicy) -> Unit = {},
    isRealtimeFftActive: Boolean = false,
    onVisualizerPermissionResult: (Boolean) -> Unit = {},
    onToggleHaptics: (Boolean) -> Unit,
    onReopenOnboarding: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    var hasAudioVisualizerPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val visualizerPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioVisualizerPerm = granted
        onVisualizerPermissionResult(granted)
    }

    var clientIdInput by remember(settings.userSpotifyClientId) {
        mutableStateOf(settings.userSpotifyClientId)
    }
    var showStepsGuide by remember { mutableStateOf(settings.userSpotifyClientId.isBlank()) }
    var copiedUri by remember { mutableStateOf(false) }
    var showPipedInput by remember { mutableStateOf(false) }
    var showImportUrlDialog by remember { mutableStateOf(false) }
    var showScrobblerSheet by remember { mutableStateOf(false) }
    var pipedDraft by remember(settings.customPipedInstance) {
        mutableStateOf(settings.customPipedInstance)
    }

    if (showImportUrlDialog) {
        ImportPlaylistUrlDialog(
            onDismiss = { showImportUrlDialog = false },
            onImportUrl = onImportPlaylistUrl
        )
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

    val clipboard = LocalClipboardManager.current
    val totalMinutes = (totalStreamedMs / 60_000L).coerceAtLeast(0L)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sources_hub_list"),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 192.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (onBack != null) {
            item(key = "account_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                            .testTag("account_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.header_account),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (uiState.spotifyProfile.isConnected) {
                                "${uiState.spotifyProfile.displayName} • Connected Accounts & Settings"
                            } else {
                                "Connected Accounts, Audio Engine & App Settings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!uiState.spotifyStatusBanner.isNullOrBlank()) {
            item(key = "spotify_status_banner") {
                FrostedGlassCapsule(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.spotifyStatusBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // 1. Synced Listening Telemetry & Top Artists
        item(key = "telemetry_metrics") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EditorialMetricColumn(value = totalScrobbles.toString(), label = "PLAYS")
                    EditorialMetricColumn(value = "${totalMinutes}m", label = "LISTENED")
                    EditorialMetricColumn(value = savedCount.toString(), label = "SAVED")
                    if (uiState.spotifyProfile.isConnected) {
                        EditorialMetricColumn(
                            value = uiState.spotifyProfile.savedTracksCount.toString(),
                            label = "SPOTIFY"
                        )
                    }
                }

                if (uiState.spotifyProfile.topArtists.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TOP SYNCED ARTISTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(
                                items = uiState.spotifyProfile.topArtists,
                                key = { idx, name -> "top_artist_${idx}_$name" }
                            ) { _, artist ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                        .clickable { onOpenArtist(artist) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f))
            }
        }

        // 2. BitChord Audio Quality & Lossless LDAC Engine
        item(key = "audio_quality_engine") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioSectionTitle(
                    title = "Audio Quality Engine",
                    subtitle = "BitChord full-length stream resolution up to 24-bit/96kHz FLAC"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                        .border(
                            0.75.dp,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(vertical = 6.dp)
                ) {
                    AudioQualityTier.entries.reversed().forEachIndexed { idx, tier ->
                        val selected = settings.audioQualityTier == tier
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudioQuality(tier) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("audio_tier_${tier.name}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectAudioQuality(tier) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.onBackground,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tier.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = tier.bitrateSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tier.shortBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.background
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        if (idx < AudioQualityTier.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        }
                    }
                }

                CleanPreferenceRow(
                    title = "Bit-Perfect USB DAC Direct",
                    subtitle = "Float PCM hardware output when external USB-C DAC is attached",
                    checked = settings.bitPerfectUsbDac,
                    onCheckedChange = onToggleBitPerfectUsb,
                    tag = "switch_bit_perfect_usb"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        .clickable(onClick = onOpenSystemEqualizer)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("open_system_equalizer_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "System Hardware Equalizer",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Tune Dolby / Nothing Dirac / Bluetooth LDAC EQ curves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
        }

        // 3. Per-Client Spotify PKCE Bridge
        if (!uiState.spotifyProfile.isConnected) {
            item(key = "spotify_pkce_setup") {
                FrostedGlassCapsule(
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Spotify Client Bridge",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Per-client PKCE • Two-way Likes, Playlists & Stats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showStepsGuide = !showStepsGuide },
                                modifier = Modifier.testTag("toggle_spotify_steps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Setup steps",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = showStepsGuide) {
                            Column(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.sp_step_1),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .clickable(onClick = onOpenSpotifyDashboard)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Open",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.sp_step_2),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = NetworkModule.SPOTIFY_REDIRECT_URI,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                            .clickable {
                                                runCatching {
                                                    clipboard.setText(AnnotatedString(NetworkModule.SPOTIFY_REDIRECT_URI))
                                                }
                                                copiedUri = true
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (copiedUri) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (copiedUri) "Copied" else "Copy",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.sp_step_3),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = {
                                clientIdInput = it
                                onSaveClientId(it)
                            },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.sp_client_id_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sources_spotify_client_id_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { onConnectSpotify(clientIdInput) },
                            enabled = !uiState.isSpotifyLoading && clientIdInput.trim().length >= 10,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("spotify_connect_button")
                        ) {
                            if (uiState.isSpotifyLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.sp_save_and_connect),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "spotify_connected_card") {
                ConnectedSpotifySection(
                    uiState = uiState,
                    onSync = onSyncSpotify,
                    onDisconnect = onDisconnectSpotify,
                    onRemoteAction = onRemoteAction
                )
            }
        }

        // 3B. Last.fm & ListenBrainz Scrobbler Bridge
        item(key = "scrobbler_bridge_card") {
            val isLinked = scrobblerState.lastFmConnected || scrobblerState.listenBrainzConnected
            val activeLabel = when {
                scrobblerState.lastFmConnected -> "Last.fm • @${scrobblerState.lastFmUsername}"
                scrobblerState.listenBrainzConnected -> "ListenBrainz • @${scrobblerState.listenBrainzUsername}"
                else -> "Last.fm & ListenBrainz Scrobbler"
            }
            FrostedGlassCapsule(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeLabel,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLinked) {
                                    "${scrobblerState.remotePlayCount} remote scrobbles • ${scrobblerState.pendingOfflineScrobbles} queued offline"
                                } else {
                                    "Sync plays, offline listens & top artists with Last.fm or ListenBrainz"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isLinked) {
                            IconButton(
                                onClick = onSyncScrobblerNow,
                                modifier = Modifier.testTag("studio_scrobbler_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Sync Scrobbler",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showScrobblerSheet = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("studio_open_scrobbler_button")
                    ) {
                        Text(
                            text = if (isLinked) "Manage Scrobbler Account" else stringResource(R.string.scrobbler_connect),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 4. YouTube Music Sync & Appearance
        item(key = "ytm_and_appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                StudioSectionTitle(
                    title = "YouTube Music & Appearance",
                    subtitle = "Ad-free playlist sync, Piped relay, and Nothing OS surface polarity"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        .clickable { showImportUrlDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("studio_sync_playlist_url_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sync YouTube Music / Spotify Playlist URL",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = settings.ytmConnectedPlaylistUrl.ifBlank {
                                    "Import any public playlist link into your Library"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                CleanPreferenceRow(
                    title = "YouTube Music Ad-Free Engine",
                    subtitle = "Stream full songs via InnerTube & Piped without login",
                    checked = settings.ytmEngineEnabled,
                    onCheckedChange = onToggleYtmEngine,
                    tag = "switch_ytm_engine"
                )

                Text(
                    text = if (showPipedInput) {
                        "Hide relay node"
                    } else {
                        "Piped relay: ${settings.customPipedInstance.removePrefix("https://")}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showPipedInput = !showPipedInput }
                )

                AnimatedVisibility(visible = showPipedInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pipedDraft,
                            onValueChange = { pipedDraft = it },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdatePipedInstance(pipedDraft)
                                showPipedInput = false
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text("Save", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Polarity Segmented Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PolaritySegment(
                        label = "OLED Dark",
                        selected = settings.oledDarkMode,
                        onClick = { onToggleOled(true) },
                        tag = "polarity_oled_button",
                        modifier = Modifier.weight(1f)
                    )
                    PolaritySegment(
                        label = "Paper Light",
                        selected = !settings.oledDarkMode,
                        onClick = { onToggleOled(false) },
                        tag = "polarity_light_button",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Display Refresh Rate Policy Segmented Pill (Auto / High 144Hz / Battery Saver)
                val peakHz = LocalPeakDisplayRefreshHz.current
                val refreshBadgeText = when (settings.displayRefreshPolicy) {
                    DisplayRefreshPolicy.HIGH -> "LOCKED ${peakHz}Hz"
                    DisplayRefreshPolicy.BATTERY_SAVER -> "60Hz CAP"
                    DisplayRefreshPolicy.AUTO -> "LTPO ${peakHz}Hz"
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Display Refresh Rate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (settings.displayRefreshPolicy == DisplayRefreshPolicy.HIGH) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("display_refresh_active_badge")
                        ) {
                            Text(
                                text = refreshBadgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = if (settings.displayRefreshPolicy == DisplayRefreshPolicy.HIGH) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Text(
                        text = settings.displayRefreshPolicy.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                            .padding(4.dp)
                            .testTag("display_refresh_policy_group"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DisplayRefreshPolicy.entries.forEach { policy ->
                            val segLabel = if (policy == DisplayRefreshPolicy.HIGH && peakHz >= 90) {
                                "High • ${peakHz}Hz"
                            } else {
                                policy.label
                            }
                            PolaritySegment(
                                label = segLabel,
                                selected = settings.displayRefreshPolicy == policy,
                                onClick = { onSelectDisplayRefreshPolicy(policy) },
                                tag = "refresh_policy_${policy.id}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                CleanPreferenceRow(
                    title = "Ambient Dot-Matrix Wave",
                    subtitle = "Artwork-tinted reactive wave while playing, calm gray grid when paused",
                    checked = settings.glyphReactiveLeds,
                    onCheckedChange = onToggleGlyphReactive,
                    tag = "switch_glyph_reactive"
                )

                if (settings.glyphReactiveLeds) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                            .clickable(enabled = !hasAudioVisualizerPerm) {
                                visualizerPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("visualizer_fft_mode_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = when {
                                    isRealtimeFftActive -> "6-Band Hardware FFT • Live"
                                    hasAudioVisualizerPerm -> "6-Band Hardware FFT • Armed"
                                    else -> "Procedural Groove Fallback"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = when {
                                    isRealtimeFftActive ->
                                        "Capturing live Sub-bass, Bass, Low-mid, Mid, Upper-mid & Treble output"
                                    hasAudioVisualizerPerm ->
                                        "Live session FFT attaches automatically during active audio playback"
                                    else ->
                                        "Tap to allow audio-session capture for real 6-band FFT waves (optional)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (hasAudioVisualizerPerm) {
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (hasAudioVisualizerPerm) "READY" else "ENABLE FFT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasAudioVisualizerPerm) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        }
                    }
                }

                CleanPreferenceRow(
                    title = "Automix Queue",
                    subtitle = "Continue playing related songs seamlessly when queue finishes",
                    checked = settings.automixEnabled,
                    onCheckedChange = onToggleAutomix,
                    tag = "switch_automix"
                )

                CleanPreferenceRow(
                    title = "Hide Unplayable Songs",
                    subtitle = "Hide unavailable songs from lists instead of dimming their tiles",
                    checked = settings.hideUnplayableSongs,
                    onCheckedChange = onToggleHideUnplayable,
                    tag = "switch_hide_unplayable"
                )

                CleanPreferenceRow(
                    title = "Tactile Haptics",
                    subtitle = "Mechanical tick feedback on scrubber, swipe-queue, and controls",
                    checked = settings.tactileHaptics,
                    onCheckedChange = onToggleHaptics,
                    tag = "switch_haptics"
                )

                TextButton(
                    onClick = onReopenOnboarding,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag("reopen_onboarding_button")
                ) {
                    Text(
                        text = "Open Setup Guide",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioSectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorialMetricColumn(
    value: String,
    label: String
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectedSpotifySection(
    uiState: NothingUiState,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onRemoteAction: (String) -> Unit
) {
    val profile = uiState.spotifyProfile
    FrostedGlassCapsule(
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = "Spotify avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = profile.displayName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Spotify ${profile.productTier} • Two-way Likes & Playlists active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onSync,
                    modifier = Modifier.testTag("spotify_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!profile.activeDeviceName.isNullOrBlank() || !profile.currentlyPlayingTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = profile.activeDeviceName ?: "Spotify Connect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!profile.currentlyPlayingTitle.isNullOrBlank()) {
                        Text(
                            text = "${profile.currentlyPlayingTitle} • ${profile.currentlyPlayingArtist.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { onRemoteAction("PREV") }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(onClick = { onRemoteAction("PLAY") }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = { onRemoteAction("PAUSE") }) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = { onRemoteAction("NEXT") }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("spotify_disconnect_button")
            ) {
                Text(
                    text = stringResource(R.string.sp_disconnect),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PolaritySegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
        animationSpec = tween(200),
        label = "pol_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "pol_fg"
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
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

@Composable
private fun CleanPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
