package com.t34400.mediaprojectionlib.webrtc

import kotlinx.serialization.Serializable

@Serializable
data class EventLogEntry(val key: String, val value: String)

@Serializable
data class EventLog(val dataList: List<EventLogEntry>)