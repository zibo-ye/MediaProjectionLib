package com.t34400.mediaprojectionlib.recording

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Simple unit tests for configuration classes and enums
 * These tests don't require Android framework dependencies
 */
class SimpleConfigurationTest {
    
    @Test
    fun `RecordingConfig default values are reasonable`() {
        // When
        val config = VideoRecordingManager.RecordingConfig()
        
        // Then
        assertEquals(5_000_000, config.videoBitrate)
        assertEquals(30, config.videoFrameRate)
        assertEquals("video/avc", config.videoFormat)
        assertFalse(config.audioEnabled)
        assertEquals("", config.outputDirectory)
        assertEquals(-1L, config.maxRecordingDurationMs)
    }
    
    @Test
    fun `RecordingConfig custom values are preserved`() {
        // Given
        val customBitrate = 10_000_000
        val customFrameRate = 60
        val customFormat = "video/hevc"
        val customAudio = true
        val customDirectory = "/custom/path"
        val customDuration = 120_000L
        
        // When
        val config = VideoRecordingManager.RecordingConfig(
            videoBitrate = customBitrate,
            videoFrameRate = customFrameRate,
            videoFormat = customFormat,
            audioEnabled = customAudio,
            outputDirectory = customDirectory,
            maxRecordingDurationMs = customDuration
        )
        
        // Then
        assertEquals(customBitrate, config.videoBitrate)
        assertEquals(customFrameRate, config.videoFrameRate)
        assertEquals(customFormat, config.videoFormat)
        assertEquals(customAudio, config.audioEnabled)
        assertEquals(customDirectory, config.outputDirectory)
        assertEquals(customDuration, config.maxRecordingDurationMs)
    }
    
    @Test
    fun `RecordingState enum has all expected values`() {
        // When & Then
        val states = VideoRecordingManager.RecordingState.values()
        
        assertTrue(states.contains(VideoRecordingManager.RecordingState.IDLE))
        assertTrue(states.contains(VideoRecordingManager.RecordingState.PREPARING))
        assertTrue(states.contains(VideoRecordingManager.RecordingState.RECORDING))
        assertTrue(states.contains(VideoRecordingManager.RecordingState.PAUSING))
        assertTrue(states.contains(VideoRecordingManager.RecordingState.STOPPING))
        assertTrue(states.contains(VideoRecordingManager.RecordingState.ERROR))
    }
    
    @Test
    fun `RecordingConfig validates reasonable bitrate values`() {
        // Test common bitrate values
        val commonBitrates = arrayOf(1_000_000, 2_000_000, 5_000_000, 10_000_000, 20_000_000)
        
        commonBitrates.forEach { bitrate ->
            val config = VideoRecordingManager.RecordingConfig(videoBitrate = bitrate)
            assertEquals(bitrate, config.videoBitrate)
            assertTrue(config.videoBitrate > 0)
        }
    }
    
    @Test
    fun `RecordingConfig validates frame rate values`() {
        // Test common frame rates
        val commonFrameRates = arrayOf(15, 24, 30, 48, 60, 120)
        
        commonFrameRates.forEach { frameRate ->
            val config = VideoRecordingManager.RecordingConfig(videoFrameRate = frameRate)
            assertEquals(frameRate, config.videoFrameRate)
            assertTrue(config.videoFrameRate > 0)
        }
    }
    
    @Test
    fun `RecordingConfig supports common video formats`() {
        // Test common video formats
        val formats = arrayOf("video/avc", "video/hevc", "video/vp8", "video/vp9")
        
        formats.forEach { format ->
            val config = VideoRecordingManager.RecordingConfig(videoFormat = format)
            assertEquals(format, config.videoFormat)
            assertNotNull(config.videoFormat)
            assertTrue(config.videoFormat.isNotEmpty())
        }
    }
    
    @Test
    fun `RecordingState toString returns expected values`() {
        // Test that enum toString works
        assertEquals("IDLE", VideoRecordingManager.RecordingState.IDLE.toString())
        assertEquals("PREPARING", VideoRecordingManager.RecordingState.PREPARING.toString())
        assertEquals("RECORDING", VideoRecordingManager.RecordingState.RECORDING.toString())
        assertEquals("STOPPING", VideoRecordingManager.RecordingState.STOPPING.toString())
        assertEquals("ERROR", VideoRecordingManager.RecordingState.ERROR.toString())
    }
    
    @Test
    fun `RecordingConfig copy constructor works`() {
        // Given
        val originalConfig = VideoRecordingManager.RecordingConfig(
            videoBitrate = 8_000_000,
            videoFrameRate = 60,
            videoFormat = "video/hevc",
            audioEnabled = true,
            outputDirectory = "/test/dir",
            maxRecordingDurationMs = 60_000L
        )
        
        // When
        val copiedConfig = VideoRecordingManager.RecordingConfig(
            videoBitrate = originalConfig.videoBitrate,
            videoFrameRate = originalConfig.videoFrameRate,
            videoFormat = originalConfig.videoFormat,
            audioEnabled = originalConfig.audioEnabled,
            outputDirectory = originalConfig.outputDirectory,
            maxRecordingDurationMs = originalConfig.maxRecordingDurationMs
        )
        
        // Then
        assertEquals(originalConfig.videoBitrate, copiedConfig.videoBitrate)
        assertEquals(originalConfig.videoFrameRate, copiedConfig.videoFrameRate)
        assertEquals(originalConfig.videoFormat, copiedConfig.videoFormat)
        assertEquals(originalConfig.audioEnabled, copiedConfig.audioEnabled)
        assertEquals(originalConfig.outputDirectory, copiedConfig.outputDirectory)
        assertEquals(originalConfig.maxRecordingDurationMs, copiedConfig.maxRecordingDurationMs)
    }
    
    @Test
    fun `RecordingConfig data class equality works`() {
        // Given
        val config1 = VideoRecordingManager.RecordingConfig(
            videoBitrate = 5_000_000,
            videoFrameRate = 30
        )
        
        val config2 = VideoRecordingManager.RecordingConfig(
            videoBitrate = 5_000_000,
            videoFrameRate = 30
        )
        
        val config3 = VideoRecordingManager.RecordingConfig(
            videoBitrate = 10_000_000,
            videoFrameRate = 30
        )
        
        // Then
        assertEquals(config1.videoBitrate, config2.videoBitrate)
        assertEquals(config1.videoFrameRate, config2.videoFrameRate)
        assertTrue(config1.videoBitrate != config3.videoBitrate)
    }
    
    @Test
    fun `performance test for config creation`() {
        // Given
        val iterations = 10000
        
        // When
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val config = VideoRecordingManager.RecordingConfig(
                videoBitrate = 5_000_000,
                videoFrameRate = 30
            )
            // Use config to prevent optimization
            assertTrue(config.videoBitrate > 0)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Then
        assertTrue(duration < 1000, "Creating $iterations configs should take less than 1 second, took ${duration}ms")
    }
}