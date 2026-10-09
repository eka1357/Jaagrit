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
        /** Fallback default baseline if uncalibrated */
        val DEFAULT = Baseline(
            openEar = 0.28f,
            closedEar = 0.05f,
            threshold = 0.165f, // 0.05 + 0.5 * (0.28 - 0.05)
            mar = 0.05f,
            blinkRate = 16.0f,
            responseLatencyMs = 1500L,
            calibratedAtMs = 0L,
            isValid = false
        )
    }
}
