package com.jaagrit.app.engine

/**
 * All thresholds, timers, and scoring parameters for Jaagrit fatigue detection.
 * Pure Kotlin — zero Android dependencies.
 *
 * Requirements reference: docs/REQUIREMENTS.md & docs/DECISIONS.md
 */
data class Config(
    // Flag for demo mode (shortened timers for faster live demos)
    val demoTimers: Boolean = false,

    // Eye closure detection (ENG-1, LAD-1)
    // Continuous eye closure duration required to confirm a microsleep / critical event
    val closureConfirmMs: Long = CLOSURE_CONFIRM_MS,
    // Brief face detection loss tolerance (glitch) that does NOT reset an in-progress closure timer
    val faceGlitchToleranceMs: Long = FACE_GLITCH_TOLERANCE_MS,
    // Duration eyes must stay continuously open with face detected to count as an awakened response (LAD-5)
    val openEyesResponseMs: Long = OPEN_EYES_RESPONSE_MS,
    // Delay before speaking a reminder when the driver's face is completely lost (ENG-4)
    val faceLostReminderMs: Long = FACE_LOST_REMINDER_MS,

    // Intervention ladder timers (LAD-1, LAD-3, LAD-4)
    // Delay after L3 alarm with no response before escalating to L4 (family voice clip)
    val l4AfterL3Ms: Long = if (demoTimers) L4_AFTER_L3_DEMO_MS else L4_AFTER_L3_MS,
    // Delay after L4 family clip with no response before escalating to L5 (emergency SMS)
    val l5AfterL4Ms: Long = if (demoTimers) L5_AFTER_L4_DEMO_MS else L5_AFTER_L4_MS,
    // Cooldown period preventing immediate repeat of L3 alarm unless condition worsens
    val l3RepeatCooldownMs: Long = L3_REPEAT_COOLDOWN_MS,

    // Head pose & droop detection (LAD-2, D3)
    // Pitch angle threshold in degrees where positive = head drooping forward/down
    val headPitchDeg: Float = HEAD_PITCH_DEG,
    // Duration pitch must exceed threshold before soft prompt triggers
    val headSustainMs: Long = HEAD_SUSTAIN_MS,
    // Escalation wait time if driver does not respond to head droop soft prompt
    val headNoResponseMs: Long = HEAD_NO_RESPONSE_MS,
    // Sustained head recovery duration required to cancel pending droop escalation (D3)
    val headRestoreCancelMs: Long = HEAD_RESTORE_CANCEL_MS,
    // Cooldown between head droop soft spoken prompts
    val softPromptCooldownMs: Long = SOFT_PROMPT_COOLDOWN_MS,

    // Companion interaction (COM-1, COM-3, D2)
    // Cooldown between companion verbal questions/openers
    val companionCooldownMs: Long = COMPANION_COOLDOWN_MS,
    // Backoff period if driver ignores 2 consecutive companion openers
    val companionBackoffMs: Long = COMPANION_BACKOFF_MS,
    // Silence period after driver gives explicit dismiss command
    val dismissSilenceMs: Long = DISMISS_SILENCE_MS,
    // Multiplier over baseline cognitive response time before flagging fatigue
    val mathLatencyFactor: Double = MATH_LATENCY_FACTOR,
    // Default fallback max wait time for an answer to a companion cognitive question
    val defaultMathMaxWaitMs: Long = DEFAULT_MATH_MAX_WAIT_MS,

    // PERCLOS & blink dynamics (ENG-2, ENG-3, D12)
    // Rolling time window over which PERCLOS is calculated (60 seconds)
    val perclosWindowMs: Long = PERCLOS_WINDOW_MS,
    // Raw L2 trigger PERCLOS ratio (12% of the window closed)
    val perclosL2: Float = PERCLOS_L2,
    // Lower threshold below which PERCLOS penalty is 0 (D1)
    val perclosZeroPenaltyThreshold: Float = PERCLOS_ZERO_PENALTY_THRESHOLD,
    // Threshold at which PERCLOS penalty reaches maximum penalty (D1)
    val perclosMaxPenaltyThreshold: Float = PERCLOS_MAX_PENALTY_THRESHOLD,
    // Baseline blink rate increase threshold (+20%) triggering raw L1 warning
    val blinkRateL1Increase: Float = BLINK_RATE_L1_INCREASE,
    // Duration blink rate elevation must be sustained to trigger L1
    val blinkRateL1SustainMs: Long = BLINK_RATE_L1_SUSTAIN_MS,

    // Calibration settings (CAL-1, D4, D9)
    val calibrationOpenMs: Long = if (demoTimers) QUICK_CALIBRATION_OPEN_MS else CALIBRATION_OPEN_MS,
    val calibrationClosedMs: Long = if (demoTimers) QUICK_CALIBRATION_CLOSED_MS else CALIBRATION_CLOSED_MS,
    val calibrationYawnMs: Long = CALIBRATION_YAWN_MS,
    val calibrationHeadPoseMs: Long = CALIBRATION_HEAD_POSE_MS,
    // Initial reaction window ignored during each calibration phase
    val calibrationIgnoreInitialMs: Long = CALIBRATION_IGNORE_INITIAL_MS,
    // Threshold factor: closedMedian + factor * (openMedian - closedMedian)
    val calibrationThresholdFactor: Float = CALIBRATION_THRESHOLD_FACTOR,
    // Minimum acceptable difference between open and closed EAR
    val calibrationMinGap: Float = CALIBRATION_MIN_GAP,

    // False alert limit (LAD-6)
    val falseAlertLimitCount: Int = FALSE_ALERT_LIMIT_COUNT,
    val falseAlertLimitWindowMs: Long = FALSE_ALERT_LIMIT_WINDOW_MS,

    // Thermal mitigation (THERMAL_REDUCE_C / THERMAL_WARN_C, D10)
    val thermalReduceC: Int = THERMAL_REDUCE_C,
    val thermalWarnC: Int = THERMAL_WARN_C,

    // Drive-time fatigue ramp (ENG-2, D1)
    // Hours before drive duration penalty starts accumulating
    val driveTimeRampStartHours: Double = DRIVE_TIME_RAMP_START_HOURS,
    // Hours at which drive duration penalty reaches maximum
    val driveTimeRampEndHours: Double = DRIVE_TIME_RAMP_END_HOURS,
    // Maximum penalty points subtracted due to continuous driving duration
    val driveTimeMaxPenalty: Double = DRIVE_TIME_MAX_PENALTY,

    // Alertness heuristic weights & EMA smoothing (ENG-2, D1)
    val alertnessWeightPerclos: Double = ALERTNESS_WEIGHT_PERCLOS,
    val alertnessWeightClosure: Double = ALERTNESS_WEIGHT_CLOSURE,
    val alertnessWeightBlinkRate: Double = ALERTNESS_WEIGHT_BLINK_RATE,
    val alertnessWeightHeadDroop: Double = ALERTNESS_WEIGHT_HEAD_DROOP,
    val alertnessEmaAlpha: Double = ALERTNESS_EMA_ALPHA,

    // Score band boundaries (ENG-2, D1)
    val alertnessBandAlertMin: Int = ALERTNESS_BAND_ALERT_MIN,
    val alertnessBandCautionMin: Int = ALERTNESS_BAND_CAUTION_MIN,
    val alertnessBandFatiguedMin: Int = ALERTNESS_BAND_FATIGUED_MIN,
    val alertnessBandCriticalMax: Int = ALERTNESS_BAND_CRITICAL_MAX
) {
    companion object {
        // --- Core Timers & Thresholds ---
        const val CLOSURE_CONFIRM_MS = 2500L
        const val FACE_GLITCH_TOLERANCE_MS = 300L
        const val OPEN_EYES_RESPONSE_MS = 3000L
        const val FACE_LOST_REMINDER_MS = 30000L

        // Intervention ladder timers
        const val L4_AFTER_L3_MS = 10000L
        const val L4_AFTER_L3_DEMO_MS = 5000L
        const val L5_AFTER_L4_MS = 20000L
        const val L5_AFTER_L4_DEMO_MS = 8000L
        const val L3_REPEAT_COOLDOWN_MS = 30000L

        // Head pitch (degrees, positive = head down)
        const val HEAD_PITCH_DEG = 15.0f
        const val HEAD_SUSTAIN_MS = 1500L
        const val HEAD_NO_RESPONSE_MS = 5000L
        const val HEAD_RESTORE_CANCEL_MS = 1500L
        const val SOFT_PROMPT_COOLDOWN_MS = 30000L

        // Companion constants
        const val COMPANION_COOLDOWN_MS = 60000L
        const val COMPANION_BACKOFF_MS = 300000L
        const val DISMISS_SILENCE_MS = 120000L
        const val MATH_LATENCY_FACTOR = 2.0
        const val DEFAULT_MATH_MAX_WAIT_MS = 5000L

        // PERCLOS & blink dynamics
        const val PERCLOS_WINDOW_MS = 60000L
        const val PERCLOS_L2 = 0.12f
        const val PERCLOS_ZERO_PENALTY_THRESHOLD = 0.05f
        const val PERCLOS_MAX_PENALTY_THRESHOLD = 0.25f
        const val BLINK_RATE_L1_INCREASE = 0.20f
        const val BLINK_RATE_L1_SUSTAIN_MS = 30000L

        // Calibration
        const val CALIBRATION_OPEN_MS = 10000L
        const val CALIBRATION_CLOSED_MS = 3000L
        const val CALIBRATION_YAWN_MS = 5000L
        const val CALIBRATION_HEAD_POSE_MS = 5000L
        const val CALIBRATION_IGNORE_INITIAL_MS = 1000L
        const val CALIBRATION_THRESHOLD_FACTOR = 0.5f
        const val CALIBRATION_MIN_GAP = 0.05f
        const val QUICK_CALIBRATION_OPEN_MS = 5000L
        const val QUICK_CALIBRATION_CLOSED_MS = 2000L
        const val DEFAULT_RESPONSE_LATENCY_MS = 1500L

        // False alert limits
        const val FALSE_ALERT_LIMIT_COUNT = 3
        const val FALSE_ALERT_LIMIT_WINDOW_MS = 600000L // 10 minutes

        // Thermal thresholds (°C)
        const val THERMAL_REDUCE_C = 42
        const val THERMAL_WARN_C = 45

        // Drive-time penalty ramp
        const val DRIVE_TIME_RAMP_START_HOURS = 2.0
        const val DRIVE_TIME_RAMP_END_HOURS = 6.0
        const val DRIVE_TIME_MAX_PENALTY = 15.0

        // Alertness score weights & EMA
        const val ALERTNESS_WEIGHT_PERCLOS = 30.0
        const val ALERTNESS_WEIGHT_CLOSURE = 35.0
        const val ALERTNESS_WEIGHT_BLINK_RATE = 20.0
        const val ALERTNESS_WEIGHT_HEAD_DROOP = 15.0
        const val ALERTNESS_EMA_ALPHA = 0.15

        // Alertness bands
        const val ALERTNESS_INITIAL_SCORE = 100
        const val ALERTNESS_BAND_ALERT_MIN = 71
        const val ALERTNESS_BAND_CAUTION_MIN = 51
        const val ALERTNESS_BAND_FATIGUED_MIN = 31
        const val ALERTNESS_BAND_CRITICAL_MAX = 30

        // MediaPipe Face Landmarks indices for Eye Aspect Ratio (CAM-3)
        // Right eye indices: [outer_corner, top1, top2, inner_corner, bottom2, bottom1]
        val LANDMARKS_EYE_RIGHT = intArrayOf(33, 160, 158, 133, 153, 144)
        // Left eye indices: [inner_corner, top1, top2, outer_corner, bottom2, bottom1]
        val LANDMARKS_EYE_LEFT = intArrayOf(362, 385, 387, 263, 373, 380)

        // Default instance
        val DEFAULT = Config()
    }
}
