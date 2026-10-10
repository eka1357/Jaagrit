# REQUIREMENTS.md — Jaagrit

Priority: **P0** demo dies without it · **P1** big scoring gain · **P2** only after P0/P1 are solid.
Cut order if time runs out (first to cut at the bottom): see end of file.

## Functional

### Camera & vision
| ID | Pri | Requirement |
|---|---|---|
| CAM-1 | P0 | Front camera via CameraX ImageAnalysis, KEEP_ONLY_LATEST, small preview on the monitoring screen |
| CAM-2 | P0 | MediaPipe FaceLandmarker in live-stream mode, one face, face detected/lost flag every frame |
| CAM-3 | P0 | Per frame: EAR (left, right, average) using landmarks R `[33,160,158,133,153,144]`, L `[362,385,387,263,373,380]`; mouth aspect ratio; head pitch/yaw/roll |
| CAM-4 | P1 | Debug panel: FPS, inference ms, phone temperature, active heavy component |

### Calibration
| ID | Pri | Requirement |
|---|---|---|
| CAL-1 | P0 | Record open-eye EAR (10 s) and closed-eye EAR (3 s); threshold = closedMedian + 0.5 × (openMedian − closedMedian). Ignore the first second of each phase (reaction time). Use median everywhere for all baselines (EAR, blink rate, MAR, latency), never mean (D4). See also `QUICK_CALIBRATION` flag (D9). |
| CAL-2 | P1 | Record yawn MAR baseline, head-pose range, baseline blink rate median (refine during first 3 min of driving) |
| CAL-3 | P1 | One math question sets the driver's baseline response time (median) |
| CAL-4 | P0 | Baseline persisted (DataStore); "Recalibrate" available anytime |

### Fatigue engine (pure Kotlin)
| ID | Pri | Requirement |
|---|---|---|
| ENG-1 | P0 | Eye "closed" only when EAR < personal threshold. Closure counts only if face is stable; a face loss under 300 ms does not reset a closure timer. |
| ENG-2 | P0 | Alertness score 0–100 (heuristic, not medical): starts at 100, subtracts weighted penalties (PERCLOS over 60 s, longest recent closure, blink-rate increase vs baseline, head droop, drive time), smoothed with EMA (`ALERTNESS_EMA_ALPHA = 0.15`). Drive-time penalty: 0 from 0–2 h, ramps linearly from 0 at 2 h to max 15 points at 6 h, then caps at 15 points. Bands: 71–100 Alert (L0), 51–70 Caution (L1), 31–50 Fatigued (L2), 0–30 Critical (L3). Closure ≥ 2.5 s forces CRITICAL regardless of score. All weights in Config.kt. Engine emits deterministic `reasons: List<String>`. Active ladder level = `max(rawTriggerLevel, scoreBandLevel)`. See D1, D12. |
| ENG-3 | P1 | Sliding 60 s PERCLOS, blink-rate trend vs baseline, sustained head droop |
| ENG-4 | P0 | **Face not found → state FACE_LOST. Never escalates to L3/L4/L5.** After 30 s, speak a reminder to adjust the phone. |
| ENG-5 | P0 | Engine emits `Action`s; it never touches Android APIs. Pipeline kept UI-independent so it can later run in a foreground service (D15). |
| ENG-6 | P1 | "Why this alert" reasons list derived deterministically from the same contributing penalty features |

