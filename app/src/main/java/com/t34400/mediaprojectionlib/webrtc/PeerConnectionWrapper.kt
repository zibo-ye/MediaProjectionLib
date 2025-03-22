package com.t34400.mediaprojectionlib.webrtc

import android.util.Log
import kotlinx.coroutines.DisposableHandle
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

@Suppress("unused")
class PeerConnectionWrapper(
    private val peerConnection: PeerConnection?,
    private var isVideoTrackRequested: Boolean,
    private var isAudioTrackRequested: Boolean,
    private val getVideoTrack: () -> VideoTrack?,
    private val getAudioTrack: () -> AudioTrack?
): DisposableHandle {
    private var videoTrack : VideoTrack? = null
    private var audioTrack : AudioTrack? = null

    init {
        peerConnection?.let { connection ->
            if (isAudioTrackRequested) {
                audioTrack = getAudioTrack()

                audioTrack?.let {
                    connection.addTransceiver(it).apply {
                        direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                        sender.streams = listOf(AUDIO_STREAM_ID)
                    }
                }
            }

            if (isVideoTrackRequested) {
                getVideoTrack()?.let { videoTrack ->
                    val transceiver = peerConnection.addTransceiver(videoTrack)?.apply {
                        direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                        sender.streams = listOf(VIDEO_STREAM_ID)
                    }

                    Log.d("PeerConnection", "Transceiver added: ${transceiver != null}")
                    Log.d("PeerConnection", "Transceiver direction: ${transceiver?.direction}")
                    Log.d("PeerConnection", "Transceiver mid: ${transceiver?.mid}")
                    Log.d("PeerConnection", "Transceiver sender stream IDs: ${transceiver?.sender?.streams}")
                    Log.d("PeerConnection", "Transceiver Media Type: ${transceiver?.mediaType}")

                    Log.d("PeerConnection", "Total transceivers count: ${peerConnection.transceivers?.size}")
                    Log.d("PeerConnection", "Senders count: ${peerConnection.senders?.size}")
                    Log.d("PeerConnection", "Sender Tracks: ${peerConnection.senders?.joinToString(separator = ", ") { sender -> "(ID=${sender.track()?.id()}, Kind=${sender.track()?.kind()})" }}")
                    Log.d("PeerConnection", "Receivers count: ${peerConnection.receivers?.size}")

                    this.videoTrack = videoTrack
                }
            }
        }
    }

    fun registerVideoTrack(videoTrack: VideoTrack) {
        if (isVideoTrackRequested) {
            val transceiver = peerConnection?.addTransceiver(videoTrack)?.apply {
                direction = RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                sender.streams = listOf(VIDEO_STREAM_ID)
            }

            Log.d("PeerConnection", "Transceiver added: ${transceiver != null}")
            Log.d("PeerConnection", "Transceiver direction: ${transceiver?.direction}")
            Log.d("PeerConnection", "Transceiver mid: ${transceiver?.mid}")
            Log.d("PeerConnection", "Transceiver sender stream IDs: ${transceiver?.sender?.streams}")
            Log.d("PeerConnection", "Transceiver Media Type: ${transceiver?.mediaType}")

            Log.d("PeerConnection", "Total transceivers count: ${peerConnection?.transceivers?.size}")
            Log.d("PeerConnection", "Senders count: ${peerConnection?.senders?.size}")
            Log.d("PeerConnection", "Sender Tracks: ${peerConnection?.senders?.joinToString(separator = ", ") { sender -> "(ID=${sender.track()?.id()}, Kind=${sender.track()?.kind()})" }}")
            Log.d("PeerConnection", "Receivers count: ${peerConnection?.receivers?.size}")
        }

        this.videoTrack = videoTrack
    }

    override fun dispose() {
        peerConnection?.dispose()
    }

    fun isVideoTrackAdded(): Boolean {
        return isVideoTrackRequested && !(videoTrack?.isDisposed ?: true)
    }

    // region SDP communication

    fun createOffer(
        observer: SdpObserver,
        constraintsJson : String
    ) {
        val constraints = parseConstraintsJson(constraintsJson)
        peerConnection?.createOffer(observer, constraints)
    }

    fun createAnswer(
        observer: SdpObserver,
        constraintsJson : String
    ) {
        val constraints = parseConstraintsJson(constraintsJson)
        peerConnection?.createAnswer(observer, constraints)
    }

    fun getLocalDescription() : String {
        return peerConnection?.localDescription?.let { sessionDescription ->
            return "${sessionDescription.type.name} ${sessionDescription.description}"
        } ?: ""
    }

    fun getRemoteDescription() : String {
        return peerConnection?.remoteDescription?.let { sessionDescription ->
            return "${sessionDescription.type.name} ${sessionDescription.description}"
        } ?: ""
    }

    fun setLocalDescription(observer: SdpObserver) {
        peerConnection?.setLocalDescription(observer)
    }

    fun setLocalDescription(observer: SdpObserver, type: String, description: String) {
        val sessionDescription = SessionDescription(enumValueOf<SessionDescription.Type>(type), description)
        peerConnection?.setLocalDescription(observer, sessionDescription)
    }

    fun setRemoteDescription(
        observer: SdpObserver,
        typeString: String,
        description: String
    ) {
        enumValues<SessionDescription.Type>().firstOrNull {
            it.name.equals(typeString, ignoreCase = true)
        }?.let { type ->
            val sessionDescription = SessionDescription(type, description)

            peerConnection?.setRemoteDescription(observer, sessionDescription)
        }
    }

    // endregion

    // region ICE communication

    fun addIceCandidate(
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String
    ) : Boolean {
        return peerConnection?.addIceCandidate(
            IceCandidate(sdpMid, sdpMLineIndex, sdp)
        ) ?: false
    }

    fun restartIce() {
        peerConnection?.restartIce()
    }

    // endregion

    // region State

    fun getConnectionState() : String {
        return peerConnection?.connectionState()?.name ?: ""
    }

    fun getIceConnectionState() : String {
        return peerConnection?.iceConnectionState()?.name ?: ""
    }

    fun getIceGatheringState() : String {
        return peerConnection?.iceGatheringState()?.name ?: ""
    }

    fun getSignalingState() : String {
        return peerConnection?.signalingState()?.name ?: ""
    }

    fun setAudioPlayout(playout: Boolean) {
        peerConnection?.setAudioPlayout(playout)
    }

    fun setAudioRecording(recording: Boolean) {
        peerConnection?.setAudioRecording(recording)
    }

    fun setBitrate(
        min: Int,
        current: Int,
        max: Int
    ): Boolean {
        return peerConnection?.setBitrate(min, current, max) ?: false
    }

    // endregion

    companion object {
        private const val VIDEO_STREAM_ID = "quest_video_stream"
        private const val AUDIO_STREAM_ID = "quest_audio_stream"

        private val TAG : String = PeerConnectionWrapper::class.java.simpleName

        private fun parseConstraintsJson(jsonString: String) : MediaConstraints {
            val mediaConstraints = MediaConstraints()

            try {
                val jsonObject = JSONObject(jsonString)

                jsonObject.keys().forEach { key ->
                    val value = jsonObject.getString(key)
                    mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair(key, value))
                }
            } catch (e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            }

            return mediaConstraints
        }
    }
}