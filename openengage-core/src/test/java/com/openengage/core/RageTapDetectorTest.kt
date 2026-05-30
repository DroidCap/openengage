package com.openengage.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RageTapDetectorTest {

    @Test
    fun testRageTapDetectedSuccessfully() {
        val detector = RageTapDetector(rageTapCount = 3, rageTapTimeframeMs = 800, rageTapRadiusPx = 100f)
        
        // Tap 1
        assertEquals(null, detector.registerTap(100f, 100f, 1000L))
        // Tap 2
        assertEquals(null, detector.registerTap(105f, 98f, 1200L))
        // Tap 3 (Within 800ms and close proximity)
        assertEquals(3, detector.registerTap(98f, 102f, 1400L))
    }

    @Test
    fun testRageTapNotDetectedDueToTimeLimit() {
        val detector = RageTapDetector(rageTapCount = 3, rageTapTimeframeMs = 800, rageTapRadiusPx = 100f)
        
        // Tap 1 at 1000ms
        assertEquals(null, detector.registerTap(100f, 100f, 1000L))
        // Tap 2 at 1500ms
        assertEquals(null, detector.registerTap(100f, 100f, 1500L))
        // Tap 3 at 2000ms (1000ms since Tap 1, which exceeds 800ms limit)
        assertEquals(null, detector.registerTap(100f, 100f, 2000L))
    }

    @Test
    fun testRageTapNotDetectedDueToDistance() {
        val detector = RageTapDetector(rageTapCount = 3, rageTapTimeframeMs = 800, rageTapRadiusPx = 100f)
        
        // Tap 1 at (100, 100)
        assertEquals(null, detector.registerTap(100f, 100f, 1000L))
        // Tap 2 at (300, 300) - far away
        assertEquals(null, detector.registerTap(300f, 300f, 1100L))
        // Tap 3 at (100, 100)
        assertEquals(null, detector.registerTap(100f, 100f, 1200L))
    }

    @Test
    fun testResetAfterRageTapDetected() {
        val detector = RageTapDetector(rageTapCount = 3, rageTapTimeframeMs = 800, rageTapRadiusPx = 100f)
        
        assertEquals(null, detector.registerTap(100f, 100f, 1000L))
        assertEquals(null, detector.registerTap(100f, 100f, 1100L))
        // Triggers rage tap and resets
        assertEquals(3, detector.registerTap(100f, 100f, 1200L))
        
        // Tap 4 should NOT trigger another rage tap immediately
        assertEquals(null, detector.registerTap(100f, 100f, 1300L))
    }
}
