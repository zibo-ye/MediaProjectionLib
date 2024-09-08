package com.t34400.mediaprojectionlib.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MediaProjectionRequestActivity : ComponentActivity() {
    private var isBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        val startMediaProjection = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent = result.data!!
                val serviceIntent = Intent(this, MediaProjectionService::class.java).apply {
                    putExtra(MediaProjectionService.KEY_DATA, data)
                }
                startService(serviceIntent)
                bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }

        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBound = true

            val binder = service as MediaProjectionService.LocalBinder
            val mediaProjectionService = binder.getService()

            imageReader = mediaProjectionService.getImageReader()
            imageReader?.let(callback)

            finish()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            imageReader = null
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    companion object {
        private var callback: (ImageReader) -> Unit = {}
        private var imageReader: ImageReader? = null

        @JvmStatic
        fun requestMediaProjection(context: Context, callback: (ImageReader) -> Unit) {
            Companion.callback = callback

            imageReader?.let { imageReader ->
                callback(imageReader)
                return
            }

            context.startActivity(
                Intent(context, MediaProjectionRequestActivity::class.java)
            )
        }
    }
}