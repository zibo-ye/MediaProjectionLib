package com.t34400.mediaprojectionlib.webrtc

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

@Serializable
data class SessionDescriptionData(
    val type: String,
    val description: String
)

class PeerConnectionSdpObserver : SdpObserver {
    private val eventLogList: MutableList<EventLogEntry> = mutableListOf()

    @Suppress("unused")
    fun getEventLogJson(): String {
        return synchronized(this) {
            val json = Json.encodeToString(EventLog(eventLogList))
            eventLogList.clear()
            json
        }
    }

    override fun onCreateSuccess(p0: SessionDescription?) {
        p0?.let {
            val json = Json.encodeToString(
                SessionDescriptionData(it.type.name, it.description)
            )

            synchronized(this) {
                eventLogList.add(EventLogEntry("onCreateSuccess", json))
            }

            Log.d("PeerConnectionSdpObserver", "onCreateSuccess: $json")
        }
    }

    override fun onSetSuccess() {
        synchronized(this) {
            eventLogList.add(EventLogEntry("onSetSuccess", "Set success"))
        }
        Log.d("PeerConnectionSdpObserver", "onSetSuccess")
    }

    override fun onCreateFailure(p0: String?) {
        p0?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onCreateFailure", it))
            }
        }
        Log.e("PeerConnectionSdpObserver", "onCreateFailure: $p0")
    }

    override fun onSetFailure(p0: String?) {
        p0?.let {
            synchronized(this) {
                eventLogList.add(EventLogEntry("onSetFailure", it))
            }
        }
        Log.e("PeerConnectionSdpObserver", "onSetFailure: $p0")
    }
}
