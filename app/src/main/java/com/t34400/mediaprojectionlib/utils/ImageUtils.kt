package com.t34400.mediaprojectionlib.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.util.Log


object ImageUtils {
    private const val TAG = "ImageUtils"
    fun convertToBitmap(buffer: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(buffer))
        return bitmap
    }

    fun copyBuffer(image: Image): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val data = ByteArray(image.width * image.height * 4)
        var bufferOffset = 0
        var outputOffset = 0
        for (row in 0 until image.height) {
            for (col in 0 until image.width) {
                buffer.position(bufferOffset)
                buffer.get(data, outputOffset, 4)
                outputOffset += 4
                bufferOffset += pixelStride
            }
            bufferOffset += rowPadding
        }
        return data
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

        buffer.rewind()
        var offset = 0
        for (row in 0 until image.height) {
            for (col in 0 until image.width) {
                // Add bounds checking to prevent buffer overflow
                if (offset + 3 < buffer.remaining()) {
                    val r = buffer[offset].toInt() and 0xff
                    val g = buffer[offset + 1].toInt() and 0xff
                    val b = buffer[offset + 2].toInt() and 0xff
                    val a = buffer[offset + 3].toInt() and 0xff
                    val pixel = Color.argb(a, r, g, b)
                    pixels[row * image.width + col] = pixel
                } else {
                    // Set to transparent black if we exceed buffer bounds
                    pixels[row * image.width + col] = 0
                }
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