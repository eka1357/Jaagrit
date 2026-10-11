# Jaagrit (जागृत)

**An offline driver-alertness companion for Android.** Mount the phone on the dashboard. Jaagrit watches the driver's face through the front camera, estimates alertness in real time, and steps in gradually: it talks to the driver first, and sounds the alarm only if talking doesn't work.

No cloud. No network permission. No face images or audio are ever stored.

> Jaagrit *estimates alertness*. It is not a medical device and does not diagnose drowsiness or any condition. It supports an attentive driver; it does not replace one.

---

## Why

Long-haul truck and bus drivers in India often drive through the night. Dedicated driver-monitoring hardware is expensive, but nearly every driver already has a capable smartphone. Jaagrit turns that phone into a fatigue monitor that:

- **Prevents first.** It starts a short, friendly conversation while fatigue is *building*, before it becomes dangerous.
- **Learns the driver.** Thresholds come from a quick calibration of the driver's own face, glasses, and lighting, not from a generic average.
- **Escalates in steps.** A gentle question comes first, then an alarm, then a family member's voice, then an emergency SMS.
- **Stays private.** Everything runs on the device. The app stores only numbers.

## The intervention ladder

| Level | Trigger | What happens |
|---|---|---|
| **L0** Normal | Alertness score 71–100 | Silent. Green UI. |
| **L1** Early warning | Blink rate ≥ 20% above baseline for 30 s, ≥ 2 yawns in 5 min, or score 51–70 | The companion asks a short, friendly question |
| **L2** Active engagement | PERCLOS ≥ 0.12 over 60 s, or score 31–50 | The companion asks a quick mental-math question. A slow or missing answer counts as a fatigue signal |
| **L3** Direct alert | Eyes closed ≥ 2.5 s, a drooped head with no reply to a spoken check, or score 0–30 | Alarm tone generated in code at max volume, a loud hardcoded Hindi phrase, red flashing screen, strong vibration |
| **L4** Family voice | No response 10 s after L3 (face must be visible) | Plays a pre-recorded clip of a family member |
| **L5** Emergency | No response 20 s after L4 | Visible countdown, then an SMS with GPS location to the emergency contacts |

Any of these counts as a **response** and cancels pending L4/L5 timers:
- tapping **I'M AWAKE**
- keeping the eyes open for 3 s with the face visible

Closing the eyes for 2.5 s again restarts the ladder at L3.

Built-in safety rules:
- **Losing the face never escalates.** If the face isn't visible, the state is *Face not visible* and the app never goes to L3, L4, or L5. After 30 s it asks the driver to adjust the phone.
- **L3–L5 never depend on an LLM, the network, or speech recognition.** They use hardcoded phrases, a bundled audio clip, and an SMS template.
- **The mic stays closed during an active alarm.** Otherwise the siren would trigger false voice matches.
- **Dismiss words** ("चुप रहो", "बंद करो", "बस करो", "रहने दे", "stop") silence the companion for 2 minutes. Closing the eyes for 2.5 s still fires L3. "ठीक हूँ" is deliberately *not* a dismiss word, because a drowsy driver says it on autopilot.

## How alertness is estimated

Each camera frame goes through MediaPipe **FaceLandmarker** (478 landmarks plus a facial transformation matrix). From those, the app computes:

| Signal | What it measures |
|---|---|
| **EAR** (eye aspect ratio) | How open each eye is, compared with the driver's calibrated open/closed threshold |
| **Closure duration** | How long the eyes stay closed. A closure ends only after the eyes stay open for 100 ms, so a single noisy frame can't reset a microsleep timer |
| **PERCLOS** | Fraction of the last 60 s with eyes closed |
| **Blink rate** | Trend compared with the driver's baseline |
| **Head pitch** | Head droop *relative to* the driver's calibrated neutral pose, so the dashboard mount angle doesn't count as a nod |
| **MAR** (mouth aspect ratio) | Yawns: mouth held open ≥ 1.5 s. Speech syllable gaps reset the hold, so talking doesn't count |
| **Drive time** | Penalty ramps from 0 at 2 h to 15 points at 6 h |

These become an **alertness score from 0 to 100**. It starts at 100, subtracts weighted penalties, and is smoothed with an EMA. The active level is `max(raw trigger level, score band level)`. The engine also gives plain-language reasons for every alert, shown in the *Why this alert?* sheet.

## Measured on the target device

iQOO 15 (Snapdragon 8 Elite, Android 16). These are the only numbers we state; see [`docs/MEASUREMENTS.md`](docs/MEASUREMENTS.md).

