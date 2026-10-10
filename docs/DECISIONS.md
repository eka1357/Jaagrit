# DECISIONS.md — Jaagrit

Design decisions made during Prompt 0 kickoff and subsequent reviews. Reference these when implementing.

---

## D1 — Alertness score heuristic & weights (Q1/Q6, ENG-2)

Score starts at 100 and subtracts weighted penalties from five signals:
1. **PERCLOS over 60 s window:**
   - 0 penalty below 0.05.
   - Escalates up to 30 points at PERCLOS = 0.12 (the L2 trigger boundary) and max 40 points at PERCLOS ≥ 0.25.
2. **Longest recent eye closure:**
   - Evaluated over a rolling 60 s window.
   - Up to 35 points penalty for closures approaching 2.5 s.
   - **Hard override:** an eye closure ≥ 2.5 s immediately forces state = `CRITICAL` regardless of the score.
3. **Blink-rate increase vs baseline:**
   - Compared against the calibrated baseline blink rate.
   - 0 penalty up to baseline; ramps to 15 points at +20% increase (L1 trigger boundary), up to 25 points at +50% increase.
4. **Head droop:**
   - Sustained pitch above threshold (15° for ≥ 1.5 s) contributes up to 20 points penalty.
5. **Drive-time penalty:**
   - **Formula:** 0 points penalty from 0 to 2 hours of driving.
   - Ramps linearly from 0 at 2 h to a maximum of 15 points at 6 h: `penalty = 15.0 * (driveHours - 2.0) / 4.0`.
   - Capped at 15 points for drive time ≥ 6 h.

**Smoothing & bounds:**
- Raw score = `100 - (penalties)`. Clamped to `[0, 100]`.
- Smoothed with Exponential Moving Average (EMA): `smoothed = alpha * raw + (1 - alpha) * smoothed` (e.g. `ALERTNESS_EMA_ALPHA = 0.15`).
- Score bands:
  - **71–100:** Alert (L0)
  - **51–70:** Caution (L1)
  - **31–50:** Fatigued (L2)
  - **0–30:** Critical (L3)
- All weights and parameters live in `Config.kt`. Initial values are sensible defaults, tuned later from `docs/MEASUREMENTS.md`.
- Engine produces a deterministic `reasons: List<String>` from the contributing penalties (e.g. "PERCLOS high (14%)", "Blink rate +25% vs baseline", "Drive time > 4h").
- Unit-tested with scripted frame sequences.

---

## D2 — Companion rules & per-question max wait (Q2, COM-1/COM-3)

- L1/L2 companion fires **once** when the ladder level enters L1 or L2, then waits for a response up to the question's max wait.
- **60 s cooldown** between companion prompts (`COMPANION_COOLDOWN_MS = 60000`).
- **L1 openers:** no reply is NOT escalated — it counts as "ignored". Two ignored openers in a row → companion pauses for 5 min (`COMPANION_BACKOFF_MS = 300000`).
- **L2 cognitive math questions:**
  - Each question stores its own **per-question max wait** from `docs/PHRASES.md` (e.g. 4 s, 5 s, 6 s) directly in its data model.
  - `Config.kt` holds only a fallback default (`DEFAULT_MATH_MAX_WAIT_MS = 5000`).
  - No answer received within that question's max wait → escalate to L3 (per COM-3).
- **Dismiss keywords:** companion goes silent for 2 min (`DISMISS_SILENCE_MS = 120000`). Passive monitoring continues; eyes-closed ≥ 2.5 s still triggers L3.

---

## D3 — Head pose & head-droop cancellation (Q3, LAD-2)

- MediaPipe `FaceLandmarker` facial transformation matrix used first (`output_facial_transformation_matrixes = true`). Show pitch/yaw/roll in debug panel.
- **Convention:** positive pitch = head down (nodding forward / droop).
- Landmark-based geometric estimate serves as fallback if matrix is unreliable. Document choice in `docs/MEASUREMENTS.md`.
- Soft prompt fires when pitch > 15° sustained for ≥ 1.5 s (`HEAD_SUSTAIN_MS = 1500`), with 30 s cooldown (`SOFT_PROMPT_COOLDOWN_MS = 30000`).
- **Cancellation:** For the head-droop path only, pitch returning below the threshold (< 15°) continuously for 1.5 s (`HEAD_RESTORE_CANCEL_MS = 1500`) cancels the pending escalation to L3.
- The eyes-closed path is independent (pitch returning does not cancel eye-closure alarms).

---

## D4 — Calibration uses medians everywhere (Q4/Q7, CAL-1)

