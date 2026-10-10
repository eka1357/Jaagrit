package com.jaagrit.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {

    @Test
    fun verifyCoreTimersAndThresholdsMatchRequirements() {
        // Closure and face glitch
        assertEquals(2500L, Config.CLOSURE_CONFIRM_MS)
        assertEquals(300L, Config.FACE_GLITCH_TOLERANCE_MS)
        assertEquals(3000L, Config.OPEN_EYES_RESPONSE_MS)
        assertEquals(30000L, Config.FACE_LOST_REMINDER_MS)

        // Ladder timers
        assertEquals(10000L, Config.L4_AFTER_L3_MS)
        assertEquals(5000L, Config.L4_AFTER_L3_DEMO_MS)
        assertEquals(20000L, Config.L5_AFTER_L4_MS)
        assertEquals(8000L, Config.L5_AFTER_L4_DEMO_MS)
        assertEquals(30000L, Config.L3_REPEAT_COOLDOWN_MS)

        // Head droop
        assertEquals(15.0f, Config.HEAD_PITCH_DEG, 0.001f)
        assertEquals(1500L, Config.HEAD_SUSTAIN_MS)
        assertEquals(5000L, Config.HEAD_NO_RESPONSE_MS)
        assertEquals(1500L, Config.HEAD_RESTORE_CANCEL_MS)
        assertEquals(30000L, Config.SOFT_PROMPT_COOLDOWN_MS)

        // Companion
        assertEquals(60000L, Config.COMPANION_COOLDOWN_MS)
        assertEquals(300000L, Config.COMPANION_BACKOFF_MS)
        assertEquals(120000L, Config.DISMISS_SILENCE_MS)
        assertEquals(2.0, Config.MATH_LATENCY_FACTOR, 0.001)
        assertEquals(5000L, Config.DEFAULT_MATH_MAX_WAIT_MS)

        // PERCLOS & blink
        assertEquals(60000L, Config.PERCLOS_WINDOW_MS)
        assertEquals(0.12f, Config.PERCLOS_L2, 0.001f)
        assertEquals(0.20f, Config.BLINK_RATE_L1_INCREASE, 0.001f)
        assertEquals(30000L, Config.BLINK_RATE_L1_SUSTAIN_MS)

        // Thermal
        assertEquals(42, Config.THERMAL_REDUCE_C)
        assertEquals(45, Config.THERMAL_WARN_C)

        // Drive time ramp
        assertEquals(2.0, Config.DRIVE_TIME_RAMP_START_HOURS, 0.001)
        assertEquals(6.0, Config.DRIVE_TIME_RAMP_END_HOURS, 0.001)
        assertEquals(15.0, Config.DRIVE_TIME_MAX_PENALTY, 0.001)

        // Alertness weights and EMA (AUDIT-013: Head droop = 20, Blink rate = 25, PERCLOS = 30..40)
        assertEquals(30.0, Config.ALERTNESS_WEIGHT_PERCLOS, 0.001)
        assertEquals(35.0, Config.ALERTNESS_WEIGHT_CLOSURE, 0.001)
        assertEquals(25.0, Config.ALERTNESS_WEIGHT_BLINK_RATE, 0.001)
        assertEquals(20.0, Config.ALERTNESS_WEIGHT_HEAD_DROOP, 0.001)
        assertEquals(0.15, Config.ALERTNESS_EMA_ALPHA, 0.001)

        // AUDIT-013 Centralized thresholds
        assertEquals(5.0, Config.REASON_PENALTY_THRESHOLD, 0.001)
        assertEquals(3_600_000.0, Config.MS_PER_HOUR, 0.001)
        assertEquals(80L, Config.BLINK_DURATION_MIN_MS)
        assertEquals(500L, Config.BLINK_DURATION_MAX_MS)
        assertEquals(10.0, Config.PERCLOS_RAMP_PENALTY, 0.001)
        assertEquals(40.0, Config.PERCLOS_MAX_PENALTY, 0.001)
        assertEquals(500L, Config.CLOSURE_PENALTY_MIN_MS)
        assertEquals(0.2, Config.BLINK_RATE_MIN_WINDOW_MINUTES, 0.001)
        assertEquals(15.0, Config.BLINK_RATE_L1_PENALTY, 0.001)
        assertEquals(0.50, Config.BLINK_RATE_MAX_INCREASE, 0.001)
        assertEquals(10.0, Config.BLINK_RATE_RAMP_PENALTY, 0.001)
        assertEquals(25.0, Config.BLINK_RATE_MAX_PENALTY, 0.001)
        assertEquals(500L, Config.HEAD_DROOP_PENALTY_MIN_MS)
        assertEquals(16.0f, Config.DEFAULT_BASELINE_BLINK_RATE, 0.001f)
        assertEquals(1000L, Config.MIN_BLINK_CALCULATION_DURATION_MS)
        assertEquals(5.0f, Config.BLINK_RATE_CLAMP_MIN, 0.001f)
        assertEquals(45.0f, Config.BLINK_RATE_CLAMP_MAX, 0.001f)
        assertEquals(1.5f, Config.HEAD_PITCH_GEOMETRIC_SCALE, 0.001f)

        // Alertness score bands
        assertEquals(100, Config.ALERTNESS_INITIAL_SCORE)
        assertEquals(71, Config.ALERTNESS_BAND_ALERT_MIN)
        assertEquals(51, Config.ALERTNESS_BAND_CAUTION_MIN)
        assertEquals(31, Config.ALERTNESS_BAND_FATIGUED_MIN)
        assertEquals(30, Config.ALERTNESS_BAND_CRITICAL_MAX)
    }

    @Test
    fun verifyDemoModeShortensTimers() {
        val normalConfig = Config(demoTimers = false, quickCalibration = false)
        val demoConfig = Config(demoTimers = true, quickCalibration = false)
        val quickCalibConfig = Config(demoTimers = false, quickCalibration = true)

        // DEMO_TIMERS affects only L4 (5s vs 10s) and L5 (8s vs 20s) (D9)
        assertEquals(10000L, normalConfig.l4AfterL3Ms)
        assertEquals(5000L, demoConfig.l4AfterL3Ms)

        assertEquals(20000L, normalConfig.l5AfterL4Ms)
        assertEquals(8000L, demoConfig.l5AfterL4Ms)

        // Normal calibration timers unaffected by demoTimers
        assertEquals(10000L, demoConfig.calibrationOpenMs)
        assertEquals(3000L, demoConfig.calibrationClosedMs)

        // QUICK_CALIBRATION is a separate flag (D9)
        assertEquals(10000L, normalConfig.calibrationOpenMs)
        assertEquals(5000L, quickCalibConfig.calibrationOpenMs)

        assertEquals(3000L, normalConfig.calibrationClosedMs)
        assertEquals(2000L, quickCalibConfig.calibrationClosedMs)
    }

    @Test
    fun verifyLandmarkIndices() {
        assertEquals(6, Config.LANDMARKS_EYE_RIGHT.size)
        assertEquals(6, Config.LANDMARKS_EYE_LEFT.size)
        assertTrue(Config.LANDMARKS_EYE_RIGHT.contentEquals(intArrayOf(33, 160, 158, 133, 153, 144)))
        assertTrue(Config.LANDMARKS_EYE_LEFT.contentEquals(intArrayOf(362, 385, 387, 263, 373, 380)))
    }
}
