# ARCHITECTURE.md — Jaagrit

## Data flow
```
CameraX frame ──► FaceLandmarker ──► FeatureExtractor ──► FaceFrame
                                                             │
Mic ──► SpeechInput (buttons / SpeechRecognizer) ──► VoiceEvent
                                                             ▼
                              FatigueEngine (pure Kotlin, injected Clock)
                                    │  state, alertness 0-100, level
                                    ▼
                               List<Action>
                                    │
        ┌───────────┬───────────────┼───────────────┬──────────────┐
        ▼           ▼               ▼               ▼              ▼
   Speaker(TTS)  FamilyClip     SmsNotifier     Haptics       UiState (Compose)
                                    │
                                    ▼
                          Room (Trip, AlertEvent, Baseline)
                                    │
                                    ▼
                  ReportExporter (PDF/JSON → Download/Jaagrit) ──► Office Kit
```

## Packages (`com.jaagrit.app`)
```
ui/          home, calibration, monitoring, whyalert, dashboard, settings, components, theme
camera/      CameraController, FaceLandmarkerWrapper, FeatureExtractor
engine/      Config, FatigueEngine, InterventionLadder, Calibration, Alertness, Action, Clock   (NO android imports)
speech/      Speaker, SpeechInput (+ ButtonInput, RecognizerInput), IntentParser, Phrases
companion/   CompanionBrain (+ PhraseBankCompanion; GemmaCompanion later)
data/        JaagritDatabase, TripDao, AlertDao, BaselineStore (DataStore)
report/      ReportExporter (PdfDocument + JSON)
platform/    SmsNotifier, LocationProvider, ThermalMonitor, Haptics
MainActivity.kt, JaagritApp.kt
```

## Core types
```kotlin
data class FaceFrame(
  val tsMs: Long, val faceFound: Boolean,
  val earL: Float, val earR: Float, val mar: Float,
  val pitchDeg: Float, val yawDeg: Float, val rollDeg: Float
)

sealed interface VoiceEvent {
  data class Command(val intent: VoiceIntent) : VoiceEvent
  data class Answer(val text: String, val latencyMs: Long) : VoiceEvent
  object ImAwake : VoiceEvent          // button tap
  object Dismiss : VoiceEvent
}

enum class DriverState { CALIBRATING, NORMAL, CAUTION, FATIGUED, CRITICAL, FACE_LOST }
enum class Level { L0, L1, L2, L3, L4, L5 }

sealed interface Action {
  data class Speak(val text: String, val lang: Lang, val urgent: Boolean) : Action
  data class PlayFamilyClip(val index: Int) : Action
  data class SendSms(val reason: String) : Action
  data class Vibrate(val pattern: VibePattern) : Action
  data class AskQuestion(val q: CompanionQuestion) : Action
  object ShowRedFlash : Action
  data class Log(val event: AlertEventType, val detail: String) : Action
}

data class EngineOutput(
  val state: DriverState, val level: Level, val alertness: Int,
  val reasons: List<String>, val actions: List<Action>
)

interface Clock { fun nowMs(): Long }

class FatigueEngine(config: Config, clock: Clock, baseline: Baseline) {
  fun onFrame(f: FaceFrame): EngineOutput
  fun onVoice(e: VoiceEvent): EngineOutput
  fun onTick(): EngineOutput     // timers: L4/L5, cooldowns, face-lost reminder
}

interface SpeechInput { fun start(); fun stop(); val events: Flow<VoiceEvent> }
interface CompanionBrain { fun opener(ctx: CompanionContext): String; fun question(ctx: CompanionContext): CompanionQuestion }
```

## Room schema
```
Trip(id, startMs, endMs?, avgAlertness?, alertCount, criticalCount)
AlertSample(tripId, tsMs, alertness)                  // every 5 s
AlertEvent(id, tripId, tsMs, level, reason, durationMs, response)   // response: none|imAwake|voice|eyesOpen|dismissed
Baseline (DataStore): openEar, closedEar, threshold, mar, blinkRate, responseLatencyMs, calibratedAtMs
```

## Threading
- CameraX analyzer thread → FaceLandmarker (live-stream callback) → `FaceFrame` to a single `Channel`.
- One coroutine consumes frames and ticks (every 100 ms), calls the engine, executes actions on Main/IO as needed.
- UI observes a `StateFlow<UiState>`.
- Thermal: `ThermalMonitor` polls battery temperature every 10 s. Above `THERMAL_REDUCE_C`, analyze every other frame.

## Testing strategy
- Engine unit tests with a `FakeClock` and scripted `FaceFrame` sequences: normal, blink (<300 ms), long closure, face lost mid-closure, head dip + response, full L3→L4→L5 with and without response, dismiss then eyes close.
- Phone checklist per milestone (in `PROMPTS.md`).
- Dev-only simulator (debug builds only, labeled "SIMULATED") may feed scripted frames to the engine to test the ladder without sitting in front of the camera.
