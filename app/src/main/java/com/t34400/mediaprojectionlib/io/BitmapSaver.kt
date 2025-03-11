package com.t34400.mediaprojectionlib.io

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.t34400.mediaprojectionlib.core.BitmapData
import com.t34400.mediaprojectionlib.core.IEventListener
import com.t34400.mediaprojectionlib.core.ScreenImageProcessManager
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Suppress("unused")
class BitmapSaver (
    context: Context,
    private val screenImageProcessManager: ScreenImageProcessManager,
    private val filenamePrefix: String,
) : IEventListener<BitmapData>, Closeable {

    private val directory: File?

    init {
        screenImageProcessManager.bitmapAvailableEvent.addListener(this)
        directory = context.getExternalFilesDir(null)
    }

    override fun onEvent(data: BitmapData) {
        Thread {
            val filename = "$filenamePrefix${data.timestamp}.jpg"
            val file = File(directory, filename)

            try {
                file.createNewFile()

                val outputStream = FileOutputStream(file)
                data.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save jpg file.")
            }
        }.start()
    }

    override fun close() {
        screenImageProcessManager.bitmapAvailableEvent.removeListener(this)
    }

    companion object {
        private val TAG = BitmapSaver::class.java.simpleName
    }
}