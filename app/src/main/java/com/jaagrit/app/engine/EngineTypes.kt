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
    ACTION_TRIGGERED
}

data class CompanionQuestion(
    val id: Int,
    val prompt: String,
    val answer: String,
    val maxWaitMs: Long
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

data class EngineOutput(
    val state: DriverState,
    val level: Level,
    val alertness: Int,
    val reasons: List<String>,
    val actions: List<Action>,
    val isAlertActive: Boolean = false
) {
    companion object {
        val INITIAL = EngineOutput(
            state = DriverState.NORMAL,
            level = Level.L0,
            alertness = 100,
            reasons = emptyList(),
            actions = emptyList(),
            isAlertActive = false
        )
    }
}