### Intervention ladder
| ID | Pri | Requirement |
|---|---|---|
| LAD-1 | P0 | Active level is `max(rawTriggerLevel, scoreBandLevel)`. Raw L3 fires when eyes closed ≥ 2.5 s (face detected). Generates alarm tone in code on USAGE_ALARM at max volume + spoken phrase + vibration (D13). Phrase picked randomly from hardcoded list (Plan B: developer voice clips if offline TTS poor, D14). 30 s cooldown on repeats unless condition worsens. See D12. |
| LAD-2 | P1 | Head pitch > 15° sustained ≥ 1.5 s → soft spoken prompt only ("Sab theek hai? Bol de."), 30 s cooldown. Becomes L3 only if no response within 5 s. Pitch returning below 15° for 1.5 s cancels pending escalation (head-droop path only). See D3. |
| LAD-3 | P0 | L4 (family clip) fires only if: face detected AND L3 fired AND no response for 10 s |
| LAD-4 | P1 | L5 starts a visible countdown if no response 20 s after L4; SMS to emergency contact with GPS; any response cancels; in-app "SMS sent to …" confirmation (don't rely on delivery) |
| LAD-5 | P0 | **"Response" = tap "I'M AWAKE", OR a recognized voice reply, OR eyes continuously open ≥ 3 s with face detected.** A response cancels pending L4/L5 timers. Re-closure ≥ 2.5 s after a response restarts the ladder at L3. Head-droop escalation canceled if pitch recovers for 1.5 s (head-droop path only; eye path independent). A sent SMS cannot be recalled. See D3, D5. |
| LAD-6 | P1 | False-positive guard: more than 3 L3 alerts dismissed within 10 min → suggest recalibration |
| LAD-7 | P1 | `DEMO_TIMERS` flag shortens L4/L5 timers for the demo; shown in the debug panel |

### Companion (L1/L2)
| ID | Pri | Requirement |
|---|---|---|
| COM-1 | P1 | Active only at L1/L2 (raw trigger or score band). Silent when alert. Questions ≤ 5 s of speech. Fire once on band entry, then wait for response. 60 s cooldown between companion prompts (`COMPANION_COOLDOWN_MS`). See D2, D12. |
| COM-2 | P1 | `PhraseBankCompanion` works with no LLM: openers + math questions from `docs/PHRASES.md` |
| COM-3 | P1 | Measure response latency (voice or button). > 2× baseline = fatigue signal. L1 no-reply = "ignored" (not escalated); two ignored openers in a row → companion stops for 5 min (`COMPANION_BACKOFF_MS`). L2 cognitive math: per-question max wait stored with question data from PHRASES.md (Config.kt holds default 5 s); no answer within max wait → escalate to L3. See D2. |
| COM-4 | P1 | Dismiss word → companion silent for 2 min; passive monitoring continues; **eyes closed ≥ 2.5 s still fires L3** |
| COM-5 | P2 | `GemmaCompanion` generates varied openers/questions; output validated (≥3 words, mostly Hindi/English chars) else fall back to the phrase bank |
| COM-6 | P1 | Pattern lines (e.g. "last time you got sleepy around now") only when real trip history exists |

### Voice & speech
| ID | Pri | Requirement |
|---|---|---|
| VOI-1 | P0 | TTS speaks Hindi (offline voice) and English; rate/pitch configurable, higher urgency for L3. Plan B: if offline Hindi voice quality is poor, developer voice recordings (`res/raw/l3_1..6.mp3`) will be played for L3 instead of TTS (D14). |
| VOI-2 | P0 | Four-button fallback panel: Drive Time / Alertness / Alerts / Report. Always available. |
| VOI-3 | P1 | `SpeechInput` via Android `SpeechRecognizer` with offline preference for commands and dismiss; keyword/intent matching is deterministic (no LLM) |
| VOI-4 | P2 | `WhisperInput` (whisper.cpp tiny/base) behind the same interface, run only after voice-activity detection, never continuously |
| VOI-5 | P1 | Language: Hindi / English toggle in settings; auto-detect only if VOI-4 ships |

### Data, reports, Office Kit
| ID | Pri | Requirement |
|---|---|---|
| DAT-1 | P1 | Room: `Trip`, `AlertEvent`, `Baseline`. Alertness sampled every 5 s into the trip. |
| DAT-2 | P1 | Drive-time and alert-count queries answer from the DB |
| OFF-1 | P0 | Phone screens work well when mirrored (large text, landscape-safe dashboard) |
| OFF-2 | P0 | Export trip report PDF (Android `PdfDocument`) + JSON to `Download/Jaagrit/` so Office Kit file transfer can pick it up |
| OFF-3 | P1 | Dashboard screen: alertness curve, alert timeline, trip score, driver profile |
| OFF-4 | P1 | Clipboard: "Copy last alert" puts a text summary on the clipboard |
| OFF-5 | P1 | Remote control: nothing special in-app; make Start/Stop large and reachable. (No remote vehicle control, ever.) |
*Assumption to verify with organizers: Office Kit has no SDK; it mirrors the screen, transfers files, shares clipboard, and gives remote control.*

### UI
| ID | Pri | Requirement |
|---|---|---|
| UI-1 | P0 | Monitoring screen as in `PRODUCT.md`: big number, state color, drive time, alerts, privacy badge |
| UI-2 | P0 | Red state: full-screen flash, big "I'M AWAKE" button, strong vibration, and alarm tone generated in code on alarm stream (USAGE_ALARM) at max volume. No audio asset file (D13). |
| UI-3 | P1 | Start Drive flow, Why-this-alert sheet, Settings/Privacy |
| UI-4 | P0 | Keep screen on and brightness max while monitoring (also acts as fill light) |

## Constants (all in `Config.kt`)
```
CLOSURE_CONFIRM_MS = 2500
FACE_GLITCH_TOLERANCE_MS = 300
L4_AFTER_L3_MS = 10000          // demo: 5000
L5_AFTER_L4_MS = 20000          // demo: 8000
L3_REPEAT_COOLDOWN_MS = 30000
HEAD_PITCH_DEG = 15             // positive = head down (D3)
HEAD_SUSTAIN_MS = 1500
HEAD_NO_RESPONSE_MS = 5000
HEAD_RESTORE_CANCEL_MS = 1500   // pitch below threshold for 1.5 s cancels pending head-droop L3 (D3)
SOFT_PROMPT_COOLDOWN_MS = 30000
FACE_LOST_REMINDER_MS = 30000
OPEN_EYES_RESPONSE_MS = 3000
DISMISS_SILENCE_MS = 120000
PERCLOS_WINDOW_MS = 60000
PERCLOS_L2 = 0.12               // raw L2 trigger (D12)
BLINK_RATE_L1_INCREASE = 0.20   // raw L1 trigger threshold: +20% (D12)
BLINK_RATE_L1_SUSTAIN_MS = 30000// sustained for 30 s (D12)
MATH_LATENCY_FACTOR = 2.0
DEFAULT_MATH_MAX_WAIT_MS = 5000 // default fallback; per-question stored with question data (D2)
FALSE_ALERT_LIMIT = 3 per 10 min
THERMAL_REDUCE_C = 42           // halve analysis rate (battery temp, D10)
THERMAL_WARN_C = 45
COMPANION_COOLDOWN_MS = 60000   // between companion prompts (D2)
COMPANION_BACKOFF_MS = 300000   // 5 min backoff after 2 ignored openers (D2)
QUICK_CALIBRATION_OPEN_MS = 5000   // demo only (D9)
QUICK_CALIBRATION_CLOSED_MS = 2000 // demo only (D9)

// Drive time penalty ramp (D1)
DRIVE_TIME_RAMP_START_HOURS = 2.0
DRIVE_TIME_RAMP_END_HOURS = 6.0
DRIVE_TIME_MAX_PENALTY = 15.0

// Alertness score weights & EMA (D1) — all tunable (AUDIT-013 aligned with code values)
ALERTNESS_WEIGHT_PERCLOS = 30.0     // Ramps to L2 boundary (0.12), max penalty 40.0 at PERCLOS >= 0.25
ALERTNESS_WEIGHT_CLOSURE = 35.0     // Max penalty for closures approaching 2.5 s
ALERTNESS_WEIGHT_BLINK_RATE = 25.0  // Max penalty (15.0 at +20% L1 boundary, 25.0 at +50%)
ALERTNESS_WEIGHT_HEAD_DROOP = 20.0  // Max penalty for sustained head droop
ALERTNESS_EMA_ALPHA = 0.15
```
Tune these from measured data, not guesses. Record changes in `docs/MEASUREMENTS.md`.

## Non-functional
- Detection-to-alert latency target: under 500 ms after the 2.5 s closure is confirmed (measure and report the real number).
- Must survive a 20-minute continuous run without crash or overheating beyond the thermal limits.
- Detection, alerts, generated alarm tone, and voice work fully offline (airplane mode). SMS (L5) requires mobile signal; show "not sent" gracefully in airplane mode. Pre-drive checklist warns about this (D6).
- Only one heavy component (Gemma or speech recognizer) active at a time alongside face analysis.
- Permissions requested during the pre-drive flow, never mid-demo: camera, mic, SMS, location, notifications.
- Every-other-frame analysis and lower resolution acceptable under thermal pressure or high inference latency (D11).
- Thermal monitoring uses `BatteryManager` temperature + `PowerManager` thermal status listener. No `/sys/class/thermal/` (D10).
- Architectural separation: camera and engine pipeline are decoupled from UI so they can later run in a foreground service (D15, AGENTS.md Rule 10).

## Cut order (cut from the bottom first)
**Never cut:** CAM-1..3, CAL-1/4, ENG-1/2/4/5, LAD-1/3/5, VOI-1/2, OFF-1/2, UI-1/2/4
**Cut last:** OFF-3/4/5, DAT-1/2, LAD-2, CAL-2/3
**Cut if needed:** COM-1..4 (fall back to L3 only), VOI-3, LAD-6/7
**Cut first:** LAD-4 (SMS), COM-5, COM-6, VOI-4/5
