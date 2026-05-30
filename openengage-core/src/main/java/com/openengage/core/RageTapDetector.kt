package com.openengage.core

import kotlin.math.hypot

/**
 * Encapsulates the algorithm to detect rage taps.
 * A rage tap is defined as multiple taps (at least rageTapCount) in a close proximity
 * (within rageTapRadiusPx) and within a short timeframe (rageTapTimeframeMs).
 */
class RageTapDetector(
    private val rageTapCount: Int,
    private val rageTapTimeframeMs: Long,
    private val rageTapRadiusPx: Float
) {
    private val tapRecords = mutableListOf<TapRecord>()

    data class TapRecord(val x: Float, val y: Float, val timestamp: Long)

    /**
     * Registers a tap at coordinates (x, y) at the given timestamp.
     * Returns the tap count if a rage tap is detected, null otherwise.
     */
    fun registerTap(x: Float, y: Float, timestamp: Long): Int? {
        tapRecords.add(TapRecord(x, y, timestamp))
        
        // Evict expired taps
        tapRecords.removeAll { timestamp - it.timestamp > rageTapTimeframeMs }
        
        // Filter taps within proximity of the current tap
        val proximityTaps = tapRecords.filter { 
            hypot(it.x - x, it.y - y) <= rageTapRadiusPx 
        }
        
        return if (proximityTaps.size >= rageTapCount) {
            val count = proximityTaps.size
            tapRecords.clear() // Reset history upon detection to prevent double logging consecutive taps
            count
        } else {
            null
        }
    }
    
    fun clear() {
        tapRecords.clear()
    }
}
