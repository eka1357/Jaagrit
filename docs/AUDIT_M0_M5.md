# AUDIT_M0_M5.md — Independent engineering audit of Jaagrit M0–M5

- **Audited revision:** `7f7f9b9` (HEAD of `main`, tag `v0-demo`)
- **Date:** 2026-10-10
- **Scope:** M0 to M5 only. No fixes were made and M6 was not started.
- **Method:** I read every source, test, and doc file. I ran a fresh build, the unit tests, and lint. I wrote **probe tests in a throwaway clone** (`%TEMP%\jaagrit_audit`, outside this repo) that copy the production clock wiring. No app code, build file, test, or Git state in this repo was changed. This file is the only addition.

Finding labels:
- **Confirmed:** reproduced by a probe, or directly evident in code.
- **Likely:** strong code evidence, needs a device check.
- **Risk:** plausible, not demonstrated.

---

## 1. Executive verdict

**GO AFTER FIXES. Do not start M6 on the current code.**

The pieces are well built in isolation:
- Under a single consistent clock the ladder logic is exact: L3 at 2.5 s, L4 at 12.5 s, L5 at 32.5 s (PROBE1b).
- The EAR/MAR math, the calibration formula, and DataStore persistence are correct.

The **integration** has one blocking defect and several high-severity ones:

1. **Mixed time bases (P0, AUDIT-001).** Frames are stamped with `SystemClock.uptimeMillis()`. The engine's `onTick()` uses `System.currentTimeMillis()`. In the real app:
   - L4 fires on the **first 100 ms tick after L3** (should be 10 s later).
   - L5 fires about 20 s after L3.
   - A head-droop soft prompt becomes a full L3 alarm immediately.
   - The 30 s face-lost reminder is spoken on the first tick after **any** dropped frame.
   - The drive-time penalty never applies.
   - Blink rate is inflated 5×, so a normal driver scores about 75 instead of 100.

   M6 would wire the family clip and the **SMS** onto these broken timers.
2. **The app ships with `INTERNET`** (AUDIT-002), pulled in through MediaPipe's DataTransport dependency. The in-app text "The app contains no INTERNET permission" is false. This violates Hard Rule 5.
3. **The engine is not thread-safe** (AUDIT-003) but is called from two `Dispatchers.Default` coroutines plus the main thread. A JVM probe reproduced `NullPointerException` and `ConcurrentModificationException` inside `computePerclos()`, which would crash the app on Android.
4. **One face-lost frame or tick during an alert silences the alarm and vibration** for the rest of that episode (AUDIT-004).
5. **Camera lifecycle defects** (AUDIT-005, 006, 007), confirmed from code and needing a device check to measure impact:
   - The calibration screen's `unbindAll()` is likely to unbind the monitoring camera after "Save & Start Drive".
   - The preview is never reattached after the first red alert.
   - Calibration never requests camera permission.

The M5 demo's happy path ("close eyes 3 s → red + alarm + voice → I'M AWAKE") works. Several other behaviours are wrong (see §9).

---

## 2. Repository and Git baseline

