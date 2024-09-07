package com.t34400.mediaprojectionlib

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MediaProjectionRequestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        val startMediaProjection = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                mediaProjection = mediaProjectionManager
                    .getMediaProjection(result.resultCode, result.data!!)
                mediaProjection?.let { mediaProjection ->
                    callback(mediaProjection)
                }
            }
        }

        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    companion object {
        private var callback: (MediaProjection) -> Unit = {}
        private var mediaProjection: MediaProjection? = null

        fun requestMediaProjection(context: Context, callback: (MediaProjection) -> Unit) {
            this.callback = callback

            mediaProjection?.let { mediaProjection ->
                callback(mediaProjection)
            }

            context.startActivity(
                Intent(context, MediaProjectionRequestActivity::class.java)
            )
        }
    }
}