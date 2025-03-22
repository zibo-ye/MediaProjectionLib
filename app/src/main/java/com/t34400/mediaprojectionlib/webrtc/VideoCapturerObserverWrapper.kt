package com.t34400.mediaprojectionlib.webrtc

import android.util.Log
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame

class VideoCapturerObserverWrapper(
    private val observer: CapturerObserver?
) : CapturerObserver {
    private var timestamp: Long = 0L
    private var latestFrameBuffer: VideoFrame.I420Buffer? = null

    fun getLatestScreenData(): ICapturedScreenData? {
        synchronized(this) {
            return latestFrameBuffer?.let { buffer ->
                CapturedScreenDataYuv420(timestamp, buffer.width, buffer.height, buffer)
            }
        }
    }

    override fun onCapturerStarted(p0: Boolean) {
        observer?.onCapturerStarted(p0)
    }

    override fun onCapturerStopped() {
        observer?.onCapturerStopped()
    }

    override fun onFrameCaptured(p0: VideoFrame?) {
        p0?.let { frame ->
            Log.d("VideoCapturerObserverWrapper", "Frame captured: ${frame.timestampNs}, ${frame.buffer.width}x${frame.buffer.height}")

            synchronized(this) {
                timestamp = frame.timestampNs
                latestFrameBuffer = frame.buffer.toI420()?.apply {
                    retain()
                }
            }
        }

        observer?.onFrameCaptured(p0)
    }
}