- Confirmed: medians are used everywhere across all baseline calculations. No means are used anywhere.
- Metrics calibrated: `openMedian`, `closedMedian`, baseline blink rate median, baseline MAR median, baseline math response latency median.
- Eye closure threshold formula: `threshold = closedMedian + 0.5 × (openMedian − closedMedian)`.
- Ignore the first second of each phase (reaction time).

---

## D5 — Response cancels L4/L5 timers; ladder restarts on re-closure (Q5, LAD-5)

- "Response" = tap "I'M AWAKE", OR recognized voice reply, OR eyes continuously open ≥ 3 s with face detected (`OPEN_EYES_RESPONSE_MS = 3000`).
- Any valid response cancels pending L4/L5 timers.
- If eyes close again for ≥ 2.5 s after a response, the ladder **restarts at L3** (not L4).
- Once an SMS has actually been sent (L5), it cannot be recalled — the app shows "SMS sent to …" and does not attempt to unsend.

---

## D6 — SMS and airplane mode (Q6, non-functional)

- Detection, alerts, generated alarm tone, and voice work fully offline in airplane mode.
- SMS (L5) requires mobile signal. In airplane mode, show "not sent" gracefully without crashing.
- Pre-drive checklist includes: **"SMS needs mobile signal — airplane mode blocks it."**

---

## D7 — Office Kit: no SDK (Q7, OFF-*)

- Assume no Office Kit SDK.
- Large text, landscape-friendly screens.
- Reports exported as PDF + JSON to `Download/Jaagrit/`.
- Start/Stop buttons large and reachable for remote control via screen mirroring.

---

## D8 — Family clip fallback (Q8, LAD-3)

- Ships with a placeholder audio clip (`res/raw/family_1.mp3`).
- If missing or unplayable, fall back to TTS speaking `docs/PHRASES.md` section 2 #1: "पापा, जल्दी घर आओ। हम इंतज़ार कर रहे हैं।"

---

## D9 — DEMO_TIMERS and QUICK_CALIBRATION flags (Q9)

- `DEMO_TIMERS` affects only L4 (5 s instead of 10 s) and L5 (8 s instead of 20 s) timers.
- `QUICK_CALIBRATION` is a separate flag: open 5 s (vs 10 s), closed 2 s (vs 3 s), skip yawn and head pose.
- Both flags must be visible in the debug panel.

---

## D10 — Thermal monitoring (Q10)

- Polled every 10 s using `BatteryManager` battery temperature + `PowerManager` `OnThermalStatusChangedListener`.
- Do not read `/sys/class/thermal/`.
- `THERMAL_REDUCE_C = 42` (halve analysis rate); `THERMAL_WARN_C = 45`.

---

## D11 — Frame skipping under load (Q11)

- Every-other-frame analysis is enabled automatically when temperature exceeds `THERMAL_REDUCE_C` or under high inference latency.
- Lower analysis resolution is also acceptable.

---

## D12 — Ladder level arbitration & raw triggers (Q4/Q8, LAD-1/ENG-2)

- The active intervention ladder level is the higher of raw trigger level and score-band level:
  `Level = max(rawTriggerLevel, scoreBandLevel)`.
- **Raw triggers:**
  - **L0:** Normal driving.
  - **L1:** Blink rate +20% vs baseline sustained for 30 s (`BLINK_RATE_L1_SUSTAIN_MS = 30000`).
  - **L2:** PERCLOS ≥ 0.12 over 60 s window (`PERCLOS_L2 = 0.12`, `PERCLOS_WINDOW_MS = 60000`).
  - **L3:** Eyes closed ≥ 2.5 s (`CLOSURE_CONFIRM_MS = 2500`) OR head drooped with no response to soft prompt within 5 s (`HEAD_NO_RESPONSE_MS = 5000`).
  - **L4:** 10 s after L3 with no response and face detected (`L4_AFTER_L3_MS = 10000`).
  - **L5:** 20 s after L4 with no response (`L5_AFTER_L4_MS = 20000`).
- **Score bands:**
  - 71–100: Alert (L0) → Green UI
  - 51–70: Caution (L1) → Yellow UI
  - 31–50: Fatigued (L2) → Orange UI
  - 0–30: Critical (L3) → Red, flashing UI
- Score bands drive the UI color and reasons list, and also feed into the ladder level arbitration.

---

## D13 — Generated alarm tone (Q5, LAD-1, UI-2)

