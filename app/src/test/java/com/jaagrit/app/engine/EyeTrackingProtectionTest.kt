package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for the protected eye-tracking subsystem.
 *
 * These pin the exact eye-closing / eye-opening detection, closure-duration timing,
 * glitch tolerance, threshold comparison, and constants as they behaved before M9-M12.
 * They must keep passing unchanged: any later feature has to integrate around this logic.
 */
class EyeTrackingProtectionTest {

    private val baseline = Baseline(
        openEar = 0.340f,
        closedEar = 0.104f,
        threshold = 0.222f,
        mar = 0.02f,
        blinkRate = 12.4f,
        responseLatencyMs = 2628L,
        calibratedAtMs = 1L,
        isValid = true
    )

    private val stepMs = 33L // ~30 FPS, close to the measured 24.5 FPS on the iQOO 15

    private fun newEngine(clock: FakeClock) =
        FatigueEngine(config = Config.DEFAULT, clock = clock, baseline = baseline).also {
            it.resetDrive(clock.nowMs())
        }

    private fun frame(clock: FakeClock, ear: Float, faceFound: Boolean = true) = FaceFrame(
        tsMs = clock.nowMs(),
        faceFound = faceFound,
        earL = ear,
        earR = ear,
        mar = 0.02f,
        pitchDeg = 5f,
        yawDeg = 0f,
        rollDeg = 0f
    )

    private fun firesL3(output: EngineOutput) = output.actions.any { it is Action.ShowRedFlash }

    @Test
    fun protectedConstants_unchanged() {
        assertEquals(2500L, Config.CLOSURE_CONFIRM_MS)
        assertEquals(300L, Config.FACE_GLITCH_TOLERANCE_MS)
        assertEquals(3000L, Config.OPEN_EYES_RESPONSE_MS)
        assertEquals(0.5f, Config.CALIBRATION_THRESHOLD_FACTOR)
        assertEquals(0.05f, Config.CALIBRATION_MIN_GAP)
        assertEquals(1000L, Config.CALIBRATION_IGNORE_INITIAL_MS)
        assertEquals(0.20f, Config.DEFAULT_BASELINE_THRESHOLD)
        assertEquals(80L, Config.BLINK_DURATION_MIN_MS)
        assertEquals(500L, Config.BLINK_DURATION_MAX_MS)
        assertEquals(listOf(33, 160, 158, 133, 153, 144), Config.LANDMARKS_EYE_RIGHT.toList())
        assertEquals(listOf(362, 385, 387, 263, 373, 380), Config.LANDMARKS_EYE_LEFT.toList())
    }

