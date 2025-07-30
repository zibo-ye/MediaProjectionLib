package com.t34400.mediaprojectionlib.recording

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Simple unit tests for configuration classes and enums
 * These tests don't require Android framework dependencies
 */
class SimpleConfigurationTest {
    
    @Test
    fun `RecordingConfig basic factory creates reasonable defaults`() {
        // When
        val config = VideoRecordingManager.RecordingConfig.createBasic(1920, 1080)
        
        // Then
        assertEquals(5_000_000, config.videoBitrate)
        assertEquals(30, config.videoFrameRate)
        assertEquals("video/avc", config.videoFormat)
        assertEquals(1920, config.videoWidth)
        assertEquals(1080, config.videoHeight)
        assertFalse(config.audioEnabled)
        assertEquals("", config.outputDirectory)
        assertEquals(-1L, config.maxRecordingDurationMs)
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
    fun `SupportedCodec enum has all expected values`() {
        // When & Then
        val codecs = VideoRecordingManager.SupportedCodec.values()
        
        assertTrue(codecs.contains(VideoRecordingManager.SupportedCodec.H264))
        assertTrue(codecs.contains(VideoRecordingManager.SupportedCodec.H265))
        assertTrue(codecs.contains(VideoRecordingManager.SupportedCodec.VP8))
        assertTrue(codecs.contains(VideoRecordingManager.SupportedCodec.VP9))
        assertTrue(codecs.contains(VideoRecordingManager.SupportedCodec.AV1))
        
        // Check MIME types
        assertEquals("video/avc", VideoRecordingManager.SupportedCodec.H264.mimeType)
        assertEquals("video/hevc", VideoRecordingManager.SupportedCodec.H265.mimeType)
        assertEquals("video/x-vnd.on2.vp8", VideoRecordingManager.SupportedCodec.VP8.mimeType)
        assertEquals("video/x-vnd.on2.vp9", VideoRecordingManager.SupportedCodec.VP9.mimeType)
        assertEquals("video/av01", VideoRecordingManager.SupportedCodec.AV1.mimeType)
        
        // Check display names
        assertEquals("H.264/AVC", VideoRecordingManager.SupportedCodec.H264.displayName)
        assertEquals("H.265/HEVC", VideoRecordingManager.SupportedCodec.H265.displayName)
        assertEquals("VP8", VideoRecordingManager.SupportedCodec.VP8.displayName)
        assertEquals("VP9", VideoRecordingManager.SupportedCodec.VP9.displayName)
        assertEquals("AV1", VideoRecordingManager.SupportedCodec.AV1.displayName)
    }
    
    @Test 
    fun `DisplayInfo data class structure is valid`() {
        // When
        val displayInfo = VideoRecordingManager.DisplayInfo(
            width = 1920,
            height = 1080,
            densityDpi = 320,
            refreshRate = 60.0f,
            supportedFrameRates = listOf(30, 60, 90)
        )
        
        // Then
        assertEquals(1920, displayInfo.width)
        assertEquals(1080, displayInfo.height)
        assertEquals(320, displayInfo.densityDpi)
        assertEquals(60.0f, displayInfo.refreshRate)
        assertEquals(3, displayInfo.supportedFrameRates.size)
        assertTrue(displayInfo.supportedFrameRates.contains(30))
        assertTrue(displayInfo.supportedFrameRates.contains(60))
        assertTrue(displayInfo.supportedFrameRates.contains(90))
    }
}