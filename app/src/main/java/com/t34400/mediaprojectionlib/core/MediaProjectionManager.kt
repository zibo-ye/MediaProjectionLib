package com.t34400.mediaprojectionlib.core

import android.content.Context
import android.media.ImageReader
import android.util.Log

class MediaProjectionManager (
    context: Context,
) {
    private var imageReader: ImageReader? = null


    init {
        MediaProjectionRequestActivity.requestMediaProjection(context, this::setupImageReader)
    }

    private fun setupImageReader(imageReader: ImageReader) {
        this.imageReader = imageReader
        Log.d("MediaProjectionManager", "setupImageReader")
    }


}