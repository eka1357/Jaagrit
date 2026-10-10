package com.jaagrit.app.companion

import com.jaagrit.app.engine.Action
import com.jaagrit.app.engine.AlertEventType
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Clock
import com.jaagrit.app.engine.CompanionPromptKind
import com.jaagrit.app.engine.CompanionQuestion
import com.jaagrit.app.engine.CompanionStatus
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.Lang
import com.jaagrit.app.engine.Level
import com.jaagrit.app.engine.VoiceEvent
import com.jaagrit.app.engine.VoiceIntent
import com.jaagrit.app.speech.Phrases

/**
 * Pure Kotlin state machine managing driver engagement at L1 and L2 (COM-1, COM-3, D2).
 * Strictly zero Android SDK imports (AGENTS.md Rule 2).
 *
 * Rules:
 * - COM-1: Active only at L1/L2. Silent at L0 (normal) and L3/L4/L5 (alert active).
 *          Never speaks when driver's eyes are closed.
 *          Fires once on band entry, with 60 s cooldown between prompts (COMPANION_COOLDOWN_MS).
 * - COM-2: Backed by [CompanionBrain] ([PhraseBankCompanion]).
 * - COM-3: L1 openers: no reply is marked "ignored". Two consecutive ignored openers -> 5 min backoff (COMPANION_BACKOFF_MS).
 *          L2 math questions: measures response latency.
 *          Latency > 2x baseline = fatigue confirmation.
 *          No answer within the question's max wait -> escalates to L3.
 * - COM-4: Dismiss keywords: companion goes silent for 2 min (DISMISS_SILENCE_MS).
 *          Passive eye tracking continues uninterrupted.
 */
