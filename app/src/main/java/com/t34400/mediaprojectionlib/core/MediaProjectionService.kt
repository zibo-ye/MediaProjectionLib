package com.t34400.mediaprojectionlib.core

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class MediaProjectionService : Service() {
    private val binder = LocalBinder()

    private lateinit var projection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Media Projection Service")
            .setContentText("Service is running")
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId)
        }
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(KEY_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(KEY_DATA)
        } ?: return START_NOT_STICKY
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(RESULT_OK, data)

        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels

        val scale = if (maxOf(rawWidth, rawHeight) > 960) {
            960f / maxOf(rawWidth, rawHeight)
        } else 1f

        val width = (rawWidth * scale).roundToInt()
        val height = (rawHeight * scale).roundToInt()

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val imageSurface = imageReader.surface

        virtualDisplay = projection.createVirtualDisplay(
            "Projection",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageSurface,
            null,
            null
        )

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay.release()
        imageReader.close()
        projection.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun getImageReader(): ImageReader? {
        return imageReader
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
    }
}