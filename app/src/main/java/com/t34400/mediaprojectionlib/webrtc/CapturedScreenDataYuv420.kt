package com.t34400.mediaprojectionlib.webrtc

import android.graphics.Bitmap
import android.graphics.Color
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
import org.webrtc.VideoFrame

class CapturedScreenDataYuv420(
    override val timestamp: Long,
    override val width: Int,
    override val height: Int,
    private val buffer: VideoFrame.I420Buffer
) : ICapturedScreenData {
    override val type: ICapturedScreenData.Type
        get() = ICapturedScreenData.Type.YUV420

    init {
        buffer.retain()
    }

    private var bitmap: Bitmap? = null
    private var pixels: IntArray? = null
    private var byteArray: ByteArray? = null

    override fun getBitmap(): Bitmap {
        if (bitmap == null) {
            pixels = pixels ?: getPixels()
            bitmap = Bitmap.createBitmap(pixels!!, width, height, Bitmap.Config.ARGB_8888)
        }
        return bitmap!!
    }

    override fun getPixels(): IntArray {
        if (pixels != null) {
            return pixels!!
        }

        val yBuffer = buffer.dataY
        val uBuffer = buffer.dataU
        val vBuffer = buffer.dataV

        val yStride = buffer.strideY
        val uStride = buffer.strideU

        pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * yStride + x
                val uvIndex = (y / 2) * uStride + (x / 2)

                val yValue = yBuffer[yIndex].toInt() and 0xFF
                val uValue = uBuffer[uvIndex].toInt() and 0xFF
                val vValue = vBuffer[uvIndex].toInt() and 0xFF

                val r = (yValue + 1.402 * (vValue - 128)).toInt().coerceIn(0, 255)
                val g = (yValue - 0.344136 * (uValue - 128) - 0.714136 * (vValue - 128)).toInt().coerceIn(0, 255)
                val b = (yValue + 1.772 * (uValue - 128)).toInt().coerceIn(0, 255)

                pixels!![y * width + x] = Color.argb(255, r, g, b)
            }
        }

        return pixels!!
    }

    override fun getByteArray(): ByteArray {
        if (byteArray == null) {
            val ySize = width * height
            val uvSize = ySize / 4
            byteArray = ByteArray(ySize + 2 * uvSize)

            val yBuffer = buffer.dataY
            val uBuffer = buffer.dataU
            val vBuffer = buffer.dataV

            val yStride = buffer.strideY
            val uStride = buffer.strideU
            val vStride = buffer.strideV

            // Y Plane
            for (y in 0 until height) {
                yBuffer.position(y * yStride)
                yBuffer.get(byteArray!!, y * width, width)
            }

            // UV Plane（NV21）
            val uvOffset = ySize
            for (y in 0 until height / 2) {
                uBuffer.position(y * uStride)
                vBuffer.position(y * vStride)
                for (x in 0 until width / 2) {
                    val u = uBuffer.get().toInt() and 0xFF
                    val v = vBuffer.get().toInt() and 0xFF
                    byteArray!![uvOffset + 2 * (y * (width / 2) + x)] = v.toByte()
                    byteArray!![uvOffset + 2 * (y * (width / 2) + x) + 1] = u.toByte()
                }
            }
        }
        return byteArray!!
    }

    override fun close() {
        buffer.release()
    }
}
