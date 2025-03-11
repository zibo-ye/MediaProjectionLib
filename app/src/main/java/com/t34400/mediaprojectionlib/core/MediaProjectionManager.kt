package com.t34400.mediaprojectionlib.core

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import kotlin.math.roundToInt

class MediaProjectionManager (
    context: Context,
) {
    private val width: Int
    private val height: Int
    private val densityDpi : Int

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    init {
        val metrics = context.resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels

        val scale = if (maxOf(rawWidth, rawHeight) > 960) {
            960f / maxOf(rawWidth, rawHeight)
        } else 1f

        width = (rawWidth * scale).roundToInt()
        height = (rawHeight * scale).roundToInt()
        densityDpi = metrics.densityDpi

        MediaProjectionRequestActivity.requestMediaProjection(context, this::registerMediaProjection)
    }

    fun getImageReader(): ImageReader? {
        if (imageReader != null) {
            return imageReader
        }

        return projection?.let { mediaProjection ->
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            imageReader?.let { imageReader ->
                val imageSurface = imageReader.surface

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "Projection",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageSurface,
                    null,
                    null
                )
            }

            return this.imageReader
        }
    }

    @Suppress("unused")
    private fun stopMediaProjection(context: Context) {
        MediaProjectionRequestActivity.stopMediaProjection(context)

        projection?.stop()
        virtualDisplay?.release()
        imageReader?.close()

        projection = null
        virtualDisplay = null
        imageReader = null

        Log.d("MediaProjectionManager", "stopMediaProjection")
    }

    private fun registerMediaProjection(context: Context, resultData: Intent) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        projection = projectionManager.getMediaProjection(RESULT_OK, resultData)

        Log.d("MediaProjectionManager", "registerMediaProjection")
    }
}