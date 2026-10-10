package com.jaagrit.app.ui.calibration

import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame

/**
 * Result of validating calibration frame sequences and computed baseline (AUDIT-010).
 */
sealed class CalibrationValidationResult {
    data class Success(val baseline: Baseline) : CalibrationValidationResult()
    data class Rejected(val reason: RejectionReason, val partialBaseline: Baseline) : CalibrationValidationResult()

    sealed class RejectionReason {
        data class InsufficientOpenFrames(val usableCount: Int) : RejectionReason()
        data class InsufficientClosedFrames(val usableCount: Int) : RejectionReason()
        object NoClosedEyeFrames : RejectionReason()
        data class GapTooSmall(val gap: Float) : RejectionReason()
    }
}

/**
 * Validates calibration input data according to AUDIT-010 and CAL-4 rules.
 * Ensures a minimum of 15 usable post-ignore frames per phase and non-empty closed-eye frames.
 */
object CalibrationValidator {

    const val MIN_USABLE_FRAMES_PER_PHASE = 15

    fun validate(
        openFrames: List<FaceFrame>,
        openStartMs: Long,
        closedFrames: List<FaceFrame>,
        closedStartMs: Long,
        baseline: Baseline,
        config: Config = Config.DEFAULT
    ): CalibrationValidationResult {
        val usableOpen = openFrames.filter {
            it.faceFound && (it.tsMs - openStartMs >= config.calibrationIgnoreInitialMs)
        }
        val usableClosed = closedFrames.filter {
            it.faceFound && (it.tsMs - closedStartMs >= config.calibrationIgnoreInitialMs)
        }

        // 1. Must have closed-eye frames with face detected
        if (closedFrames.none { it.faceFound } || usableClosed.isEmpty()) {
            return CalibrationValidationResult.Rejected(
                CalibrationValidationResult.RejectionReason.NoClosedEyeFrames,
                baseline.copy(isValid = false)
            )
        }

        // 2. Open phase must have at least 15 usable post-ignore frames
        if (usableOpen.size < MIN_USABLE_FRAMES_PER_PHASE) {
            return CalibrationValidationResult.Rejected(
                CalibrationValidationResult.RejectionReason.InsufficientOpenFrames(usableOpen.size),
                baseline.copy(isValid = false)
            )
        }

        // 3. Closed phase must have at least 15 usable post-ignore frames
        if (usableClosed.size < MIN_USABLE_FRAMES_PER_PHASE) {
            return CalibrationValidationResult.Rejected(
                CalibrationValidationResult.RejectionReason.InsufficientClosedFrames(usableClosed.size),
                baseline.copy(isValid = false)
            )
        }

        // 4. Separation gap check (CAL-4)
        if (!baseline.isValid || baseline.earGap < config.calibrationMinGap) {
            return CalibrationValidationResult.Rejected(
                CalibrationValidationResult.RejectionReason.GapTooSmall(baseline.earGap),
                baseline.copy(isValid = false)
            )
        }

        return CalibrationValidationResult.Success(baseline)
    }
}