| Item | Observed |
|---|---|
| Directory / branch | `C:\dev\jaagrit`, `main`, tracking `origin/main` (https://github.com/eka1357/Jaagrit.git) |
| Working tree | Clean (no staged, unstaged, or untracked non-ignored files) before and after the audit, apart from this file |
| HEAD | `7f7f9b9 M5: connect camera to engine pipeline, TTS, alarm tone, and monitoring UI` |
| `v0-demo` | Annotated tag object `ba4fad5` → commit `7f7f9b9` (= HEAD = `origin/main`) |
| History | `bad9101` init → `df285dd` M0 → `f80c5d5` M1 → `cdf5a3b` M2 → `e1ccf8e` M3 → `f71f93c` M4a → `2fda790` M4b → `7f7f9b9` M5. All hashes from the brief match. |
| Docs present | `AGENTS.md`, `docs/{PRODUCT,REQUIREMENTS,ARCHITECTURE,DECISIONS,PROMPTS,PHRASES,MEASUREMENTS}.md`. **No `README.md`.** `PROMPTS.md` and `PHRASES.md` live in `docs/`, not the repo root. |
| Toolchain | Gradle wrapper 9.6.0, AGP 9.4.1, Kotlin 2.2.10, KSP 2.2.10-2.0.2, JDK 17.0.20 on PATH, Gradle daemon toolchain = JDK 25 (`gradle/gradle-daemon-jvm.properties`) |
| SDK levels | compileSdk 37, targetSdk 37, minSdk 31. Package `com.jaagrit.app`. |
| Device access | `adb` not on PATH. No device was used, installed to, or launched. |
| Tracked extras | `.idea/*`, `tests/test_face_mesh.py`, `tests/test_whisper.py` (desktop probe scripts), empty package dirs with `.gitkeep` |

---

## 3. Build and test results

| # | Command (cwd) | Result | Status |
|---|---|---|---|
| B1 | `./gradlew --console=plain testDebugUnitTest assembleDebug` (repo) | BUILD SUCCESSFUL in 4 s. **All 44 tasks UP-TO-DATE**, so nothing was recompiled and no tests re-ran. | Not a fresh verification |
| B2 | Fresh `git clone` of `v0-demo` into `%TEMP%\jaagrit_audit` + `local.properties` copied; `./gradlew --console=plain --no-configuration-cache testDebugUnitTest assembleDebug lintDebug` | 53 tasks executed, 3 m 35 s. Details in the rows below. | — |
| B2a | └ `compileDebugKotlin` | OK. 2 warnings: deprecated `Locale(String,String)` at `TtsSpeaker.kt:32,71`. Repeated "Kotlin does not yet support 25 JDK target, falling back to JVM_24". | **Verified passing** |
| B2b | └ `testDebugUnitTest` | **36 tests, 0 failures, 0 errors, 0 skipped**: FatigueEngineTest 12, InterventionLadderTest 12, BaselineCalculatorTest 5, FeatureExtractorTest 3, ConfigTest 3, ExampleUnitTest 1 | **Verified passing** |
| B2c | └ `assembleDebug` | `app-debug.apk` = 89,296,054 bytes | **Verified passing** |
| B2d | └ `lintDebug` | **FAILED: 2 errors, 31 warnings** (AUDIT-017) | **Verified failing** |
| B3 | Probe tests in the temp clone only: `./gradlew testDebugUnitTest --tests com.jaagrit.app.engine.AuditProbeTest -i` | Ran. The probes print observations and assert nothing (Appendix A). | Evidence only |
| B4 | `connectedAndroidTest` (`ExampleInstrumentedTest`, boilerplate) | Not run: no device or adb, and it would install on the phone | **Blocked by environment** |
| B5 | On-device behaviour (camera, TTS, alarm, vibration, brightness, FPS) | — | **Requires physical-device verification** (§10) |

**Lint errors (B2d):**
- `AndroidManifest.xml:9` **CoarseFineLocation**: `ACCESS_FINE_LOCATION` is declared without `ACCESS_COARSE_LOCATION`.
- `AndroidManifest.xml:8` **PermissionImpliesUnsupportedChromeOsHardware**: `SEND_SMS` is declared without `<uses-feature android:name="android.hardware.telephony" android:required="false"/>`.

**Notable lint warnings:**
- `Aligned16KB`: `libmediapipe_tasks_vision_jni.so` is not 16 KB aligned.
- 17 outdated-dependency warnings.
- `ObsoleteSdkInt`, unused colors.

---

## 4. Milestone-by-milestone findings

### M0: Scaffold
- ✅ Single module, Kotlin DSL, version catalog, Compose + Material 3, minSdk 31, package `com.jaagrit.app`. The ARCHITECTURE package layout is created. Home → Monitoring navigation works, and Config.kt holds every REQUIREMENTS constant (checked by `ConfigTest`).
- ✅ Declared permissions match M0: CAMERA, RECORD_AUDIO, SEND_SMS, ACCESS_FINE_LOCATION, POST_NOTIFICATIONS, VIBRATE.
- ❌ **The merged manifest adds `INTERNET` and `ACCESS_NETWORK_STATE`** plus a DataTransport JobService and AlarmManager receiver (AUDIT-002).
- ⚠️ Room and KSP are included but unused until M7 (expected per M0 prompt).
- ⚠️ `debugImplementation ui-test-manifest` adds an exported `ComponentActivity` to debug builds only.
- ⚠️ `allowBackup="true"` with template backup rules (AUDIT-024).
- ⚠️ The daemon JVM toolchain is 25 while Kotlin supports up to 24 (AUDIT-030).

### M1: Camera and FaceLandmarker
- ✅ Front camera, `STRATEGY_KEEP_ONLY_LATEST`, RGBA_8888 output, dedicated single-thread analyzer executor (`CameraController.kt:25,44-55`). `ImageProxy` is always closed in `finally` (`FaceLandmarkerWrapper.kt:109-111`).
- ✅ LIVE_STREAM mode, `numFaces=1`, transformation matrix enabled, strictly monotonic timestamps (`FaceLandmarkerWrapper.kt:159-167`), error listener. The default (CPU) delegate is used. FPS and inference time are computed. The inference time measured (submit → callback) includes queueing.
- ✅ Rotation is applied to the bitmap before inference. The image is not mirrored (harmless because the engine uses the average EAR).
- ❌ `ProcessCameraProvider.unbindAll()` (`CameraController.kt:59,78`) is process-wide. Calibration's release can unbind the monitoring session (AUDIT-005).
- ❌ After the first red alert, the preview `PreviewView` is never reattached (AUDIT-006).
- ❌ Calibration binds the camera without ever requesting the CAMERA permission (AUDIT-007).
- ⚠️ `FaceLandmarker.createFromOptions` runs on the main thread inside `remember {}` (jank risk). `frameStartTimes` can grow if frames are dropped. There are two full-frame bitmaps per frame (AUDIT-025).
- ⚠️ Background: the camera stops through the lifecycle but the engine ticker keeps running and assumes the face is present. Rotation recreates the whole pipeline (no orientation lock or `configChanges`) (AUDIT-015).

### M2: Feature extraction
- ✅ **EAR indices and formula are correct.**
  - R `[33,160,158,133,153,144]` → pairs 160–144 and 158–153 over 33–133.
  - L `[362,385,387,263,373,380]` → pairs 385–380 and 387–373 over 362–263.
  - Formula `(|p2−p6|+|p3−p5|)/(2|p1−p4|)` with a zero-width guard (`FeatureExtractor.kt:91-108`). I recomputed the test fixture: open = (0.03+0.03)/(2·0.10) = 0.30 ✔, closed = 0.004/0.2 = 0.02 ✔.
- ✅ MAR uses inner-lip pairs 13–14, 81–178, 311–402 over corners 78–308: `Σv/(3h)`. Fixture recomputed: normal ≈ 0.044, yawn ≈ 0.633 ✔. MAR is not used by the engine yet.
- ⚠️ EAR and MAR use **normalized** x/y. In a 3:4 portrait frame that scales EAR by about 0.75 and makes it depend on aspect ratio (AUDIT-018).
- ❌ **Head pitch from the matrix (the path actually used on device) has no test, and its sign is likely inverted** (AUDIT-008). The only pose test covers the landmark fallback, which never runs while the matrix is enabled.
- ⚠️ No NaN or infinity guards on the matrix path. Missing faces return `faceFound=false` with zeros, which is handled correctly downstream.

### M3: Calibration and persistence
- ✅ Medians everywhere. `threshold = closed + 0.5·(open − closed)` is correct. First-second ignore uses consistent `uptimeMillis` on both frames and phase start. The gap < 0.05 message is shown. Save → DataStore. Recalibrate is on both Home and Settings. Reset clears the keys. `MonitoringPipeline` loads the baseline at session start.
- ❌ An **empty or insufficient closed-eye phase still produces a "valid" baseline** (AUDIT-010, PROBE8).
- ⚠️ The baseline blink rate comes from about 9 s of samples and is clamped to 5–45/min (AUDIT-012).
- ⚠️ CAL-2 is partial: yawn frames are collected but never used, and the look-around step collects nothing. The neutral MAR comes from the open phase.
- ⚠️ `QUICK_CALIBRATION` is tied to `demoTimers` (`Config.kt:70-71`), but D9 says they are separate flags (AUDIT-027).
- ⚠️ No `ReplaceFileCorruptionHandler`, so a corrupt DataStore throws inside the pipeline coroutine (AUDIT-024).
- ⚠️ When uncalibrated, monitoring silently uses `Baseline.DEFAULT` (threshold 0.165), which is close to the measured phone open-EAR floor of 0.20 (AUDIT-018, AUDIT-022).

### M4a: FatigueEngine
- ✅ No Android imports in `engine/`. The clock is injected. Closure confirm is 2.5 s. Glitch tolerance is measured from the last closed frame (≤ 300 ms keeps the timer, > 300 ms resets it). FACE_LOST returns `Level.L0`. EMA uses α = 0.15. Bands are 71/51/31. The drive-time ramp formula is correct.
- ❌ In production wiring, the drive-time penalty, blink-rate ratio, raw L1 trigger, and face-lost reminder are all broken by the time-base mismatch (AUDIT-001).
- ⚠️ Raw L1 (blink) and L2 (PERCLOS) triggers are already active, although ENG-3 belongs to M9a. The "sustained 30 s" blink condition is implemented as "drive older than 30 s" (`FatigueEngine.kt:138`) (AUDIT-012).
- ⚠️ Magic numbers in engine logic, an unused `ALERTNESS_WEIGHT_BLINK_RATE`, and D1 vs REQUIREMENTS weight conflicts (AUDIT-013).
- ⚠️ The ladder → engine reset depends on matching the string `"Response processed"` (`FatigueEngine.kt:152`). Any response clears the PERCLOS window and resets the score to 100. That is a design choice worth re-confirming.

### M4b: InterventionLadder
- ✅ Under a single clock the timings are exact (PROBE1b: 2.5 / 12.5 / 32.5 s).
  - Response cancels L4 and L5.
  - Re-closure restarts at L3.
  - The 30 s cooldown applies.
  - Phrases are random with no immediate repeat.
  - Head-droop pitch recovery cancels a pending escalation.
  - L4 does not fire *while* the face is lost.
- ❌ **Head-droop L3 cancels itself one frame later**, because the eyes-open ≥ 3 s timer started before the alert (AUDIT-009, PROBE4: 40 ms).
- ❌ **L4 fires on the first face-found frame after a long face loss**, even with eyes open (AUDIT-011, PROBE5).
- ⚠️ "30 s cooldown unless condition worsens" (LAD-1) is not implemented. Only the plain cooldown exists.

### M5: Integration
- ✅ Camera → wrapper → FeatureExtractor → FatigueEngine → actions → TTS, alarm, vibration, and `StateFlow<MonitoringUiState>` → Compose are genuinely connected.
  - "I'M AWAKE" stops all three actuators and calls `engine.onVoice(ImAwake)`.
  - Full-screen flashing red with a big button.
  - Keep-screen-on plus `BRIGHTNESS_OVERRIDE_FULL`, restored on dispose.
  - Privacy badge, generated two-tone alarm on `USAGE_ALARM`, no audio asset.
  - L3 phrases match `PHRASES.md` §1 exactly.
- ❌ Defects: AUDIT-001, 003, 004, 005, 006, 014, 015, 016 (see §6).

---

## 5. End-to-end integration trace

```
CameraX ImageAnalysis (cameraExecutor, KEEP_ONLY_LATEST)                         CameraController.kt:44-55
  → FaceLandmarkerWrapper.processImageProxy: toBitmap → rotate → detectAsync(ts = uptime, monotonic)  FaceLandmarkerWrapper.kt:77-112
  → MediaPipe result thread: processDetectionResult(now = SystemClock.uptimeMillis())                 :116-157
      → FeatureExtractor.extract(result, now)  ⇒ FaceFrame.tsMs = UPTIME                              :137
      → _visionResult (StateFlow, conflated)
  → MonitoringPipeline coroutine #1 on Dispatchers.Default: engine.onFrame(frame)                     MonitoringPipeline.kt:107-111,164
        engine = FatigueEngine(baseline)  ⇒ clock = Clock.SYSTEM = EPOCH; resetDrive(EPOCH)           :103-104
        onFrame uses now = frame.tsMs (UPTIME)                                                        FatigueEngine.kt:54
  → MonitoringPipeline coroutine #2 on Dispatchers.Default: every 100 ms engine.onTick() (now = EPOCH) MonitoringPipeline.kt:114-119,198
  → Main thread: onImAwake → engine.onVoice (now = EPOCH)                                             :126-146
  → executeActions: ShowRedFlash → alarm.start + alertCount++; Vibrate; Speak → TTS;
                    PlayFamilyClip → alertCount++ only; SendSms → log only                            :227-260
  → _uiState → MonitoringScreen.collectAsState → red screen if isRedFlashActive
                    (early return removes the preview)                                                MonitoringScreen.kt:147,158-165
```

**Verdict:** the loop is genuinely wired and reacts to closed eyes. However:
- The **timer half of the engine runs on a different clock** from the frame half.
- The engine is **called from three threads with no synchronization**. ARCHITECTURE.md specifies "one coroutine consumes frames and ticks".
- **Actuator state is derived per output** rather than from ladder state, so a single FACE_LOST output turns the alarm off.

---

## 6. Findings ranked by severity

Counts: **P0 = 1, P1 = 7, P2 = 12, P3 = 10** (30 total).

### P0: Blocker

**AUDIT-001 · P0 · Confirmed: frame and tick time bases differ; escalation timers fire early or never**
- **Where:** `FaceLandmarkerWrapper.kt:117,137` (tsMs = `uptimeMillis`), `MonitoringPipeline.kt:80,103-104` (engine on `Clock.SYSTEM` = epoch, `resetDrive(System.currentTimeMillis())`), `FatigueEngine.kt:54,108,138,193,197,213,428`, `InterventionLadder.kt:131,167,286,298`.
- **Observed (PROBE1–3, 6; production wiring copied in the JVM):**
  - Eyes closed: **L3 at 2.5 s, L4 at 2.5 s (same 100 ms tick), L5 at 22.5 s.** The spec is L4 at 12.5 s and L5 at 32.5 s.
  - One face-lost frame, then a tick 10 ms later: **FACE_LOST reminder spoken** (spec: after 30 s).
  - Head droop with eyes open: the **soft prompt and the L3 alarm fire on the same tick** (spec: 5 s later, and cancellable).
  - Normal 16 blinks/min against a baseline of 16/min: **alertness 75, "Blink rate elevated (+400%)"**. Consistent clock: 100.
  - 5 h into a drive: alertness 100 (spec ≈ 89). The drive-time penalty never applies.
  - In the app, each L3 adds **+2** to "Alerts" (ShowRedFlash and PlayFamilyClip both increment, `MonitoringPipeline.kt:233,247`).
- **Why it matters:** this is the core safety timing. M6 will attach the family clip and the **SMS** to these timers. With this bug, an SMS would go out about 20 s after any 2.5 s closure.
- **Why the tests miss it:** every test stamps frames with the same `FakeClock` the engine uses.
- **Fix:** use one monotonic clock everywhere. Inject `Clock { SystemClock.elapsedRealtime() }` (or uptime) into `FatigueEngine` in `MonitoringPipeline`, and use the same source for `resetDrive` and `driveTimeMs`. Optionally have the engine use `clock.nowMs()` only, or `require` that `f.tsMs` agrees with the clock within a tolerance.
- **Regression tests:**
  1. An engine test where frame timestamps and `onTick` share a clock with a large offset from zero, and L4 is driven *only* by `onTick` (expect ≥ 10 s after L3).
  2. A face-lost reminder test via `onTick` (none at 29.9 s, one at 30 s).
  3. Blink-rate and drive-time tests with production-like clock values.
  4. On device: L4 log line about 10 s after L3.

### P1: High

**AUDIT-002 · P1 · Confirmed: `INTERNET` and `ACCESS_NETWORK_STATE` in the shipped manifest; in-app privacy claims are false**
- **Where:** merged manifest `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:29-30`. The manifest-merger report (lines 564-573) shows the source is `com.google.android.datatransport:transport-backend-cct:3.1.0` (transitive via MediaPipe tasks). It also brings `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`, `TransportBackendDiscovery`. The APK contains `firebase-encoders*.properties` and `transport-*.properties`.
- **Why it matters:** Hard Rule 5 ("no INTERNET permission") is broken. These on-screen statements are untrue:
  - `SettingsScreen.kt:232`: "The app contains no INTERNET permission"
  - `MonitoringScreen.kt:621`: "No internet access (100% offline)"
  - `HomeScreen.kt:164`
  - The badge "ON-DEVICE • OFFLINE"

  Whether any network traffic actually happens is **unverified**.
- **Fix:** add `<uses-permission android:name="android.permission.INTERNET" tools:node="remove"/>` and the same for `ACCESS_NETWORK_STATE` in `app/src/main/AndroidManifest.xml`. Consider also removing the DataTransport components with `tools:node="remove"`. Do not exclude the library without testing, because MediaPipe may reference its classes.
- **Verify:** check the merged manifest (CI grep). On device: `adb shell dumpsys package com.jaagrit.app | grep permission`. Run detection in airplane mode.

**AUDIT-003 · P1 · Confirmed (JVM): engine and ladder are mutated from three threads without synchronization; crash risk**
- **Where:** `MonitoringPipeline.kt:77,107-119` (two child coroutines on multi-threaded `Dispatchers.Default`), `:134` (`onImAwake` on Main). Unsynchronized state lives in `FatigueEngine` (`ArrayDeque`s, `!!` on nullable fields) and in `InterventionLadder`.
- **Observed (PROBE10):** concurrent `onFrame`/`onTick`/`onVoice` for 3 s threw `NullPointerException` and `ConcurrentModificationException` at `FatigueEngine.computePerclos` (`FatigueEngine.kt:394`) in all 3 runs.
  - In the app, an uncaught exception in these coroutines (no `CoroutineExceptionHandler`) crashes the process.
  - Duplicate L4/L5 firing between `onFrame` and `onTick` is also possible.
  - `engine` is a plain `var` reassigned on another thread (`:79,103`).
  - This contradicts ARCHITECTURE.md §Threading.
- **Fix:** confine all engine calls to one thread. Options: `Dispatchers.Default.limitedParallelism(1)`, or a single consumer that merges frames, ticks, and voice events through one `Channel`/`select`. Route `onImAwake` through that same path.
- **Verify:** a stress test like PROBE10 against the pipeline's dispatcher, plus a 20-minute run on device.

**AUDIT-004 · P1 · Confirmed (code): any FACE_LOST output during an alert silences the alarm and vibration for the rest of the episode**
- **Where:** `MonitoringPipeline.kt:167-178` and `:201-212`. A FACE_LOST output gives `isCriticalAlert=false`, which calls `stopAlarm()` and `vibeManager.cancel()` and clears the flash. The alarm restarts **only** on an `Action.ShowRedFlash` (`:231-235`).
- **Scenario:** one dropped face frame (the engine reports FACE_LOST even for sub-300 ms glitches, see `FatigueEngineTest.kt:156-161`), or a tick landing in that window, or a slumped head leaving the frame. The ladder still says L3/L4, so when the face returns the screen turns red again but **the alarm stays silent**. With AUDIT-001, L4 has already fired, so an L3 repeat (and its ShowRedFlash) is blocked (`InterventionLadder.kt:110`). The alarm never returns in that episode.
- **Fix:** make actuator state a function of ladder state (`isL3Active || isL4Active || isL5Active`), not of each output's `state`. Decide explicitly (product decision) whether an active alarm continues during FACE_LOST. A slumped head is the case where it matters most.
- **Verify:** a pipeline-level test with fake actuators. On device: trigger L3, briefly cover the camera, uncover → the alarm must still sound.

**AUDIT-005 · P1 · Likely: "Calibrate → Save & Start Drive" can leave Monitoring with no camera**
- **Where:** `CalibrationScreen.kt:220-224` calls `cameraController.release()` → `stopCamera()` → `ProcessCameraProvider.unbindAll()` (`CameraController.kt:76-80`). `ProcessCameraProvider` is a process singleton. Navigation-compose keeps the exiting destination composed during its default crossfade (~700 ms). Monitoring binds its camera at once (`MonitoringScreen.kt:275-284`), so calibration's later `unbindAll()` very likely unbinds **Monitoring's** use cases.
- **Expected symptom:** black preview, "NO FACE", but **green 100 / "Focused"**, because with no frames the engine never sees a face loss. This is a silent non-monitoring state.
- **Fix:** `unbind(preview, imageAnalysis)` for this controller's own use cases only, never `unbindAll()`.
- **Verify on device:** Home → Calibrate → complete → "Save & Start Drive" → the preview is live and FPS > 0 in the debug panel. Repeat from Settings → Recalibrate.

**AUDIT-006 · P1 · Confirmed (code), impact needs a device check: preview not reattached after the first red alert**
- **Where:** `MonitoringScreen.kt:158-165` returns early while `isRedFlashActive`, which removes the `AndroidView`. When the alert ends, a **new** `PreviewView` is created and `pipeline.start()` returns immediately (`MonitoringPipeline.kt:92` `if (isStarted) return`), so its `surfaceProvider` is never set.
- **Expected:** a black preview for the rest of the drive (CAM-1). Whether ImageAnalysis keeps streaming after the original surface is destroyed is **unverified**.
- **Fix:** keep the preview in composition and draw the red screen as an overlay, or move preview-surface binding out of `start()` so it can be reattached.
- **Verify:** trigger L3 → I'M AWAKE → the preview is live and FPS keeps updating.

**AUDIT-007 · P1 · Confirmed (code): Calibration never requests CAMERA permission**
- **Where:** `CalibrationScreen.kt:277-289` binds the camera directly. Only `MonitoringScreen` has a permission flow.
- **Scenario:** fresh install → Home → "Calibrate Profile" → no frames → open median 0 → "Calibration not done properly" forever. The demo phone is unaffected only because permission was granted earlier.
- **Fix:** reuse the permission gate from MonitoringScreen.
- **Verify:** `adb shell pm revoke com.jaagrit.app android.permission.CAMERA`, then calibrate first.

**AUDIT-008 · P1 · Risk (unverified, high): head pitch sign from the transformation matrix is likely inverted**
- **Where:** `FeatureExtractor.kt:142` `pitch = atan2(-m[6], m[10])`.
- **Evidence:**
  - MediaPipe's Java layer copies `MatrixData.packed_data` verbatim (disassembled `FaceLandmarkerResult.create`, no reordering). `MatrixData`'s default layout is column-major.
  - PROBE9: a +20° nod-down rotation about X (Y-up metric space) gives **−20°** if column-major and +20° if row-major.
  - `MEASUREMENTS.md` records **+7–9° at "neutral dashboard angle"**. A camera below the face sees the face tilted *up*, so a "positive = down" convention would read negative there. This is consistent with an inverted sign, though the phone mount is not documented.
  - The only pose test covers the landmark fallback, which never runs while the matrix is enabled.
- **Impact if inverted:** head droop (LAD-2) fires when the driver looks **up** and never on a real nod.
- **Fix:** verify on the device first. If inverted, change the sign and add a matrix-path unit test built from a known rotation.
- **Verify (2 min):** debug panel → nod slowly down → pitch must **increase** past +15°.

### P2: Medium

**AUDIT-009 · P2 · Confirmed: head-droop L3 cancels itself on the next frame**
- **Where:** `InterventionLadder.kt:89-98`. The eyes-open timer runs continuously from before the alert. After a head-droop L3 (eyes open the whole time), the next frame counts as a ≥ 3 s eyes-open "response".
- **Observed (PROBE4, consistent clock):** L3 at 6.92 s, auto-response 40 ms later, back to L0. The alarm blips, the urgent phrase still plays (TTS is not stopped on an eyes-open response), and the head-droop path can never reach L4.
- **Decision needed:** should the LAD-5 eyes-open timer start at or after the L3 trigger? Should it count at all for the head-droop path?
- **Test:** a head-droop L3 must persist for at least 3 s of post-alert open eyes.

**AUDIT-010 · P2 · Confirmed: calibration accepts empty or minimal samples**
- **Where:** `BaselineCalculator.kt:73,78,92`.
- **Observed (PROBE8):**
  - No closed-eye frames → `closed=0`, `threshold=0.13`, **isValid=true**.
  - One open and one closed frame → valid.
  - The "ignore first second" filter silently falls back to all frames when none survive.
- **Fix:** require a minimum count per phase (for example ≥ 15 frames after the ignore window) and non-empty closed samples. Otherwise mark the baseline invalid with a specific message.
- **Test:** unit tests for empty, short, and all-ignored phases.

**AUDIT-011 · P2 · Confirmed: L4 fires on the first face-found frame after a long face loss**
- **Where:** `InterventionLadder.kt:285-293`. The L4 deadline keeps running while the face is lost, then fires on return.
- **Observed (PROBE5):** L3 → face lost 12 s → face returns **with eyes open** → `PlayFamilyClip` on that first frame.
- **Why it matters:** the spec (LAD-3, ENG-4) allows a literal reading, but this plays the family clip to a driver who is visibly looking at the camera.
- **Decision needed:** pause or restart the L4 countdown when the face returns.

**AUDIT-012 · P2 · Confirmed: blink-rate baseline is unreliable, and ENG-3 triggers are implemented ahead of M9a**
- The baseline comes from about 9 s of open-phase frames. Zero blinks gives a rate clamped to **5/min** (PROBE8c), so a normal 16/min driver is then +220% above baseline: maximum blink penalty plus raw L1 even after AUDIT-001 is fixed.
- Raw L1 and L2 triggers are already active (`FatigueEngine.kt:135-140`) even though ENG-3 is scheduled for M9a. "Sustained 30 s" is not implemented.
- **Fix:** gate the blink signal until M9a, or require a longer baseline window. Implement the sustain properly.

**AUDIT-013 · P2 · Confirmed: thresholds hardcoded outside `Config.kt` (Rule 3); spec weight conflicts**
- **Hardcoded values:** `FatigueEngine.kt:83-110` (5.0 reason gates), `:108` (3_600_000), `:356` (80..500 ms blink), `:406,409` (10.0, 40.0), `:420,444` (500 ms), `:428` (0.2 minimum window), `:436-438` (15/0.50/25), `BaselineCalculator.kt:151-182` (16.0, 5–45 clamp, 80..500), `FeatureExtractor.kt:176` (×1.5).
- `ALERTNESS_WEIGHT_BLINK_RATE = 20.0` is **never read**.
- **Document conflict:**

  | Penalty | D1 maximum | REQUIREMENTS weight |
  |---|---|---|
  | Head droop | 20 | 15 |
  | Blink rate | 25 | 20 |
  | PERCLOS | 40 | 30 |

  The maximum combined penalty in code is 130.
- **Fix:** move every constant into Config with a rationale comment. Decide which document is authoritative and record that decision.

**AUDIT-014 · P2 · Confirmed (code): "max volume" is not enforced**
- `AlarmToneGenerator.kt:40` sets **track** gain 1.0 only. The `STREAM_ALARM` device volume is never raised, so a low alarm slider means a quiet alarm.
- TTS uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` (`TtsSpeaker.kt:43`), which follows a different volume, so the L3 phrase may be inaudible under the alarm.
- **Fix:** raise and later restore `STREAM_ALARM`, and decide which stream TTS uses for L3.
- **Verify on device:** set alarm volume to minimum, then trigger L3.

**AUDIT-015 · P2 · Confirmed (code): lifecycle gaps**
- **Backgrounding:** CameraX stops frames, but the 100 ms ticker keeps calling `ladder.onTick(faceFound=true)`. Timers escalate with no frames, and a started alarm keeps looping in the background. There is no stale-frame detection.
- **Rotation:** the Activity is recreated, so a new pipeline starts. Drive time and the alert count reset, and the FaceLandmarker is re-initialized on the main thread.
- **Fix:** treat "no frame for X ms" as FACE_LOST or paused. Lock orientation for monitoring, or hold the pipeline outside composition (a ViewModel now, a foreground service later per D15).

**AUDIT-016 · P2 · Risk: race in `release()` can leave an orphaned looping alarm**
- `MonitoringPipeline.kt:151-159` releases the alarm before `scope.cancel()`. A frame being processed concurrently can emit ShowRedFlash, and `startAlarm()` then **recreates** an AudioTrack (`AlarmToneGenerator.kt:36-37`) that nothing will stop.
- The landmarker is also closed while the analyzer thread may be inside `detectAsync`.
- **Fix:** cancel and join the scope first, then add an `isReleased` guard in AlarmToneGenerator.

**AUDIT-017 · P2 · Confirmed: `lintDebug` fails; FINE-only location will block GPS in M6**
- Two lint errors, listed in §3.
- Separately, on Android 12+ a runtime request for `ACCESS_FINE_LOCATION` without `ACCESS_COARSE_LOCATION` is ignored by the system. Add both before LAD-4.

**AUDIT-018 · P2 · Confirmed (code): EAR computed in normalized units; default threshold close to open-eye values**
- `FeatureExtractor.kt:193-197` takes the distance on (x/W, y/H). EAR scales with W/H (×0.75 for a 3:4 portrait frame). This matches the low measured phone EAR (0.20–0.28).
- Personal calibration absorbs the scale, but any change in analysis resolution or orientation invalidates it.
- The default threshold of 0.165 is close to measured open EAR, which risks false closures for uncalibrated users.
- **Fix:** multiply by image width and height before taking distances (re-measure afterwards), or re-derive the default threshold.

**AUDIT-019 · P2 · Confirmed: test-suite gaps**
- 36 tests pass, but **none** covers:
  - the production clock wiring (AUDIT-001)
  - `onTick`-driven L4/L5 in the engine, or the face-lost reminder *timing via `onTick`*
  - the matrix pose path
  - any pipeline, actuator, UI, or lifecycle integration
  - concurrency
  - calibration with empty phases
  - the head-droop eyes-open interaction
- `ExampleUnitTest` and `ExampleInstrumentedTest` are boilerplate.
- `testFaceLost…` only asserts that *frames* emit no reminder before 30 s.
- `testScoreBandTransitions` relies on raw-trigger arbitration rather than isolating the score band.
- I did not measure coverage; no coverage tool is configured.

**AUDIT-020 · P2 · Unverified: offline Hindi TTS and Plan B**
- `isLanguageAvailable(hi_IN)` (`TtsSpeaker.kt:33-36`) does not prove an **offline** voice is installed (no check of `Voice.isNetworkConnectionRequired` or `KEY_FEATURE_NOT_INSTALLED`).
- The D14/M5 "Plan B developer clips" is not implemented.
- `MEASUREMENTS.md` Test 1 is blank.
- Only one pending utterance is kept before init; earlier ones are dropped.
- **Verify:** airplane mode → L3 phrase spoken in Hindi.

### P3: Low

- **AUDIT-021 · Confirmed: APK size.** 89,352,835 B in the existing build and 89,296,054 B fresh. The reported "≈89.3 MB" reproduces.

  | Content | Size |
  |---|---|
  | Native libs, total | 42.4 MB |
  | └ **x86** | **21.3 MB** |
  | └ arm64 | 13.1 MB |
  | └ armv7 | 8.0 MB |
  | Unminified debug dex | ≈42 MB |
  | Model (`face_landmarker.task`, one copy, no duplicate) | 3.76 MB |

  `abiFilters += "arm64-v8a"` would save about 29 MB. Release builds with R8 (already configured with a limited `packageScope`) would shrink the dex. There is no runtime impact.
- **AUDIT-022 · Confirmed:** Monitoring gives no on-screen warning when running on the default (uncalibrated) baseline. Only the Home chip shows it.
- **AUDIT-023 · Confirmed (code), not observed in PROBE7:** `onTick()` level and state omit the raw trigger level and the closure override that `onFrame()` includes (`FatigueEngine.kt:215-221` vs `:161-170`). The UI could flicker between frame and tick outputs when the raw trigger exceeds the score band.
- **AUDIT-024 · Confirmed:** no DataStore corruption handler. `allowBackup="true"` with template rules (`AndroidManifest.xml:19`) means the numeric baseline can go to cloud backup, which is at odds with "on-device only".
- **AUDIT-025 · Risk:**
  - Landmarker initialized on the main thread.
  - `frameStartTimes` grows if LIVE_STREAM drops frames.
  - Two full bitmaps allocated per frame (GC churn).
  - The inference time shown includes queue time.
- **AUDIT-026 · Risk:** `VibeManager.vibrate` sets no `VibrationAttributes.USAGE_ALARM` (`VibeManager.kt:50`), so the alarm vibration may follow notification and haptics settings.
- **AUDIT-027 · Confirmed (doc and spec drift):**
  - QUICK_CALIBRATION is tied to DEMO_TIMERS (D9 says separate).
  - Neither flag is reachable or shown in the debug panel (LAD-7 is M6).
  - The debug panel lacks yaw and roll (the M2 prompt asked for them).
  - `engine/` imports `speech.Phrases` (pure Kotlin, but a cross-package dependency).
  - `PlayFamilyClip` increments the alert count.
  - Most of `MEASUREMENTS.md` is blank: TTS, latency, accuracy, temperature.
  - There is no README.
- **AUDIT-028 · Confirmed:** after a permanent camera-permission denial there is no "open Settings" path.
- **AUDIT-029 · Confirmed:** MediaPipe 0.10.20 arm64 `.so` is not 16 KB aligned (lint `Aligned16KB`). It currently runs on the target per the recorded FPS, but watch for this on 16 KB-page devices.
- **AUDIT-030 · Confirmed:** the daemon toolchain JDK 25 triggers the "Kotlin does not yet support 25" fallback warning. `Locale(String,String)` is deprecated. The `jvmTarget` is not pinned.

---

## 7. Requirements traceability matrix (M0–M5 scope)

| Req | Status | Evidence / file |
|---|---|---|
| CAM-1 front camera, KEEP_ONLY_LATEST, small preview | **PARTIAL** | `CameraController.kt:44-65`. Preview lost after first alert (006). Possible unbind after calibration (005). |
| CAM-2 LIVE_STREAM, 1 face, found/lost per frame | **PASS — verified** (code) | `FaceLandmarkerWrapper.kt:53-58,136` |
| CAM-3 EAR indices and formula | **PASS — verified** | `Config.kt:194-196`, `FeatureExtractor.kt:91-108`, recomputed |
| CAM-3 MAR | **PASS — verified** (math) | `FeatureExtractor.kt:114-125` |
| CAM-3 head pitch/yaw/roll, positive = down | **UNVERIFIED** (likely sign issue) | `FeatureExtractor.kt:132-154` (008) |
| CAM-4 debug panel FPS/ms (temp/component are M11a) | **PARTIAL** | `MonitoringScreen.kt:413-448` |
| CAL-1 10 s / 3 s, ignore 1 s, medians, formula | **PARTIAL** | Formula and medians verified. Sample guards missing (010). |
| CAL-2 MAR/head range/blink baseline | **PARTIAL** | Yawn and head steps unused. Blink baseline noisy (012). |
| CAL-3 math latency baseline | **PASS — verified** (code) | `CalibrationScreen.kt:353-369` |
| CAL-4 DataStore persist + Recalibrate | **PASS — verified** (code); restart persistence **UNVERIFIED** on device | `BaselineStore.kt`, Home/Settings |
| ENG-1 closure < threshold, 300 ms glitch tolerance | **PASS — verified** (unit) | `FatigueEngine.kt:312-365`, tests |
| ENG-2 score/EMA/bands/≥ 2.5 s CRITICAL | **PARTIAL** | Engine correct under one clock. Production: drive-time and blink broken (001). Weights conflict (013). |
| ENG-3 PERCLOS/blink/droop (M9a) | **NOT APPLICABLE** (implemented early, partially) | (012) |
| ENG-4 FACE_LOST never escalates; 30 s reminder | **FAIL** | Reminder fires immediately in production (001). L4 fires on face return (011). |
| ENG-5 pure Kotlin, Actions, UI-independent | **PARTIAL** | No Android imports ✔. Not thread-confined (003). |
| ENG-6 reasons list | **PARTIAL** | Deterministic strings. Not shown during red state. |
| LAD-1 L3 at 2.5 s + alarm + phrase + vibration + 30 s cooldown | **PARTIAL** | L3 ✔. Alarm silenced by face glitch (004). Volume not maxed (014). "Unless condition worsens" missing. |
| LAD-2 head droop prompt → L3 after 5 s, recovery cancels | **FAIL** | Production fires L3 at once (001). Self-cancels (009). Sign unverified (008). |
| LAD-3 L4 after L3 + 10 s, face detected | **FAIL** | Production: same tick as L3 (001). Fires on face return (011). Playback itself is M6. |
| LAD-4 L5 SMS (M6) | **NOT APPLICABLE** | Action logged only. Timer currently ≈ 20 s after L3 (001). |
| LAD-5 response = tap / voice / eyes ≥ 3 s; cancels L4/L5; re-closure → L3 | **PARTIAL** | Tap ✔, eyes ✔, cancel ✔, re-closure ✔ (unit). Voice is M8. Head-droop interaction (009). |
| LAD-6 / LAD-7 (M6) | **NOT APPLICABLE** | `demoTimers` exists, not exposed |
| VOI-1 Hindi offline TTS, urgent rate/pitch, Plan B | **PARTIAL / UNVERIFIED** | `TtsSpeaker.kt`. Offline not verified, Plan B absent (020). |
| UI-1 big number, state colour, drive time, alerts, privacy badge | **PASS — verified** (code) | `MonitoringScreen.kt:168-410`. Badge claim is false (002). Alert count doubles (001). |
| UI-2 red flash + I'M AWAKE + vibration + USAGE_ALARM tone | **PARTIAL** | Present. Silenced by face glitch (004). Volume (014). |
| UI-4 keep screen on + max brightness | **PASS — verified** (code); **UNVERIFIED** on device | `MonitoringScreen.kt:80-96` |
| NFR offline / no INTERNET (Rule 5) | **FAIL** | (002) |
| NFR 20-minute run without crash | **UNVERIFIED** | Crash risk (003) |
| NFR detection-to-alert latency < 500 ms measured | **UNVERIFIED** | Not in `MEASUREMENTS.md` |
| Rule 3 all thresholds in Config.kt | **FAIL** | (013) |
| Rule 7 minimal driver UI, raw values only in debug | **PASS — verified** | `MonitoringScreen.kt` |
| Rule 10 / D15 pipeline independent of UI | **PARTIAL** | Separate class, but created and released by the composable, with preview coupling (006, 015) |

---

## 8. Performance, offline/privacy, and safety

- **Measured (by the developer, from `MEASUREMENTS.md`):** inference about 18–30 ms on CPU, about 24.5 FPS, open EAR 0.20–0.28, neutral pitch +7–9°.
- **Not measured:** detection-to-alert latency (an NFR), temperature, memory, battery, 20-minute stability, accuracy by condition, TTS offline status.
- **Estimates from code only:**
  - Two full RGBA bitmap allocations per frame (about 1.2 MB each at 640×480).
  - CPU delegate.
  - Landmarker init on the main thread (startup jank on each monitoring entry and each rotation).
- **Offline:** not guaranteed by the build because `INTERNET` is present (002). Runtime traffic is unknown.
- **Privacy:** only numbers are stored ✔. No images or audio are persisted ✔. The baseline can enter cloud backup (024).
- **Safety-critical limitations:**
  - Alarm continuity depends on uninterrupted face detection (004).
  - Escalation timing is wrong in production (001).
  - The head-droop path is unreliable (008, 009).
  - Thresholds are hand-picked, with no tuning recorded in `MEASUREMENTS.md`.
  - **No real-world accuracy data exists. No claim of fatigue-detection accuracy can be made.** The product must keep saying it "estimates alertness".

---

## 9. What is genuinely ready for demonstration (v0-demo as is)

**Reasonably safe to show** (code-verified; device behaviour per your M5 checklist):
- Open eyes → green, silent.
- Close eyes about 3 s → red flash, alarm tone, urgent Hindi phrase, vibration.
- Tap I'M AWAKE → reset.
- Cover the camera → grey FACE_LOST with no new alarm.

**Avoid, or expect oddities, in a v0-demo session:**
- "Alerts" jumps by 2 per alert (001).
- Looking down at the dashboard for 1.5 s gives an instant full alarm instead of a soft prompt (001, and possibly the reverse direction per 008).
- Covering the camera triggers the "adjust phone" voice almost immediately, not after 30 s (001).
- The camera preview is likely black after the first alert (006).
- If you calibrate and go straight to monitoring, check that the preview is live (005).
- The alertness number rests around 75 after a minute of normal blinking (001).
- Any brief tracking loss during an alarm silences it (004).

---

## 10. Requires physical-device testing

1. Pitch sign: nod down, debug pitch must rise (008).
2. Calibrate → Save & Start Drive → preview live and FPS > 0. Same from Settings → Recalibrate (005).
3. After L3 → I'M AWAKE: preview live and FPS still updating (006).
4. Fresh-install calibration with camera permission revoked (007).
5. Airplane mode: TTS Hindi offline, detection works. `dumpsys package` permissions list (002, 020).
6. Alarm at minimum alarm volume; TTS audible over the alarm (014).
7. Alarm continuity through a brief camera cover during L3 (004).
8. Second L3 in the same session: alarm still loops (static `AudioTrack` after `pause()` + `reloadStaticData()`).
9. Background and foreground during L3; rotation during monitoring (015).
10. 20-minute continuous run: crash, temperature, memory (003).
11. Closure-to-phrase latency (NFR), FPS and temperature after 10 minutes.
12. Brightness restore and keep-screen-on release after End Drive.

---

## 11. Recommended repair order (each step with its regression test; do not move the `v0-demo` tag)

1. **AUDIT-001** single monotonic clock. *(Blocks everything timing-related and M6.)*
2. **AUDIT-003** confine the engine to one thread, and route I'M AWAKE through the same path. *(Do it together with step 1; both change `MonitoringPipeline`.)*
3. **AUDIT-004** derive alarm and vibration from ladder state, and decide the FACE_LOST-during-alert policy. *(Depends on 2.)*
4. **AUDIT-002** strip `INTERNET` and `ACCESS_NETWORK_STATE` via `tools:node="remove"`, then verify MediaPipe works in airplane mode.
5. **AUDIT-005, 006, 007** camera lifecycle: no `unbindAll`, preview reattachment, calibration permission gate.
6. **AUDIT-008** device pitch check, then a sign fix plus a matrix-path unit test if needed.
7. Product decisions, then implementation:
   - **AUDIT-009**: when the eyes-open timer starts.
   - **AUDIT-011**: L4 after face return.
   - **AUDIT-012**: gate the blink signal until M9a.
8. **AUDIT-010, 013–020**: hardening before or with M6. AUDIT-017 (COARSE location) is a prerequisite for LAD-4.
9. P3 items as time allows. The ABI filter (AUDIT-021) is a quick win.
10. Re-run the M5 checklist on the phone and tag a new checkpoint (for example `v0.1-demo`), keeping `v0-demo` as the fallback.

---

## 12. Go/no-go for M6

**NO-GO now → GO AFTER FIXES.** At minimum, steps 1–4 above must land first, plus a re-verification of the M5 checklist on the phone. M6 attaches the family clip and the emergency SMS to timers that currently fire L4 instantly and L5 about 20 s after any L3. It would also run on an engine that can crash from concurrent access.

**Preserve the `v0-demo` tag unchanged** as the fallback demo. Do all repairs as new commits on `main` or a branch.

---

### Appendix A: Probe outputs (temp clone `%TEMP%\jaagrit_audit`, `app/src/test/java/com/jaagrit/app/engine/AuditProbeTest.kt`, not committed)

```
PROBE1  prod-wiring eyes closed: L3 at 2500ms, L4 at 2500ms (spec L3+10000), L5 at 22500ms (spec L4+20000)
PROBE1b consistent clock: L3 at 2500ms, L4 at 12500ms, L5 at 32500ms
PROBE2  prod-wiring: one face-lost frame then tick 10ms later -> state=FACE_LOST, actions=[Speak] (FACE_LOST_30S)
PROBE3  prod-wiring head droop (eyes open): soft prompt at 1900ms, L3 at 1900ms (spec prompt+5000), auto 'response' at 3000ms
PROBE4  consistent clock head droop, eyes open: L3 at 6920ms, auto-response at 6960ms (delta 40ms), level after=L0
PROBE5  face back (eyes open) after 12s loss during L3: actions=[PlayFamilyClip, Vibrate, Log], level=L4
PROBE6  normal 16 blinks/min vs baseline 16: prod-wiring alertness=75 reasons=[Blink rate elevated (+400% vs baseline)]; consistent-clock alertness=100
PROBE6b prod-wiring 5h into drive, eyes open: alertness=100 (spec ≈ 89)
PROBE7  PERCLOS≈0.17: onFrame FATIGUED/L2/41; onTick FATIGUED/L2/41 (no disagreement in this case)
PROBE8  no closed-eye frames: closed=0.0 threshold=0.13 isValid=true;  1+1 frames: threshold=0.155 isValid=true;  0 blinks → 5.0/min
PROBE9  nod-down +20° about X: pitch(colMajor) = -20.0, pitch(rowMajor) = +20.0
PROBE10 concurrent onFrame/onTick/onVoice (3 runs): NPE / ConcurrentModificationException in FatigueEngine.computePerclos (FatigueEngine.kt:394)
```

Production wiring copied in the probes: engine clock = epoch milliseconds (`Clock.SYSTEM`), `resetDrive(epoch)`, `FaceFrame.tsMs` = uptime-like values. Ticks every 100 ms, frames every 20–40 ms.
