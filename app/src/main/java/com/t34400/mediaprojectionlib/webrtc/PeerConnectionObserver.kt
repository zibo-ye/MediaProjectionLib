package com.t34400.mediaprojectionlib.webrtc

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*

@Serializable
data class IceCandidateData(
    val adapterType: String,
    val sdp: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val serverUrl: String
)

class PeerConnectionObserver : PeerConnection.Observer {
    private val eventLogList: MutableList<EventLogEntry> = mutableListOf()

    @Suppress("unused")
    fun getEventLogJson(): String {
        return synchronized(this) {
            val json = Json.encodeToString(EventLog(eventLogList))
            eventLogList.clear()
            json
        }
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        state?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onSignalingChange", it.name))
            }
        }
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        state?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onIceConnectionChange", it.name))
            }
        }
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        synchronized(this) {
            eventLogList.add(EventLogEntry("onIceConnectionReceivingChange", receiving.toString()))
        }
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        state?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onIceGatheringChange", it.name))
            }
        }
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            val candidateData = IceCandidateData(
                it.adapterType.name,
                it.sdp,
                it.sdpMid,
                it.sdpMLineIndex,
                it.serverUrl
            )
            val dataJson = Json.encodeToString(candidateData)

            synchronized(this) {
                eventLogList.add(EventLogEntry("onIceCandidate", dataJson))
            }
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        candidates?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onIceCandidatesRemoved", it.size.toString()))
            }
        }
    }

    override fun onAddStream(stream: MediaStream?) {}

    override fun onRemoveStream(stream: MediaStream?) {}

    override fun onTrack(transceiver: RtpTransceiver?) {
        transceiver?.receiver?.track()?.also { track ->
            if (track is AudioTrack) {
                track.setEnabled(true)
                synchronized(this) {
                    eventLogList.add(EventLogEntry("onTrack", "AudioTrack received"))
                }
            } else if (track is VideoTrack) {
                    Log.w("PeerConnectionObserver", "VideoTrack received but not supported")
                    synchronized(this) {
                        eventLogList.add(EventLogEntry("onTrack", "VideoTrack received but not supported"))
                    }
                }
            }
    }

    override fun onDataChannel(channel: DataChannel?) {}

    override fun onRenegotiationNeeded() {
        synchronized(this) {
            eventLogList.add(EventLogEntry("onRenegotiationNeeded", "true"))
        }
    }
}