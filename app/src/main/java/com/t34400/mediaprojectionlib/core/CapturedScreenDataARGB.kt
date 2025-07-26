package com.t34400.mediaprojectionlib.core

import android.graphics.Bitmap
import android.media.Image
import com.t34400.mediaprojectionlib.utils.ImageUtils

class CapturedScreenDataARGB(
    private val image: Image
): ICapturedScreenData {
    override val type: ICapturedScreenData.Type
        get() = ICapturedScreenData.Type.ARGB8888

    override val timestamp: Long
    override val width: Int
    override val height: Int

    private val byteArray: ByteArray

    init {
        timestamp = image.timestamp
        width = image.width
        height = image.height
        byteArray = ImageUtils.copyBuffer(image)
        image.close()
    }

    private var bitmap: Bitmap? = null
    private var pixels: IntArray? = null

    override fun getBitmap(): Bitmap {
        bitmap = bitmap ?: ImageUtils.convertToBitmap(byteArray, width, height)
        return bitmap!!
    }

    override fun getPixels(): IntArray {
        if (pixels == null) {
            val intBuffer = java.nio.ByteBuffer.wrap(byteArray).asIntBuffer()
            pixels = IntArray(intBuffer.remaining())
            intBuffer.get(pixels)
        }
        return pixels!!
    }

    override fun getByteArray(): ByteArray {
        return byteArray
    }

    override fun close() {
        // The image is already closed in the init block
    }
}