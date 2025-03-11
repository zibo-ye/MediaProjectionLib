package com.t34400.mediaprojectionlib.core

import android.graphics.Bitmap
import android.media.Image
import com.t34400.mediaprojectionlib.utils.ImageUtils
import java.io.ByteArrayOutputStream

data class BitmapData (
    val bitmap: Bitmap,
    val timestamp: Long
)

class ScreenImageProcessManager(
    private val mediaProjectionManager: MediaProjectionManager
) {
    val imageAvailableEvent = EventManager<Image>()
    val bitmapAvailableEvent = EventManager<BitmapData>()

    private var latestTimestamp: Long = 0L

    // Called from Unity to get the latest image data
    @Suppress("unused")
    fun getLatestImageIfAvailable(
        textureRequired: Boolean
    ) : ByteArray {
        return getLatestImage()?.let { image ->
            imageAvailableEvent.notifyListeners(image)

            val bitmap = ImageUtils.convertToBitmap(image)
            val timestamp = image.timestamp
            image.close()
            bitmapAvailableEvent.notifyListeners(BitmapData(bitmap, timestamp))

            val stream = ByteArrayOutputStream()
            return@let if (textureRequired && bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                stream.toByteArray()
            } else null
        } ?: ByteArray(0)
    }

    private fun getLatestImage() : Image? {
        return mediaProjectionManager.getImageReader()?.let { reader ->
            val image = reader.acquireLatestImage() ?: return null

            val timestamp = image.timestamp
            return@let if (timestamp != latestTimestamp) {
                latestTimestamp = timestamp
                image
            } else null
        }
    }
}