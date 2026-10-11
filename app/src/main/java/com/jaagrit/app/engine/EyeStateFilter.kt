package com.jaagrit.app.engine

/**
 * Instantaneous eye state with reopen hysteresis (D18).
 * Pure Kotlin — zero Android dependencies (AGENTS.md Rule 2).
 *
 * Closing keeps the protected strict comparison `ear < threshold`. Once closed, the eye only
 * counts as open again above `threshold + earHysteresisFraction × earGap`, so EAR jitter around
 * the threshold can't flicker into fake blinks or chop a closure.
 */
class EyeStateFilter(
    private val config: Config,
    private val baseline: Baseline
) {
    var isClosed: Boolean = false
        private set

    private val reopenEar: Float =
        baseline.threshold + config.earHysteresisFraction * baseline.earGap.coerceAtLeast(0f)

    fun update(ear: Float): Boolean {
        isClosed = if (isClosed) ear <= reopenEar else ear < baseline.threshold
        return isClosed
    }

    fun reset() {
        isClosed = false
    }
}
