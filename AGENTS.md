# AGENTS.md — Jaagrit

Read this first, every session. Then read what the current task points to in `docs/`:
`PRODUCT.md` (what/why), `REQUIREMENTS.md` (exact behavior + numbers), `ARCHITECTURE.md` (modules + interfaces).

## What we're building
Jaagrit (जागृत): an offline Android app for the iQOO 15 (Android 16). The phone sits on the dashboard, watches the driver's face with the front camera, estimates alertness, and intervenes in 5 graduated levels: talk, alarm, family voice, SMS. Solo dev, 48-hour hackathon, live demo on the phone. **A simple demo that works beats a complex one that breaks.**

## Fixed stack (do not swap or add without asking)
- Kotlin, Jetpack Compose + Material 3, single-module Gradle (Kotlin DSL, version catalog)
- CameraX (front camera, ImageAnalysis, KEEP_ONLY_LATEST)
- MediaPipe Tasks Vision `FaceLandmarker` (live-stream mode, with facial transformation matrix for head pose if available)
- Android `TextToSpeech` (Hindi, offline voice), `MediaPlayer`/`SoundPool` for the family clip
- Room + DataStore. No network, no cloud, no Firebase.
- minSdk 31, targetSdk 35 or higher. Package: `com.jaagrit.app`

## Hard rules
1. **Safety path never depends on an LLM, the network, or speech recognition.** Levels 3, 4, 5 use hardcoded phrases, a bundled audio clip, and an SMS template.
2. **`FatigueEngine` is pure Kotlin** (no Android imports), takes an injected `Clock`, and has unit tests driven by fake frames. It returns `Action`s; the Android layer executes them.
3. **All thresholds live in `Config.kt`.** No magic numbers anywhere else.
4. **No fake data in the real UI.** A dev simulator may exist only in debug builds, clearly labeled "SIMULATED", and is never used in the demo.
5. **Privacy:** no INTERNET permission, never store face images or audio. Store only numbers (EAR, pose, timestamps, alert events).
6. **Speech input and the companion "brain" sit behind interfaces** (`SpeechInput`, `CompanionBrain`) so they can be swapped or cut. Button fallback must always work.
7. **Driver UI is minimal:** one big alertness number + state word + drive time + alert count. Raw EAR/FPS only in the debug panel (long-press the title).
8. Phrases are stored in Devanagari (with a romanized comment) in `Phrases.kt`. Voice tone: short, natural, how a friend would say it.
9. Never claim medical diagnosis. The product "estimates alertness".
10. **Keep the camera and engine pipeline independent of the UI** so it can later run inside a foreground service.

## How to work
- One milestone at a time (see `docs/PROMPTS.md`). Don't refactor unrelated code. Don't build ahead.
- Before coding a milestone: write a short plan (files to touch, risks). If the spec is ambiguous or conflicts with these rules, ask instead of guessing.
- After coding: run the build and unit tests, fix failures, then give me a **numbered "how to verify on the phone" checklist**.
- If something fails twice, stop, explain the likely root cause, and propose options. Don't loop on random fixes.
- Log with tag `JAAGRIT`. Keep functions small. Comment the "why" for any threshold.
- Commit message format: `M<n>: <what>`.

## Definition of done (every milestone)
Builds clean, unit tests pass, runs on the real phone, I can verify it with the checklist, and nothing from earlier milestones broke.