    @Test
    fun thresholdComparison_isStrictlyLessThan() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        // EAR exactly at the threshold is "open": hold it for 4 s, no closure may fire.
        repeat((4000 / stepMs).toInt()) {
            clock.advance(stepMs)
            assertFalse(firesL3(engine.onFrame(frame(clock, baseline.threshold))))
        }
    }

    @Test
    fun closureConfirm_firesOnFirstFrameAtOrAfter2500ms() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        repeat(30) { clock.advance(stepMs); engine.onFrame(frame(clock, 0.34f)) }

        clock.advance(stepMs)
        val closedStart = clock.nowMs()
        engine.onFrame(frame(clock, 0.10f))
        var firedAt: Long? = null
        while (firedAt == null && clock.nowMs() - closedStart < 4000L) {
            clock.advance(stepMs)
            val out = engine.onFrame(frame(clock, 0.10f))
            if (firesL3(out)) {
                firedAt = clock.nowMs() - closedStart
                assertEquals(DriverState.CRITICAL, out.state)
                assertEquals(Level.L3, out.level)
            }
        }
        val expected = ((2500L + stepMs - 1) / stepMs) * stepMs
        assertEquals(expected, firedAt)
    }

    @Test
    fun closureShorterThanConfirm_neverFires_andReopenResetsTimer() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        repeat(2) {
            // 2.4 s closed, then open: no L3, and the next closure starts from zero again.
            repeat((2400 / stepMs).toInt()) {
                clock.advance(stepMs)
                assertFalse(firesL3(engine.onFrame(frame(clock, 0.10f))))
            }
            repeat(10) {
                clock.advance(stepMs)
                assertFalse(firesL3(engine.onFrame(frame(clock, 0.34f))))
            }
        }
    }

    @Test
    fun faceGlitchUnderTolerance_keepsClosureTimer() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        clock.advance(stepMs)
        val closedStart = clock.nowMs()
        engine.onFrame(frame(clock, 0.10f))
        while (clock.nowMs() - closedStart < 1500L) {
            clock.advance(stepMs); engine.onFrame(frame(clock, 0.10f))
        }
        // 264 ms face loss (< 300 ms tolerance)
        repeat(8) { clock.advance(stepMs); engine.onFrame(frame(clock, 0f, faceFound = false)) }
        var firedAt: Long? = null
        while (firedAt == null && clock.nowMs() - closedStart < 4000L) {
            clock.advance(stepMs)
            if (firesL3(engine.onFrame(frame(clock, 0.10f)))) firedAt = clock.nowMs() - closedStart
        }
        assertNotNull("closure timer must survive a sub-300 ms face glitch", firedAt)
        assertTrue(firedAt!! in 2500L..(2500L + stepMs))
    }

    @Test
    fun faceGlitchOverTolerance_resetsClosureTimer() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        clock.advance(stepMs)
        val closedStart = clock.nowMs()
        engine.onFrame(frame(clock, 0.10f))
        while (clock.nowMs() - closedStart < 1500L) {
            clock.advance(stepMs); engine.onFrame(frame(clock, 0.10f))
        }
        // 396 ms face loss (> 300 ms tolerance)
        repeat(12) { clock.advance(stepMs); engine.onFrame(frame(clock, 0f, faceFound = false)) }
        val resumed = clock.nowMs()
        var firedAt: Long? = null
        while (firedAt == null && clock.nowMs() - resumed < 4000L) {
            clock.advance(stepMs)
            if (firesL3(engine.onFrame(frame(clock, 0.10f)))) firedAt = clock.nowMs() - resumed
        }
        assertNotNull(firedAt)
        assertTrue("timer restarted after a >300 ms loss", firedAt!! >= 2500L)
    }

    @Test
    fun eyesOpenResponse_resolvesAlertAfterExactly3sOpen() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        var fired = false
        while (!fired) {
            clock.advance(stepMs)
            fired = firesL3(engine.onFrame(frame(clock, 0.10f)))
        }
        assertTrue(engine.isAlertActive)

        clock.advance(stepMs)
        val openStart = clock.nowMs()
        engine.onFrame(frame(clock, 0.34f))
        var resolvedAt: Long? = null
        while (resolvedAt == null && clock.nowMs() - openStart < 5000L) {
            clock.advance(stepMs)
            val out = engine.onFrame(frame(clock, 0.34f))
            if (out.actions.any { it is Action.Log && it.detail.contains("Eyes open >= 3s") }) {
                resolvedAt = clock.nowMs() - openStart
            }
        }
        val expected = ((3000L + stepMs - 1) / stepMs) * stepMs
        assertEquals(expected, resolvedAt)
        assertFalse(engine.isAlertActive)
    }

    @Test
    fun oneSecondClosure_producesClosureReasonButNoAlert() {
        val clock = FakeClock(1_000_000L)
        val engine = newEngine(clock)
        repeat((1000 / stepMs).toInt() + 1) { clock.advance(stepMs); engine.onFrame(frame(clock, 0.10f)) }
        clock.advance(stepMs)
        val out = engine.onFrame(frame(clock, 0.34f))
        assertFalse(engine.isAlertActive)
        assertTrue(out.reasons.any { it.startsWith("Long eye closure (1.0s)") })
        assertNull(out.actions.firstOrNull { it is Action.ShowRedFlash })
    }
}
