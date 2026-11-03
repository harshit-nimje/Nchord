package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.example.data.model.AudioQualityTier
import com.example.data.model.AudioSourceType
import com.example.data.model.StreamQualityTelemetry
import com.example.data.model.TrackItem
import com.example.data.remote.NetworkModule
import com.example.data.remote.ResolvedStreamResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileInputStream

private val IDLE_SPECTRUM = List(12) { 0.08f }
private val IDLE_SIX_BANDS = List(6) { 0.08f }

@Immutable
data class AudioEngineState(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioSessionId: Int = 0,
    val streamTelemetry: StreamQualityTelemetry = StreamQualityTelemetry(),
    val glyphSpectrum: List<Float> = IDLE_SPECTRUM,
    val sixBandSpectrum: List<Float> = IDLE_SIX_BANDS,
    val isRealtimeFftActive: Boolean = false,
    val errorMessage: String? = null
)

class NothingAudioEngine(private val context: Context) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkCache = LruCache<String, Bitmap>(16)
    private val localStreamProxy = LocalAudioStreamProxy(NetworkModule.okHttpClient)
    private val audioVisualizer = NothingAudioVisualizer(context)
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var prepareWatchdogJob: Job? = null
    private var artworkLoadJob: Job? = null
    private var activeSessionToken: Long = 0L
    private var desiredSpeed: Float = 1.0f
    private var currentArtworkBitmap: Bitmap? = null
    private var smoothedBands = FloatArray(12) { 0.08f }

    private var hasAudioFocus: Boolean = false
    private var resumeOnFocusGain: Boolean = false
    private var isDucked: Boolean = false
    private var noisyReceiverRegistered: Boolean = false
    @Volatile private var pendingSeekTargetMs: Long = -1L
    @Volatile private var pendingSeekDeadlineElapsedMs: Long = 0L

    private val playbackAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
            }
        }
        .build()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        engineScope.launch {
            handleAudioFocusChange(focusChange)
        }
    }

    private val audioFocusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAudioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        } else {
            null
        }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                engineScope.launch {
                    pauseInternal(abandonFocus = true, clearResumeFlag = true)
                }
            }
        }
    }

    var onTrackEnded: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null
    var onStreamError: ((TrackItem) -> Unit)? = null

    private var mediaSession: MediaSession? = runCatching {
        MediaSession(context, "NchordMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!_state.value.isPlaying) togglePlayPause()
                }

                override fun onPause() {
                    if (_state.value.isPlaying) togglePlayPause()
                }

                override fun onSkipToNext() {
                    onTrackEnded?.invoke()
                }

                override fun onSkipToPrevious() {
                    onSkipPrevious?.invoke() ?: seekTo(0L)
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }
            })
            isActive = true
        }
    }.getOrNull()

    init {
        NothingMediaPlaybackService.actionHandler = { action ->
            engineScope.launch {
                when (action) {
                    NothingMediaPlaybackService.ACTION_PREV -> onSkipPrevious?.invoke() ?: seekTo(0L)
                    NothingMediaPlaybackService.ACTION_PLAY_PAUSE -> togglePlayPause()
                    NothingMediaPlaybackService.ACTION_NEXT -> onTrackEnded?.invoke()
                }
            }
        }
    }

    private fun syncSystemMediaSession(
        track: TrackItem?,
        isPlaying: Boolean,
        posMs: Long,
        durMs: Long,
        publishNotification: Boolean = true
    ) {
        val ms = mediaSession ?: return
        if (track == null) {
            ms.isActive = false
            NothingMediaPlaybackService.dismiss(context)
            return
        }
        runCatching {
            val metaBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { "Nchord" })
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durMs.coerceAtLeast(1L))
            currentArtworkBitmap?.let { bmp ->
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bmp)
            }
            ms.setMetadata(metaBuilder.build())

            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    posMs.coerceAtLeast(0L),
                    if (isPlaying) desiredSpeed else 0f
                )
                .build()
            ms.setPlaybackState(state)
            ms.isActive = true

            if (publishNotification) {
                NothingMediaPlaybackService.publishMediaNotification(
                    context = context,
                    sessionToken = ms.sessionToken,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkBitmap = currentArtworkBitmap,
                    isPlaying = isPlaying
                )
            }
        }
    }

    private fun loadNotificationArtworkAsync(track: TrackItem, expectedToken: Long) {
        artworkLoadJob?.cancel()
        val artUrl = track.artworkUrl.trim()
        if (artUrl.isBlank()) {
            currentArtworkBitmap = null
            return
        }
        artworkCache.get(artUrl)?.let { cached ->
            currentArtworkBitmap = cached
            return
        }
        currentArtworkBitmap = null
        artworkLoadJob = cleanupScope.launch {
            val bmp = runCatching {
                val req = Request.Builder().url(artUrl).get().build()
                NetworkModule.okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            }.getOrNull()
            if (bmp != null) {
                artworkCache.put(artUrl, bmp)
                engineScope.launch {
                    if (expectedToken == activeSessionToken && _state.value.currentTrack?.id == track.id) {
                        currentArtworkBitmap = bmp
                        val cur = _state.value
                        syncSystemMediaSession(
                            track = cur.currentTrack,
                            isPlaying = cur.isPlaying,
                            posMs = cur.currentPositionMs,
                            durMs = cur.durationMs,
                            publishNotification = true
                        )
                    }
                }
            }
        }
    }

    private val _state = MutableStateFlow(AudioEngineState())
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    /**
     * Frame-0 UI feedback: immediately reflects the selected track in Mini-Player & Player Deck
     * without falsely claiming lossless bitrate before the stream is resolved.
     */
    fun prepareImmediateFeedback(
        track: TrackItem,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC
    ) {
        val token = ++activeSessionToken
        resumeOnFocusGain = false
        pendingSeekTargetMs = -1L
        releasePlayerOnly()
        smoothedBands = FloatArray(12) { 0.08f }
        loadNotificationArtworkAsync(track, token)
        _state.value = AudioEngineState(
            currentTrack = track,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0L,
            durationMs = track.durationMs.coerceAtLeast(60_000L),
            playbackSpeed = desiredSpeed,
            streamTelemetry = StreamQualityTelemetry(
                tier = qualityTier,
                codecLabel = "Resolving…",
                containerLabel = "Stream",
                bitrateKbps = 0,
                sampleRateHz = 44_100,
                bitDepth = 16,
                outputRoute = queryHardwareOutputRoute()
            ),
            glyphSpectrum = IDLE_SPECTRUM,
            sixBandSpectrum = IDLE_SIX_BANDS,
            isRealtimeFftActive = false,
            errorMessage = null
        )
    }

    fun playTrack(
        track: TrackItem,
        resolved: ResolvedStreamResult,
        qualityTier: AudioQualityTier = AudioQualityTier.LOSSLESS_LDAC,
        bitPerfectUsb: Boolean = true,
        allowRetryCallback: Boolean = true
    ) {
        val myToken = ++activeSessionToken
        resumeOnFocusGain = false
        releasePlayerOnly()

        val resolvedUrl = resolved.url
        if (resolvedUrl.isNullOrBlank()) {
            if (allowRetryCallback && onStreamError != null) {
                onStreamError?.invoke(track)
            } else {
                _state.value = _state.value.copy(
                    currentTrack = track,
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Full stream unavailable for this track"
                )
            }
            return
        }

        loadNotificationArtworkAsync(track, myToken)

        val routeName = queryHardwareOutputRoute(bitPerfectUsb)
        val telemetry = StreamQualityTelemetry(
            tier = qualityTier,
            codecLabel = resolved.codecLabel,
            containerLabel = resolved.containerLabel,
            bitrateKbps = resolved.bitrateKbps,
            sampleRateHz = resolved.sampleRateHz,
            bitDepth = resolved.bitDepth,
            outputRoute = routeName
        )

        _state.value = AudioEngineState(
            currentTrack = track,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0L,
            durationMs = resolved.durationMs.coerceAtLeast(60_000L),
            playbackSpeed = desiredSpeed,
            streamTelemetry = telemetry,
            glyphSpectrum = IDLE_SPECTRUM,
            sixBandSpectrum = IDLE_SIX_BANDS,
            isRealtimeFftActive = false,
            errorMessage = null
        )

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(playbackAudioAttributes)

                when {
                    resolvedUrl.startsWith("/") || resolvedUrl.startsWith("file://") -> {
                        val filePath = resolvedUrl.removePrefix("file://")
                        val file = File(filePath)
                        FileInputStream(file).use { fis ->
                            setDataSource(fis.fd)
                        }
                    }
                    track.sourceType == AudioSourceType.LOCAL_FILE || resolvedUrl.startsWith("content://") -> {
                        setDataSource(context, resolvedUrl.toUri())
                    }
                    else -> {
                        val headers = resolved.requestHeaders.ifEmpty {
                            mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                            )
                        }
                        setDataSource(context, resolvedUrl.toUri(), headers)
                    }
                }

                setOnPreparedListener { player ->
                    if (myToken != activeSessionToken) {
                        runCatching { player.release() }
                        return@setOnPreparedListener
                    }
                    prepareWatchdogJob?.cancel()
                    val actualDur = player.duration.toLong().takeIf { it > 0L } ?: resolved.durationMs
                    applyPlaybackSpeedInternal(player, desiredSpeed)
                    val grantedFocus = requestAudioFocusForPlayback()
                    val activeTrack = track.copy(durationMs = actualDur)
                    if (grantedFocus) {
                        isDucked = false
                        runCatching { player.setVolume(1.0f, 1.0f) }
                        player.start()
                        registerBecomingNoisyReceiver()
                        val sessionId = runCatching { player.audioSessionId }.getOrDefault(0)
                        audioVisualizer.attachOrUpdate(sessionId, playing = true)
                        _state.value = _state.value.copy(
                            currentTrack = activeTrack,
                            isPlaying = true,
                            isBuffering = false,
                            durationMs = actualDur,
                            audioSessionId = sessionId,
                            streamTelemetry = telemetry.copy(outputRoute = queryHardwareOutputRoute(bitPerfectUsb)),
                            errorMessage = null
                        )
                        syncSystemMediaSession(activeTrack, true, 0L, actualDur, publishNotification = true)
                        startProgressTelemetry()
                    } else {
                        // Delayed focus or active call — stay prepared and paused cleanly
                        resumeOnFocusGain = true
                        val sessionId = runCatching { player.audioSessionId }.getOrDefault(0)
                        _state.value = _state.value.copy(
                            currentTrack = activeTrack,
                            isPlaying = false,
                            isBuffering = false,
                            durationMs = actualDur,
                            audioSessionId = sessionId,
                            streamTelemetry = telemetry.copy(outputRoute = queryHardwareOutputRoute(bitPerfectUsb)),
                            errorMessage = null
                        )
                        syncSystemMediaSession(activeTrack, false, 0L, actualDur, publishNotification = true)
                    }
                }

                setOnBufferingUpdateListener { _, percent ->
                    if (myToken == activeSessionToken && percent >= 100 && _state.value.isBuffering && isPlaying) {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }

                setOnSeekCompleteListener { player ->
                    if (myToken != activeSessionToken) return@setOnSeekCompleteListener
                    pendingSeekTargetMs = -1L
                    val settledPos = runCatching { player.currentPosition.toLong() }
                        .getOrDefault(_state.value.currentPositionMs)
                    _state.value = _state.value.copy(currentPositionMs = settledPos)
                }

                setOnCompletionListener {
                    if (myToken != activeSessionToken) return@setOnCompletionListener
                    unregisterBecomingNoisyReceiver()
                    audioVisualizer.pauseCapture()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = _state.value.durationMs,
                        glyphSpectrum = IDLE_SPECTRUM,
                        sixBandSpectrum = IDLE_SIX_BANDS,
                        isRealtimeFftActive = false
                    )
                    progressJob?.cancel()
                    onTrackEnded?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    if (myToken != activeSessionToken) return@setOnErrorListener true
                    Log.e("NothingAudioEngine", "MediaPlayer error what=$what extra=$extra")
                    prepareWatchdogJob?.cancel()
                    progressJob?.cancel()
                    unregisterBecomingNoisyReceiver()
                    val isAlreadyLoopback = resolvedUrl.startsWith("http://127.0.0.1:", ignoreCase = true)
                    if (!isAlreadyLoopback && resolvedUrl.startsWith("http", ignoreCase = true)) {
                        tryLoopbackProxyPlaybackFallback(
                            myToken = myToken,
                            track = track,
                            resolved = resolved,
                            qualityTier = qualityTier,
                            bitPerfectUsb = bitPerfectUsb
                        )
                    } else if (allowRetryCallback && onStreamError != null) {
                        onStreamError?.invoke(track)
                    } else {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playback interrupted ($what)"
                        )
                        syncSystemMediaSession(
                            track = _state.value.currentTrack,
                            isPlaying = false,
                            posMs = _state.value.currentPositionMs,
                            durMs = _state.value.durationMs,
                            publishNotification = true
                        )
                    }
                    true
                }

                prepareAsync()
            }
            mediaPlayer = mp
            // 7.2s watchdog prevents silent socket hangs during prepareAsync()
            prepareWatchdogJob = engineScope.launch {
                delay(7_200L)
                if (myToken == activeSessionToken && _state.value.isBuffering && !_state.value.isPlaying) {
                    Log.w("NothingAudioEngine", "prepareAsync watchdog timeout — cascading to alternate engine")
                    releasePlayerOnly()
                    if (allowRetryCallback && onStreamError != null) {
                        onStreamError?.invoke(track)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NothingAudioEngine", "Failed starting MediaPlayer", e)
            val isAlreadyLoopback = resolvedUrl.startsWith("http://127.0.0.1:", ignoreCase = true)
            if (!isAlreadyLoopback && resolvedUrl.startsWith("http", ignoreCase = true)) {
                tryLoopbackProxyPlaybackFallback(
                    myToken = myToken,
                    track = track,
                    resolved = resolved,
                    qualityTier = qualityTier,
                    bitPerfectUsb = bitPerfectUsb
                )
            } else if (allowRetryCallback && onStreamError != null) {
                onStreamError?.invoke(track)
            } else {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = e.localizedMessage ?: "Audio engine error"
                )
            }
        }
    }

    /**
     * When Android's native Stagefright HTTP client rejects a valid CDN URL (e.g. TLS cipher,
     * custom header, or chunked redirect), route the stream progressively through [localStreamProxy]
     * on `127.0.0.1` with native HTTP `Range` support instead of blocking on a full-file download.
     */
    private fun tryLoopbackProxyPlaybackFallback(
        myToken: Long,
        track: TrackItem,
        resolved: ResolvedStreamResult,
        qualityTier: AudioQualityTier,
        bitPerfectUsb: Boolean
    ) {
        if (myToken != activeSessionToken) return
        val streamUrl = resolved.url ?: return
        val proxyUrl = localStreamProxy.registerStream(
            streamKey = "tok_${myToken}_${track.id.hashCode()}",
            upstreamUrl = streamUrl,
            headers = resolved.requestHeaders
        )
        if (proxyUrl != null) {
            playTrack(
                track = track,
                resolved = resolved.copy(url = proxyUrl, requestHeaders = emptyMap()),
                qualityTier = qualityTier,
                bitPerfectUsb = bitPerfectUsb,
                allowRetryCallback = true
            )
        } else if (onStreamError != null) {
            onStreamError?.invoke(track)
        } else {
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = "Unable to open local stream relay"
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        desiredSpeed = clamped
        val mp = mediaPlayer
        if (mp != null && _state.value.isPlaying) {
            applyPlaybackSpeedInternal(mp, clamped)
        }
        _state.value = _state.value.copy(playbackSpeed = clamped)
        val cur = _state.value
        if (cur.currentTrack != null) {
            syncSystemMediaSession(
                track = cur.currentTrack,
                isPlaying = cur.isPlaying,
                posMs = cur.currentPositionMs,
                durMs = cur.durationMs,
                publishNotification = false
            )
        }
    }

    private fun applyPlaybackSpeedInternal(player: MediaPlayer, speed: Float) {
        if (speed != 1.0f) {
            runCatching {
                player.playbackParams = PlaybackParams().setSpeed(speed)
            }
        } else if (_state.value.playbackSpeed != 1.0f) {
            runCatching {
                player.playbackParams = PlaybackParams().setSpeed(1.0f)
            }
        }
    }

    private fun requestAudioFocusForPlayback(): Boolean {
        val am = audioManager ?: return true
        if (hasAudioFocus) return true
        val req = audioFocusRequest
        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && req != null) {
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocusInternal() {
        val am = audioManager ?: return
        if (!hasAudioFocus && !resumeOnFocusGain) return
        hasAudioFocus = false
        val req = audioFocusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && req != null) {
            runCatching { am.abandonAudioFocusRequest(req) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(audioFocusChangeListener) }
        }
    }

    private fun registerBecomingNoisyReceiver() {
        if (noisyReceiverRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(becomingNoisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun pauseInternal(abandonFocus: Boolean, clearResumeFlag: Boolean) {
        val mp = mediaPlayer ?: return
        if (clearResumeFlag) {
            resumeOnFocusGain = false
        }
        unregisterBecomingNoisyReceiver()
        audioVisualizer.pauseCapture()
        if (abandonFocus) {
            abandonAudioFocusInternal()
        }
        try {
            if (mp.isPlaying) {
                mp.pause()
            }
            progressJob?.cancel()
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.currentPositionMs)
            _state.value = _state.value.copy(
                isPlaying = false,
                currentPositionMs = pos,
                glyphSpectrum = IDLE_SPECTRUM,
                sixBandSpectrum = IDLE_SIX_BANDS,
                isRealtimeFftActive = false
            )
            syncSystemMediaSession(
                track = _state.value.currentTrack,
                isPlaying = false,
                posMs = pos,
                durMs = _state.value.durationMs,
                publishNotification = true
            )
        } catch (_: Exception) {
        }
    }

    private fun resumeInternal(): Boolean {
        val mp = mediaPlayer ?: return false
        if (!requestAudioFocusForPlayback()) {
            resumeOnFocusGain = true
            return false
        }
        return try {
            isDucked = false
            runCatching { mp.setVolume(1.0f, 1.0f) }
            mp.start()
            applyPlaybackSpeedInternal(mp, desiredSpeed)
            registerBecomingNoisyReceiver()
            val sessionId = runCatching { mp.audioSessionId }.getOrDefault(_state.value.audioSessionId)
            audioVisualizer.attachOrUpdate(sessionId, playing = true)
            val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.currentPositionMs)
            _state.value = _state.value.copy(
                isPlaying = true,
                currentPositionMs = pos,
                audioSessionId = sessionId
            )
            syncSystemMediaSession(
                track = _state.value.currentTrack,
                isPlaying = true,
                posMs = pos,
                durMs = _state.value.durationMs,
                publishNotification = true
            )
            startProgressTelemetry()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        val mp = mediaPlayer ?: return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                pauseInternal(abandonFocus = true, clearResumeFlag = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                val wasPlaying = _state.value.isPlaying || runCatching { mp.isPlaying }.getOrDefault(false)
                if (wasPlaying) {
                    resumeOnFocusGain = true
                    pauseInternal(abandonFocus = false, clearResumeFlag = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_state.value.isPlaying) {
                    isDucked = true
                    runCatching { mp.setVolume(0.22f, 0.22f) }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (isDucked) {
                    isDucked = false
                    runCatching { mp.setVolume(1.0f, 1.0f) }
                }
                if (resumeOnFocusGain && !_state.value.isPlaying) {
                    resumeOnFocusGain = false
                    resumeInternal()
                }
            }
        }
    }

    fun togglePlayPause(): Boolean {
        val mp = mediaPlayer ?: return false
        val currentlyPlaying = runCatching { mp.isPlaying }.getOrDefault(_state.value.isPlaying)
        return if (currentlyPlaying) {
            pauseInternal(abandonFocus = true, clearResumeFlag = true)
            false
        } else {
            resumeOnFocusGain = false
            resumeInternal()
        }
    }

    fun refreshVisualizerAttachment() {
        val mp = mediaPlayer ?: return
        if (_state.value.isPlaying) {
            val sessionId = runCatching { mp.audioSessionId }.getOrDefault(_state.value.audioSessionId)
            audioVisualizer.attachOrUpdate(sessionId, playing = true)
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _state.value.durationMs.coerceAtLeast(1L)
        val target = positionMs.coerceIn(0L, dur)
        pendingSeekTargetMs = target
        pendingSeekDeadlineElapsedMs = SystemClock.elapsedRealtime() + 1_100L
        _state.value = _state.value.copy(currentPositionMs = target)
        val mp = mediaPlayer ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(target, MediaPlayer.SEEK_CLOSEST_SYNC)
            } else {
                mp.seekTo(target.toInt())
            }
            syncSystemMediaSession(
                track = _state.value.currentTrack,
                isPlaying = _state.value.isPlaying,
                posMs = target,
                durMs = dur,
                publishNotification = false
            )
        } catch (_: Exception) {
            pendingSeekTargetMs = -1L
        }
    }

    private fun queryHardwareOutputRoute(bitPerfectUsb: Boolean = true): String {
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching "System Stereo"
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            when {
                outputs.any {
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                } -> {
                    val usbName = outputs.firstOrNull {
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                    }?.productName?.toString()?.takeIf { it.isNotBlank() }
                    if (bitPerfectUsb) "${usbName ?: "USB DAC"} • Bit-Perfect" else (usbName ?: "USB Audio")
                }
                outputs.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                } -> {
                    val btName = outputs.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    }?.productName?.toString()?.takeIf { it.isNotBlank() }
                    "${btName ?: "Bluetooth"} • A2DP"
                }
                outputs.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                } -> "Wired 3.5mm Analog"
                else -> "Stereo Speaker"
            }
        }.getOrDefault("System Stereo")
    }

    /**
     * Synthesizes a high-contrast, song-tempo-locked 12-band musical spectrum when hardware
     * Visualizer PCM tap is unavailable. Uses deterministic track BPM + arrangement phrasing +
     * sharp exponential kick/snare/hat envelopes so inter-beat valleys drop cleanly to ~0.07f
     * while beat hits punch up to ~0.97f in lockstep with the song timeline.
     */
    private fun computeOrganicMusicalSpectrum(
        posMs: Long,
        trackSeed: Int,
        titleHint: String = "",
        durationMs: Long = 210_000L
    ): List<Float> {
        val lower = titleHint.lowercase()
        val baseBpm = when {
            lower.contains("slow") || lower.contains("acoustic") || lower.contains("nocturn") ||
                lower.contains("lullaby") || lower.contains("ballad") -> 84f + (abs(trackSeed) % 14)
            lower.contains("club") || lower.contains("techno") || lower.contains("house") ||
                lower.contains("synth") || lower.contains("dance") || lower.contains("upbeat") -> 120f + (abs(trackSeed) % 18)
            else -> 92f + (abs(trackSeed) % 40) // 92..131 BPM seeded deterministically per track
        }
        val beatPeriodSec = 60f / baseBpm
        val timeSec = posMs / 1000f
        val beatPos = timeSec / beatPeriodSec
        val beatIndex = kotlin.math.floor(beatPos).toInt()
        val beatFrac = (beatPos - kotlin.math.floor(beatPos)).coerceIn(0f, 1f)
        val barBeat = beatIndex and 3
        val barNumber = (beatIndex shr 2)

        // Syncopated 8th/16th ghost kick (gives hip-hop / electronic / indie groove depending on trackSeed)
        val grooveStyle = abs(trackSeed shr 3) and 3
        val subBeatPos = beatPos * 2f
        val eighthFrac = (subBeatPos - kotlin.math.floor(subBeatPos)).coerceIn(0f, 1f)
        val syncopatedGhost = when (grooveStyle) {
            1 -> if (barBeat == 1 && beatFrac >= 0.5f) exp(-eighthFrac * 7.2f) * 0.78f else 0f
            2 -> if (barBeat == 2 && beatFrac >= 0.5f) exp(-eighthFrac * 6.8f) * 0.82f else 0f
            3 -> if (beatFrac >= 0.75f && barBeat == 3) exp(-((beatFrac - 0.75f) * 4f) * 8f) * 0.74f else 0f
            else -> 0f
        }

        // Sharp kick transient: punches hard on downbeats, decays fast so dots breathe between hits
        val kickAccent = when (barBeat) {
            0 -> 1.00f
            2 -> if (grooveStyle == 1) 0.65f else 0.92f
            else -> if (grooveStyle == 0) 0.80f else 0.30f
        }
        val kickEnvelope = maxOf(exp(-beatFrac * 8.4f) * kickAccent, syncopatedGhost).coerceIn(0f, 1f)

        // Crisp snare / clap transient on backbeats 1 & 3
        val snareAccent = if (barBeat == 1 || barBeat == 3) 0.96f else 0.14f
        val snareEnvelope = (exp(-beatFrac * 10.5f) * snareAccent).coerceIn(0f, 1f)

        // 16th-note hi-hat / ride groove pulse
        val sixteenthPos = beatPos * 4f
        val sixteenthIdx = kotlin.math.floor(sixteenthPos).toInt() and 3
        val sixteenthFrac = (sixteenthPos - kotlin.math.floor(sixteenthPos)).coerceIn(0f, 1f)
        val hatAccent = when (sixteenthIdx) {
            2 -> 0.88f
            0 -> 0.54f
            else -> 0.34f
        }
        val hatEnvelope = (exp(-sixteenthFrac * 9.2f) * hatAccent).coerceIn(0f, 1f)

        // Song arrangement contour (intro -> verse -> chorus lift -> bridge breath -> outro)
        val songRatio = if (durationMs > 15_000L) (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0.35f
        val sectionEnergy = when {
            songRatio < 0.07f -> 0.52f + (songRatio / 0.07f) * 0.38f
            songRatio in 0.56f..0.68f -> 0.62f + 0.18f * sin((songRatio - 0.56f) * 26f)
            songRatio > 0.93f -> (1f - (songRatio - 0.93f) * 8f).coerceIn(0.35f, 1f)
            (barNumber and 7) in 4..7 -> 1.00f
            else -> 0.80f
        }

        return List(12) { band ->
            val normBand = band / 11f
            val subWeight = (1f - normBand * 2.15f).coerceIn(0f, 1f)
            val midWeight = (1f - abs(normBand - 0.46f) * 2.35f).coerceIn(0f, 1f)
            val highWeight = ((normBand - 0.44f) * 1.78f).coerceIn(0f, 1f)

            val bassGroove = 0.08f + kickEnvelope * 0.90f +
                0.14f * (0.5f + 0.5f * sin(beatPos * 3.14159f + band * 0.4f))
            val vocalChordSwell = 0.09f +
                snareEnvelope * 0.58f +
                0.32f * (0.5f + 0.5f * sin(beatPos * 1.5708f + band * 0.75f + (trackSeed and 7))).pow(2)
            val highShimmer = 0.06f +
                hatEnvelope * 0.62f +
                snareEnvelope * 0.34f

            val rawTarget = (
                (subWeight * bassGroove + midWeight * vocalChordSwell + highWeight * highShimmer) * sectionEnergy
                ).coerceIn(0.06f, 0.98f)

            val prev = smoothedBands[band]
            val coeff = if (rawTarget > prev) 0.74f else 0.30f
            val next = prev + (rawTarget - prev) * coeff
            smoothedBands[band] = next
            next.coerceIn(0.06f, 0.98f)
        }
    }

    private fun startProgressTelemetry() {
        progressJob?.cancel()
        progressJob = engineScope.launch {
            var sessionSyncCounter = 0
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && _state.value.isPlaying) {
                    val rawPos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                    val seekTarget = pendingSeekTargetMs
                    val pos = if (seekTarget >= 0L && SystemClock.elapsedRealtime() < pendingSeekDeadlineElapsedMs) {
                        if (abs(rawPos - seekTarget) <= 750L) {
                            pendingSeekTargetMs = -1L
                            rawPos
                        } else {
                            seekTarget
                        }
                    } else {
                        if (seekTarget >= 0L) pendingSeekTargetMs = -1L
                        rawPos
                    }
                    val dur = runCatching { mp.duration.toLong() }
                        .getOrDefault(_state.value.durationMs)
                        .takeIf { it > 0L } ?: _state.value.durationMs

                    val liveFft = audioVisualizer.readLiveSpectraIfActive()
                    val sixBands: List<Float>
                    val twelveBars: List<Float>
                    val realtimeActive: Boolean
                    if (liveFft != null) {
                        sixBands = liveFft.first
                        twelveBars = liveFft.second
                        realtimeActive = true
                    } else {
                        val activeTrack = _state.value.currentTrack
                        val trackSeed = if (activeTrack != null) {
                            (activeTrack.id.hashCode() * 31) xor activeTrack.title.hashCode()
                        } else {
                            17
                        }
                        twelveBars = computeOrganicMusicalSpectrum(
                            posMs = pos,
                            trackSeed = trackSeed,
                            titleHint = "${activeTrack?.title.orEmpty()} ${activeTrack?.album.orEmpty()}",
                            durationMs = dur
                        )
                        sixBands = listOf(
                            (twelveBars[0] * 0.65f + twelveBars[1] * 0.35f), // Sub-bass
                            (twelveBars[2] * 0.55f + twelveBars[3] * 0.45f), // Bass
                            (twelveBars[4] * 0.55f + twelveBars[5] * 0.45f), // Low mids
                            (twelveBars[6] * 0.55f + twelveBars[7] * 0.45f), // Midrange
                            (twelveBars[8] * 0.55f + twelveBars[9] * 0.45f), // Upper mids
                            (twelveBars[10] * 0.45f + twelveBars[11] * 0.55f) // Treble
                        )
                        realtimeActive = false
                    }

                    _state.value = _state.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        glyphSpectrum = twelveBars,
                        sixBandSpectrum = sixBands,
                        isRealtimeFftActive = realtimeActive
                    )

                    sessionSyncCounter++
                    if (sessionSyncCounter >= 22) {
                        sessionSyncCounter = 0
                        syncSystemMediaSession(
                            track = _state.value.currentTrack,
                            isPlaying = true,
                            posMs = pos,
                            durMs = dur,
                            publishNotification = false
                        )
                    }
                }
                delay(46L)
            }
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerDetentHaptic(enabled: Boolean) {
        if (!enabled) return
        try {
            val vibrator = getVibrator() ?: return
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(10L, 70))
                }
            }
        } catch (_: Exception) {
        }
    }

    fun triggerConfirmHaptic(enabled: Boolean) {
        if (!enabled) return
        try {
            val vibrator = getVibrator() ?: return
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(16L, 120))
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releasePlayerOnly() {
        prepareWatchdogJob?.cancel()
        progressJob?.cancel()
        unregisterBecomingNoisyReceiver()
        audioVisualizer.detachSession()
        val old = mediaPlayer
        mediaPlayer = null
        if (old != null) {
            cleanupScope.launch {
                runCatching { old.reset() }
                runCatching { old.release() }
            }
        }
    }

    fun release() {
        activeSessionToken++
        resumeOnFocusGain = false
        artworkLoadJob?.cancel()
        prepareWatchdogJob?.cancel()
        progressJob?.cancel()
        unregisterBecomingNoisyReceiver()
        abandonAudioFocusInternal()
        audioVisualizer.release()
        localStreamProxy.close()
        val old = mediaPlayer
        mediaPlayer = null
        if (old != null) {
            Thread {
                runCatching { old.reset() }
                runCatching { old.release() }
            }.start()
        }
        if (NothingMediaPlaybackService.actionHandler != null) {
            NothingMediaPlaybackService.actionHandler = null
        }
        NothingMediaPlaybackService.dismiss(context)
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        engineScope.cancel()
        cleanupScope.cancel()
    }
}
