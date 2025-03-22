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
        get() = image.timestamp

    override val width: Int
        get() = image.width

    override val height: Int
        get() = image.height

    private var bitmap: Bitmap? = null
    private var pixels: IntArray? = null
    private var byteArray: ByteArray? = null

    override fun getBitmap(): Bitmap {
        bitmap = bitmap ?: ImageUtils.convertToBitmap(image)
        return bitmap!!
    }

    override fun getPixels(): IntArray {
        pixels = pixels ?: ImageUtils.convertToPixels(image)
        return pixels!!
    }

    override fun getByteArray(): ByteArray {
        byteArray = byteArray ?: ImageUtils.convertToByteArray(image)
        return byteArray!!
    }

    override fun close() {
        image.close()
    }
}