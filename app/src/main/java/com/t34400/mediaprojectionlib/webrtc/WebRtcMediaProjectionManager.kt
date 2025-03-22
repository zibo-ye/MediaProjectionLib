package com.t34400.mediaprojectionlib.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.t34400.mediaprojectionlib.core.ICapturedScreenData
import com.t34400.mediaprojectionlib.core.IMediaProjectionManager
import com.t34400.mediaprojectionlib.core.MediaProjectionRequestActivity
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID
import kotlin.math.roundToInt


class WebRtcMediaProjectionManager(
    context: Context,
    private val callback: MediaProjection.Callback? = null,
): IMediaProjectionManager {
    private val width: Int
    private val height: Int
    private val densityDpi : Int

    private val eglBase: EglBase by lazy { EglBase.create() }

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setOptions(
                PeerConnectionFactory.Options()
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var videoSource : VideoSource? = null
    private var audioSource : AudioSource? = null
    private var videoTrack : VideoTrack? = null
    private var audioTrack : AudioTrack? = null

    private var peerConnections : MutableList<PeerConnectionWrapper> = ArrayList()

    private var observer: VideoCapturerObserverWrapper? = null

    init {
        val metrics = context.resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels

        val scale = if (maxOf(rawWidth, rawHeight) > 960) {
            960f / maxOf(rawWidth, rawHeight)
        } else 1f

        width = (rawWidth * scale).roundToInt()
        height = (rawHeight * scale).roundToInt()
        densityDpi = metrics.densityDpi

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        MediaProjectionRequestActivity.requestMediaProjection(context, this::registerMediaProjection)
    }

    @Suppress("unused")
    private fun stopMediaProjection(context: Context) {
        screenCapturer?.stopCapture()

        MediaProjectionRequestActivity.stopMediaProjection(context)

        factory.dispose()
        screenCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        videoSource?.dispose()
        videoTrack?.dispose()
        audioSource?.dispose()
        audioTrack?.dispose()

        screenCapturer = null
        surfaceTextureHelper = null
        videoSource = null
        videoTrack = null
        audioSource = null
        audioTrack = null

        Log.d(TAG, "stopMediaProjection")
    }

    @Suppress("unused")
    private fun createPeerConnection(
        observer: PeerConnection.Observer,
        iceServerList: String,
        isVideoTrackRequested: Boolean,
        isAudioTrackRequested: Boolean
    ) : PeerConnectionWrapper {
        val peerConnection = factory.let { peerConnectionFactory ->
            val iceServers = iceServerList.split(" ")
                .map { iceServer ->
                    PeerConnection.IceServer.builder(iceServer).createIceServer()
                }
            val rtcConfig = RTCConfiguration(iceServers)

            return@let peerConnectionFactory.createPeerConnection(
                rtcConfig,
                observer
            )
        }

        val wrapper = PeerConnectionWrapper(
            peerConnection,
            isVideoTrackRequested,
            isAudioTrackRequested,
            this::getVideoTrack,
            this::getAudioTrack
        )

        synchronized(this) {
            peerConnections.add(wrapper)
        }

        return wrapper
    }

    @Suppress("unused")
    private fun disposePeerConnection(connection: PeerConnectionWrapper) {
        connection.dispose()
        peerConnections.remove(connection)
    }

    override fun getCapturedScreenData(): ICapturedScreenData? {
        return observer?.getLatestScreenData()
    }

    private fun registerMediaProjection(context: Context, resultData: Intent) {
        val eglBaseContext = eglBase.eglBaseContext

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        Log.d(TAG, "SurfaceTextureHelper created: $surfaceTextureHelper")

        videoSource = factory.createVideoSource(true)
        Log.d(TAG, "VideoSource created: $videoSource")

        screenCapturer = ScreenCapturerAndroid(resultData, callback).also { capturer ->
            Log.d(TAG, "ScreenCapturerAndroid initialized: $capturer")

            observer = VideoCapturerObserverWrapper(videoSource?.capturerObserver)
            capturer.initialize(surfaceTextureHelper, context.applicationContext, observer)
            Log.d(TAG, "Capturer initialized with surfaceTextureHelper and observer.")

            capturer.startCapture(width, height, 30)
            Log.d(TAG, "Capturer started with width: $width, height: $height, frame rate: 30")
        }

        videoTrack = factory.createVideoTrack(UUID.randomUUID().toString(), videoSource)?.apply {
            setEnabled(true)
            Log.d(TAG, "VideoTrack created and enabled: $this")
        }

        videoTrack?.let { track ->
            synchronized(this) {
                peerConnections.forEach { it.registerVideoTrack(track) }
            }
        }

        Log.d(TAG, "registerMediaProjection")
    }

    private fun getVideoTrack() : VideoTrack? {
        return videoTrack
    }

    private fun getAudioTrack() : AudioTrack? {
        audioSource = audioSource ?: factory.createAudioSource(MediaConstraints())
        audioTrack = audioTrack ?: factory.createAudioTrack(UUID.randomUUID().toString(), audioSource)

        return audioTrack
    }

    companion object {
        private val TAG : String = WebRtcMediaProjectionManager::class.java.simpleName
    }
}