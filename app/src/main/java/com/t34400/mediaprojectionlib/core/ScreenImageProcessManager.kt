package com.t34400.mediaprojectionlib.core

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class ScreenImageProcessManager(
    private val mediaProjectionManager: IMediaProjectionManager
) {
    val screenDataAvailableEvent = EventManager<ICapturedScreenData>()

    private var latestTimestamp: Long = 0L

    // Called from Unity to get the latest image data
    @Suppress("unused")
    fun getLatestImageIfAvailable(
        textureRequired: Boolean
    ) : ByteArray {
        return getLatestImage()?.let { image ->
            screenDataAvailableEvent.notifyListeners(image)

            val bitmap = image.getBitmap()

            image.close()

            val stream = ByteArrayOutputStream()
            return@let if (textureRequired && bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                stream.toByteArray()
            } else null
        } ?: ByteArray(0)
    }

    private fun getLatestImage() : ICapturedScreenData? {
        return mediaProjectionManager.getCapturedScreenData()?.let { image ->
            val timestamp = image.timestamp

            return@let if (timestamp != latestTimestamp) {
                latestTimestamp = timestamp
                image
            } else null
        }
    }
}