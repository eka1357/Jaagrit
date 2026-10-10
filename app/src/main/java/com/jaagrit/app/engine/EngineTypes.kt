package com.jaagrit.app.engine

/**
 * Core engine domain types and contracts.
 * Pure Kotlin — zero Android imports (AGENTS.md Rule 2).
 *
 * Requirements: docs/ARCHITECTURE.md, docs/REQUIREMENTS.md, docs/DECISIONS.md
 */

enum class DriverState {
    CALIBRATING,
    NORMAL,
    CAUTION,
    FATIGUED,
    CRITICAL,
    FACE_LOST
}

enum class Level {
    L0,
    L1,
    L2,
    L3,
    L4,
    L5
}

enum class Lang {
    HI,
    EN
}

enum class VibePattern {
    SOFT,
    WARNING,
    URGENT
}

enum class VoiceIntent {
    DRIVE_TIME,
    ALERTNESS,
    ALERT_COUNT,
    REPORT,
    RECALIBRATE,
    DISMISS
}

enum class AlertEventType {
    LEVEL_CHANGE,
    CLOSURE,
    HEAD_DROOP,
    FACE_LOST,
    FACE_RESTORED,
    ACTION_TRIGGERED,
    L5_NOT_SENT
}

data class CompanionQuestion(
    val id: Int,
    val prompt: String,
    val answer: String,
    val maxWaitMs: Long,
    // On-screen quick-answer buttons (includes the correct answer), used when speech fails
    val choices: List<String> = emptyList()
)

enum class CompanionPromptKind {
    OPENER,      // L1 friendly question; no reply = "ignored", never escalates (D2)
    MATH,        // L2 cognitive question; no reply within max wait escalates to L3 (COM-3)
    SOFT_CHECK   // LAD-2 head-droop "Sab theek hai?" check
}

/** Companion state for the UI card and debug panel. */
data class CompanionStatus(
    val kind: CompanionPromptKind?,
    val text: String?,
    val choices: List<String> = emptyList(),
    val silenced: Boolean = false,
    val backedOff: Boolean = false,
    val lastLatencyMs: Long? = null,
    val lastAnswerSlow: Boolean = false,
    val lastAnswerCorrect: Boolean? = null
)

sealed interface VoiceEvent {
    data class Command(val intent: VoiceIntent) : VoiceEvent
    data class Answer(val text: String, val latencyMs: Long) : VoiceEvent
    object ImAwake : VoiceEvent
    object Dismiss : VoiceEvent
}

sealed interface Action {
    data class Speak(
        val text: String,
        val lang: Lang = Lang.HI,
        val urgent: Boolean = false
    ) : Action

    data class PlayFamilyClip(val index: Int = 1) : Action
    data class SendSms(val reason: String) : Action
    data class Vibrate(val pattern: VibePattern) : Action
    data class AskQuestion(val q: CompanionQuestion) : Action
    object ShowRedFlash : Action
    data class Log(val event: AlertEventType, val detail: String) : Action
}

/** Raw signal values for the debug panel only (AGENTS.md Rule 7). Present on frame outputs. */
data class EngineMetrics(
    val perclos: Double,
    val blinkRatePerMin: Double,
    // null while the in-drive blink baseline is still being learned (first 3 min, CAL-2)
    val blinkBaselinePerMin: Float?
)

data class EngineOutput(
    val state: DriverState,
    val level: Level,
    val alertness: Int,
    val reasons: List<String>,
    val actions: List<Action>,
    val isAlertActive: Boolean = false,
    val l5CountdownSeconds: Int? = null,
    val falseAlertCount: Int = 0,
    val suggestRecalibration: Boolean = false,
    val metrics: EngineMetrics? = null,
    val companion: CompanionStatus? = null
) {
    companion object {
        val INITIAL = EngineOutput(
            state = DriverState.NORMAL,
            level = Level.L0,
            alertness = 100,
            reasons = emptyList(),
            actions = emptyList(),
            isAlertActive = false,
            l5CountdownSeconds = null,
            falseAlertCount = 0,
            suggestRecalibration = false
        )
    }
}
