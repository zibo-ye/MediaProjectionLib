package com.t34400.mediaprojectionlib.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

            mediaProjectionService.getResultData()?.let { resultData ->
                resultDataCallback(this@MediaProjectionRequestActivity, resultData)
            }

            finish()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
        private var resultDataCallback: (Context, Intent) -> Unit = { _, _ -> }

        @JvmStatic
        fun requestMediaProjection(context: Context, resultDataCallback: (Context, Intent) -> Unit) {
            Companion.resultDataCallback = resultDataCallback

            context.startActivity(
                Intent(context, MediaProjectionRequestActivity::class.java)
            )
        }
        @JvmStatic
        fun stopMediaProjection(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java)
            context.stopService(intent)
        }
    }
}