package com.t34400.mediaprojectionlib

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics

public class MediaProjectionManager (
    context: Context,
    private val captureWidth: Int,
    private val captureHeight: Int
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    init {
        MediaProjectionRequestActivity.requestMediaProjection(context, this::setupImageReader)
    }

    private fun setupImageReader(mediaProjection: MediaProjection) {
        val displayMetrics = DisplayMetrics()
        val screenDensity = displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            // TODO
            image?.close()
        }, null)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }
}