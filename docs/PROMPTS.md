# PROMPTS.md — Step-by-step build prompts for Jaagrit

## Setup (do once, ~20 min)
1. Install Android Studio (SDK + platform-tools). Enable USB debugging on the iQOO. Check `adb devices` shows the phone.
2. Create a repo folder `jaagrit/` with this layout:
```
jaagrit/
  AGENTS.md                (from this pack, repo root)
  docs/
    PRODUCT.md  REQUIREMENTS.md  ARCHITECTURE.md  PROMPTS.md
    PHRASES.md             (your phrase bank, the one you already wrote)
    MEASUREMENTS.md        (your test numbers: EAR values, Whisper results, TTS notes)
  assets/family_clip.mp3   (your recorded clip, later moved to res/raw)
```
3. `git init`, commit. Open the folder in Antigravity. If it has a rules/instructions setting, paste a 10-line version of AGENTS.md there too.
4. Make sure the Android SDK path works from the terminal (`./gradlew --version` after the scaffold exists).

## How to use these prompts
- **One prompt = one milestone.** Paste it, let the agent plan and build, then YOU run it on the phone and tick the checklist. Commit only when it passes.
- If a prompt gets a messy result, `git reset --hard` to the last good commit and re-prompt with more detail. Don't pile fixes on top of a broken base.
- Always start a new chat per milestone and begin with: "Read AGENTS.md and the docs it points to."
- Bug reports: paste the logcat lines (filter `JAAGRIT`) + what you saw vs expected. Not "it doesn't work".
- Hackathon rhythm: write code in the laptop phase; spend the phone-only phase testing, measuring, and rehearsing.

---

## Prompt 0 — Kickoff (no code)
```
Read AGENTS.md and everything in docs/. Do not write code yet.
1. Summarize what we're building in 10 bullets.
2. List anything ambiguous, contradictory, or risky as numbered questions.
3. Propose the milestone plan (M0-M12 from docs/PROMPTS.md) and flag any milestone you think is too big.
Wait for my answers.
```

## M0 — Scaffold
```
Read AGENTS.md. Milestone M0: create the Android project exactly per docs/ARCHITECTURE.md packages.
- Kotlin, Compose, Material 3, minSdk 31, package com.jaagrit.app, version catalog.
- Add dependencies: CameraX, MediaPipe tasks-vision, Room (with KSP), DataStore. No INTERNET permission.
- Permissions declared: CAMERA, RECORD_AUDIO, SEND_SMS, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS, VIBRATE.
- A Home screen with a "Start Drive" button that navigates to an empty Monitoring screen. Keep screen on there.
- Config.kt with every constant from docs/REQUIREMENTS.md.
Run the build and install on the phone. Give me a verification checklist.
A bare Android Studio project already exists here (Kotlin, Compose, version catalog). Build on it, don't recreate it.
```
Verify: app installs, Home shows, button navigates, no crash. Commit `M0`.

## M1 — Camera + face detection
```
Milestone M1 (CAM-1, CAM-2). On the Monitoring screen show a small front-camera preview.
Run MediaPipe FaceLandmarker in live-stream mode on CameraX frames (KEEP_ONLY_LATEST).
Download the face_landmarker.task model into assets. Request camera permission with a clear rationale screen.
Overlay text: FACE FOUND / NO FACE, inference ms, FPS.
Do not compute EAR yet. Keep it simple and stable. Handle lifecycle (pause/resume, camera release).
Keep vision pipeline decoupled from UI (AGENTS.md Rule 10).
```
Verify: face found/lost toggles correctly, FPS and ms shown, no crash on rotate/background. Write the ms/FPS in MEASUREMENTS.md. Commit.

## M2 — Features (EAR, MAR, head pose)
```
Milestone M2 (CAM-3). Create FeatureExtractor producing FaceFrame (see docs/ARCHITECTURE.md).
- EAR from landmark indices in REQUIREMENTS.md CAM-3 (left, right, average).
- Mouth aspect ratio from lip landmarks.
- Head pitch/yaw/roll: use the facial transformation matrix output if available, otherwise a landmark-based estimate. Tell me which you used (positive pitch = head down).
- Debug panel (long-press the title) showing live EAR, MAR, pitch, yaw, roll.
- Unit-test the EAR/MAR math with synthetic landmark sets (open eye vs closed eye).
```
Verify: EAR drops when you close eyes, pitch changes when you nod, values are stable and not jittery. Compare to your laptop numbers. Commit.

