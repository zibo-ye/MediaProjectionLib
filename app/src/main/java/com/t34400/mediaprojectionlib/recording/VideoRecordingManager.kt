package com.t34400.mediaprojectionlib.recording

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.t34400.mediaprojectionlib.core.MediaProjectionRequestActivity
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * VideoRecordingManager implements the zero-copy pipeline:
 * MediaProjection -> VirtualDisplay -> MediaCodec Input Surface -> Hardware Encoder -> Encoded Buffer -> MediaMuxer -> MP4 File
 *
 * This implementation follows the architectural blueprint for maximum performance by ensuring
 * video frame data never enters the Unity C# environment and utilizes hardware acceleration throughout.
 */
class VideoRecordingManager(
    private val context: Context,
    private val callback: MediaProjection.Callback? = null
) {
    // Recording configuration
    data class RecordingConfig(
        val videoBitrate: Int = 5_000_000,      // 5 Mbps
        val videoFrameRate: Int = 30,            // 30 fps
        val videoFormat: String = MediaFormat.MIMETYPE_VIDEO_AVC, // H.264
        val videoWidth: Int = 0,                 // 0 = use display width
        val videoHeight: Int = 0,                // 0 = use display height
        val audioEnabled: Boolean = false,       // Audio recording (future enhancement)
        val outputDirectory: String = "",        // Output directory path
        val maxRecordingDurationMs: Long = -1L,  // -1 for unlimited
        val writeToFileWhileRecording: Boolean = true // Real-time file writing
    )

    // Available hardware codecs
    enum class SupportedCodec(val mimeType: String, val displayName: String) {
        H264(MediaFormat.MIMETYPE_VIDEO_AVC, "H.264/AVC"),
        H265(MediaFormat.MIMETYPE_VIDEO_HEVC, "H.265/HEVC"),
        VP8(MediaFormat.MIMETYPE_VIDEO_VP8, "VP8"),
        VP9(MediaFormat.MIMETYPE_VIDEO_VP9, "VP9")
        // Note: AV1 support requires API 29+, uncomment when targeting higher API
        // AV1("video/av01", "AV1")
    }

    // Recording state
    enum class RecordingState {
        IDLE, PREPARING, RECORDING, PAUSING, STOPPING, ERROR
    }

    // Display and projection properties
    private val displayWidth: Int
    private val displayHeight: Int
    private val displayDensityDpi: Int
    
    // MediaProjection components
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    // MediaCodec components (zero-copy pipeline)
    private var videoEncoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    
    // Threading for background processing
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null
    
    // Recording state management
    private var currentState = RecordingState.IDLE
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var recordingConfig: RecordingConfig? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0L
    private var isIntentionallyStopping = false
    
    // Callbacks for Unity integration
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onRecordingComplete: ((String) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null

    init {
        // Initialize display metrics as defaults
        val metrics = context.resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels
        
        // Scale down if resolution is too high for performance (default fallback)
        val scale = if (maxOf(rawWidth, rawHeight) > 1920) {
            1920f / maxOf(rawWidth, rawHeight)
        } else 1f
        
        displayWidth = (rawWidth * scale).roundToInt()
        displayHeight = (rawHeight * scale).roundToInt()
        displayDensityDpi = metrics.densityDpi
        
        Log.d(TAG, "Initialized VideoRecordingManager: ${displayWidth}x${displayHeight}@${displayDensityDpi}dpi (defaults)")
    }

    /**
     * Start video recording with the specified configuration
     */
    fun startRecording(config: RecordingConfig): Boolean {
        if (currentState != RecordingState.IDLE) {
            Log.w(TAG, "Cannot start recording: current state is $currentState")
            return false
        }
        
        recordingConfig = config
        changeState(RecordingState.PREPARING)
        
        try {
            // Step 1: Request MediaProjection permission if needed
            if (mediaProjection == null) {
                MediaProjectionRequestActivity.requestMediaProjection(context) { context, resultData ->
                    registerMediaProjection(context, resultData)
                    setupRecordingPipeline()
                }
                return true
            }
            
            // Step 2: Setup the complete recording pipeline
            return setupRecordingPipeline()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            handleError("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Start video recording with MediaProjection permission data already obtained
     */
    fun startRecordingWithPermission(config: RecordingConfig, resultCode: Int, resultData: Intent): Boolean {
        if (currentState != RecordingState.IDLE) {
            Log.w(TAG, "Cannot start recording: current state is $currentState")
            if (currentState == RecordingState.ERROR) {
                Log.d(TAG, "Resetting from ERROR state")
                changeState(RecordingState.IDLE)
            } else {
                return false
            }
        }
        
        recordingConfig = config
        changeState(RecordingState.PREPARING)
        
        try {
            // Register MediaProjection with provided permission data
            registerMediaProjection(context, resultData)
            
            // Setup the complete recording pipeline
            return setupRecordingPipeline()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording with permission", e)
            handleError("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Stop video recording and finalize the output file
     */
    fun stopRecording(): Boolean {
        if (currentState != RecordingState.RECORDING) {
            Log.w(TAG, "Cannot stop recording: current state is $currentState")
            return false
        }
        
        changeState(RecordingState.STOPPING)
        isIntentionallyStopping = true
        
        try {
            // Stop the encoder gracefully by signaling end of stream to the input surface
            encoderInputSurface?.let { surface ->
                // For surface input, we need to signal end of stream differently
                // The proper way is to stop the VirtualDisplay first, which will cause
                // the encoder to receive end of stream when no more frames are available
                Log.d(TAG, "Stopping VirtualDisplay to signal end of stream")
                virtualDisplay?.release()
                virtualDisplay = null
            }
            
            // This will be handled in the encoder thread
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            handleError("Failed to stop recording: ${e.message}")
            return false
        }
    }

    /**
     * Get current recording status
     */
    fun getRecordingState(): RecordingState = currentState
    
    /**
     * Get current recording status as string (for Unity JNI bridge)
     */
    fun getRecordingStateString(): String = currentState.name
    
    /**
     * Get output file path (null if recording not started or completed)
     */
    fun getOutputFilePath(): String? = outputFile?.absolutePath

    /**
     * Get available hardware-accelerated video codecs on this device
     */
    fun getAvailableCodecs(): List<SupportedCodec> {
        val availableCodecs = mutableListOf<SupportedCodec>()
        
        for (codec in SupportedCodec.values()) {
            try {
                // Try to create an encoder for this codec to test availability
                val testFormat = MediaFormat.createVideoFormat(codec.mimeType, 1920, 1080).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                }
                
                val encoder = MediaCodec.createEncoderByType(codec.mimeType)
                encoder.release() // Clean up immediately
                
                availableCodecs.add(codec)
                Log.d(TAG, "Found available codec: ${codec.displayName}")
                
            } catch (e: Exception) {
                Log.d(TAG, "Codec ${codec.displayName} not available: ${e.message}")
            }
        }
        
        // Ensure H.264 is always included as fallback (most widely supported)
        if (availableCodecs.isEmpty()) {
            availableCodecs.add(SupportedCodec.H264)
            Log.w(TAG, "No codecs detected, adding H.264 as fallback")
        }
        
        Log.i(TAG, "Available codecs: ${availableCodecs.map { it.displayName }}")
        return availableCodecs
    }

    /**
     * Get optimal recording resolution for current display and VR requirements
     */
    fun getOptimalResolutions(): List<Pair<Int, Int>> {
        val resolutions = mutableListOf<Pair<Int, Int>>()
        
        // VR-optimized resolutions for high-quality recording
        // These are designed for VR content capture and streaming
        val vrResolutions = listOf(
            Pair(3840, 2160), // 4K UHD - Premium VR quality
            Pair(2560, 1440), // QHD - High VR quality
            Pair(1920, 1080), // FHD - Standard VR quality
            Pair(1280, 720),  // HD - Performance VR mode
            Pair(1024, 512),  // Ultra-wide VR (2:1 aspect ratio)
            Pair(2048, 1024), // High-res ultra-wide VR
        )
        
        // Always include VR resolutions regardless of display size
        // VR recording often needs higher resolution than the display
        resolutions.addAll(vrResolutions)
        
        // Add current display resolution as fallback if not already included
        val displayRes = Pair(displayWidth, displayHeight)
        if (!resolutions.contains(displayRes)) {
            resolutions.add(displayRes)
        }
        
        Log.d(TAG, "Optimal resolutions for VR recording: $resolutions")
        return resolutions
    }

    /**
     * Get recommended bitrates for different resolutions
     */
    fun getRecommendedBitrate(width: Int, height: Int, frameRate: Int = 30): Int {
        val pixels = width * height
        val basePixels = 1920 * 1080 // 1080p reference
        val baseBitrate = 5_000_000 // 5 Mbps for 1080p
        
        // Scale bitrate based on resolution and frame rate
        val scaleFactor = (pixels.toFloat() / basePixels) * (frameRate / 30f)
        val recommendedBitrate = (baseBitrate * scaleFactor).toInt()
        
        // Clamp to reasonable ranges
        return recommendedBitrate.coerceIn(1_000_000, 50_000_000) // 1-50 Mbps
    }

    /**
     * Setup the complete zero-copy recording pipeline
     */
    private fun setupRecordingPipeline(): Boolean {
        val config = recordingConfig ?: return false
        
        try {
            // Step 1: Create output file
            setupOutputFile(config)
            
            // Step 2: Create MediaMuxer
            setupMediaMuxer()
            
            // Step 3: Create and configure MediaCodec encoder
            setupVideoEncoder(config)
            
            // Step 4: Create VirtualDisplay with encoder input surface
            setupVirtualDisplay()
            
            // Step 5: Start encoding thread
            startEncodingThread()
            
            // Step 6: Begin recording
            recordingStartTime = System.currentTimeMillis()
            changeState(RecordingState.RECORDING)
            
            // Give MediaProjection a moment to stabilize before declaring success
            Log.d(TAG, "MediaProjection pipeline established, waiting for stabilization...")
            
            Log.i(TAG, "Recording pipeline setup complete: ${outputFile?.absolutePath}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup recording pipeline", e)
            handleError("Failed to setup recording pipeline: ${e.message}")
            cleanupResources()
            return false
        }
    }

    /**
     * Create output file for recording
     */
    private fun setupOutputFile(config: RecordingConfig) {
        val outputDir = if (config.outputDirectory.isNotEmpty()) {
            File(config.outputDirectory)
        } else {
            File(context.getExternalFilesDir(null), "recordings")
        }
        
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        
        val timestamp = System.currentTimeMillis()
        outputFile = File(outputDir, "recording_$timestamp.mp4")
        
        Log.d(TAG, "Output file: ${outputFile!!.absolutePath}")
    }

    /**
     * Setup MediaMuxer for MP4 output
     */
    private fun setupMediaMuxer() {
        outputFile?.let { file ->
            mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.d(TAG, "MediaMuxer created: ${file.absolutePath}")
        }
    }

    /**
     * Setup MediaCodec hardware encoder with optimal settings
     */
    private fun setupVideoEncoder(config: RecordingConfig) {
        // Determine actual recording resolution
        val actualWidth = if (config.videoWidth > 0) config.videoWidth else displayWidth
        val actualHeight = if (config.videoHeight > 0) config.videoHeight else displayHeight
        
        // Create video format with configurable codec and resolution
        val videoFormat = MediaFormat.createVideoFormat(config.videoFormat, actualWidth, actualHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.videoFrameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // I-frame every 2 seconds
            
            // Configure codec-specific settings
            when (config.videoFormat) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> {
                    // H.264 settings
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                }
                MediaFormat.MIMETYPE_VIDEO_HEVC -> {
                    // H.265 settings
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31)
                }
                // VP8, VP9, AV1 use default settings
            }
        }
        
        // Create encoder
        videoEncoder = MediaCodec.createEncoderByType(config.videoFormat)
        videoEncoder!!.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        // Get the input surface for zero-copy pipeline
        encoderInputSurface = videoEncoder!!.createInputSurface()
        
        Log.d(TAG, "Video encoder configured: ${actualWidth}x${actualHeight}, ${config.videoBitrate} bps, ${config.videoFrameRate} fps, codec: ${config.videoFormat}")
    }

    /**
     * Setup VirtualDisplay connected to encoder input surface (zero-copy pipeline)
     */
    private fun setupVirtualDisplay() {
        val projection = mediaProjection ?: throw IllegalStateException("MediaProjection not available")
        val surface = encoderInputSurface ?: throw IllegalStateException("Encoder input surface not available")
        val config = recordingConfig ?: throw IllegalStateException("Recording config not available")
        
        // Use configurable resolution for VirtualDisplay
        val actualWidth = if (config.videoWidth > 0) config.videoWidth else displayWidth
        val actualHeight = if (config.videoHeight > 0) config.videoHeight else displayHeight
        
        virtualDisplay = projection.createVirtualDisplay(
            "VideoRecording",
            actualWidth,
            actualHeight,
            displayDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, // Direct connection to MediaCodec input surface
            null,
            null
        )
        
        Log.d(TAG, "VirtualDisplay created with direct surface connection")
    }

    /**
     * Start encoding thread for processing encoded frames
     */
    private fun startEncodingThread() {
        encoderThread = HandlerThread("VideoEncodingThread").apply {
            start()
        }
        encoderHandler = Handler(encoderThread!!.looper)
        
        // Start the encoder
        videoEncoder!!.start()
        
        // Start processing encoded frames
        encoderHandler!!.post { processEncodedFrames() }
        
        Log.d(TAG, "Encoding thread started")
    }

    /**
     * Process encoded frames from MediaCodec and write to MediaMuxer
     * This runs on the background encoding thread
     */
    private fun processEncodedFrames() {
        val encoder = videoEncoder ?: return
        val muxer = mediaMuxer ?: return
        
        val bufferInfo = MediaCodec.BufferInfo()
        
        while (currentState == RecordingState.RECORDING || currentState == RecordingState.STOPPING) {
            try {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000) // 10ms timeout
            
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Encoder output format changed, setup muxer
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                    Log.d(TAG, "MediaMuxer started, video track index: $videoTrackIndex")
                }
                
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available, continue
                    if (currentState == RecordingState.STOPPING) {
                        break
                    }
                }
                
                outputBufferIndex >= 0 -> {
                    // Got encoded frame
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    
                    if (encodedData == null) {
                        Log.w(TAG, "Encoder output buffer was null")
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        continue
                    }
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Codec config frame (SPS/PPS), don't write to muxer
                        Log.d(TAG, "Ignoring codec config frame")
                        bufferInfo.size = 0
                    }
                    
                    if (bufferInfo.size > 0 && muxerStarted) {
                        // Write encoded frame to MP4 file
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        Log.v(TAG, "Wrote frame: ${bufferInfo.size} bytes, pts: ${bufferInfo.presentationTimeUs}")
                    }
                    
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    // Check for end of stream
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.i(TAG, "End of stream reached")
                        break
                    }
                }
            }
            } catch (e: IllegalStateException) {
                // Handle case where encoder is stopped while dequeuing
                Log.w(TAG, "Encoder dequeue interrupted, likely due to cleanup: ${e.message}")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error processing encoded frames", e)
                break
            }
        }
        
        // Finalize recording
        finalizeRecording()
    }

    /**
     * Finalize recording and cleanup resources
     */
    private fun finalizeRecording() {
        try {
            // Stop and release MediaMuxer
            if (muxerStarted) {
                mediaMuxer?.stop()
                Log.d(TAG, "MediaMuxer stopped")
            }
            
            // Cleanup all resources
            cleanupResources()
            
            // Notify completion
            val outputPath = outputFile?.absolutePath ?: ""
            changeState(RecordingState.IDLE)
            onRecordingComplete?.invoke(outputPath)
            
            val duration = System.currentTimeMillis() - recordingStartTime
            Log.i(TAG, "Recording completed: $outputPath (duration: ${duration}ms)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording", e)
            handleError("Error finalizing recording: ${e.message}")
        }
    }

    /**
     * Register MediaProjection from permission result
     */
    private fun registerMediaProjection(context: Context, resultData: Intent) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(RESULT_OK, resultData)
        
        // Register a callback to handle MediaProjection lifecycle
        val projectionCallback = callback ?: object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                // Only treat as error if we're actively recording AND not intentionally stopping
                if ((currentState == RecordingState.RECORDING || currentState == RecordingState.PREPARING) && !isIntentionallyStopping) {
                    Log.w(TAG, "MediaProjection stopped unexpectedly during recording")
                    // Try to finalize the recording gracefully instead of error
                    if (currentState == RecordingState.RECORDING) {
                        isIntentionallyStopping = true
                        stopRecording()
                    } else {
                        handleError("MediaProjection stopped during setup")
                    }
                } else {
                    Log.d(TAG, "MediaProjection stopped normally (intentional: $isIntentionallyStopping)")
                    // Reset the flag
                    isIntentionallyStopping = false
                }
            }
        }
        mediaProjection?.registerCallback(projectionCallback, null)
        
        Log.d(TAG, "MediaProjection registered")
    }

    /**
     * Change recording state and notify observers
     */
    private fun changeState(newState: RecordingState) {
        val oldState = currentState
        currentState = newState
        
        Log.d(TAG, "State changed: $oldState -> $newState")
        onRecordingStateChanged?.invoke(newState)
    }

    /**
     * Handle recording errors
     */
    private fun handleError(message: String) {
        Log.e(TAG, "Recording error: $message")
        changeState(RecordingState.ERROR)
        onRecordingError?.invoke(message)
        cleanupResources()
    }

    /**
     * Cleanup all recording resources
     */
    private fun cleanupResources() {
        try {
            // Stop VirtualDisplay first to stop new frames
            virtualDisplay?.release()
            virtualDisplay = null
            
            // Stop encoding thread BEFORE stopping encoder
            encoderThread?.let { thread ->
                thread.quitSafely()
                try {
                    thread.join(2000) // Wait up to 2 seconds
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Interrupted while waiting for encoder thread to finish")
                }
            }
            encoderThread = null
            encoderHandler = null
            
            // Now stop and release MediaCodec
            videoEncoder?.let { encoder ->
                try {
                    encoder.stop()
                    encoder.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping encoder", e)
                }
            }
            videoEncoder = null
            
            // Release encoder input surface
            encoderInputSurface?.release()
            encoderInputSurface = null
            
            // Release MediaMuxer
            mediaMuxer?.let { muxer ->
                try {
                    muxer.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing muxer", e)
                }
            }
            mediaMuxer = null
            
            // Stop and release MediaProjection
            mediaProjection?.let { projection ->
                try {
                    projection.stop()
                    Log.d(TAG, "MediaProjection stopped and released")
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaProjection", e)
                }
            }
            mediaProjection = null
            
            // Reset state
            videoTrackIndex = -1
            muxerStarted = false
            isIntentionallyStopping = false
            
            Log.d(TAG, "Resources cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Release all resources when VideoRecordingManager is no longer needed
     */
    fun release() {
        if (currentState == RecordingState.RECORDING) {
            stopRecording()
        }
        
        cleanupResources()
        
        // MediaProjection is already stopped in cleanupResources()
        // No need to stop it again here
        
        Log.d(TAG, "VideoRecordingManager released")
    }

    companion object {
        private const val TAG = "VideoRecordingManager"
    }
}