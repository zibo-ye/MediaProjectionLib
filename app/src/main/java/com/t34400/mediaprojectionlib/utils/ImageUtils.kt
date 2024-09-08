package com.t34400.mediaprojectionlib.utils

import android.graphics.Bitmap
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
}