| Metric | Value |
|---|---|
| FaceLandmarker inference | **18 ms** median (14–25 ms, CPU) |
| Camera pipeline rate | **~24.8 FPS** |
| EAR separation | **0.34 open vs 0.06 closed** (5.6×) |
| Battery temperature | **29.8 °C** idle, **< 34 °C** sustained (throttles at 42 °C) |
| Network traffic | **0 bytes** (INTERNET permission stripped from the merged manifest) |

## Features

- **~60 s calibration:** look ahead normally, close eyes, yawn, and answer one math question. The math answer sets the driver's response-time baseline. Medians are used everywhere.
- **Minimal driver UI:** one big alertness number, a state word, drive time, and alert count. Colors: green focused, amber/orange fatigue building, red critical, grey face not visible, blue calibrating.
- **Hindi-first voice:** offline Android TTS in Hindi and English, with phrases written the way a friend would say them.
- **Voice commands:** push-to-talk ("Ask Jaagrit") for drive time, alertness, alerts today, last alert, recalibrate, and dismiss. A four-button panel always works as a fallback.
- **Companion:** a phrase-bank companion with openers and math questions. It's **off by default**; turn it on in Settings, otherwise L1/L2 only change the UI. It has a 60 s cooldown and backs off for 5 minutes after two ignored openers. An on-device LLM companion (`GemmaCompanion`) fits behind the same `CompanionBrain` interface and falls back to the phrase bank, but it isn't wired in by default.
- **Trip history and dashboard:** alertness sampled every 5 s, alert timeline, trip safety score.
- **Report export:** one-page PDF plus JSON to `Download/Jaagrit/`, ready for laptop file transfer or mirroring.
- **Hardening:** thermal monitoring (analyzes every other frame above 42 °C), low-light warning, camera error recovery, screen kept on at full brightness as a fill light, and a recalibration suggestion after repeated dismissed alerts.
- **Debug panel:** long-press the title to see raw EAR, FPS, inference time, temperature, and demo flags.

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| Camera | CameraX `ImageAnalysis` (front camera, `KEEP_ONLY_LATEST`) |
| Vision | MediaPipe Tasks Vision `FaceLandmarker` (live-stream mode), model bundled in `assets/` |
| Voice | Android `TextToSpeech` (offline Hindi), `SpeechRecognizer` (push-to-talk) |
| Audio | Alarm tone generated in code on `USAGE_ALARM`; family clip via `MediaPlayer` |
| Storage | Room (trips, samples, alert events) and DataStore (baseline, settings) |
| Build | Single-module Gradle, Kotlin DSL, version catalog, KSP |
| SDK | minSdk 31, target/compile SDK 37, `arm64-v8a` only |

## Getting started

### Requirements

- Android Studio with support for **AGP 9.4** (Gradle 9.6 wrapper included)
- **JDK 17+**
- A physical **arm64 Android 12+ phone** with a front camera. The build ships `arm64-v8a` only and needs a real face, so emulators aren't supported.
- On the phone: the **offline Hindi voice** for Google Text-to-Speech (Settings → System → Languages → Text-to-speech → Google → Install voice data → Hindi). For voice commands, also install the offline Hindi speech recognition pack.

### Build and install

```bash
git clone https://github.com/eka1357/Jaagrit.git
cd Jaagrit

# Build and install the debug APK on the connected phone
./gradlew installDebug          # Windows: gradlew.bat installDebug
```

The FaceLandmarker model (`app/src/main/assets/face_landmarker.task`) and the sample family clip (`app/src/main/res/raw/family_1.wav`) are committed, so there's nothing extra to download.

### Run the tests

```bash
./gradlew testDebugUnitTest       # JVM unit tests (engine, ladder, calibration, parsers, export, manifest)
./gradlew connectedDebugAndroidTest   # instrumented tests (Room, locale), needs a connected device
```

`FatigueEngine` is pure Kotlin with an injected `Clock`, so most behavior is tested with scripted fake frames. Covered cases include normal blinks, long closures, face loss mid-closure, head droop with and without a response, the full L3 → L4 → L5 ladder, dismiss followed by eye closure, yawns vs talking, and concurrency stress.

## Using the app

1. **Mount the phone** on the dashboard so your whole face is visible in the front camera.
2. **Start Drive.** The pre-drive checklist requests camera, mic, SMS, location, and notification permissions up front, never mid-drive. It also warns that SMS (L5) needs mobile signal; everything else works in airplane mode.
3. **Calibrate** (~60 s). Look ahead normally, close your eyes when asked, yawn once, and answer the math question. Recalibrate whenever the lighting, glasses, or mount position changes.
4. **Drive.** Most of the time the screen just shows the alertness number. If an alert fires, tap **I'M AWAKE** or open your eyes for 3 s.
5. **Review** the trip on the Dashboard and export the PDF/JSON report.

