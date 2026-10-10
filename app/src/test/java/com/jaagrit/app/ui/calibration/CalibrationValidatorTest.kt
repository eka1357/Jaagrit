package com.jaagrit.app.ui.calibration

import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for AUDIT-010: calibration must reject a baseline when a phase has fewer than 15 usable frames
 * or no closed-eye frames, with a specific message.
 */
class CalibrationValidatorTest {

    private fun makeFrames(
        count: Int,
        startMs: Long,
        stepMs: Long = 40L,
        ear: Float = 0.28f,
        faceFound: Boolean = true
    ): List<FaceFrame> {
        return (0 until count).map { i ->
            FaceFrame(
                tsMs = startMs + i * stepMs,
                faceFound = faceFound,
                earL = ear,
                earR = ear,
                mar = 0.05f,
                pitchDeg = 5f,
                yawDeg = 0f,
                rollDeg = 0f
            )
        }
    }

    private val validBaseline = Baseline(
        openEar = 0.28f,
        closedEar = 0.04f,
        threshold = 0.16f,
        mar = 0.05f,
        blinkRate = 16f,
        responseLatencyMs = 1500L,
        calibratedAtMs = 1000L,
        isValid = true
    )

    @Test
    fun testRejectsWhenNoClosedEyeFrames() {
        // Open frames: 50 frames (sufficient)
        val openFrames = makeFrames(count = 50, startMs = 0L, ear = 0.28f)
        // Closed frames: empty
        val closedFrames = emptyList<FaceFrame>()

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 2000L,
            baseline = validBaseline
        )

        assertTrue(result is CalibrationValidationResult.Rejected)
        val rejected = result as CalibrationValidationResult.Rejected
        assertEquals(CalibrationValidationResult.RejectionReason.NoClosedEyeFrames, rejected.reason)
        assertFalse(rejected.partialBaseline.isValid)
    }

    @Test
    fun testRejectsWhenClosedFramesHaveNoFaceFound() {
        val openFrames = makeFrames(count = 50, startMs = 0L, ear = 0.28f)
        // Closed frames exist but faceFound = false
        val closedFrames = makeFrames(count = 50, startMs = 2000L, ear = 0.04f, faceFound = false)

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 2000L,
            baseline = validBaseline
        )

        assertTrue(result is CalibrationValidationResult.Rejected)
        val rejected = result as CalibrationValidationResult.Rejected
        assertEquals(CalibrationValidationResult.RejectionReason.NoClosedEyeFrames, rejected.reason)
        assertFalse(rejected.partialBaseline.isValid)
    }

    @Test
    fun testRejectsWhenFewerThan15UsableOpenFrames() {
        // 14 usable frames after 1000ms ignore window
        // Ignore window is 1000ms. Start at 0L -> frames after 1000ms:
        val preIgnore = makeFrames(count = 10, startMs = 0L, stepMs = 50L) // ts 0..450ms (ignored)
        val postIgnore = makeFrames(count = 14, startMs = 1050L, stepMs = 50L) // 14 usable frames (< 15)
        val openFrames = preIgnore + postIgnore

        val closedFrames = makeFrames(count = 50, startMs = 3000L, stepMs = 50L, ear = 0.04f)

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 3000L,
            baseline = validBaseline
        )

        assertTrue(result is CalibrationValidationResult.Rejected)
        val rejected = result as CalibrationValidationResult.Rejected
        assertTrue(rejected.reason is CalibrationValidationResult.RejectionReason.InsufficientOpenFrames)
        assertEquals(14, (rejected.reason as CalibrationValidationResult.RejectionReason.InsufficientOpenFrames).usableCount)
        assertFalse(rejected.partialBaseline.isValid)
    }

    @Test
    fun testRejectsWhenFewerThan15UsableClosedFrames() {
        val openFrames = makeFrames(count = 50, startMs = 0L, stepMs = 50L, ear = 0.28f)

        val preIgnore = makeFrames(count = 10, startMs = 3000L, stepMs = 50L) // ts 3000..3450ms (ignored)
        val postIgnore = makeFrames(count = 10, startMs = 4050L, stepMs = 50L, ear = 0.04f) // 10 usable frames (< 15)
        val closedFrames = preIgnore + postIgnore

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 3000L,
            baseline = validBaseline
        )

        assertTrue(result is CalibrationValidationResult.Rejected)
        val rejected = result as CalibrationValidationResult.Rejected
        assertTrue(rejected.reason is CalibrationValidationResult.RejectionReason.InsufficientClosedFrames)
        assertEquals(10, (rejected.reason as CalibrationValidationResult.RejectionReason.InsufficientClosedFrames).usableCount)
        assertFalse(rejected.partialBaseline.isValid)
    }

    @Test
    fun testRejectsWhenGapTooSmall() {
        val openFrames = makeFrames(count = 50, startMs = 0L, stepMs = 50L, ear = 0.20f)
        val closedFrames = makeFrames(count = 50, startMs = 3000L, stepMs = 50L, ear = 0.18f)

        val smallGapBaseline = validBaseline.copy(openEar = 0.20f, closedEar = 0.18f) // gap = 0.02 < 0.05

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 3000L,
            baseline = smallGapBaseline
        )

        assertTrue(result is CalibrationValidationResult.Rejected)
        val rejected = result as CalibrationValidationResult.Rejected
        assertTrue(rejected.reason is CalibrationValidationResult.RejectionReason.GapTooSmall)
        assertEquals(0.02f, (rejected.reason as CalibrationValidationResult.RejectionReason.GapTooSmall).gap, 0.001f)
        assertFalse(rejected.partialBaseline.isValid)
    }

    @Test
    fun testAcceptsValidCalibrationWithSufficientFrames() {
        // >= 15 usable frames in both phases, gap >= 0.05
        val openFrames = makeFrames(count = 50, startMs = 0L, stepMs = 50L, ear = 0.28f)
        val closedFrames = makeFrames(count = 50, startMs = 3000L, stepMs = 50L, ear = 0.04f)

        val result = CalibrationValidator.validate(
            openFrames = openFrames,
            openStartMs = 0L,
            closedFrames = closedFrames,
            closedStartMs = 3000L,
            baseline = validBaseline
        )

        assertTrue(result is CalibrationValidationResult.Success)
        val success = result as CalibrationValidationResult.Success
        assertTrue(success.baseline.isValid)
        assertEquals(0.24f, success.baseline.earGap, 0.001f)
    }
}
