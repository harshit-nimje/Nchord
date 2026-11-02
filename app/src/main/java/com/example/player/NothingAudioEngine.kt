package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private val IDLE_SPECTRUM = List(12) { 0.08f }

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
    val errorMessage: String? = null
)

class NothingAudioEngine(private val context: Context) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkCache = LruCache<String, Bitmap>(16)

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var prepareWatchdogJob: Job? = null
    private var artworkLoadJob: Job? = null
    private var activeSessionToken: Long = 0L
    private var desiredSpeed: Float = 1.0f
    private var currentArtworkBitmap: Bitmap? = null
    private var smoothedBands = FloatArray(12) { 0.08f }

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
            errorMessage = null
        )

        try {
            val mp = MediaPlayer().apply {
                val attrsBuilder = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    attrsBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                }
                setAudioAttributes(attrsBuilder.build())

                when {
                    resolvedUrl.startsWith("/") || resolvedUrl.startsWith("file://") -> {
                        val filePath = resolvedUrl.removePrefix("file://")
                        val file = File(filePath)
                        FileInputStream(file).use { fis ->
                            setDataSource(fis.fd)
                        }
                    }
                    track.sourceType == AudioSourceType.LOCAL_FILE || resolvedUrl.startsWith("content://") -> {
                        setDataSource(context, Uri.parse(resolvedUrl))
                    }
                    else -> {
                        val headers = resolved.requestHeaders.ifEmpty {
                            mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                            )
                        }
                        setDataSource(context, Uri.parse(resolvedUrl), headers)
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
                    player.start()
                    val activeTrack = track.copy(durationMs = actualDur)
                    _state.value = _state.value.copy(
                        currentTrack = activeTrack,
                        isPlaying = true,
                        isBuffering = false,
                        durationMs = actualDur,
                        audioSessionId = runCatching { player.audioSessionId }.getOrDefault(0),
                        streamTelemetry = telemetry.copy(outputRoute = queryHardwareOutputRoute(bitPerfectUsb)),
                        errorMessage = null
                    )
                    syncSystemMediaSession(activeTrack, true, 0L, actualDur, publishNotification = true)
                    startProgressTelemetry()
                }

                setOnBufferingUpdateListener { _, percent ->
                    if (myToken == activeSessionToken && percent >= 100 && _state.value.isBuffering && isPlaying) {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }

                setOnCompletionListener {
                    if (myToken != activeSessionToken) return@setOnCompletionListener
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = _state.value.durationMs,
                        glyphSpectrum = IDLE_SPECTRUM
                    )
                    progressJob?.cancel()
                    onTrackEnded?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    if (myToken != activeSessionToken) return@setOnErrorListener true
                    Log.e("NothingAudioEngine", "MediaPlayer error what=$what extra=$extra")
                    prepareWatchdogJob?.cancel()
                    progressJob?.cancel()
                    if (resolvedUrl.startsWith("http", ignoreCase = true)) {
                        tryOkHttpCachePlaybackFallback(
                            myToken = myToken,
                            track = track,
                            resolved = resolved,
                            qualityTier = qualityTier,
                            bitPerfectUsb = bitPerfectUsb,
                            telemetry = telemetry,
                            allowRetryCallback = allowRetryCallback
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
            if (resolvedUrl.startsWith("http", ignoreCase = true)) {
                tryOkHttpCachePlaybackFallback(
                    myToken = myToken,
                    track = track,
                    resolved = resolved,
                    qualityTier = qualityTier,
                    bitPerfectUsb = bitPerfectUsb,
                    telemetry = telemetry,
                    allowRetryCallback = allowRetryCallback
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
     * custom header, or chunked redirect on googlevideo / monochrome), stream via OkHttp into
     * app cacheDir and play from FileDescriptor before triggering alternate-extractor retry.
     */
    private fun tryOkHttpCachePlaybackFallback(
        myToken: Long,
        track: TrackItem,
        resolved: ResolvedStreamResult,
        qualityTier: AudioQualityTier,
        bitPerfectUsb: Boolean,
        telemetry: StreamQualityTelemetry,
        allowRetryCallback: Boolean
    ) {
        val streamUrl = resolved.url ?: return
        engineScope.launch {
            val cachedFile = kotlinx.coroutines.withTimeoutOrNull(6_500L) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val target = File(context.cacheDir, "nchord_proxy_${myToken % 4}.audio")
                        val reqBuilder = Request.Builder().url(streamUrl)
                        val headers = resolved.requestHeaders.ifEmpty {
                            mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                            )
                        }
                        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                        NetworkModule.fastHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val body = resp.body ?: return@use null
                            body.byteStream().use { input ->
                                FileOutputStream(target).use { out ->
                                    input.copyTo(out, 32 * 1024)
                                }
                            }
                            if (target.exists() && target.length() > 8_192L) target else null
                        }
                    }.getOrNull()
                }
            }

            if (myToken != activeSessionToken) return@launch

            if (cachedFile != null) {
                playTrack(
                    track = track,
                    resolved = resolved.copy(url = cachedFile.absolutePath),
                    qualityTier = qualityTier,
                    bitPerfectUsb = bitPerfectUsb,
                    allowRetryCallback = allowRetryCallback
                )
            } else if (allowRetryCallback && onStreamError != null) {
                onStreamError?.invoke(track)
            } else {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Unable to buffer stream"
                )
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
            runCatching {
                player.playbackParams = PlaybackParams().setSpeed(speed)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _state.value.playbackSpeed != 1.0f) {
            runCatching {
                player.playbackParams = PlaybackParams().setSpeed(1.0f)
            }
        }
    }

    fun togglePlayPause(): Boolean {
        val mp = mediaPlayer ?: return false
        return try {
            if (mp.isPlaying) {
                mp.pause()
                progressJob?.cancel()
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.currentPositionMs)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    currentPositionMs = pos,
                    glyphSpectrum = IDLE_SPECTRUM
                )
                syncSystemMediaSession(
                    track = _state.value.currentTrack,
                    isPlaying = false,
                    posMs = pos,
                    durMs = _state.value.durationMs,
                    publishNotification = true
                )
                false
            } else {
                mp.start()
                applyPlaybackSpeedInternal(mp, desiredSpeed)
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.currentPositionMs)
                _state.value = _state.value.copy(isPlaying = true, currentPositionMs = pos)
                syncSystemMediaSession(
                    track = _state.value.currentTrack,
                    isPlaying = true,
                    posMs = pos,
                    durMs = _state.value.durationMs,
                    publishNotification = true
                )
                startProgressTelemetry()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        try {
            val target = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(1L))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(target, MediaPlayer.SEEK_CLOSEST_SYNC)
            } else {
                mp.seekTo(target.toInt())
            }
            _state.value = _state.value.copy(currentPositionMs = target)
            syncSystemMediaSession(
                track = _state.value.currentTrack,
                isPlaying = _state.value.isPlaying,
                posMs = target,
                durMs = _state.value.durationMs,
                publishNotification = false
            )
        } catch (_: Exception) {
        }
    }

    private fun queryHardwareOutputRoute(bitPerfectUsb: Boolean = true): String {
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching "System Stereo"
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            when {
                outputs.any {
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                } -> {
                    val usbName = outputs.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
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
     * Synthesizes an organic, track-tempo-locked 12-band musical spectrum (sub-bass kick transient,
     * warm bassline, harmonic vocal mids, and crisp hi-hat treble shimmer) smoothed with asymmetric
     * attack/release envelopes so audio-reactive particles & Glyph bars feel alive without jank.
     */
    private fun computeOrganicMusicalSpectrum(posMs: Long, trackSeed: Int): List<Float> {
        val bpm = 94f + (abs(trackSeed) % 38) // 94..131 BPM seeded deterministically per track
        val beatPeriodSec = 60f / bpm
        val timeSec = posMs / 1000f
        val beatPos = (timeSec / beatPeriodSec)
        val beatFrac = beatPos - kotlin.math.floor(beatPos)
        val barBeat = kotlin.math.floor(beatPos).toInt() and 3

        // Kick transient: fast attack, smooth exponential decay on beats 0, 2 and syncopated 16th
        val kickAccent = if (barBeat == 0 || barBeat == 2) 1.0f else 0.72f
        val kickEnvelope = (exp(-beatFrac * 5.8f) * kickAccent).coerceIn(0f, 1f)

        // Snare / clap transient on backbeats 1 & 3
        val snareAccent = if (barBeat == 1 || barBeat == 3) 0.92f else 0.25f
        val snareEnvelope = (exp(-beatFrac * 7.5f) * snareAccent).coerceIn(0f, 1f)

        // Phrase swell (8-bar breathing contour so it never feels monotonous or robotic)
        val phraseSwell = 0.72f + 0.28f * sin(timeSec * 0.42f + (trackSeed and 15) * 0.4f)

        return List(12) { band ->
            val normBand = band / 11f // 0 = sub-bass, 0.5 = vocal mids, 1.0 = air/treble
            val subWeight = (1f - normBand * 2.1f).coerceIn(0f, 1f)
            val midWeight = (1f - abs(normBand - 0.46f) * 2.4f).coerceIn(0f, 1f)
            val highWeight = ((normBand - 0.45f) * 1.82f).coerceIn(0f, 1f)

            val subEnergy = kickEnvelope * 0.78f + (0.20f * (0.5f + 0.5f * sin(timeSec * 3.1f + band * 0.3f)))
            val midHarmonic = 0.38f +
                0.34f * (0.5f + 0.5f * sin(timeSec * 4.7f + band * 0.85f + (trackSeed and 7))) +
                snareEnvelope * 0.32f
            val highShimmer = 0.22f +
                0.36f * (0.5f + 0.5f * cos(timeSec * 9.4f + band * 1.35f)).pow(2) +
                snareEnvelope * 0.28f

            val rawTarget = ((subWeight * subEnergy + midWeight * midHarmonic + highWeight * highShimmer) * phraseSwell)
                .coerceIn(0.08f, 0.98f)

            // Asymmetric attack / release smoothing for buttery motion
            val prev = smoothedBands[band]
            val coeff = if (rawTarget > prev) 0.52f else 0.24f
            val next = prev + (rawTarget - prev) * coeff
            smoothedBands[band] = next
            next.coerceIn(0.08f, 0.98f)
        }
    }

    private fun startProgressTelemetry() {
        progressJob?.cancel()
        progressJob = engineScope.launch {
            var sessionSyncCounter = 0
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && _state.value.isPlaying) {
                    val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                    val dur = runCatching { mp.duration.toLong() }
                        .getOrDefault(_state.value.durationMs)
                        .takeIf { it > 0L } ?: _state.value.durationMs

                    val trackSeed = _state.value.currentTrack?.id?.hashCode() ?: 17
                    val spectrum = computeOrganicMusicalSpectrum(pos, trackSeed)

                    _state.value = _state.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        glyphSpectrum = spectrum
                    )

                    sessionSyncCounter++
                    if (sessionSyncCounter >= 15) {
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
                delay(66L)
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
        artworkLoadJob?.cancel()
        releasePlayerOnly()
        NothingMediaPlaybackService.dismiss(context)
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
    }
}
