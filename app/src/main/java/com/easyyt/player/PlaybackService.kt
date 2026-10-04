package com.easyyt.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "easyyt_playback"
        const val NOTIF_ID = 1001
        const val ACTION_TOGGLE = "com.easyyt.player.TOGGLE"
        const val ACTION_STOP = "com.easyyt.player.STOP"

        @Volatile
        var webViewRef: WeakReference<WebView>? = null

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }

        private fun execJs(js: String) {
            val w = webViewRef?.get() ?: return
            w.post { w.evaluateJavascript(js, null) }
        }

        fun playVideo() {
            execJs("(function(){var v=document.querySelector('video');if(v)v.play();})()")
        }

        fun pauseVideo() {
            execJs("(function(){var v=document.querySelector('video');if(v)v.pause();})()")
        }

        fun toggleVideo() {
            execJs(
                "(function(){var v=document.querySelector('video');" +
                    "if(v){if(v.paused)v.play();else v.pause();}})()"
            )
        }
    }

    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyYTPlayer:playback")
        wakeLock?.acquire(4 * 60 * 60 * 1000L)

        session = MediaSession(this, "EasyYTPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    playVideo()
                    updateNotification(true)
                }

                override fun onPause() {
                    pauseVideo()
                    updateNotification(false)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleVideo()
            ACTION_STOP -> {
                pauseVideo()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification(true))
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    private fun updateNotification(playing: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(playing))
    }

    private fun buildNotification(playing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (playing) R.drawable.ic_stat_play else R.drawable.ic_stat_pause)
            .setContentTitle("Easy YT Player")
            .setContentText(if (playing) "Playing in background" else "Paused")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, if (playing) "Pause" else "Play", toggle)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Background playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
