package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BaselineCalculator] calibration math.
 * Pure Kotlin — runs in local JVM unit test runner.
 */
class BaselineCalculatorTest {

    @Test
    fun testMedianFloat_oddAndEvenAndEmpty() {
        assertEquals(0f, BaselineCalculator.medianFloat(emptyList()), 0.0001f)
        assertEquals(0.25f, BaselineCalculator.medianFloat(listOf(0.25f)), 0.0001f)

        // Odd count: middle element of sorted
        val oddList = listOf(0.10f, 0.35f, 0.22f) // sorted: 0.10, 0.22, 0.35 -> median 0.22
        assertEquals(0.22f, BaselineCalculator.medianFloat(oddList), 0.0001f)

        // Even count: average of two middle elements of sorted
        val evenList = listOf(0.30f, 0.10f, 0.40f, 0.20f) // sorted: 0.10, 0.20, 0.30, 0.40 -> (0.20+0.30)/2 = 0.25
        assertEquals(0.25f, BaselineCalculator.medianFloat(evenList), 0.0001f)
    }

    @Test
    fun testThresholdCalculation_formulaMatchesSpec() {
        // Spec formula: threshold = closedMedian + 0.5 * (openMedian - closedMedian) (CAL-1, D4)
        val openSamples = listOf(0.30f, 0.28f, 0.32f, 0.30f, 0.29f) // median = 0.30
        val closedSamples = listOf(0.04f, 0.03f, 0.05f, 0.04f, 0.04f) // median = 0.04

        val baseline = BaselineCalculator.compute(
            openEarSamples = openSamples,
            closedEarSamples = closedSamples
        )

        assertEquals(0.30f, baseline.openEar, 0.001f)
        assertEquals(0.04f, baseline.closedEar, 0.001f)
        assertEquals(0.26f, baseline.earGap, 0.001f)

        val expectedThreshold = 0.04f + 0.5f * (0.30f - 0.04f) // 0.04 + 0.13 = 0.17
        assertEquals(expectedThreshold, baseline.threshold, 0.001f)
        assertTrue("Baseline must be valid when gap >= 0.05", baseline.isValid)
    }

    @Test
    fun testGapTooSmall_failsValidation() {
        // Gap < 0.05 must be marked invalid (CAL-4)
        val openSamples = listOf(0.18f, 0.19f, 0.18f)
        val closedSamples = listOf(0.15f, 0.16f, 0.15f) // gap = 0.18 - 0.15 = 0.03 < 0.05

        val baseline = BaselineCalculator.compute(
            openEarSamples = openSamples,
            closedEarSamples = closedSamples
        )

        assertFalse("Baseline must be marked invalid when gap is under 0.05", baseline.isValid)
        assertTrue(baseline.earGap < 0.05f)
    }

    @Test
    fun testComputeFromFrames_ignoresFirstSecondOfPhase() {
        val openPhaseStart = 1000L
        val closedPhaseStart = 12000L

        // Open frames: First 1s has bad/noisy frames, followed by stable open eye frames
        val openFrames = listOf(
            // tsMs - 1000L < 1000L -> ignored!
            createFrame(tsMs = 1200L, earAvg = 0.02f),
            createFrame(tsMs = 1500L, earAvg = 0.05f),
            createFrame(tsMs = 1900L, earAvg = 0.08f),
            // After 1000ms: valid frames
            createFrame(tsMs = 2100L, earAvg = 0.28f),
            createFrame(tsMs = 3000L, earAvg = 0.30f),
            createFrame(tsMs = 4000L, earAvg = 0.32f)
        )

        // Closed frames: First 1s has reaction time frames (still open), followed by stable closed frames
        val closedFrames = listOf(
            // tsMs - 12000L < 1000L -> ignored!
            createFrame(tsMs = 12200L, earAvg = 0.30f),
            createFrame(tsMs = 12500L, earAvg = 0.28f),
            createFrame(tsMs = 12800L, earAvg = 0.20f),
            // After 1000ms: valid closed frames
            createFrame(tsMs = 13100L, earAvg = 0.04f),
            createFrame(tsMs = 13500L, earAvg = 0.05f),
            createFrame(tsMs = 14000L, earAvg = 0.04f)
        )

        val baseline = BaselineCalculator.computeFromFrames(
            openEyeFrames = openFrames,
            openPhaseStartMs = openPhaseStart,
            closedEyeFrames = closedFrames,
            closedPhaseStartMs = closedPhaseStart
        )

        // If first second wasn't ignored, open median would be ruined by 0.02 and closed by 0.30
        assertEquals(0.30f, baseline.openEar, 0.005f)
        assertEquals(0.04f, baseline.closedEar, 0.005f)
        assertTrue(baseline.isValid)
    }

    @Test
    fun testBlinkRateEstimation() {
        val baseMs = 5000L
        val threshold = 0.16f

        // Create 15 seconds of frames at 25 fps (~40ms per frame)
        // Insert 3 blinks (~120ms each)
        val frames = mutableListOf<FaceFrame>()
        var currentMs = baseMs

        for (i in 0 until 350) {
            val isBlink1 = currentMs in (baseMs + 2000L)..(baseMs + 2120L)
            val isBlink2 = currentMs in (baseMs + 6000L)..(baseMs + 6120L)
            val isBlink3 = currentMs in (baseMs + 10000L)..(baseMs + 10120L)

            val ear = if (isBlink1 || isBlink2 || isBlink3) 0.05f else 0.28f
            frames.add(createFrame(tsMs = currentMs, earAvg = ear))
            currentMs += 40L
        }

        val rate = BaselineCalculator.computeBlinkRate(frames, threshold)
        // 3 blinks in ~14 seconds = ~12.8 blinks/min
        assertTrue("Blink rate should be reasonably estimated around 10-18 blinks/min", rate in 10.0f..18.0f)
    }

    private fun createFrame(
        tsMs: Long,
        earAvg: Float,
        faceFound: Boolean = true
    ): FaceFrame {
        return FaceFrame(
            tsMs = tsMs,
            faceFound = faceFound,
            earL = earAvg,
            earR = earAvg,
            mar = 0.05f,
            pitchDeg = 5f,
            yawDeg = 0f,
            rollDeg = 0f
        )
    }
}
