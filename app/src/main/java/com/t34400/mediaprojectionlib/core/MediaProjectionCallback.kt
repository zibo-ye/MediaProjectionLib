package com.t34400.mediaprojectionlib.core

import android.media.projection.MediaProjection
import android.util.Log
import com.t34400.mediaprojectionlib.logging.UnityLogManager

class MediaProjectionCallback : MediaProjection.Callback() {
    @Suppress("MemberVisibilityCanBePrivate")
    var isRunning = false
    var isValid = true

    override fun onCapturedContentResize(width: Int, height: Int) {
        isRunning = true

        UnityLogManager.logDebug(TAG, "onCapturedContentResize(width=$width, height=$height)")
    }

    override fun onStop() {
        isRunning = false
        isValid = false

        UnityLogManager.logDebug(TAG, "onStop")
    }

    companion object {
        val TAG: String = MediaProjectionCallback::class.java.simpleName
    }
}