class CompanionLoop(
    val config: Config = Config.DEFAULT,
    val clock: Clock = Clock.SYSTEM,
    val brain: CompanionBrain = PhraseBankCompanion(config),
    var baseline: Baseline = Baseline.DEFAULT
) {
    // Current active prompt state
    var currentPromptKind: CompanionPromptKind? = null
        private set
    var currentPromptText: String? = null
        private set
    var currentQuestion: CompanionQuestion? = null
        private set
    var questionAskedAtMs: Long? = null
        private set
    var questionDeadlineMs: Long? = null
        private set

    // Timing and backoff state
    var lastPromptTimeMs: Long? = null
        private set
    var silencedUntilMs: Long? = null
        private set
    var backedOffUntilMs: Long? = null
        private set
    var consecutiveIgnoredOpeners: Int = 0
        private set

    // Latency & performance tracking (COM-3)
    var lastLatencyMs: Long? = null
        private set
    var lastAnswerSlow: Boolean = false
        private set
    var lastAnswerCorrect: Boolean? = null
        private set
    private var slowAnswerReportedUntilMs: Long? = null

    // Track ladder level for band transition detection
    private var previousLevel: Level = Level.L0

    sealed interface CompanionOutcome {
        data class Actions(val actions: List<Action>) : CompanionOutcome
        data class EscalateToL3(val reason: String, val actions: List<Action>) : CompanionOutcome
    }

    /**
     * Process periodic tick or frame evaluation.
     */
    fun evaluate(
        activeLevel: Level,
        isEyesClosed: Boolean,
        isAlertActive: Boolean,
        hasSleepyHistoryNow: Boolean = false,
        lang: Lang = Lang.HI,
        now: Long = clock.nowMs()
    ): CompanionOutcome {
        val actions = mutableListOf<Action>()

        // 1. If critical alert is active (L3, L4, L5) or L0 normal: clear active prompt if any
        if (isAlertActive || activeLevel in listOf(Level.L0, Level.L3, Level.L4, Level.L5)) {
            if (isAlertActive) {
                // Active emergency cancels pending prompt
                clearPrompt()
            }
            previousLevel = activeLevel
            return CompanionOutcome.Actions(emptyList())
        }

        // 2. Check timeouts on active question/opener
        if (currentPromptKind != null && questionDeadlineMs != null && now >= questionDeadlineMs!!) {
            val timedOutKind = currentPromptKind
            val timedOutQuestion = currentQuestion
            clearPrompt()

            if (timedOutKind == CompanionPromptKind.MATH) {
                // COM-3: No answer within max wait -> escalate to L3!
                val escalateReason = "Cognitive question timeout (${timedOutQuestion?.prompt ?: "math"})"
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, escalateReason))
                previousLevel = activeLevel
                return CompanionOutcome.EscalateToL3(escalateReason, actions)
            } else if (timedOutKind == CompanionPromptKind.OPENER) {
                // COM-3 / D2: No reply to opener = "ignored" (not escalated)
                consecutiveIgnoredOpeners++
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Opener ignored ($consecutiveIgnoredOpeners in a row)"))
                if (consecutiveIgnoredOpeners >= 2) {
                    backedOffUntilMs = now + config.companionBackoffMs
                    consecutiveIgnoredOpeners = 0
                    actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Companion backoff for 5 min after 2 ignored openers"))
                }
            }
        }

        // 3. Check if eligible to initiate a new prompt at L1 or L2
        val isSilenced = silencedUntilMs != null && now < silencedUntilMs!!
        val isBackedOff = backedOffUntilMs != null && now < backedOffUntilMs!!
        val isWaitingAnswer = currentPromptKind != null

        val isBandEntry = activeLevel != previousLevel && (activeLevel == Level.L1 || activeLevel == Level.L2)
        val cooldownPassed = lastPromptTimeMs == null || (now - lastPromptTimeMs!!) >= config.companionCooldownMs

        previousLevel = activeLevel

        if (!isEyesClosed && !isSilenced && !isBackedOff && !isWaitingAnswer && (isBandEntry || cooldownPassed)) {
            val ctx = CompanionContext(
                level = activeLevel,
                lang = lang,
                hasSleepyHistoryNow = hasSleepyHistoryNow
            )

            if (activeLevel == Level.L2) {
                // L2: Ask cognitive math question
                val q = brain.question(ctx)
                currentQuestion = q
                currentPromptKind = CompanionPromptKind.MATH
                currentPromptText = q.prompt
                questionAskedAtMs = now
                // Allow speech time + question max wait
                questionDeadlineMs = now + q.maxWaitMs + config.companionSpeechAllowanceMs
                lastPromptTimeMs = now

                actions.add(Action.AskQuestion(q))
                actions.add(Action.Speak(q.prompt, lang = lang, urgent = false))
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Companion math question: ${q.prompt} (max wait ${q.maxWaitMs}ms)"))
            } else if (activeLevel == Level.L1) {
                // L1: Friendly opener
                val openerText = brain.opener(ctx)
                currentQuestion = null
                currentPromptKind = CompanionPromptKind.OPENER
                currentPromptText = openerText
                questionAskedAtMs = now
                questionDeadlineMs = now + config.companionSpeechAllowanceMs + config.companionOpenerReplyWindowMs
                lastPromptTimeMs = now

                actions.add(Action.Speak(openerText, lang = lang, urgent = false))
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Companion opener: $openerText"))
            }
        }

        return CompanionOutcome.Actions(actions)
    }

    /**
     * Handle incoming voice events or manual on-screen answer button presses.
     */
    fun onVoice(e: VoiceEvent, lang: Lang = Lang.HI, now: Long = clock.nowMs()): List<Action> {
        val actions = mutableListOf<Action>()

        when (e) {
            is VoiceEvent.Dismiss -> {
                silencedUntilMs = now + config.dismissSilenceMs
                clearPrompt()
                consecutiveIgnoredOpeners = 0
                val ack = Phrases.ACK_DISMISS.pick(lang)
                actions.add(Action.Speak(ack, lang = lang, urgent = false))
                actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Companion dismissed for 2 min"))
            }
            is VoiceEvent.Answer -> {
                consecutiveIgnoredOpeners = 0 // Driver responded
                val promptKind = currentPromptKind
                val question = currentQuestion
                val askedAt = questionAskedAtMs ?: now
                val latency = if (e.latencyMs > 0L) e.latencyMs else (now - askedAt).coerceAtLeast(0L)

                lastLatencyMs = latency
                clearPrompt()
                lastPromptTimeMs = now // Cooldown starts from response

                if (promptKind == CompanionPromptKind.MATH && question != null) {
                    val baselineLatency = baseline.responseLatencyMs.takeIf { it > 0L } ?: Config.DEFAULT_RESPONSE_LATENCY_MS
                    val isSlow = latency > (baselineLatency * config.mathLatencyFactor)
                    lastAnswerSlow = isSlow
                    if (isSlow) {
                        slowAnswerReportedUntilMs = now + config.companionSlowAnswerWindowMs
                    }

                    val parsed = NumberAnswerParser.parse(e.text)
                    val isCorrect = parsed?.toString() == question.answer
                    lastAnswerCorrect = isCorrect

                    val ack = if (isSlow) Phrases.ACK_SLOW.pick(lang) else Phrases.ACK_GOOD.pick(lang)
                    actions.add(Action.Speak(ack, lang = lang, urgent = false))
                    actions.add(
                        Action.Log(
                            AlertEventType.ACTION_TRIGGERED,
                            "Math response: '${e.text}' (latency=${latency}ms, slow=$isSlow, correct=$isCorrect)"
                        )
                    )
                } else {
                    val ack = Phrases.ACK_GOOD.pick(lang)
                    actions.add(Action.Speak(ack, lang = lang, urgent = false))
                    actions.add(Action.Log(AlertEventType.ACTION_TRIGGERED, "Opener response: '${e.text}'"))
                }
            }
            is VoiceEvent.Command, is VoiceEvent.ImAwake -> {
                // If driver spoke any command or tapped I'M AWAKE while an opener was waiting, mark opener responded
                if (currentPromptKind == CompanionPromptKind.OPENER) {
                    consecutiveIgnoredOpeners = 0
                    clearPrompt()
                    lastPromptTimeMs = now
                }
            }
        }

        return actions
    }

    /**
     * True if a confirmed slow cognitive response penalty/reason is active.
     */
    fun isSlowCognitiveResponseActive(now: Long = clock.nowMs()): Boolean {
        return slowAnswerReportedUntilMs?.let { now < it } == true
    }

    /**
     * Current companion status for UI and EngineOutput.
     */
    fun status(now: Long = clock.nowMs()): CompanionStatus {
        return CompanionStatus(
            kind = currentPromptKind,
            text = currentPromptText,
            choices = currentQuestion?.choices ?: emptyList(),
            silenced = silencedUntilMs?.let { now < it } == true,
            backedOff = backedOffUntilMs?.let { now < it } == true,
            lastLatencyMs = lastLatencyMs,
            lastAnswerSlow = isSlowCognitiveResponseActive(now),
            lastAnswerCorrect = lastAnswerCorrect
        )
    }

    fun reset(now: Long = clock.nowMs()) {
        clearPrompt()
        lastPromptTimeMs = null
        silencedUntilMs = null
        backedOffUntilMs = null
        consecutiveIgnoredOpeners = 0
        lastLatencyMs = null
        lastAnswerSlow = false
        lastAnswerCorrect = null
        slowAnswerReportedUntilMs = null
        previousLevel = Level.L0
    }

    private fun clearPrompt() {
        currentPromptKind = null
        currentPromptText = null
        currentQuestion = null
        questionAskedAtMs = null
        questionDeadlineMs = null
    }
}
