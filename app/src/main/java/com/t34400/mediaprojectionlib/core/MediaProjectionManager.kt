package com.t34400.mediaprojectionlib.core

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.util.Log
import com.t34400.mediaprojectionlib.utils.ImageUtils
import java.io.ByteArrayOutputStream

class MediaProjectionManager (
    context: Context,
) {
    private var imageReader: ImageReader? = null

    private var latestTimestamp: Long = 0L

    init {
        MediaProjectionRequestActivity.requestMediaProjection(context, this::setupImageReader)
    }

    // Called from Unity to get the latest image data
    fun getLatestImageIfAvailable() : ByteArray {
        return getLatestImage()?.let { image ->
            val bitmap = ImageUtils.convertToBitmap(image)
            image.close()

            val stream = ByteArrayOutputStream()
            return@let if (bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                stream.toByteArray()
            } else null
        } ?: ByteArray(0)
    }

    fun getLatestImage() : Image? {
        return imageReader?.let { reader ->
            val image = reader.acquireLatestImage() ?: return null

            val timestamp = image.timestamp
            return@let if (timestamp != latestTimestamp) {
                latestTimestamp = timestamp
                image
            } else null
        }
    }

    private fun setupImageReader(imageReader: ImageReader) {
        this.imageReader = imageReader
        Log.d("MediaProjectionManager", "setupImageReader")
    }
}