Set the **emergency contacts**, language, and the L1/L2 **companion** toggle in **Settings**.

### Demo flags

Settings contains two toggles for live demos. Both appear in the debug panel and should be disclosed when used:
- `DEMO_TIMERS` shortens the L4/L5 timers.
- `QUICK_CALIBRATION` shortens the calibration phases.

## Architecture

```
CameraX frame ─► FaceLandmarker ─► FeatureExtractor ─► FaceFrame
                                                          │
Push-to-talk / buttons ─► SpeechInput ─► VoiceEvent       │
                                              ▼           ▼
                             FatigueEngine  (pure Kotlin, injected Clock)
                                  │  state · alertness 0–100 · level · reasons
                                  ▼
                             List<Action>
                                  │
     ┌──────────┬──────────────┬──┴───────────┬────────────┬─────────────┐
     ▼          ▼              ▼              ▼            ▼             ▼
  TTS/alarm  Family clip   SmsNotifier     Haptics     UiState      Room (trips,
                                                      (Compose)     alert events)
                                                                         │
                                                       ReportExporter (PDF/JSON)
```

- **The engine decides; the Android layer executes.** `FatigueEngine` and `InterventionLadder` have no Android imports. They take `FaceFrame`s, voice events, and ticks, and return `Action`s.
- **The pipeline is independent of the UI.** `MonitoringPipeline` talks to the UI only through `StateFlow`, so it can move into a foreground service without a rewrite.
- **Swappable inputs.** `SpeechInput` and `CompanionBrain` are interfaces, so the recognizer or companion can be replaced or removed while the button fallback keeps working.
- **One config file.** Every threshold lives in [`Config.kt`](app/src/main/java/com/jaagrit/app/engine/Config.kt), with the reason for its value.

### Source layout (`com.jaagrit.app`)

```
audio/       FamilyClipPlayer, PlanBAudioPlayer
camera/      CameraController, FaceLandmarkerWrapper, FeatureExtractor, MonitoringPipeline
companion/   CompanionBrain, PhraseBankCompanion, GemmaCompanion, CompanionLoop
data/        JaagritDatabase, DAOs, TripRepository, BaselineStore, SettingsStore
engine/      Config, FatigueEngine, InterventionLadder, EyeStateFilter, BaselineCalculator, Clock
platform/    AlarmToneGenerator, ThermalMonitor, VibeManager
report/      ReportExporter
sms/         SmsNotifier
speech/      TtsSpeaker, RecognizerInput, IntentParser, Phrases
ui/          home, calibration, monitoring, dashboard, history, settings, components, theme
```

## Privacy

- No `INTERNET` or `ACCESS_NETWORK_STATE` permission. Both are explicitly removed from the merged manifest, along with MediaPipe's transitive telemetry services. A unit test (`ManifestSecurityTest`) guards this.
- Camera frames are analyzed in memory and discarded. No images or audio are ever written to disk.
- Only numbers are stored: EAR, head pose, timestamps, alertness samples, and alert events.
- Backups are disabled (`allowBackup="false"`).
- The only data that leaves the phone is an L5 SMS, sent to contacts the driver chose.

## Known limitations

- Low light, sunglasses, and extreme head angles reduce accuracy. The app warns in low light and treats a lost face as *not visible* rather than *asleep*.
- Yawn and eye-hysteresis thresholds are defaults that are still being verified on the phone (see the threshold log in `MEASUREMENTS.md`).
- L5 SMS needs mobile signal. The app reports "not sent" honestly and doesn't assume delivery.
- Voice replies don't dismiss an active L3+ alarm. Tap **I'M AWAKE** or open your eyes.
- Tuned and tested on the iQOO 15. Other phones should work but may need recalibration and threshold tuning.

## Documentation

| Doc | Contents |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Hard rules and working agreement. Read first |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | What and why: users, ladder, screens, demo script |
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) | Exact behavior, priorities, and constants |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Modules, core types, threading, testing strategy |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Design decisions D1–D18 and their rationale |
| [`docs/MEASUREMENTS.md`](docs/MEASUREMENTS.md) | Measured numbers and the threshold change log |
| [`docs/PHRASES.md`](docs/PHRASES.md) | Every spoken phrase, in Devanagari with romanization |
| [`docs/PROMPTS.md`](docs/PROMPTS.md) | Milestone plan (M0–M13) |

`tests/` holds the Python prototypes (MediaPipe face mesh, Whisper) used to validate EAR and speech recognition on a laptop before building the Android version.

---

Built as a solo project for a 48-hour hackathon.
