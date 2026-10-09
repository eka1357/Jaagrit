package com.jaagrit.app.camera

/**
 * Data class representing the output of the vision pipeline for a single frame.
 * Kept independent of UI per AGENTS.md Rule 10.
 */
data class VisionResult(
    val faceFound: Boolean = false,
    val inferenceTimeMs: Long = 0L,
    val fps: Float = 0f,
    val timestampMs: Long = 0L
)
