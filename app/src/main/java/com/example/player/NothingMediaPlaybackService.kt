package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import com.example.R

/**
 * Foreground MediaPlayback Service & System Notification Controller for Nchord.
 * Ensures Android 11–15 System UI, Lockscreen, and Quick Settings Media Resumption
 * recognize Nchord as an active native music player with interactive transport controls.
 */
class NothingMediaPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = latestNotification
        if (notification != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }.onFailure {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                runCatching { nm?.notify(NOTIFICATION_ID, notification) }
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val CHANNEL_ID = "nchord_media_playback"
        const val NOTIFICATION_ID = 4091
        const val ACTION_STOP_SERVICE = "com.example.player.STOP_MEDIA_SERVICE"
        const val ACTION_PREV = "com.example.player.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.example.player.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.player.ACTION_NEXT"

        @Volatile
        private var latestNotification: Notification? = null

        @Volatile
        internal var actionHandler: ((String) -> Unit)? = null

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Music Playback",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Active Nchord music playback controls"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        fun publishMediaNotification(
            context: Context,
            sessionToken: MediaSession.Token,
            title: String,
            artist: String,
            album: String,
            artworkBitmap: Bitmap?,
            isPlaying: Boolean
        ) {
            runCatching {
                ensureChannel(context)

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPending = PendingIntent.getActivity(
                    context,
                    100,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                fun buildActionIntent(action: String, reqCode: Int): PendingIntent {
                    val i = Intent(context, NothingMediaActionReceiver::class.java).apply {
                        this.action = action
                    }
                    return PendingIntent.getBroadcast(
                        context,
                        reqCode,
                        i,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                val prevAction = Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    buildActionIntent(ACTION_PREV, 101)
                ).build()

                val playPauseAction = Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    buildActionIntent(ACTION_PLAY_PAUSE, 102)
                ).build()

                val nextAction = Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    buildActionIntent(ACTION_NEXT, 103)
                ).build()

                val mediaStyle = Notification.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)

                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(context, CHANNEL_ID)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(context)
                }

                builder
                    .setSmallIcon(R.drawable.ic_stat_nchord)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setSubText(album.ifBlank { "Nchord" })
                    .setContentIntent(contentPending)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)
                    .setOnlyAlertOnce(true)
                    .setStyle(mediaStyle)
                    .addAction(prevAction)
                    .addAction(playPauseAction)
                    .addAction(nextAction)

                if (artworkBitmap != null) {
                    builder.setLargeIcon(artworkBitmap)
                }

                val notification = builder.build()
                latestNotification = notification

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, notification)

                if (isPlaying) {
                    val svcIntent = Intent(context, NothingMediaPlaybackService::class.java)
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(svcIntent)
                        } else {
                            context.startService(svcIntent)
                        }
                    }
                }
            }
        }

        fun dismiss(context: Context) {
            runCatching {
                latestNotification = null
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                val stopIntent = Intent(context, NothingMediaPlaybackService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(stopIntent)
            }
        }
    }
}

class NothingMediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        NothingMediaPlaybackService.actionHandler?.invoke(action)
    }
}
