package com.t34400.mediaprojectionlib.recording

/**
 * Callback interface for video recording events.
 * This interface allows Unity to receive callbacks from the Android recording service.
 */
interface IVideoRecordingCallback {
    
    /**
     * Called when the recording state changes
     * @param state The new recording state as a string
     */
    fun onRecordingStateChanged(state: String)
    
    /**
     * Called when recording completes successfully
     * @param outputPath The path to the recorded video file
     */
    fun onRecordingComplete(outputPath: String)
    
    /**
     * Called when a recording error occurs
     * @param errorMessage Description of the error
     */
    fun onRecordingError(errorMessage: String)
}