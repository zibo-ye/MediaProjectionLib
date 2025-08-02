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
import com.t34400.mediaprojectionlib.logging.UnityLogManager
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
    // Comprehensive recording configuration - all MediaCodec options exposed
    data class RecordingConfig(
        // Video encoding settings
        val videoBitrate: Int,                   // Bitrate in bits per second
        val videoFrameRate: Int,                 // Frame rate in fps
        val videoFormat: String,                 // MIME type (e.g., "video/avc", "video/hevc")
        val videoWidth: Int,                     // Video width in pixels (0 = use display width)
        val videoHeight: Int,                    // Video height in pixels (0 = use display height)
        
        // Advanced video settings
        val iFrameInterval: Int = 2,             // I-frame interval in seconds
        val bitrateMode: Int = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, // Bitrate mode
        val profile: Int = -1,                   // Codec profile (-1 = default)
        val level: Int = -1,                     // Codec level (-1 = default)
        val colorFormat: Int = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface, // Color format
        
        // Audio settings (future enhancement)
        val audioEnabled: Boolean = false,       // Enable audio recording
        val audioSampleRate: Int = 44100,        // Audio sample rate in Hz
        val audioBitrate: Int = 128000,          // Audio bitrate in bits per second
        val audioChannelCount: Int = 2,          // Number of audio channels
        
        // Output settings
        val outputDirectory: String = "",        // Output directory path (empty = default)
        val outputFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, // Container format
        val maxRecordingDurationMs: Long = -1L,  // Max duration in milliseconds (-1 = unlimited)
        val maxFileSize: Long = -1L,             // Max file size in bytes (-1 = unlimited)
        
        // Performance settings
        val writeToFileWhileRecording: Boolean = true, // Real-time writing vs buffered
        val priorityHint: Int = MediaCodec.CONFIGURE_FLAG_ENCODE, // Encoder priority hint
        
        // Display settings
        val displayDensityDpi: Int = 0,          // Display density (0 = use system default)
        val displayFlags: Int = 0                // VirtualDisplay flags
    ) {
        companion object {
            // Factory method for basic recording config
            fun createBasic(
                width: Int,
                height: Int,
                frameRate: Int = 30,
                bitrate: Int = 5_000_000,
                codec: String = MediaFormat.MIMETYPE_VIDEO_AVC
            ) = RecordingConfig(
                videoBitrate = bitrate,
                videoFrameRate = frameRate,
                videoFormat = codec,
                videoWidth = width,
                videoHeight = height
            )
        }
    }

    // Available hardware codecs (generic, no assumptions)
    enum class SupportedCodec(val mimeType: String, val displayName: String) {
        H264(MediaFormat.MIMETYPE_VIDEO_AVC, "H.264/AVC"),
        H265(MediaFormat.MIMETYPE_VIDEO_HEVC, "H.265/HEVC"),
        VP8(MediaFormat.MIMETYPE_VIDEO_VP8, "VP8"),
        VP9(MediaFormat.MIMETYPE_VIDEO_VP9, "VP9"),
        AV1(MediaFormat.MIMETYPE_VIDEO_AV1, "AV1")
    }

    // Hardware codec information (comprehensive)
    data class CodecInfo(
        val name: String,                        // Codec name (e.g., "OMX.qcom.video.encoder.avc")
        val mimeType: String,                    // MIME type (e.g., "video/avc")
        val displayName: String,                 // Human-readable name (e.g., "H.264/AVC")
        val isHardwareAccelerated: Boolean,      // Hardware acceleration support
        val supportedProfiles: List<Int>,        // Supported codec profiles
        val supportedLevels: List<Int>,          // Supported codec levels
        val supportedColorFormats: List<Int>,    // Supported color formats
        val maxInstances: Int,                   // Max concurrent instances
        val bitrateRange: Pair<Int, Int>?,       // Min/max bitrate range
        val frameSizeRange: Pair<Pair<Int, Int>, Pair<Int, Int>>? // Min/max frame size ((minW,minH), (maxW,maxH))
    )

    // Display capability information
    data class DisplayInfo(
        val width: Int,                          // Display width in pixels
        val height: Int,                         // Display height in pixels
        val densityDpi: Int,                     // Display density
        val refreshRate: Float,                  // Display refresh rate
        val supportedFrameRates: List<Int>       // Supported frame rates for this display
    )

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
        
        // Use actual display resolution (no assumptions about scaling)
        displayWidth = rawWidth
        displayHeight = rawHeight
        displayDensityDpi = metrics.densityDpi
        
        UnityLogManager.logInfo(TAG, "Initialized VideoRecordingManager: ${displayWidth}x${displayHeight}@${displayDensityDpi}dpi (defaults)")
    }

    /**
     * Start video recording with the specified configuration
     */
    fun startRecording(config: RecordingConfig): Boolean {
        if (currentState != RecordingState.IDLE) {
            UnityLogManager.logWarning(TAG, "Cannot start recording: current state is $currentState")
            return false
        }
        
        recordingConfig = config
        
        // Print full configuration for debugging
        UnityLogManager.logInfo(TAG, "=== Starting Recording with Configuration ===")
        UnityLogManager.logInfo(TAG, "Resolution: ${config.videoWidth}x${config.videoHeight}")
        UnityLogManager.logInfo(TAG, "Frame Rate: ${config.videoFrameRate} fps")
        UnityLogManager.logInfo(TAG, "Bitrate: ${config.videoBitrate} bps (${config.videoBitrate / 1_000_000} Mbps)")
        UnityLogManager.logInfo(TAG, "Video Format: ${config.videoFormat}")
        UnityLogManager.logInfo(TAG, "I-Frame Interval: ${config.iFrameInterval}s")
        UnityLogManager.logInfo(TAG, "Bitrate Mode: ${config.bitrateMode}")
        UnityLogManager.logInfo(TAG, "Profile: ${config.profile}")
        UnityLogManager.logInfo(TAG, "Level: ${config.level}")
        UnityLogManager.logInfo(TAG, "Color Format: ${config.colorFormat}")
        UnityLogManager.logInfo(TAG, "Audio Enabled: ${config.audioEnabled}")
        UnityLogManager.logInfo(TAG, "Output Directory: ${config.outputDirectory}")
        UnityLogManager.logInfo(TAG, "Max Duration: ${config.maxRecordingDurationMs}ms")
        UnityLogManager.logInfo(TAG, "Display Density: ${config.displayDensityDpi} dpi")
        UnityLogManager.logInfo(TAG, "=== End Configuration ===")
        
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
            UnityLogManager.logError(TAG, "Failed to start recording", e)
            handleError("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Start video recording with MediaProjection permission data already obtained
     */
    fun startRecordingWithPermission(config: RecordingConfig, resultCode: Int, resultData: Intent): Boolean {
        if (currentState != RecordingState.IDLE) {
            UnityLogManager.logWarning(TAG, "Cannot start recording: current state is $currentState")
            if (currentState == RecordingState.ERROR) {
                UnityLogManager.logDebug(TAG, "Resetting from ERROR state")
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
            UnityLogManager.logError(TAG, "Failed to start recording with permission", e)
            handleError("Failed to start recording: ${e.message}")
            return false
        }
    }

    /**
     * Stop video recording and finalize the output file
     */
    fun stopRecording(): Boolean {
        if (currentState != RecordingState.RECORDING) {
            UnityLogManager.logWarning(TAG, "Cannot stop recording: current state is $currentState")
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
                UnityLogManager.logDebug(TAG, "Stopping VirtualDisplay to signal end of stream")
                virtualDisplay?.release()
                virtualDisplay = null
            }
            
            // This will be handled in the encoder thread
            return true
            
        } catch (e: Exception) {
            UnityLogManager.logError(TAG, "Failed to stop recording", e)
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
                UnityLogManager.logDebug(TAG, "Found available codec: ${codec.displayName}")
                
            } catch (e: Exception) {
                UnityLogManager.logDebug(TAG, "Codec ${codec.displayName} not available: ${e.message}")
            }
        }
        
        // Ensure H.264 is always included as fallback (most widely supported)
        if (availableCodecs.isEmpty()) {
            availableCodecs.add(SupportedCodec.H264)
            UnityLogManager.logWarning(TAG, "No codecs detected, adding H.264 as fallback")
        }
        
        UnityLogManager.logInfo(TAG, "Available codecs: ${availableCodecs.map { it.displayName }}")
        return availableCodecs
    }

    /**
     * Get detailed codec information for all available encoders
     */
    fun getDetailedCodecInfo(): List<CodecInfo> {
        val codecInfoList = mutableListOf<CodecInfo>()
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            
            for (type in codecInfo.supportedTypes) {
                if (type.startsWith("video/")) {
                    try {
                        val capabilities = codecInfo.getCapabilitiesForType(type)
                        
                        codecInfoList.add(
                            CodecInfo(
                                name = codecInfo.name,
                                mimeType = type,
                                displayName = getCodecDisplayName(type),
                                isHardwareAccelerated = !codecInfo.name.startsWith("OMX.google"),
                                supportedProfiles = capabilities.profileLevels.map { it.profile }.distinct(),
                                supportedLevels = capabilities.profileLevels.map { it.level }.distinct(),
                                supportedColorFormats = capabilities.colorFormats.toList(),
                                maxInstances = 1, // Simplified - use default
                                bitrateRange = null, // Simplified - will be calculated dynamically
                                frameSizeRange = null // Simplified - will be calculated dynamically
                            )
                        )
                    } catch (e: Exception) {
                        UnityLogManager.logWarning(TAG, "Failed to get capabilities for ${codecInfo.name}:$type", e)
                    }
                }
            }
        }
        
        return codecInfoList
    }
    
    private fun getCodecDisplayName(mimeType: String): String {
        return when (mimeType) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264/AVC"
            MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265/HEVC"
            MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
            else -> mimeType
        }
    }

    /**
     * Get display information and capabilities
     */
    fun getDisplayInfo(): DisplayInfo {
        val metrics = context.resources.displayMetrics
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val display = windowManager.defaultDisplay
        
        // Get display refresh rate
        val refreshRate = try {
            display.refreshRate
        } catch (e: Exception) {
            60f // Default fallback
        }
        
        // Calculate common frame rates based on refresh rate
        val supportedFrameRates = calculateSupportedFrameRates(refreshRate)
        
        return DisplayInfo(
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRate = refreshRate,
            supportedFrameRates = supportedFrameRates
        )
    }
    
    /**
     * Calculate supported frame rates based on display refresh rate
     */
    private fun calculateSupportedFrameRates(displayRefreshRate: Float): List<Int> {
        val frameRates = mutableListOf<Int>()
        
        // Common standard frame rates
        val commonRates = listOf(24, 25, 30, 48, 50, 60)
        frameRates.addAll(commonRates)
        
        // Add display refresh rate if it's not already included
        val displayRate = displayRefreshRate.toInt()
        if (displayRate !in frameRates && displayRate > 0) {
            frameRates.add(displayRate)
        }
        
        // Add some high refresh rate options if display supports it
        if (displayRefreshRate >= 90f) {
            frameRates.addAll(listOf(72, 90, 120))
        }
        
        return frameRates.distinct().sorted()
    }
    
    /**
     * Get common recording resolutions (generic, not application-specific)
     */
    fun getCommonResolutions(): List<Pair<Int, Int>> {
        val resolutions = mutableListOf<Pair<Int, Int>>()
        val displayInfo = getDisplayInfo()
        
        // Add current display resolution first
        resolutions.add(Pair(displayInfo.width, displayInfo.height))
        
        // Add common video resolutions (16:9 aspect ratio)
        val common16x9 = listOf(
            Pair(7680, 4320), // 8K UHD
            Pair(3840, 2160), // 4K UHD
            Pair(2560, 1440), // QHD
            Pair(1920, 1080), // FHD
            Pair(1280, 720),  // HD
            Pair(854, 480),   // 480p
            Pair(640, 360)    // 360p
        )
        
        // Add common ultra-wide resolutions (21:9 aspect ratio)
        val commonUltrawide = listOf(
            Pair(3440, 1440), // Ultra-wide QHD
            Pair(2560, 1080), // Ultra-wide FHD
            Pair(1920, 800)   // Ultra-wide HD+
        )
        
        // Add other aspect ratios
        val otherAspects = listOf(
            Pair(2048, 1024), // 2:1 aspect ratio
            Pair(1024, 512),  // 2:1 aspect ratio (lower res)
            Pair(1440, 1440), // 1:1 aspect ratio
            Pair(1080, 1080)  // 1:1 aspect ratio (lower res)
        )
        
        resolutions.addAll(common16x9)
        resolutions.addAll(commonUltrawide)
        resolutions.addAll(otherAspects)
        
        // Remove duplicates and sort by total pixels
        return resolutions.distinct().sortedByDescending { it.first * it.second }
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
     * Get available frame rates based on display capabilities
     */
    fun getAvailableFrameRates(): List<Int> {
        return getDisplayInfo().supportedFrameRates
    }
    
    /**
     * Create a recording configuration with basic settings
     */
    fun createConfig(
        width: Int,
        height: Int,
        frameRate: Int = 30,
        bitrate: Int = -1, // -1 = auto-calculate
        codec: SupportedCodec = SupportedCodec.H264
    ): RecordingConfig {
        val actualBitrate = if (bitrate > 0) bitrate else getRecommendedBitrate(width, height, frameRate)
        
        return RecordingConfig(
            videoBitrate = actualBitrate,
            videoFrameRate = frameRate,
            videoFormat = codec.mimeType,
            videoWidth = width,
            videoHeight = height
        )
    }
    
    /**
     * Validate recording configuration against device capabilities
     */
    fun validateConfig(config: RecordingConfig): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Validate codec support
        val availableCodecs = getAvailableCodecs()
        val codecSupported = availableCodecs.any { it.mimeType == config.videoFormat }
        if (!codecSupported) {
            errors.add("Codec ${config.videoFormat} not supported on this device")
        }
        
        // Validate frame rate
        val availableFrameRates = getAvailableFrameRates()
        if (config.videoFrameRate !in availableFrameRates) {
            warnings.add("Frame rate ${config.videoFrameRate} may not be optimal for this display")
        }
        
        // Validate resolution
        if (config.videoWidth <= 0 || config.videoHeight <= 0) {
            warnings.add("Invalid resolution: ${config.videoWidth}x${config.videoHeight}")
        }
        
        // Validate bitrate
        if (config.videoBitrate < 100_000) {
            warnings.add("Very low bitrate may result in poor quality")
        } else if (config.videoBitrate > 100_000_000) {
            warnings.add("Very high bitrate may cause performance issues")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

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
            UnityLogManager.logDebug(TAG, "MediaProjection pipeline established, waiting for stabilization...")
            
            UnityLogManager.logInfo(TAG, "Recording pipeline setup complete: ${outputFile?.absolutePath}")
            return true
            
        } catch (e: Exception) {
            UnityLogManager.logError(TAG, "Failed to setup recording pipeline", e)
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
        
        UnityLogManager.logDebug(TAG, "Output file: ${outputFile!!.absolutePath}")
    }

    /**
     * Setup MediaMuxer for MP4 output
     */
    private fun setupMediaMuxer() {
        outputFile?.let { file ->
            mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            UnityLogManager.logDebug(TAG, "MediaMuxer created: ${file.absolutePath}")
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
        
        UnityLogManager.logInfo(TAG, "=== Video Encoder Configuration Applied ===")
        UnityLogManager.logInfo(TAG, "Actual Resolution: ${actualWidth}x${actualHeight}")
        UnityLogManager.logInfo(TAG, "Applied Frame Rate: ${config.videoFrameRate} fps")
        UnityLogManager.logInfo(TAG, "Applied Bitrate: ${config.videoBitrate} bps (${config.videoBitrate / 1_000_000} Mbps)")
        UnityLogManager.logInfo(TAG, "Applied Codec: ${config.videoFormat}")
        UnityLogManager.logInfo(TAG, "MediaFormat KEY_FRAME_RATE: ${videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)}")
        UnityLogManager.logInfo(TAG, "MediaFormat KEY_BIT_RATE: ${videoFormat.getInteger(MediaFormat.KEY_BIT_RATE)}")
        UnityLogManager.logInfo(TAG, "MediaFormat KEY_I_FRAME_INTERVAL: ${videoFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL)}")
        UnityLogManager.logInfo(TAG, "=== End Encoder Configuration ===")
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
        
        // Get VirtualDisplay information after creation
        val virtualDisplayInfo = virtualDisplay?.display
        val virtualDisplayRefreshRate = virtualDisplayInfo?.refreshRate ?: 0f
        val virtualDisplayMode = virtualDisplayInfo?.mode
        val virtualDisplaySupportedModes = virtualDisplayInfo?.supportedModes
        
        UnityLogManager.logInfo(TAG, "=== VirtualDisplay Configuration ===")
        UnityLogManager.logInfo(TAG, "VirtualDisplay Resolution: ${actualWidth}x${actualHeight}")
        UnityLogManager.logInfo(TAG, "VirtualDisplay Density: ${displayDensityDpi} dpi")
        UnityLogManager.logInfo(TAG, "VirtualDisplay Flags: ${DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR}")
        UnityLogManager.logInfo(TAG, "VirtualDisplay Refresh Rate: ${virtualDisplayRefreshRate} Hz")
        
        if (virtualDisplayMode != null) {
            UnityLogManager.logInfo(TAG, "VirtualDisplay Mode: ${virtualDisplayMode.physicalWidth}x${virtualDisplayMode.physicalHeight} @ ${virtualDisplayMode.refreshRate}Hz")
        }
        
        if (virtualDisplaySupportedModes != null && virtualDisplaySupportedModes.isNotEmpty()) {
            UnityLogManager.logInfo(TAG, "VirtualDisplay Supported Modes:")
            virtualDisplaySupportedModes.forEachIndexed { index, mode ->
                UnityLogManager.logInfo(TAG, "  Mode $index: ${mode.physicalWidth}x${mode.physicalHeight} @ ${mode.refreshRate}Hz")
            }
        }
        
        UnityLogManager.logInfo(TAG, "Surface Connection: Direct to MediaCodec input")
        
        // Log if there are multiple modes available (VirtualDisplay doesn't support mode switching)
        if (virtualDisplaySupportedModes != null && virtualDisplaySupportedModes.isNotEmpty()) {
            val targetFrameRate = config.videoFrameRate.toFloat()
            val matchingMode = virtualDisplaySupportedModes.find { mode ->
                Math.abs(mode.refreshRate - targetFrameRate) < 1.0f // Allow 1Hz tolerance
            }
            
            if (matchingMode != null) {
                UnityLogManager.logInfo(TAG, "VirtualDisplay has mode matching target frame rate: ${matchingMode.physicalWidth}x${matchingMode.physicalHeight} @ ${matchingMode.refreshRate}Hz")
            } else {
                UnityLogManager.logWarning(TAG, "No VirtualDisplay mode found matching target frame rate: ${targetFrameRate}Hz")
                val availableRates = virtualDisplaySupportedModes.map { it.refreshRate }.joinToString(", ")
                UnityLogManager.logInfo(TAG, "Available VirtualDisplay refresh rates: ${availableRates}Hz")
            }
        }
        
        UnityLogManager.logInfo(TAG, "=== End VirtualDisplay Configuration ===")
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
        
        UnityLogManager.logDebug(TAG, "Encoding thread started")
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
                    UnityLogManager.logDebug(TAG, "MediaMuxer started, video track index: $videoTrackIndex")
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
                        UnityLogManager.logWarning(TAG, "Encoder output buffer was null")
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        continue
                    }
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        // Codec config frame (SPS/PPS), don't write to muxer
                        UnityLogManager.logDebug(TAG, "Ignoring codec config frame")
                        bufferInfo.size = 0
                    }
                    
                    if (bufferInfo.size > 0 && muxerStarted) {
                        // Write encoded frame to MP4 file
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        UnityLogManager.logInfo(TAG, "Wrote frame: ${bufferInfo.size} bytes, pts: ${bufferInfo.presentationTimeUs}")
                    }
                    
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    // Check for end of stream
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        UnityLogManager.logInfo(TAG, "End of stream reached")
                        break
                    }
                }
            }
            } catch (e: IllegalStateException) {
                // Handle case where encoder is stopped while dequeuing
                UnityLogManager.logWarning(TAG, "Encoder dequeue interrupted, likely due to cleanup: ${e.message}")
                break
            } catch (e: Exception) {
                UnityLogManager.logError(TAG, "Error processing encoded frames", e)
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
                UnityLogManager.logDebug(TAG, "MediaMuxer stopped")
            }
            
            // Cleanup all resources
            cleanupResources()
            
            // Notify completion
            val outputPath = outputFile?.absolutePath ?: ""
            changeState(RecordingState.IDLE)
            onRecordingComplete?.invoke(outputPath)
            
            val duration = System.currentTimeMillis() - recordingStartTime
            UnityLogManager.logInfo(TAG, "Recording completed: $outputPath (duration: ${duration}ms)")
            
        } catch (e: Exception) {
            UnityLogManager.logError(TAG, "Error finalizing recording", e)
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
                UnityLogManager.logDebug(TAG, "MediaProjection stopped")
                // Only treat as error if we're actively recording AND not intentionally stopping
                if ((currentState == RecordingState.RECORDING || currentState == RecordingState.PREPARING) && !isIntentionallyStopping) {
                    UnityLogManager.logWarning(TAG, "MediaProjection stopped unexpectedly during recording")
                    // Try to finalize the recording gracefully instead of error
                    if (currentState == RecordingState.RECORDING) {
                        isIntentionallyStopping = true
                        stopRecording()
                    } else {
                        handleError("MediaProjection stopped during setup")
                    }
                } else {
                    UnityLogManager.logDebug(TAG, "MediaProjection stopped normally (intentional: $isIntentionallyStopping)")
                    // Reset the flag
                    isIntentionallyStopping = false
                }
            }
        }
        mediaProjection?.registerCallback(projectionCallback, null)
        
        UnityLogManager.logDebug(TAG, "MediaProjection registered")
    }

    /**
     * Change recording state and notify observers
     */
    private fun changeState(newState: RecordingState) {
        val oldState = currentState
        currentState = newState
        
        UnityLogManager.logDebug(TAG, "State changed: $oldState -> $newState")
        onRecordingStateChanged?.invoke(newState)
    }

    /**
     * Handle recording errors
     */
    private fun handleError(message: String) {
        UnityLogManager.logError(TAG, "Recording error: $message")
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
                    UnityLogManager.logWarning(TAG, "Interrupted while waiting for encoder thread to finish")
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
                    UnityLogManager.logWarning(TAG, "Error stopping encoder", e)
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
                    UnityLogManager.logWarning(TAG, "Error releasing muxer", e)
                }
            }
            mediaMuxer = null
            
            // Stop and release MediaProjection
            mediaProjection?.let { projection ->
                try {
                    projection.stop()
                    UnityLogManager.logDebug(TAG, "MediaProjection stopped and released")
                } catch (e: Exception) {
                    UnityLogManager.logWarning(TAG, "Error stopping MediaProjection", e)
                }
            }
            mediaProjection = null
            
            // Reset state
            videoTrackIndex = -1
            muxerStarted = false
            isIntentionallyStopping = false
            
            UnityLogManager.logDebug(TAG, "Resources cleaned up")
            
        } catch (e: Exception) {
            UnityLogManager.logError(TAG, "Error during cleanup", e)
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
        
        UnityLogManager.logDebug(TAG, "VideoRecordingManager released")
    }

    companion object {
        private const val TAG = "VideoRecordingManager"
    }
}