package com.t34400.mediaprojectionlib.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * VideoRecordingService provides a foreground service for video recording operations.
 * This service is required for Android 14+ (API level 34) to maintain MediaProjection
 * functionality even when the app is backgrounded.
 *
 * The service manages the VideoRecordingManager lifecycle and provides notification
 * feedback to the user about recording status.
 */
class VideoRecordingService : Service() {
    
    companion object {
        private const val TAG = "VideoRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "video_recording_channel"
        
        // Intent actions
        const val ACTION_START_RECORDING = "action_start_recording"
        const val ACTION_STOP_RECORDING = "action_stop_recording"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        
        // Intent extras for recording configuration
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_VIDEO_FRAMERATE = "extra_video_framerate"
        const val EXTRA_OUTPUT_DIRECTORY = "extra_output_directory"
        const val EXTRA_MAX_DURATION_MS = "extra_max_duration_ms"
        
        /**
         * Start the video recording service
         */
        fun startService(context: Context) {
            val intent = Intent(context, VideoRecordingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Start video recording with configuration
         */
        fun startRecording(
            context: Context,
            videoBitrate: Int = 5_000_000,
            videoFrameRate: Int = 30,
            outputDirectory: String = "",
            maxDurationMs: Long = -1L
        ) {
            val intent = Intent(context, VideoRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_VIDEO_BITRATE, videoBitrate)
                putExtra(EXTRA_VIDEO_FRAMERATE, videoFrameRate)
                putExtra(EXTRA_OUTPUT_DIRECTORY, outputDirectory)
                putExtra(EXTRA_MAX_DURATION_MS, maxDurationMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Stop video recording
         */
        fun stopRecording(context: Context) {
            val intent = Intent(context, VideoRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Stop the service completely
         */
        fun stopService(context: Context) {
            val intent = Intent(context, VideoRecordingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
    
    // Service binder for local connections
    inner class VideoRecordingBinder : Binder() {
        fun getService(): VideoRecordingService = this@VideoRecordingService
    }
    
    private val binder = VideoRecordingBinder()
    private var videoRecordingManager: VideoRecordingManager? = null
    private var notificationManager: NotificationManager? = null
    
    // Recording state tracking
    private var isRecording = false
    private var recordingStartTime = 0L
    private var currentOutputFile: String? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VideoRecordingService created")
        
        // Initialize notification system
        setupNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Initialize VideoRecordingManager
        initializeRecordingManager()
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createIdleNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                handleStartRecording(intent)
            }
            ACTION_STOP_RECORDING -> {
                handleStopRecording()
            }
            ACTION_STOP_SERVICE -> {
                handleStopService()
            }
        }
        
        // Service should be restarted if killed by system during recording
        return if (isRecording) START_REDELIVER_INTENT else START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }
    
    override fun onDestroy() {
        Log.d(TAG, "VideoRecordingService destroyed")
        
        // Ensure recording is stopped
        if (isRecording) {
            videoRecordingManager?.stopRecording()
        }
        
        // Release resources
        videoRecordingManager?.release()
        videoRecordingManager = null
        
        super.onDestroy()
    }
    
    /**
     * Initialize the VideoRecordingManager with callbacks
     */
    private fun initializeRecordingManager() {
        videoRecordingManager = VideoRecordingManager(this).apply {
            // Setup callbacks for state changes
            onRecordingStateChanged = { state ->
                handleRecordingStateChanged(state)
            }
            
            onRecordingComplete = { outputPath ->
                handleRecordingComplete(outputPath)
            }
            
            onRecordingError = { errorMessage ->
                handleRecordingError(errorMessage)
            }
        }
        
        Log.d(TAG, "VideoRecordingManager initialized")
    }
    
    /**
     * Handle start recording intent
     */
    private fun handleStartRecording(intent: Intent) {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        val config = VideoRecordingManager.RecordingConfig(
            videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 5_000_000),
            videoFrameRate = intent.getIntExtra(EXTRA_VIDEO_FRAMERATE, 30),
            outputDirectory = intent.getStringExtra(EXTRA_OUTPUT_DIRECTORY) ?: "",
            maxRecordingDurationMs = intent.getLongExtra(EXTRA_MAX_DURATION_MS, -1L)
        )
        
        Log.i(TAG, "Starting recording with config: $config")
        
        val success = videoRecordingManager?.startRecording(config) ?: false
        if (success) {
            recordingStartTime = System.currentTimeMillis()
            updateNotification(createRecordingNotification("Preparing to record..."))
        } else {
            Log.e(TAG, "Failed to start recording")
            updateNotification(createErrorNotification("Failed to start recording"))
        }
    }
    
    /**
     * Handle stop recording intent
     */
    private fun handleStopRecording() {
        if (!isRecording) {
            Log.w(TAG, "No recording in progress")
            return
        }
        
        Log.i(TAG, "Stopping recording")
        updateNotification(createRecordingNotification("Stopping recording..."))
        
        val success = videoRecordingManager?.stopRecording() ?: false
        if (!success) {
            Log.e(TAG, "Failed to stop recording")
            updateNotification(createErrorNotification("Failed to stop recording"))
        }
    }
    
    /**
     * Handle stop service intent
     */
    private fun handleStopService() {
        Log.i(TAG, "Stopping service")
        
        if (isRecording) {
            videoRecordingManager?.stopRecording()
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Handle recording state changes
     */
    private fun handleRecordingStateChanged(state: VideoRecordingManager.RecordingState) {
        Log.d(TAG, "Recording state changed: $state")
        
        when (state) {
            VideoRecordingManager.RecordingState.PREPARING -> {
                updateNotification(createRecordingNotification("Preparing to record..."))
            }
            VideoRecordingManager.RecordingState.RECORDING -> {
                isRecording = true
                updateNotification(createRecordingNotification("Recording in progress"))
            }
            VideoRecordingManager.RecordingState.STOPPING -> {
                updateNotification(createRecordingNotification("Stopping recording..."))
            }
            VideoRecordingManager.RecordingState.IDLE -> {
                isRecording = false
                if (currentOutputFile != null) {
                    updateNotification(createCompletedNotification(currentOutputFile!!))
                } else {
                    updateNotification(createIdleNotification())
                }
            }
            VideoRecordingManager.RecordingState.ERROR -> {
                isRecording = false
                updateNotification(createErrorNotification("Recording error occurred"))
            }
            else -> {
                // Handle other states as needed
            }
        }
    }
    
    /**
     * Handle recording completion
     */
    private fun handleRecordingComplete(outputPath: String) {
        Log.i(TAG, "Recording completed: $outputPath")
        
        currentOutputFile = outputPath
        isRecording = false
        
        val duration = System.currentTimeMillis() - recordingStartTime
        val durationText = formatDuration(duration)
        
        updateNotification(createCompletedNotification(outputPath, durationText))
        
        // Auto-stop service after recording completion (optional)
        // You may want to keep it running for multiple recordings
        // stopSelf()
    }
    
    /**
     * Handle recording errors
     */
    private fun handleRecordingError(errorMessage: String) {
        Log.e(TAG, "Recording error: $errorMessage")
        
        isRecording = false
        updateNotification(createErrorNotification(errorMessage))
    }
    
    /**
     * Setup notification channel for Android 8.0+
     */
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for video recording status"
                setSound(null, null) // Disable sound for recording notifications
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
    
    /**
     * Create idle state notification
     */
    private fun createIdleNotification(): Notification {
        val stopIntent = Intent(this, VideoRecordingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Recording Service")
            .setContentText("Ready to record")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Service",
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * Create recording in progress notification
     */
    private fun createRecordingNotification(status: String): Notification {
        val stopIntent = Intent(this, VideoRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val recordingDuration = if (recordingStartTime > 0) {
            formatDuration(System.currentTimeMillis() - recordingStartTime)
        } else ""
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Screen")
            .setContentText("$status $recordingDuration")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Use a better icon in production
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Recording",
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * Create recording completed notification
     */
    private fun createCompletedNotification(outputPath: String, duration: String = ""): Notification {
        val stopServiceIntent = Intent(this, VideoRecordingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent = PendingIntent.getService(
            this, 0, stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Completed")
            .setContentText("Saved to: ${outputPath.substringAfterLast('/')} $duration")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Close",
                stopServicePendingIntent
            )
            .build()
    }
    
    /**
     * Create error notification
     */
    private fun createErrorNotification(errorMessage: String): Notification {
        val stopServiceIntent = Intent(this, VideoRecordingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent = PendingIntent.getService(
            this, 0, stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Error")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Close",
                stopServicePendingIntent
            )
            .build()
    }
    
    /**
     * Update the foreground notification
     */
    private fun updateNotification(notification: Notification) {
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Format duration in milliseconds to readable string
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        
        return if (hours > 0) {
            String.format("(%02d:%02d:%02d)", hours, minutes, seconds)
        } else {
            String.format("(%02d:%02d)", minutes, seconds)
        }
    }
    
    /**
     * Get current recording manager for external access
     */
    fun getRecordingManager(): VideoRecordingManager? = videoRecordingManager
    
    /**
     * Check if currently recording
     */
    fun isCurrentlyRecording(): Boolean = isRecording
    
    /**
     * Get current output file path
     */
    fun getCurrentOutputFile(): String? = currentOutputFile
}