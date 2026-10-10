package com.jaagrit.app.engine

/**
 * Pure-Kotlin calculator for computing a driver's personal Baseline from calibration samples.
 *
 * Requirements:
 * - CAL-1: Record open-eye EAR (10 s) and closed-eye EAR (3 s);
 *          threshold = closedMedian + 0.5 * (openMedian - closedMedian).
 *          Ignore the first second of each phase (reaction time).
 *          Use medians everywhere for all baselines, never mean (D4).
 * - CAL-4: Baseline validation (gap >= 0.05).
 * - Zero Android dependencies (AGENTS.md Rule 2).
 */
object BaselineCalculator {

    /**
     * Compute median of a list of Floats.
     * Always uses median, never mean (DECISIONS D4).
     */
    fun medianFloat(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }

    /**
     * Compute median of a list of Longs.
     */
    fun medianLong(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2
        }
    }

    /**
     * Compute [Baseline] from raw frame sequences collected during calibration phases.
     *
     * @param openEyeFrames Frames collected during the open-eye phase.
     * @param openPhaseStartMs Timestamp marking the start of the open-eye phase.
     * @param closedEyeFrames Frames collected during the closed-eye phase.
     * @param closedPhaseStartMs Timestamp marking the start of the closed-eye phase.
     * @param yawnFrames Optional frames collected during yawn phase.
     * @param yawnPhaseStartMs Optional timestamp marking the start of the yawn phase.
     * @param responseLatencyMs Latency measured from cognitive math question, if tested.
     * @param calibratedAtMs Timestamp of calibration completion.
     * @param config Threshold and timer configuration.
     */
    fun computeFromFrames(
        openEyeFrames: List<FaceFrame>,
        openPhaseStartMs: Long,
        closedEyeFrames: List<FaceFrame>,
        closedPhaseStartMs: Long,
        yawnFrames: List<FaceFrame> = emptyList(),
        yawnPhaseStartMs: Long = 0L,
        responseLatencyMs: Long = 0L,
        calibratedAtMs: Long = 0L,
        config: Config = Config.DEFAULT
    ): Baseline {
        // Ignore the first second of each phase (reaction time, D4)
        val filteredOpen = openEyeFrames.filter {
            it.faceFound && (it.tsMs - openPhaseStartMs >= config.calibrationIgnoreInitialMs)
        }
        val validOpenFrames = if (filteredOpen.isNotEmpty()) filteredOpen else openEyeFrames.filter { it.faceFound }

        val filteredClosed = closedEyeFrames.filter {
            it.faceFound && (it.tsMs - closedPhaseStartMs >= config.calibrationIgnoreInitialMs)
        }
        val validClosedFrames = if (filteredClosed.isNotEmpty()) filteredClosed else closedEyeFrames.filter { it.faceFound }

        val openEarValues = validOpenFrames.map { it.earAvg }
        val closedEarValues = validClosedFrames.map { it.earAvg }
        val neutralMarValues = validOpenFrames.map { it.mar }

        // Medians everywhere (CAL-1, D4)
        val openMedian = medianFloat(openEarValues)
        val closedMedian = medianFloat(closedEarValues)
        val neutralMarMedian = medianFloat(neutralMarValues)

        // Threshold formula: closedMedian + factor * (openMedian - closedMedian)
        val gap = openMedian - closedMedian
        val threshold = closedMedian + config.calibrationThresholdFactor * gap
        val isValid = gap >= config.calibrationMinGap && openMedian > 0f

        // Baseline blink rate from open phase
        val blinkRate = computeBlinkRate(validOpenFrames, threshold)

        val finalLatency = if (responseLatencyMs > 0L) {
            responseLatencyMs
        } else {
            Config.DEFAULT_RESPONSE_LATENCY_MS
        }

        return Baseline(
            openEar = openMedian,
            closedEar = closedMedian,
            threshold = threshold,
            mar = neutralMarMedian,
            blinkRate = blinkRate,
            responseLatencyMs = finalLatency,
            calibratedAtMs = calibratedAtMs,
            isValid = isValid
        )
    }

    /**
     * Simplified computation directly from pre-filtered EAR lists for unit testing or custom flows.
     */
    fun compute(
        openEarSamples: List<Float>,
        closedEarSamples: List<Float>,
        neutralMarSamples: List<Float> = emptyList(),
        responseLatencyMs: Long = Config.DEFAULT_RESPONSE_LATENCY_MS,
        calibratedAtMs: Long = 0L,
        config: Config = Config.DEFAULT
    ): Baseline {
        val openMedian = medianFloat(openEarSamples)
        val closedMedian = medianFloat(closedEarSamples)
        val marMedian = if (neutralMarSamples.isNotEmpty()) medianFloat(neutralMarSamples) else 0.05f

        val gap = openMedian - closedMedian
        val threshold = closedMedian + config.calibrationThresholdFactor * gap
        val isValid = gap >= config.calibrationMinGap && openMedian > 0f

        return Baseline(
            openEar = openMedian,
            closedEar = closedMedian,
            threshold = threshold,
            mar = marMedian,
            blinkRate = 16.0f, // default baseline blink rate (approx 16 blinks/min)
            responseLatencyMs = responseLatencyMs,
            calibratedAtMs = calibratedAtMs,
            isValid = isValid
        )
    }

    /**
     * Estimates baseline blink rate (blinks per minute) from a sequence of frames during normal open gaze.
     * A blink is identified as a dip below the eye closure threshold lasting between 80ms and 500ms.
     */
    fun computeBlinkRate(frames: List<FaceFrame>, threshold: Float): Float {
        if (frames.size < 5) return Config.DEFAULT_BASELINE_BLINK_RATE

        val durationMs = frames.last().tsMs - frames.first().tsMs
        if (durationMs <= Config.MIN_BLINK_CALCULATION_DURATION_MS) return Config.DEFAULT_BASELINE_BLINK_RATE

        var blinkCount = 0
        var isUnderThreshold = false
        var dipStartMs = 0L

        for (frame in frames) {
            if (frame.earAvg < threshold) {
                if (!isUnderThreshold) {
                    isUnderThreshold = true
                    dipStartMs = frame.tsMs
                }
            } else {
                if (isUnderThreshold) {
                    val closureDuration = frame.tsMs - dipStartMs
                    // Valid natural blink is typically between 80ms and 500ms (Config.BLINK_DURATION_MIN_MS..Config.BLINK_DURATION_MAX_MS)
                    if (closureDuration in Config.BLINK_DURATION_MIN_MS..Config.BLINK_DURATION_MAX_MS) {
                        blinkCount++
                    }
                    isUnderThreshold = false
                }
            }
        }

        // Convert count over duration to blinks per minute
        val minutes = durationMs / 60000.0f
        val rate = if (minutes > 0f) blinkCount / minutes else Config.DEFAULT_BASELINE_BLINK_RATE
        // Clamp to sensible human bounds [5, 45] blinks per min
        return rate.coerceIn(Config.BLINK_RATE_CLAMP_MIN, Config.BLINK_RATE_CLAMP_MAX)
    }
}
