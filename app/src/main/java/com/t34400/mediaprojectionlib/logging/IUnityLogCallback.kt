package com.t34400.mediaprojectionlib.logging

/**
 * Callback interface for sending log messages from Android native code to Unity.
 * Unity can register this callback to receive real-time log messages from the native library.
 */
interface IUnityLogCallback {
    
    /**
     * Called when a log message is generated from the native side
     * @param level The log level (e.g., "DEBUG", "INFO", "WARN", "ERROR")
     * @param tag The log tag (component/class name)
     * @param message The log message
     */
    fun onLogMessage(level: String, tag: String, message: String)
}