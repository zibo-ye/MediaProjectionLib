package com.t34400.mediaprojectionlib.core

import android.graphics.Bitmap
import java.io.Closeable

interface ICapturedScreenData: Closeable {
    val type: Type
    val timestamp : Long
    val width: Int
    val height: Int

    fun getBitmap() : Bitmap
    fun getPixels() : IntArray
    fun getByteArray(): ByteArray

    enum class Type {
        ARGB8888,
        YUV420
    }
}