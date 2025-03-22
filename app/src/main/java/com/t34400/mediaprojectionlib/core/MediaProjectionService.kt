package com.t34400.mediaprojectionlib.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat


class MediaProjectionService : Service() {
    private val binder = LocalBinder()

    private var resultData: Intent? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val stopIntent = Intent(this, MediaProjectionService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Media Projection Service")
            .setContentText("Service is running")
            .setSmallIcon(com.t34400.mediaprojectionlib.R.drawable.baseline_smart_display_24)
            .addAction(NotificationCompat.Action.Builder(
                com.t34400.mediaprojectionlib.R.drawable.baseline_stop_24,
                "Stop Service",
                stopPendingIntent
            ).build())
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_STOP_SERVICE -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                else -> {}
            }
        } ?: return START_NOT_STICKY

        resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(KEY_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(KEY_DATA)
        } ?: return START_NOT_STICKY

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun getResultData() : Intent? {
        val data = resultData
        resultData = null

        return data
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Media Projection Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    companion object {
        const val KEY_DATA = "SCREEN_CAPTURE_INTENT"
        private const val CHANNEL_ID = "MediaProjectionServiceChannel"
        private const val NOTIFICATION_ID = 34214218
        private const val ACTION_STOP_SERVICE = "com.t34400.mediaprojectionlib.core.STOP_SERVICE"
    }
}