## M3 — Calibration
```
Milestone M3 (CAL-1, CAL-4, and CAL-2/CAL-3 if simple). Build the calibration flow screens (blue UI):
look normally 10 s, close eyes 3 s, then (optional) yawn once, look left/right/down.
Ignore the first second of each phase; use medians everywhere (openMedian, closedMedian, baseline blink rate, baseline latency).
threshold = closedMedian + 0.5 * (openMedian - closedMedian) as in REQUIREMENTS and DECISIONS D4.
Persist the Baseline in DataStore. Add a Recalibrate button in Settings. Show a clear message if the open/closed gap is too small (< 0.05).
Big on-screen prompts for each phase, with a countdown. Add unit tests for the baseline computation.
```
Verify: calibration produces sane numbers, survives app restart, works with glasses. Commit.

## M4a — Fatigue engine core (closure, alertness, FACE_LOST)
```
Milestone M4a (ENG-1, 2, 4, 5). Implement the pure-Kotlin FatigueEngine and core types (Config, Clock, Action, EngineOutput, FaceFrame) exactly per docs/ARCHITECTURE.md.
Behavior from docs/REQUIREMENTS.md and docs/DECISIONS.md D1, D12:
- Eye closure detection with 2.5 s confirm and 300 ms face-glitch tolerance.
- FACE_LOST state: never escalates, 30 s reminder.
- Alertness score 0-100: start at 100, subtract weighted penalties (PERCLOS over 60 s, longest recent closure, blink-rate increase vs baseline, head droop, drive time linear ramp 2h..6h max 15 pts), smooth with EMA (alpha = 0.15), all weights in Config.kt. Closure >= 2.5 s forces CRITICAL regardless of score.
- Emit reasons list from the contributing features.
- State bands: Alert 71-100 (L0), Caution 51-70 (L1), Fatigued 31-50 (L2), Critical 0-30 (L3).
- Ladder level arbitration: max(rawTriggerLevel, scoreBandLevel).
Write unit tests with FakeClock: normal driving, short blink, 1 s closure, 3 s closure (must go CRITICAL), face lost mid-closure, drive time penalty ramp, alertness score transitions.
No ladder escalation logic yet. No Android code in engine/.
Initial alertness weights: choose sensible defaults, comment rationale in Config.kt, and unit test each signal individually.
```
Verify: all tests pass, alertness score responds to scripted fatigue scenarios, FACE_LOST never produces escalation actions. Commit.

## M4b — Intervention ladder (L3/L4/L5 timers, cooldowns, response)
```
Milestone M4b (LAD-1, 2, 3, 5; D3, D5, D12). Add InterventionLadder to the engine.
- Active level is max(rawTriggerLevel, scoreBandLevel).
- Raw L3 fires when closure >= 2.5 s (face detected). Random phrase pick, 30 s cooldown.
- Head droop (pitch > 15° for 1.5 s) -> soft prompt ("Sab theek hai? Bol de."). Escalates to L3 if no reply in 5 s.
- Head-droop cancellation (D3): pitch returning below 15° for 1.5 s cancels pending L3 escalation. Eyes-closed path is independent.
- L4 timer starts after L3 + no response for 10 s (face must be detected). L5 timer after L4 + 20 s.
- Response definition (LAD-5, D5): tap, voice reply, or eyes open >= 3 s. Response cancels pending L4/L5 timers. Re-closure >= 2.5 s after a response restarts at L3.
- DEMO_TIMERS flag shortens L4/L5 only.
Unit tests with FakeClock: L3 fires and resets on response, head-droop cancellation on pitch recovery, L3 -> L4 timing, L4 -> L5 timing, response cancels L5, re-closure restarts at L3, L4 never fires when face is lost, cooldown prevents repeats.
No Android code in engine/.
```
Verify: all tests pass, every rule in LAD-1/2/3/5 and D3/D5 has a test. Commit.

## M5 — Wire it up: TTS, alarm, UI states (first demoable slice)
```
Milestone M5 (VOI-1, UI-1, UI-2, UI-4, LAD-1, D13, D14). Connect camera -> FeatureExtractor -> FatigueEngine -> Actions.
- Speaker using Android TTS (Hindi offline voice, English fallback); urgent actions use higher rate/pitch. Plan B: support developer pre-recorded audio clips if offline voice is poor.
- Alarm sound: generate alarm tone programmatically in code on USAGE_ALARM stream at max volume (D13). No bundled audio file asset.
- Phrases.kt with the L3 list from docs/PHRASES.md in Devanagari (romanized comments), random pick, no immediate repeat.
- Monitoring UI: giant alertness number, state word, drive time, alert count, privacy badge. Green/amber/red/grey per PRODUCT.md. Red = full-screen flash + big "I'M AWAKE" button + strong vibration + generated alarm tone + TTS phrase.
- Max brightness and keep-screen-on while monitoring.
- "I'M AWAKE" button sends VoiceEvent.ImAwake to the engine.
Keep pipeline independent of UI (AGENTS.md Rule 10).
```
Verify: eyes open = green and silent; close eyes 3 s = red + voice + vibration + alarm tone; "I'M AWAKE" resets; cover camera = grey FACE_LOST with no alarm. **Tag this commit `v0-demo`. You now have a working demo.**

