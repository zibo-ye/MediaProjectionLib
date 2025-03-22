package com.t34400.mediaprojectionlib.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image


object ImageUtils {
    fun convertToBitmap(image: Image): Bitmap {
        val planes = image.planes

        val buffer = planes[0].buffer

        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return bitmap
    }

    fun convertToPixels(image: Image, reusePixels: IntArray? = null): IntArray {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val pixels = if (reusePixels != null && reusePixels.size == image.width * image.height) {
            reusePixels
        } else {
            IntArray(image.width * image.height)
        }

        var offset = 0
        for (row in 0 until image.height) {
            for (col in 0 until image.width) {
                val r = buffer[offset].toInt() and 0xff
                val g = buffer[offset + 1].toInt() and 0xff
                val b = buffer[offset + 2].toInt() and 0xff
                val a = buffer[offset + 3].toInt() and 0xff
                val pixel = Color.argb(a, r, g, b)
                pixels[row * image.width + col] = pixel
                offset += pixelStride
            }
            offset += rowPadding
        }
        return pixels
    }

    fun convertToByteArray(image: Image): ByteArray {
        val planeBuffer = image.planes[0].buffer
        val data = ByteArray(planeBuffer.remaining())
        planeBuffer.get(data)

        return data
    }
}