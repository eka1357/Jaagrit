package com.jaagrit.app.engine

import com.jaagrit.app.speech.Phrases
import kotlin.math.roundToInt

/**
 * Pure Kotlin FatigueEngine implementing driver alertness estimation and state management.
 * Strictly zero Android SDK imports (AGENTS.md Rule 2).
 *
 * Requirements:
 * - ENG-1: Eye closure detection (2.5 s confirmation, 300 ms face glitch tolerance).
 * - ENG-2: Heuristic alertness score (0-100) combining PERCLOS, longest closure, blink rate,
 *          head droop, and drive time ramp (2h..6h max 15 pts), smoothed with EMA.
 * - ENG-4: FACE_LOST state: never escalates to alarms, 30 s verbal reminder.
 * - ENG-5: Emits pure Action list without Android API dependencies.
 * - DECISIONS: D1 (weights & scoring), D12 (ladder level arbitration: max(rawTrigger, scoreBand)).
 */
class FatigueEngine(
    val config: Config = Config.DEFAULT,
    val clock: Clock = Clock.SYSTEM,
    val baseline: Baseline = Baseline.DEFAULT,
    val ladder: InterventionLadder = InterventionLadder(config, clock)
) {
    // Drive start timestamp
    private var driveStartTimeMs: Long = clock.nowMs()

    // Alertness score state
    private var smoothedAlertness: Double = 100.0
    private var isFirstScoreSample: Boolean = true

    // Eye closure tracking
    private var closureStartTimeMs: Long? = null
    private var lastClosedFrameTimeMs: Long? = null
    private val recentClosures = ArrayDeque<Pair<Long, Long>>() // (closureEndTimeMs, durationMs)

    // Glitch & Face lost tracking
    private var faceLostStartTimeMs: Long? = null
    private var hasSpokenFaceLostReminder: Boolean = false

    // Sliding 60s sample window for PERCLOS
    private data class EarSample(val tsMs: Long, val isClosed: Boolean)
    private val slidingWindow = ArrayDeque<EarSample>()

    // Blink rate tracking (detected blinks within 60s)
    private val recentBlinks = ArrayDeque<Long>()

    // Head droop tracking
    private var headDroopStartTimeMs: Long? = null

    val isAlertActive: Boolean
        get() = ladder.isAlertActive

    /**
     * Ingest a new face frame and return updated driver state, alertness, and actions.
     */
    fun onFrame(f: FaceFrame): EngineOutput {
        val now = if (f.tsMs > 0L) f.tsMs else clock.nowMs()

        // 1. Handle Face Lost (ENG-4)
        if (!f.faceFound) {
            ladder.onFaceLost(now)
            return handleFaceLost(now)
        }

        // 2. Face is Present: recover from face lost if needed
        faceLostStartTimeMs = null
        hasSpokenFaceLostReminder = false

        // 3. Eye Closure Detection (ENG-1)
        val isClosed = f.earAvg < baseline.threshold
        val currentClosureDuration = processEyeClosure(isClosed, now)

        // 4. Update sliding window for PERCLOS
        slidingWindow.addLast(EarSample(now, isClosed))
        pruneOldSamples(now)

        // 5. Update Head Droop tracking (D3)
        val droopDurationMs = processHeadDroop(f.pitchDeg, now)

        // 6. Compute Heuristic Alertness Penalties (D1)
        val reasons = mutableListOf<String>()

        // 6a. PERCLOS penalty (0 to 40 points)
        val perclos = computePerclos()
        val perclosPenalty = computePerclosPenalty(perclos)
        if (perclosPenalty > 5.0) {
            reasons.add("PERCLOS high (${(perclos * 100).toInt()}%)")
        }

        // 6b. Longest recent closure penalty (0 to 35 points)
        val longestRecentClosureMs = computeLongestClosure(currentClosureDuration, now)
        val closurePenalty = computeClosurePenalty(longestRecentClosureMs)
        if (closurePenalty > 5.0) {
            reasons.add("Long eye closure (${"%.1f".format(longestRecentClosureMs / 1000.0)}s)")
        }

        // 6c. Blink rate increase penalty (0 to 25 points, gated until M9a per AUDIT-012)
        val blinkRatio = if (config.blinkSignalEnabled) computeBlinkRateRatio(now) else 0.0
        val blinkPenalty = if (config.blinkSignalEnabled) computeBlinkRatePenalty(blinkRatio) else 0.0
        if (config.blinkSignalEnabled && blinkPenalty > 5.0) {
            reasons.add("Blink rate elevated (+${(blinkRatio * 100).toInt()}% vs baseline)")
        }

        // 6d. Head droop penalty (0 to 20 points)
        val headDroopPenalty = computeHeadDroopPenalty(droopDurationMs)
        if (headDroopPenalty > 5.0) {
            reasons.add("Head droop detected (${f.pitchDeg.toInt()}°)")
        }

        // 6e. Drive time penalty ramp (0 to 15 points, 2h..6h ramp)
        val driveHours = (now - driveStartTimeMs).coerceAtLeast(0L) / 3_600_000.0
        val driveTimePenalty = computeDriveTimePenalty(driveHours)
        if (driveTimePenalty > 5.0) {
            reasons.add("Long drive duration (${"%.1f".format(driveHours)}h)")
        }

        // 7. Calculate smoothed alertness score (EMA smoothing alpha = 0.15)
        val totalPenalty = perclosPenalty + closurePenalty + blinkPenalty + headDroopPenalty + driveTimePenalty
        val rawScore = (100.0 - totalPenalty).coerceIn(0.0, 100.0)

        if (isFirstScoreSample) {
            smoothedAlertness = rawScore
            isFirstScoreSample = false
        } else {
            smoothedAlertness = config.alertnessEmaAlpha * rawScore + (1.0 - config.alertnessEmaAlpha) * smoothedAlertness
        }

        val alertnessScore = smoothedAlertness.roundToInt().coerceIn(0, 100)

        // 8. Ladder Level Arbitration (ENG-2, D12)
        val scoreBandLevel = when {
            alertnessScore >= config.alertnessBandAlertMin -> Level.L0
            alertnessScore >= config.alertnessBandCautionMin -> Level.L1
            alertnessScore >= config.alertnessBandFatiguedMin -> Level.L2
            else -> Level.L3
        }

        val rawTriggerLevel = when {
            currentClosureDuration >= config.closureConfirmMs -> Level.L3
            perclos >= config.perclosL2 -> Level.L2
            config.blinkSignalEnabled && blinkRatio >= config.blinkRateL1Increase && (now - driveStartTimeMs >= config.blinkRateL1SustainMs) -> Level.L1
            else -> Level.L0
        }

        // 9. Intervention ladder onFrame
        val ladderActions = ladder.onFrame(
            faceFound = true,
            isEyesClosed = isClosed,
            closureDurationMs = currentClosureDuration,
            pitchDeg = f.pitchDeg,
            now = now
        )

        // If ladder processed an awake response (e.g. eyes open >= 3s), reset score & closure history
        if (ladderActions.any { it is Action.Log && it.detail.contains("Response processed") }) {
            closureStartTimeMs = null
            lastClosedFrameTimeMs = null
            recentClosures.clear()
            slidingWindow.clear()
            smoothedAlertness = 100.0
            isFirstScoreSample = true
        }

        val activeLevel = maxOf(ladder.currentLadderLevel, rawTriggerLevel, scoreBandLevel)

        // State mapping: Hard override if eyes closed >= 2.5 s or activeLevel in [L3, L4, L5] -> CRITICAL
        val activeState = when {
            currentClosureDuration >= config.closureConfirmMs -> DriverState.CRITICAL
            activeLevel in listOf(Level.L3, Level.L4, Level.L5) -> DriverState.CRITICAL
            activeLevel == Level.L2 -> DriverState.FATIGUED
            activeLevel == Level.L1 -> DriverState.CAUTION
            else -> DriverState.NORMAL
        }

        if (ladder.isL5Active) {
            reasons.add(0, "L5: Unresponsive - SMS dispatched")
        } else if (ladder.isL4Active) {
            reasons.add(0, "L4: Unresponsive to alarm - Family voice active")
        } else if (ladder.isL3Active) {
            reasons.add(0, "L3: Critical drowsiness detected")
        }

        return EngineOutput(
            state = activeState,
            level = activeLevel,
            alertness = alertnessScore,
            reasons = reasons,
            actions = ladderActions,
            isAlertActive = ladder.isAlertActive,
            l5CountdownSeconds = ladder.getL5RemainingSeconds(now),
            falseAlertCount = ladder.falseAlertCount,
            suggestRecalibration = ladder.suggestRecalibration
        )
    }

    /**
     * Handle periodic clock ticks (e.g. every 100ms) for timer checks like L4/L5, cooldowns, and FACE_LOST reminder.
     */
    fun onTick(): EngineOutput {
        val now = clock.nowMs()
        val actions = mutableListOf<Action>()

        if (faceLostStartTimeMs != null) {
            val lostDuration = now - faceLostStartTimeMs!!
            if (lostDuration >= config.faceLostReminderMs && !hasSpokenFaceLostReminder) {
                hasSpokenFaceLostReminder = true
                actions.add(Action.Speak(Phrases.FACE_LOST_30S, Lang.HI, urgent = false))
            }

            return EngineOutput(
                state = DriverState.FACE_LOST,
                level = Level.L0,
                alertness = smoothedAlertness.roundToInt().coerceIn(0, 100),
                reasons = listOf("Face not visible in camera"),
                actions = actions,
                isAlertActive = ladder.isAlertActive,
                l5CountdownSeconds = ladder.getL5RemainingSeconds(now),
                falseAlertCount = ladder.falseAlertCount,
                suggestRecalibration = ladder.suggestRecalibration
            )
        }

        // Tick ladder timers
        actions.addAll(ladder.onTick(now, faceFound = true))

        val activeLevel = maxOf(ladder.currentLadderLevel, currentScoreBandLevel())
        val activeState = when {
            activeLevel in listOf(Level.L3, Level.L4, Level.L5) -> DriverState.CRITICAL
            activeLevel == Level.L2 -> DriverState.FATIGUED
            activeLevel == Level.L1 -> DriverState.CAUTION
            else -> DriverState.NORMAL
        }

        val reasons = mutableListOf<String>()
        if (ladder.isL5Active) {
            reasons.add("L5: Unresponsive - SMS dispatched")
        } else if (ladder.isL4Active) {
            reasons.add("L4: Unresponsive to alarm - Family voice active")
        } else if (ladder.isL3Active) {
            reasons.add("L3: Critical drowsiness detected")
        }

        return EngineOutput(
            state = activeState,
            level = activeLevel,
            alertness = smoothedAlertness.roundToInt().coerceIn(0, 100),
            reasons = reasons,
            actions = actions,
            isAlertActive = ladder.isAlertActive,
            l5CountdownSeconds = ladder.getL5RemainingSeconds(now),
            falseAlertCount = ladder.falseAlertCount,
            suggestRecalibration = ladder.suggestRecalibration
        )
    }

    /**
     * Handle voice events (commands, answers, dismiss, I'M AWAKE).
     */
    fun onVoice(e: VoiceEvent): EngineOutput {
        val now = clock.nowMs()
        val actions = mutableListOf<Action>()
        if (e is VoiceEvent.ImAwake || e is VoiceEvent.Answer || e is VoiceEvent.Command) {
            actions.addAll(ladder.processResponse(now, e.toString()))
            closureStartTimeMs = null
            lastClosedFrameTimeMs = null
            recentClosures.clear()
            slidingWindow.clear()
            smoothedAlertness = 100.0
            isFirstScoreSample = true
        }

        val activeLevel = maxOf(ladder.currentLadderLevel, currentScoreBandLevel())
        val activeState = when {
            faceLostStartTimeMs != null -> DriverState.FACE_LOST
            activeLevel in listOf(Level.L3, Level.L4, Level.L5) -> DriverState.CRITICAL
            activeLevel == Level.L2 -> DriverState.FATIGUED
            activeLevel == Level.L1 -> DriverState.CAUTION
            else -> DriverState.NORMAL
        }

        val reasons = mutableListOf<String>()
        if (ladder.isL5Active) {
            reasons.add("L5: Unresponsive - SMS dispatched")
        } else if (ladder.isL4Active) {
            reasons.add("L4: Unresponsive to alarm - Family voice active")
        } else if (ladder.isL3Active) {
            reasons.add("L3: Critical drowsiness detected")
        }

        return EngineOutput(
            state = activeState,
            level = activeLevel,
            alertness = smoothedAlertness.roundToInt().coerceIn(0, 100),
            reasons = reasons,
            actions = actions,
            isAlertActive = ladder.isAlertActive,
            l5CountdownSeconds = ladder.getL5RemainingSeconds(now),
            falseAlertCount = ladder.falseAlertCount,
            suggestRecalibration = ladder.suggestRecalibration
        )
    }

    /** Reset engine session for a new drive */
    fun resetDrive(startTimeMs: Long = clock.nowMs()) {
        driveStartTimeMs = startTimeMs
        smoothedAlertness = 100.0
        isFirstScoreSample = true
        closureStartTimeMs = null
        lastClosedFrameTimeMs = null
        recentClosures.clear()
        faceLostStartTimeMs = null
        hasSpokenFaceLostReminder = false
        slidingWindow.clear()
        recentBlinks.clear()
        headDroopStartTimeMs = null
        ladder.reset(startTimeMs)
    }

    private fun currentScoreBandLevel(): Level {
        val score = smoothedAlertness.roundToInt().coerceIn(0, 100)
        return when {
            score >= config.alertnessBandAlertMin -> Level.L0
            score >= config.alertnessBandCautionMin -> Level.L1
            score >= config.alertnessBandFatiguedMin -> Level.L2
            else -> Level.L3
        }
    }

    // --- Private Helper Logic ---

    private fun handleFaceLost(now: Long): EngineOutput {
        if (faceLostStartTimeMs == null) {
            faceLostStartTimeMs = now
            hasSpokenFaceLostReminder = false
        }

        // Glitch tolerance check: if face loss exceeds 300ms, cancel in-progress closure
        if (closureStartTimeMs != null) {
            val lastClosed = lastClosedFrameTimeMs ?: now
            val glitchMs = now - lastClosed
            if (glitchMs > config.faceGlitchToleranceMs) {
                closureStartTimeMs = null
                lastClosedFrameTimeMs = null
            }
        }

        val lostDuration = now - faceLostStartTimeMs!!
        val actions = mutableListOf<Action>()

        if (lostDuration >= config.faceLostReminderMs && !hasSpokenFaceLostReminder) {
            hasSpokenFaceLostReminder = true
            actions.add(Action.Speak(Phrases.FACE_LOST_30S, Lang.HI, urgent = false))
        }

        return EngineOutput(
            state = DriverState.FACE_LOST,
            level = Level.L0, // Never escalates while face is lost (ENG-4)
            alertness = smoothedAlertness.roundToInt(),
            reasons = listOf("Face not visible in camera"),
            actions = actions,
            isAlertActive = ladder.isAlertActive,
            l5CountdownSeconds = ladder.getL5RemainingSeconds(now),
            falseAlertCount = ladder.falseAlertCount,
            suggestRecalibration = ladder.suggestRecalibration
        )
    }

    private fun processEyeClosure(isClosed: Boolean, now: Long): Long {
        return if (isClosed) {
            if (closureStartTimeMs == null) {
                closureStartTimeMs = now
            }
            lastClosedFrameTimeMs = now
            now - closureStartTimeMs!!
        } else {
            if (closureStartTimeMs != null) {
                val duration = now - closureStartTimeMs!!
                // If duration in blink range [80ms..500ms], record as a blink
                if (duration in 80L..500L) {
                    recentBlinks.addLast(now)
                }
                recentClosures.addLast(now to duration)
                closureStartTimeMs = null
                lastClosedFrameTimeMs = null
            }
            0L
        }
    }

    private fun processHeadDroop(pitchDeg: Float, now: Long): Long {
        return if (pitchDeg > config.headPitchDeg) {
            if (headDroopStartTimeMs == null) {
                headDroopStartTimeMs = now
            }
            now - headDroopStartTimeMs!!
        } else {
            headDroopStartTimeMs = null
            0L
        }
    }

    private fun pruneOldSamples(now: Long) {
        val cutoff = now - config.perclosWindowMs
        while (slidingWindow.isNotEmpty() && slidingWindow.first().tsMs < cutoff) {
            slidingWindow.removeFirst()
        }
        while (recentClosures.isNotEmpty() && recentClosures.first().first < cutoff) {
            recentClosures.removeFirst()
        }
        while (recentBlinks.isNotEmpty() && recentBlinks.first() < cutoff) {
            recentBlinks.removeFirst()
        }
    }

    private fun computePerclos(): Double {
        if (slidingWindow.isEmpty()) return 0.0
        val closedCount = slidingWindow.count { it.isClosed }
        return closedCount.toDouble() / slidingWindow.size
    }

    private fun computePerclosPenalty(perclos: Double): Double {
        return when {
            perclos <= config.perclosZeroPenaltyThreshold -> 0.0
            perclos <= config.perclosL2 -> {
                config.alertnessWeightPerclos * (perclos - config.perclosZeroPenaltyThreshold) /
                        (config.perclosL2 - config.perclosZeroPenaltyThreshold)
            }
            perclos <= config.perclosMaxPenaltyThreshold -> {
                config.alertnessWeightPerclos + 10.0 * (perclos - config.perclosL2) /
                        (config.perclosMaxPenaltyThreshold - config.perclosL2)
            }
            else -> 40.0
        }
    }

    private fun computeLongestClosure(currentDuration: Long, now: Long): Long {
        val completedMax = recentClosures.maxOfOrNull { it.second } ?: 0L
        return maxOf(currentDuration, completedMax)
    }

    private fun computeClosurePenalty(longestClosureMs: Long): Double {
        return when {
            longestClosureMs < 500L -> 0.0
            longestClosureMs >= config.closureConfirmMs -> config.alertnessWeightClosure
            else -> config.alertnessWeightClosure * (longestClosureMs - 500.0) / (config.closureConfirmMs - 500.0)
        }
    }

    private fun computeBlinkRateRatio(now: Long): Double {
        if (baseline.blinkRate <= 0f) return 0.0
        val windowMinutes = minOf((now - driveStartTimeMs) / 60000.0, 1.0).coerceAtLeast(0.2)
        val currentRate = recentBlinks.size / windowMinutes
        return (currentRate - baseline.blinkRate) / baseline.blinkRate
    }

    private fun computeBlinkRatePenalty(blinkRatio: Double): Double {
        return when {
            blinkRatio <= 0.0 -> 0.0
            blinkRatio <= config.blinkRateL1Increase -> 15.0 * (blinkRatio / config.blinkRateL1Increase)
            blinkRatio <= 0.50 -> 15.0 + 10.0 * (blinkRatio - config.blinkRateL1Increase) / (0.50 - config.blinkRateL1Increase)
            else -> 25.0
        }
    }

    private fun computeHeadDroopPenalty(droopDurationMs: Long): Double {
        return when {
            droopDurationMs < 500L -> 0.0
            droopDurationMs >= config.headSustainMs -> config.alertnessWeightHeadDroop
            else -> config.alertnessWeightHeadDroop * (droopDurationMs - 500.0) / (config.headSustainMs - 500.0)
        }
    }

    private fun computeDriveTimePenalty(driveHours: Double): Double {
        return when {
            driveHours < config.driveTimeRampStartHours -> 0.0
            driveHours >= config.driveTimeRampEndHours -> config.driveTimeMaxPenalty
            else -> config.driveTimeMaxPenalty * (driveHours - config.driveTimeRampStartHours) /
                    (config.driveTimeRampEndHours - config.driveTimeRampStartHours)
        }
    }
}
