package com.jaagrit.app.engine

import com.jaagrit.app.speech.Phrases
import kotlin.random.Random

/**
 * Pure Kotlin InterventionLadder implementing graduated driver interventions (L0 -> L5).
 * Strictly zero Android SDK imports (AGENTS.md Rule 2).
 *
 * Requirements:
 * - LAD-1: Active level = max(rawTriggerLevel, scoreBandLevel).
 *          Raw L3 fires when closure >= 2.5 s (face detected). Random phrase pick, 30 s cooldown.
 * - LAD-2 / D3: Head droop (pitch > 15° for 1.5 s) -> soft prompt ("Sab theek hai? Bol de.").
 *          Escalates to L3 if no reply in 5 s.
 *          Pitch returning below 15° for 1.5 s cancels pending L3 escalation.
 *          Eyes-closed path is independent.
 * - LAD-3: L4 timer starts after L3 + no response for 10 s (face must be detected).
 * - LAD-4: L5 timer starts after L4 + 20 s if no response.
 * - LAD-5 / D5: Response definition: tap, voice reply, or eyes open >= 3 s.
 *          Response cancels pending L4/L5 timers. Re-closure >= 2.5 s after a response restarts at L3.
 * - LAD-7 / D9: DEMO_TIMERS flag shortens L4/L5 only (L4: 5 s, L5: 8 s).
 */