## M6 — L4 family voice, L5 SMS, false-positive guard
```
Milestone M6 (LAD-3, LAD-4, LAD-6, LAD-7). Add res/raw/family_clip playback for L4 (only after L3 + no response + face detected; TTS fallback per D8).
L5: visible countdown, SmsNotifier using SmsManager with the template in docs/PHRASES.md and GPS from last known location, in-app "SMS sent to <number>" confirmation, any response cancels. Emergency contact entered in Settings. Pre-request SMS and location permissions in pre-drive flow.
Add DEMO_TIMERS flag (shorter L4/L5) shown in debug panel. Add false-alert counter and recalibration suggestion.
```
Verify with the SMS going to a second phone you control. Check airplane mode behavior (should show "not sent" clearly, not crash per D6). Commit.

## M7 — Data layer + Start Drive flow
```
Milestone M7 (DAT-1, DAT-2, UI-3 partly). Room entities Trip, AlertSample, AlertEvent per docs/ARCHITECTURE.md.
Start Drive flow: Home -> pre-drive checklist (warn: SMS needs mobile signal, D6) -> (calibration if none) -> Monitoring. End Drive saves the trip with average alertness and alert counts.
Sample alertness every 5 s. Log every alert event with response type. Add a simple History screen listing trips.
```
Verify: trips and alerts persist across restarts; counts match what you did. Commit.

## M8 — Voice commands (buttons first)
```
Milestone M8 (VOI-2, VOI-3). First build the 4-button fallback panel (Drive Time / Alertness / Alerts / Report) answered by TTS from the DB and engine state, in Hindi and English.
Then add SpeechInput via Android SpeechRecognizer preferring offline recognition, keyword/intent matching in IntentParser (deterministic, accept Devanagari and romanized variants, see docs/PHRASES.md section 6 and 8). Dismiss words: chup raho, band karo, bas karo, rehne de, stop. NOT "theek hoon".
Speech recognition must start only on a push-to-talk "Ask Jaagrit" button for now (no always-on listening). Everything must work with buttons alone if recognition fails.
```
Verify with your voice in quiet and noisy rooms; if recognition is poor, keep push-to-talk plus buttons and move on (Whisper is P2). Commit.

## M9a — PERCLOS and blink-rate signals in the engine
```
Milestone M9a (ENG-3, D12). Add to the FatigueEngine:
- PERCLOS over a 60 s sliding window. Raw L2 triggers when PERCLOS >= 0.12.
- Blink-rate trend vs calibrated baseline median. Raw L1 triggers when blink rate +20% sustained for 30 s.
- Active ladder level = max(rawTriggerLevel, scoreBandLevel).
These feed into the alertness score (already weighted in Config.kt from M4a) and trigger L1/L2 band transitions.
Unit-test: PERCLOS rising above threshold enters Caution/L2, blink-rate increase enters L1 range, both together push toward Fatigued.
```
Verify: tests pass, alertness score now reacts to PERCLOS and blink-rate, not just closure. Commit.

## M9b1 — Companion openers loop (PhraseBankCompanion)
```
Milestone M9b1 (COM-1, COM-2, COM-4, COM-6, D2). Implement opener loop in PhraseBankCompanion:
- Openers from docs/PHRASES.md section 4.
- Active only at L1/L2, never during normal driving, never with eyes closed, openers <= 5 s.
- Fire once on band entry, 60 s cooldown between prompts (COMPANION_COOLDOWN_MS).
- L1: no reply = "ignored" (not escalated); two ignored openers in a row -> companion stops for 5 min (COMPANION_BACKOFF_MS).
- Dismiss words: silence companion for 2 min (DISMISS_SILENCE_MS), passive monitoring continues, eyes closed >= 2.5 s still fires L3.
- Pattern lines (e.g. "last time you got sleepy around now") only when real history exists.
Unit-test the opener loop, ignored backoff, and dismiss silence rules.
```
Verify: blink a lot, companion asks an opener; ignore twice, companion pauses for 5 min; say dismiss word, companion silences for 2 min; close eyes 3 s, L3 still fires. Commit.

