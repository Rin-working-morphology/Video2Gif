package com.example.video2gif.media

import com.example.video2gif.domain.OutputSpec
import org.junit.Assert.*
import org.junit.Test

class SizeOptimizationTest {
    @Test fun `estimation accounts for duplicate frames without multiplying full frame size`() {
        fun estimate(frames: Int, rectangle: Double, repeat: Double) = SizeEstimator.fromSamples(
            OutputSpec(480, 270, frames, frames * 100L), 128, 5.0, 0.3, 0.0, 4, 4,
            rectangle, repeat)
        val single = estimate(1, 1.0, 0.0)
        val static = estimate(100, 0.0, 1.0)
        val local = estimate(100, 0.1, 0.0)
        val full = estimate(100, 1.0, 0.0)
        assertEquals(single.centerBytes, static.centerBytes)
        assertTrue(local.centerBytes > static.centerBytes)
        assertTrue(local.centerBytes < full.centerBytes / 5)
        assertTrue(static.lowBytes <= static.centerBytes)
        assertTrue(static.highBytes >= static.centerBytes)
    }
}