class InterventionLadder(
    val config: Config = Config.DEFAULT,
    val clock: Clock = Clock.SYSTEM,
    private val randomPhrasePicker: (List<String>, Int) -> Int = { list, lastIdx ->
        if (list.size <= 1) 0
        else {
            var next = Random.nextInt(list.size)
            if (next == lastIdx) {
                next = (next + 1) % list.size
            }
            next
        }
    }
) {
    // Current ladder level (L0, L3, L4, or L5)
    var currentLadderLevel: Level = Level.L0
        private set

    // L3 State
    var isL3Active: Boolean = false
        private set
    var lastL3TriggerTimeMs: Long? = null
        private set
    var lastL3PhraseIndex: Int = -1
        private set
    private var hasRespondedSinceL3: Boolean = true

    // Escalation Timers (L4 & L5)
    var l4EscalateAtMs: Long? = null
        private set
    var isL4Active: Boolean = false
        private set
    var l5EscalateAtMs: Long? = null
        private set
    var isL5Active: Boolean = false
        private set

    // Head Droop tracking (LAD-2, D3)
    private var droopSustainedStartTimeMs: Long? = null
    var lastSoftPromptTimeMs: Long? = null
        private set
    var pendingHeadEscalateAtMs: Long? = null
        private set
    private var headRestoreStartTimeMs: Long? = null

    // Continuous Open Eyes tracking (LAD-5)
    private var eyesOpenContinuousStartTimeMs: Long? = null

    // Face lost state (AUDIT-011)
    private var isFaceLost: Boolean = false

    val isAlertActive: Boolean
        get() = isL3Active || isL4Active || isL5Active

    /**
     * Process a frame and return any newly triggered ladder actions.
     */
    fun onFrame(
        faceFound: Boolean,
        isEyesClosed: Boolean,
        closureDurationMs: Long,
        pitchDeg: Float,
        now: Long = clock.nowMs()
    ): List<Action> {
        val actions = mutableListOf<Action>()

        if (!faceFound) {
            onFaceLost(now)
            return emptyList()
        }

        // Face returned after loss: restart L4/L5 countdown from that moment (AUDIT-011)
        if (isFaceLost) {
            isFaceLost = false
            if (isL3Active && !isL4Active && !isL5Active) {
                l4EscalateAtMs = now + config.l4AfterL3Ms
            } else if (isL4Active && !isL5Active) {
                l5EscalateAtMs = now + config.l5AfterL4Ms
            }
        }

        // 1. Continuous Open Eyes for Awake Response (LAD-5)
        if (!isEyesClosed) {
            if (eyesOpenContinuousStartTimeMs == null) {
                eyesOpenContinuousStartTimeMs = now
            }
            val openDuration = now - eyesOpenContinuousStartTimeMs!!
            if (openDuration >= config.openEyesResponseMs) {
                if (isL3Active || isL4Active || isL5Active) {
                    actions.addAll(processResponse(now, "Eyes open >= 3s"))
                }
            }
        } else {
            eyesOpenContinuousStartTimeMs = null
        }

        // 2. Head Droop Evaluation (LAD-2, D3)
        actions.addAll(processHeadDroop(pitchDeg, now, faceFound = true))

        // 3. Eye Closure Evaluation (LAD-1) — independent of head pose
        if (closureDurationMs >= config.closureConfirmMs) {
            if (!isL3Active && !isL4Active && !isL5Active) {
                actions.addAll(fireL3(now, "Eye closure >= 2.5s"))
            } else if (isL3Active && !isL4Active && !isL5Active) {
                val lastL3 = lastL3TriggerTimeMs ?: 0L
                if (now - lastL3 >= config.l3RepeatCooldownMs) {
                    actions.addAll(fireL3(now, "Eye closure repeated after cooldown"))
                }
            }
        }

        // 4. Check Escalation Timers (L4 & L5) — face must be detected
        actions.addAll(checkEscalationTimers(now, faceFound = true))

        return actions
    }

    /**
     * Process periodic tick (e.g. 100ms timer) when no frame is arriving or to evaluate timers.
     */
    fun onTick(now: Long = clock.nowMs(), faceFound: Boolean = true): List<Action> {
        val actions = mutableListOf<Action>()
        if (faceFound) {
            // Face returned after loss: restart L4/L5 countdown from that moment (AUDIT-011)
            if (isFaceLost) {
                isFaceLost = false
                if (isL3Active && !isL4Active && !isL5Active) {
                    l4EscalateAtMs = now + config.l4AfterL3Ms
                } else if (isL4Active && !isL5Active) {
                    l5EscalateAtMs = now + config.l5AfterL4Ms
                }
            }

            // Check head droop escalation timeout if pending
            if (pendingHeadEscalateAtMs != null && now >= pendingHeadEscalateAtMs!!) {
                pendingHeadEscalateAtMs = null
                headRestoreStartTimeMs = null
                actions.addAll(fireL3(now, "Head droop no response in 5s"))
            }
            actions.addAll(checkEscalationTimers(now, faceFound = true))
        } else {
            onFaceLost(now)
        }
        return actions
    }

    /**
     * Called when face detection is lost (ENG-4).
     * Cancels head droop continuous tracking; timers for L4/L5 never fire while face is lost.
     */
    fun onFaceLost(now: Long = clock.nowMs()) {
        isFaceLost = true
        eyesOpenContinuousStartTimeMs = null
        droopSustainedStartTimeMs = null
        headRestoreStartTimeMs = null
    }

    /**
     * Trigger L3 alert with alarm actions, phrase pick, and L4 timer schedule.
     */
    fun fireL3(now: Long, reason: String): List<Action> {
        // Cooldown check: if no response has been given since last L3, 30s cooldown prevents repeats
        if (!hasRespondedSinceL3 && lastL3TriggerTimeMs != null) {
            val elapsedSinceL3 = now - lastL3TriggerTimeMs!!
            if (elapsedSinceL3 < config.l3RepeatCooldownMs) {
                return emptyList()
            }
        }

        isL3Active = true
        hasRespondedSinceL3 = false
        lastL3TriggerTimeMs = now
        currentLadderLevel = Level.L3
        l4EscalateAtMs = now + config.l4AfterL3Ms
        l5EscalateAtMs = null
        isL4Active = false
        isL5Active = false
        pendingHeadEscalateAtMs = null
        // Reset eyes-open response timer so awake response requires >= 3s AFTER L3 fired (AUDIT-009)
        eyesOpenContinuousStartTimeMs = null

        lastL3PhraseIndex = randomPhrasePicker(Phrases.L3_ALERTS, lastL3PhraseIndex)
        val phrase = Phrases.L3_ALERTS[lastL3PhraseIndex]

        return listOf(
            Action.ShowRedFlash,
            Action.Vibrate(VibePattern.URGENT),
            Action.Speak(phrase, Lang.HI, urgent = true),
            Action.Log(AlertEventType.ACTION_TRIGGERED, "L3 alert: $reason")
        )
    }

    /**
     * Handle driver response (tap, voice reply, or eyes open >= 3s) (LAD-5, D5).
     * Cancels pending L4/L5 timers, cancels head droop escalation, and resets ladder.
     */
    fun processResponse(now: Long = clock.nowMs(), reason: String): List<Action> {
        val wasActive = isL3Active || isL4Active || isL5Active || pendingHeadEscalateAtMs != null
        isL3Active = false
        isL4Active = false
        isL5Active = false
        l4EscalateAtMs = null
        l5EscalateAtMs = null
        pendingHeadEscalateAtMs = null
        headRestoreStartTimeMs = null
        eyesOpenContinuousStartTimeMs = null
        isFaceLost = false
        currentLadderLevel = Level.L0
        hasRespondedSinceL3 = true

        return if (wasActive) {
            listOf(Action.Log(AlertEventType.ACTION_TRIGGERED, "Response processed: $reason"))
        } else {
            emptyList()
        }
    }

    /** Reset ladder for a new session */
    fun reset(now: Long = clock.nowMs()) {
        currentLadderLevel = Level.L0
        isL3Active = false
        lastL3TriggerTimeMs = null
        lastL3PhraseIndex = -1
        hasRespondedSinceL3 = true
        l4EscalateAtMs = null
        isL4Active = false
        l5EscalateAtMs = null
        isL5Active = false
        droopSustainedStartTimeMs = null
        lastSoftPromptTimeMs = null
        pendingHeadEscalateAtMs = null
        headRestoreStartTimeMs = null
        eyesOpenContinuousStartTimeMs = null
        isFaceLost = false
    }

    // --- Private Helpers ---

    private fun processHeadDroop(pitchDeg: Float, now: Long, faceFound: Boolean): List<Action> {
        val actions = mutableListOf<Action>()
        if (!faceFound) return emptyList()

        // 1. Track pitch droop (> 15°)
        if (pitchDeg > config.headPitchDeg) {
            if (droopSustainedStartTimeMs == null) {
                droopSustainedStartTimeMs = now
            }
            val duration = now - droopSustainedStartTimeMs!!
            if (duration >= config.headSustainMs) {
                val cooldownPassed = lastSoftPromptTimeMs == null || (now - lastSoftPromptTimeMs!!) >= config.softPromptCooldownMs
                if (cooldownPassed && pendingHeadEscalateAtMs == null && !isL3Active && !isL4Active && !isL5Active) {
                    lastSoftPromptTimeMs = now
                    pendingHeadEscalateAtMs = now + config.headNoResponseMs
                    headRestoreStartTimeMs = null
                    actions.add(Action.Speak(Phrases.SOFT_PROMPTS.first(), Lang.HI, urgent = false))
                    actions.add(Action.Log(AlertEventType.HEAD_DROOP, "Head droop soft prompt emitted"))
                }
            }
        } else {
            droopSustainedStartTimeMs = null
        }

        // 2. Check pending head droop escalation & cancellation (D3)
        if (pendingHeadEscalateAtMs != null) {
            if (pitchDeg <= config.headPitchDeg) {
                if (headRestoreStartTimeMs == null) {
                    headRestoreStartTimeMs = now
                }
                val restoreDuration = now - headRestoreStartTimeMs!!
                if (restoreDuration >= config.headRestoreCancelMs) {
                    // Canceled! Pitch restored below threshold for >= 1.5s
                    pendingHeadEscalateAtMs = null
                    headRestoreStartTimeMs = null
                    actions.add(Action.Log(AlertEventType.HEAD_DROOP, "Head droop escalation cancelled: pitch recovered"))
                }
            } else {
                headRestoreStartTimeMs = null
            }

            // Check 5s timeout
            if (pendingHeadEscalateAtMs != null && now >= pendingHeadEscalateAtMs!!) {
                pendingHeadEscalateAtMs = null
                headRestoreStartTimeMs = null
                actions.addAll(fireL3(now, "Head droop no response in 5s"))
            }
        }

        return actions
    }

    private fun checkEscalationTimers(now: Long, faceFound: Boolean): List<Action> {
        val actions = mutableListOf<Action>()
        // L4 and L5 never fire when face is lost (ENG-4, LAD-3, M4b)
        if (!faceFound) return emptyList()

        // L4 check (after L3 + no response for 10s, face detected)
        if (isL3Active && !isL4Active && !isL5Active && l4EscalateAtMs != null) {
            if (now >= l4EscalateAtMs!!) {
                isL4Active = true
                currentLadderLevel = Level.L4
                l5EscalateAtMs = now + config.l5AfterL4Ms
                actions.add(Action.PlayFamilyClip(1))
                actions.add(Action.Vibrate(VibePattern.URGENT))
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "L4 family clip triggered"))
            }
        }

        // L5 check (after L4 + 20s with no response)
        if (isL4Active && !isL5Active && l5EscalateAtMs != null) {
            if (now >= l5EscalateAtMs!!) {
                isL5Active = true
                currentLadderLevel = Level.L5
                actions.add(Action.SendSms("Driver unresponsive after repeated warnings"))
                actions.add(Action.Vibrate(VibePattern.URGENT))
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "L5 SMS triggered"))
            }
        }

        return actions
    }
}
