package com.jaagrit.app.engine

/**
 * Driver-specific baseline calibrated at the start of a drive or from Settings.
 * Pure Kotlin — zero Android dependencies (AGENTS.md Rule 2).
 *
 * Requirements: docs/ARCHITECTURE.md, docs/REQUIREMENTS.md (CAL-1, CAL-4) & docs/DECISIONS.md (D4).
 */
data class Baseline(
    val openEar: Float,
    val closedEar: Float,
    val threshold: Float,
    val mar: Float = 0f,
    val blinkRate: Float = 0f, // blinks per minute
    val responseLatencyMs: Long = 0L,
    val calibratedAtMs: Long = 0L,
    val isValid: Boolean = true
) {
    /** Difference between open and closed eye aspect ratios */
    val earGap: Float get() = openEar - closedEar

    companion object {
        /** Fallback default baseline if uncalibrated (AUDIT-018: isotropic pixel-space) */
        val DEFAULT = Baseline(
            openEar = Config.DEFAULT_BASELINE_OPEN_EAR,
            closedEar = Config.DEFAULT_BASELINE_CLOSED_EAR,
            threshold = Config.DEFAULT_BASELINE_THRESHOLD,
            mar = Config.DEFAULT_BASELINE_MAR,
            blinkRate = Config.DEFAULT_BASELINE_BLINK_RATE,
            responseLatencyMs = Config.DEFAULT_RESPONSE_LATENCY_MS,
            calibratedAtMs = 0L,
            isValid = false
        )
    }
}
