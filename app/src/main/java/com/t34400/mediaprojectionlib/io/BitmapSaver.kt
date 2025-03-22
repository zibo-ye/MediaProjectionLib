package com.t34400.mediaprojectionlib.io

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
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
) : IEventListener<ICapturedScreenData>, Closeable {

    private val directory: File?

    init {
        screenImageProcessManager.screenDataAvailableEvent.addListener(this)
        directory = context.getExternalFilesDir(null)
    }

    override fun onEvent(data: ICapturedScreenData) {
        Thread {
            val filename = "$filenamePrefix${data.timestamp}.jpg"
            val file = File(directory, filename)

            try {
                file.createNewFile()

                val outputStream = FileOutputStream(file)
                data.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save jpg file.")
            }
        }.start()
    }

    override fun close() {
        screenImageProcessManager.screenDataAvailableEvent.removeListener(this)
    }

    companion object {
        private val TAG = BitmapSaver::class.java.simpleName
    }
}