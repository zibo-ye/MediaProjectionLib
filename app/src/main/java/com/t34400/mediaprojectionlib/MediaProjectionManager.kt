package com.t34400.mediaprojectionlib

import android.content.Context
import android.media.ImageReader
import android.util.Log

public class MediaProjectionManager (
    context: Context,
) {
    init {
        MediaProjectionRequestActivity.requestMediaProjection(context, this::setupImageReader)
    }

    private fun setupImageReader(imageReader: ImageReader) {
        Log.d("MediaProjectionManager", "setupImageReader")
    }
}