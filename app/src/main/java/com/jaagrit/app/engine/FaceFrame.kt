package com.jaagrit.app.engine

/**
 * Single frame visual feature representation extracted by FeatureExtractor.
 * Pure Kotlin data class with zero Android dependencies per AGENTS.md Rule 2.
 *
 * Reference: docs/ARCHITECTURE.md line 40
 */
data class FaceFrame(
    val tsMs: Long,
    val faceFound: Boolean,
    val earL: Float,
    val earR: Float,
    val mar: Float,
    val pitchDeg: Float,
    val yawDeg: Float,
    val rollDeg: Float
) {
    val earAvg: Float
        get() = if (faceFound) (earL + earR) / 2.0f else 0.0f

    companion object {
        val EMPTY = FaceFrame(
            tsMs = 0L,
            faceFound = false,
            earL = 0f,
            earR = 0f,
            mar = 0f,
            pitchDeg = 0f,
            yawDeg = 0f,
            rollDeg = 0f
        )
    }
}
