package com.t34400.mediaprojectionlib.logging

import android.util.Log

/**
 * Centralized logging manager that can forward log messages to Unity.
 * This replaces direct Android Log calls with a mechanism that can notify Unity.
 */
object UnityLogManager {
    
    private var unityLogCallback: IUnityLogCallback? = null
    private var isEnabled = true
    
    /**
     * Register a Unity callback to receive log messages
     * @param callback The Unity callback implementation
     */
    fun registerUnityCallback(callback: IUnityLogCallback?) {
        unityLogCallback = callback
        if (callback != null) {
            logInfo("UnityLogManager", "Unity log callback registered successfully")
        } else {
            Log.i("UnityLogManager", "Unity log callback unregistered")
        }
    }
    
    /**
     * Enable or disable log forwarding to Unity
     * @param enabled Whether to forward logs to Unity
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        logInfo("UnityLogManager", "Unity logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if Unity logging is enabled
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * Log a debug message
     */
    fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("DEBUG", tag, message)
        }
    }
    
    /**
     * Log an info message
     */
    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("INFO", tag, message)
        }
    }
    
    /**
     * Log a warning message
     */
    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("WARN", tag, message)
        }
    }
    
    /**
     * Log a warning message with exception
     */
    fun logWarning(tag: String, message: String, exception: Throwable?) {
        val fullMessage = if (exception != null) {
            "$message: ${exception.message}"
        } else {
            message
        }
        Log.w(tag, fullMessage, exception)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("WARN", tag, fullMessage)
        }
    }
    
    /**
     * Log an error message
     */
    fun logError(tag: String, message: String) {
        Log.e(tag, message)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("ERROR", tag, message)
        }
    }
    
    /**
     * Log an error message with exception
     */
    fun logError(tag: String, message: String, exception: Throwable?) {
        val fullMessage = if (exception != null) {
            "$message: ${exception.message}"
        } else {
            message
        }
        Log.e(tag, fullMessage, exception)
        if (isEnabled) {
            unityLogCallback?.onLogMessage("ERROR", tag, fullMessage)
        }
    }
}