## M9b2 — Cognitive math loop (latency & escalation)
```
Milestone M9b2 (COM-2, COM-3, D2). Add cognitive math questions to PhraseBankCompanion:
- Math questions from docs/PHRASES.md section 5.
- Store per-question max wait directly with each question (e.g. 4 s, 5 s, 6 s). Config.kt holds default fallback (5 s).
- Measure answer latency (voice or on-screen quick-answer buttons). Latency > 2x baseline = fatigue signal.
- No answer within the question's max wait -> escalate to L3 (COM-3).
- Answer matching: accept digits, Hindi number words, English number words, on-screen buttons. Wrong answer still counts as response (only latency matters for fatigue signal).
Unit-test math question latency tracking, correct/wrong response handling, and timeout escalation to L3.
```
Verify: math question asked at L2; quick answer acknowledged; slow answer (> 2x baseline) confirms fatigue; no answer within max wait escalates to L3. Commit.

## M10 — Dashboard, report, Office Kit
```
Milestone M10 (OFF-1 to OFF-4, UI-3, D7). Build a Dashboard screen in Compose: alertness-over-time line graph (draw with Canvas), alert timeline, trip safety score, driver profile. Large text; works in landscape. This screen will be mirrored to a laptop via Office Kit.
ReportExporter: generate a one-page PDF (PdfDocument) with trip summary, graph, timeline, and the honest-limits note; also JSON. Save to Download/Jaagrit/ and show the path plus a Share button.
"Copy last alert" button puts a text summary on the clipboard. Make Start/Stop large and reachable.
```
Verify: PDF opens, graph matches the trip; test the actual Office Kit mirror, file transfer, clipboard and remote control with the phone and laptop. Commit.

## M11a — Hardening (code & architecture)
```
Milestone M11a (CAM-4, D10, D11, D15, polish).
- Full debug panel: FPS, inference ms, phone temperature, active component, pitch/yaw/roll, DEMO_TIMERS, QUICK_CALIBRATION.
- ThermalMonitor (D10): battery temp + PowerManager thermal status listener. Above 42 C analyze every other frame, log it (D11).
- Verify pipeline separation (AGENTS.md Rule 10, D15): camera analyzer and FatigueEngine decoupled from UI lifecycle, ready for foreground service.
- Error states: FACE_LOST, LOW_LIGHT hint, camera error screen.
- Privacy/Settings screen with on-device summary, emergency contact, language toggle.
- Code review: fix crashes on permission denial, lifecycle leaks, camera release, TTS initialization.
```
Verify: debug panel toggles, thermal throttling simulates correctly, permission revoking handled gracefully without crash. Commit.

## M11b — Hardening verification (hostile testing on phone)
```
Milestone M11b. Run the hostile test suite on the real phone:
- Conditions: clear glasses, face turned 30°, looking down, rapid blinking, 300 ms vs 1 s vs 3 s closure, low light, dim light + max screen brightness, noisy room, Hindi + Hinglish speech, repeated commands, airplane mode.
- 20-minute continuous monitoring run: verify no crash, no memory leak, temperature stays within thermal limits.
- Record all measured numbers in docs/MEASUREMENTS.md (FPS, inference ms, latencies, false alerts).
Commit and tag `v1-demo`.
```
Verify: 20-minute run passes cleanly, MEASUREMENTS.md updated. Tag `v1-demo`.

## M12 — P2 extras (only if everything above is solid)
```
Milestone M12. Implement ONE of these behind the existing interfaces, never touching the safety path, and fall back automatically on failure:
(a) GemmaCompanion (on-device LLM) for varied openers/questions, with output validation and fallback to PhraseBankCompanion; load only on demand, never while speech recognition runs.
(b) WhisperInput (whisper.cpp tiny/base) after voice-activity detection, with language detection.
Report latency, memory, and temperature impact before we decide to keep it.
```
Keep it only if the 20-minute run still passes. Otherwise delete the branch and demo without it.

---

## Reusable prompts

**Bug fix**
```
Bug: <what I did> -> <what happened> vs <what I expected>.
Logcat (tag JAAGRIT): <paste>.
Find the root cause first and explain it in 3 lines. Then fix only that. Add or adjust a unit test if it's engine logic. Don't touch other modules.
```

**Stuck / looping**
```
Stop. Don't change code. Explain what you think is failing, what you've tried, and give me 2-3 options with trade-offs.
```

**Pre-demo review**
```
Review the whole app against docs/REQUIREMENTS.md. For every P0 item say PASS / FAIL / UNVERIFIED with the file that implements it. List anything that could break in the first 90 seconds of the demo. Do not change code.
```

**Freeze**
At hour 44: bug fixes only, no new features, tag `final`, build a release APK and keep the debug one as backup.