- No bundled audio file asset for the alarm.
- Alarm tone is generated dynamically in code on the alarm audio stream (`AudioAttributes.USAGE_ALARM` / `STREAM_ALARM`) at maximum volume.
- Played simultaneously with the spoken L3 TTS phrase and strong haptic vibration.

---

## D14 — Risk 10 Plan B: Developer voice fallback for L3 (Risk 10)

- If the offline Hindi TTS voice on the phone sounds poor or unnatural, Plan B is used:
  - Developer records the 6 hardcoded L3 phrases in their own voice (`res/raw/l3_1.mp3` through `l3_6.mp3`).
  - The app plays these pre-recorded clips via `MediaPlayer`/`SoundPool` instead of TTS for L3 alerts.

---

## D15 — UI / Pipeline independence for foreground service (AGENTS.md Rule 10)

- The camera, vision analyzer, and `FatigueEngine` pipeline are kept strictly decoupled from Android Activity and Compose UI lifecycles.
- Communication is handled via Kotlin `Flow`s / `Channel`s and pure interfaces.
- This ensures the vision and engine pipeline can later run inside an Android Foreground Service without architectural rewrites.

---

## D16 — Milestone sequence & splits

| Milestone | Scope | Key Deliverable |
|---|---|---|
| **M0** | Scaffold Android project | Compose, Material 3, CameraX, MediaPipe, Room, DataStore, `Config.kt` |
| **M1** | CameraX preview & lifecycle | Small preview, KEEP_ONLY_LATEST, front camera |
| **M2** | MediaPipe FaceLandmarker & FeatureExtractor | EAR, MAR, head pose, debug panel, unit tests |
| **M3** | Calibration flow | Open/closed medians, baseline persisted in DataStore |
| **M4a** | FatigueEngine core | Closure detection, FACE_LOST, score heuristic (D1), reasons list, unit tests |
| **M4b** | Intervention ladder | L3/L4/L5 timers, cooldowns, responses, head-droop cancellation (D3), unit tests |
| **M5** | Integration slice (**v0-demo**) | Camera→engine→UI, TTS, generated alarm (D13), red flash, "I'M AWAKE" button |
| **M6** | Escalation & safety guard | L4 family clip (D8), L5 SMS (D6), false-positive guard, `DEMO_TIMERS` |
| **M7** | Room data layer & Start Drive | Trip, AlertSample, AlertEvent, pre-drive checklist, history |
| **M8** | Voice commands & fallback panel | 4-button panel, push-to-talk SpeechRecognizer, IntentParser, dismiss words |
| **M9a** | PERCLOS & blink-rate engine signals | Sliding 60 s PERCLOS, blink rate trend vs baseline, ladder arbitration (D12) |
| **M9b1** | Companion openers loop | PhraseBank openers, 60 s cooldown, 2-ignored backoff, dismiss handling (D2) |
| **M9b2** | Companion cognitive math loop | Math questions, per-question max wait (D2), latency measurement, L3 escalation |
| **M10** | Dashboard, PDF/JSON export, Office Kit | Canvas alertness graph, PDF export to `Download/Jaagrit/`, large controls |
| **M11a** | Hardening code | Full debug panel, ThermalMonitor (D10, D11), error states, Settings/Privacy |
| **M11b** | Hardening verification (**v1-demo**) | Hostile testing on phone, 20-min continuous run, `MEASUREMENTS.md` recording |
| **M12** | P2 extras (optional) | GemmaCompanion or WhisperInput (behind interfaces; cut-first) |

Full milestone sequence: M0, M1, M2, M3, M4a, M4b, M5, M6, M7, M8, M9a, M9b1, M9b2, M10, M11a, M11b, M12.

---

## D17 — Voice commands & microphone isolation during active alerts (M8)

- **Push-to-talk only:** Speech recognition is triggered solely via the "Ask Jaagrit" push-to-talk button with a 6-second timeout. Always-on listening is avoided to preserve battery, CPU, and privacy.
- **Button fallback first:** 4 on-screen buttons (Drive time, Alertness, Alerts today, Last alert) are always available in the UI when not in alert state.
- **Microphone isolation during active alerts (L3+):**
  During any active alert (L3, L4, or L5), the microphone is never opened.
  The loud alarm siren and spoken prompt would be picked up by the microphone and garble or cause false speech recognition.
  Therefore, a voice reply must not count as an alert response in this milestone.
- **TODO (M9+):** Evaluate Acoustic Echo Cancellation (AEC) or silence-first listening windows if voice dismissal of L3 alarms is desired. In M8, the "I'M AWAKE" button remains the sole deterministic dismissal path for active alerts.

