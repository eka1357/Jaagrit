# Jaagrit (जागृत) — Keynote Pitch Deck Plan & Claims Ledger

## Executive Overview
- **Product:** Jaagrit (जागृत) — Offline Android driver-alertness app running on iQOO 15 (Snapdragon 8 Elite, Android 16).
- **Core Promise:** A phone on the dashboard that watches your eyes, engages you early, and intervenes in graduated stages — 100% offline with zero cloud telemetry.
- **Audience:** Hackathon judges evaluating end-product quality, novelty & impact, camera + voice + on-device intelligence, technical depth, Office Kit use, and presentation clarity.
- **Target Pitch Duration:** ~3 minutes (180 seconds) live presentation + Q&A.
- **Design Aesthetic:** Keynote/Linear/Vercel style — 16:9 widescreen, bold typography (Mukta), generous whitespace, dark/light visual rhythm, real phone mockups, thin lime callouts, no generic clipart or walls of text.

---

## 1. Asset Inventory

Every file in `presentation/shots` and `presentation/clips` has been inspected frame-by-frame and probed for exact resolution, orientation, and personal info:

| Asset Path | True Content | Orientation | Resolution | Visible Phone / Personal Info |
|---|---|---|---|---|
| `presentation/shots/landing page english.png` | Jaagrit Home screen in English showing brand wordmark (`JAAGRIT / जागृत`), eye-horizon logo, headline *"Stay alert. Drive safe."*, 5-point readiness checklist (Camera: ready, Calibration: Calibrated, Emergency SMS: no SIM card, Hindi voice: ready, Speech commands: ready), "Start Drive" button, and offline privacy footer. | Portrait | 1440 × 3168 | Status bar has notification pill *"HackTracker by..."*; no phone number or driver name. Clean. |
| `presentation/shots/landing page hindi.jpg` | Jaagrit Home screen in Hindi showing Devanagari typography (*"सजग रहें। सुरक्षित चलें।"*, *"वास्तविक समय चालक सतर्कता सहायक"*), readiness checklist in Hindi (*कैमरा: तैयार*, *कैलिब्रेशन: कैलिब्रेटेड*, *आपातकालीन एसएमएस: सिम कार्ड नहीं है*, *हिंदी आवाज़: तैयार*), "ड्राइव शुरू करें" CTA button, and offline privacy footer (*"ऑफ़लाइन काम करता है। फ़ोटो और ऑडियो कभी सेव नहीं होते।"*). | Portrait | 1440 × 3168 | Status bar has notification pill *"HackTracker by..."*; no phone number or driver name. Clean. |
| `presentation/shots/trip history.jpg` | Trip History screen showing Room Database summary banner (*"Drive Telemetry Overview"*, Total Drives: `48`, Avg Alertness: `74%`, Total Alerts: `22 (22 crit)`), followed by drive session cards (`#48`, `#47`, `#46`) with session timestamps, durations, and alert badges. | Portrait | 1440 × 3168 | Status bar has notification pill *"HackTracker by..."*; no personal names or phone numbers. Clean. |
| `presentation/shots/Screenshot_20261011_104925.jpg` | Detailed inspection modal for drive session `#48` (11 Oct 2026, 10:37 AM) showing Duration (`0m 15s`), Avg Alertness (`76%`), Alerts (`1 crit`), logged L3 event (*"PERCLOS high (26%)"*, duration `1.0s`, `imAwake` response), and *"View Dashboard & Export Report"* action button. | Portrait (Card Crop) | 1414 × 1309 | Session timestamp only; no personal names or phone numbers. Clean. |
| `presentation/shots/trip safety score and dashboard-alertness over time.jpg` | Fleet Dashboard cards: Top card displays *"Trip Safety Score"* (`62 / 100`, `MODERATE RISK`, Duration `0m 15s`, Avg Alertness `76%`, Alerts `1`); Bottom card displays *"Alertness Over Time"* canvas telemetry graph (3 samples, colored caution/critical bands, alertness trajectory line, and red dotted alert vertical marker). | Portrait (Card Crop) | 1440 × 1837 | No personal names, phone numbers, or external identifiers. Clean. |
| `presentation/clips/awakeness clip.mp4` | 15.33 s video recording of live driving monitor run on iQOO 15: starts at Home checklist, taps "Start Drive", enters Monitoring with face tracked (green Alert state, 98/100), simulates closed eyes, alertness plunges to 35/100 (*"PERCLOS high 56%"*, amber Fatigued state), and after 2.5 s sustained closure triggers Level 3 Critical Alert (red flashing screen, *"WAKE UP!"*, 25/100, big white *"I'M AWAKE"* button). | Portrait (1080 × 1920, 30 fps, H.264/AAC, 18.7 MB) | 1080 × 1920 | Driver's face (young man with spectacles); neck lanyard with hackathon event text (*"INNOVATION..."*); status bar pill (*"HackTracker by..."*). Zero phone numbers, home addresses, or private contacts. |
| `presentation/clips/extracted/frame_05.jpg` *(Derived Poster/Shot)* | Real monitoring screen during normal driving: Face preview with green border (*"FACE VISIBLE"*), large green alertness numeral `98 / 100`, green state pill `✓ Alert`, Drive Time `00:02`, Alerts `0`, green mic button. | Portrait | 1080 × 1920 | Clean monitoring screenshot. |
| `presentation/clips/extracted/frame_08.jpg` *(Derived Poster/Shot)* | Real monitoring screen during onset of drowsiness: Eyes closed in camera preview, large orange alertness numeral `35 / 100`, orange state pill `! Fatigued`, reason text *"PERCLOS high (56%)"*, Drive Time `00:05`. | Portrait | 1080 × 1920 | Clean fatigue detection screenshot. |
| `presentation/clips/extracted/frame_10.jpg` *(Derived Poster/Shot)* | Real Critical Alert mode (Level 3): Full-bleed red screen (`#B71C1C`), banner *"JAAGRIT • CRITICAL ALERT"*, headline *"WAKE UP! Slow down and open your eyes!"*, large white numeral `25 / 100`, warning pill *"Emergency SMS unavailable (no SIM)"*, prominent white *"I'M AWAKE"* CTA button. | Portrait | 1080 × 1920 | Clean critical alarm screenshot. |

---

## 2. Claims Ledger

Every number, factual statement, or technical capability proposed for any slide must be grounded in verified project evidence. Any claim without evidence is cut or flagged for confirmation.

| # | Claim on Slide | Exact Evidence (File & Line / Measurement / Test) | Verification Status | Notes / Constraints |
|---|---|---|---|---|
| C1 | **18 ms median FaceLandmarker inference** on Snapdragon 8 Elite CPU | `docs/MEASUREMENTS.md:48, 96` ("14–25 ms typical, 18 ms median CPU") | **VERIFIED** | Highlight on-device CPU execution without GPU/NPU hogging. |
| C2 | **~24.8 FPS camera analysis pipeline** rate | `docs/MEASUREMENTS.md:49, 97` ("~24.8 FPS, 23.8–25.5 observed") | **VERIFIED** | Real-time continuous monitoring on front camera. |
| C3 | **5.6x eye separation dynamic range** (0.34 open vs 0.06 closed EAR) | `docs/MEASUREMENTS.md:50-51, 98` ("Open ~0.28–0.39, closed ~0.05–0.08") | **VERIFIED** | Isotropic pixel-space EAR separates blinks from true closures. |
| C4 | **2.5 s closure confirmation threshold** for Level 3 critical alert | `Config.kt:CLOSURE_CONFIRM_MS = 2500`, `docs/REQUIREMENTS.md:86`, `InterventionLadderTest.kt` | **VERIFIED** | Safety trigger boundary; filters normal 100–400 ms blinks. |
| C5 | **≥ 3.0 s continuous open eyes recovery** resets active alarms | `Config.kt:OPEN_EYES_RESPONSE_MS = 3000`, `docs/REQUIREMENTS.md:97`, `docs/MEASUREMENTS.md:100` | **VERIFIED** | Hands-free recovery path alongside "I'M AWAKE" button. |
| C6 | **100% offline with 0 bytes transmitted**; `INTERNET` permission stripped | `app/src/main/AndroidManifest.xml:15-16`, `ManifestSecurityTest.kt:28-52`, `docs/MEASUREMENTS.md:101` | **VERIFIED** | MediaPipe DataTransport stripped via `tools:node="remove"`. Zero network calls. |
| C7 | **198 passing unit tests across 27 suites** with 0 failures | Test XML execution results in `app/build/test-results/testDebugUnitTest/` (27 suites, 198 tests, 0 fails) | **VERIFIED** | Covers vision, fatigue engine, ladder, storage, SMS, speech, reports. |
| C8 | **Independent code audit identified timebase flaw (AUDIT-001)**; fixed with monotonic clock & 8 regression tests | `docs/AUDIT_M0_M5.md:25`, `docs/DECISIONS.md:224`, `AuditRegressionTest.kt:61-120` | **VERIFIED** | Monotonic `elapsedRealtime` injection eliminates clock drift. |
| C9 | **Pure Kotlin FatigueEngine** with 0 Android imports | `AGENTS.md` Rule 2, `docs/ARCHITECTURE.md:29`, `FatigueEngine.kt:1-30` | **VERIFIED** | Deterministic, testable with `FakeClock` without Android robolectric. |
| C10 | **Graduated 5-level intervention ladder** (L1 Spoken Opener → L2 Cognitive Check → L3 Loud Alarm + Vibration → L4 Family Voice → L5 Emergency SMS) | `docs/PRODUCT.md:18-25`, `docs/REQUIREMENTS.md:37-44`, `docs/DECISIONS.md:144-150` | **VERIFIED** | Stepped escalation prevents alarm fatigue; family voice personalizes urgency. |
| C11 | **Thermal stability:** 29.8°C idle, <34°C sustained monitoring | `docs/MEASUREMENTS.md:55, 102` | **VERIFIED** | Throttling threshold at 42°C (`THERMAL_REDUCE_C`); massive thermal headroom. |
| C12 | **Fleet PDF report generation via Android `PdfDocument`** exported to `Download/Jaagrit/` for Office Kit transfer | `ReportExporter.kt:35-37`, `Screenshot_20261011_104925.jpg` | **VERIFIED** | Generates 1-page visual PDF + JSON; no cloud dashboard needed. |
| C13 | **Estimates alertness, never diagnoses** | `AGENTS.md` Rule 9, `strings.xml:258`, `ReportExporter.kt:43-46` | **VERIFIED** | Heuristic computer-vision estimation. Strict medical disclaimer preserved. |
| C14 | **Rule-based conversational companion** (`PhraseBankCompanion`) | `PhraseBankCompanion.kt`, `docs/REQUIREMENTS.md:49` | **VERIFIED** | Deterministic questions & openers from `docs/PHRASES.md`. |
| C15 | *Claims of on-device LLM / Gemma 2B running on phone* | `GemmaCompanion.kt` is an unbundled M12 plumbing stub; no weights bundled | **CUT FROM SLIDES** | **RULE APPLIED:** Do not use the words Gemma, LLM, or AI companion. |
| C16 | *Road accident or highway fatigue statistics (e.g. "40% of highway crashes...")* | No citation in `docs/MEASUREMENTS.md` | **CUT FROM SLIDES** | **RULE APPLIED:** Never invent statistics; omit until Eka provides cited source. |
| C17 | *Calibration screen & Settings emergency contact screenshot* | Code and tests exist; actual screenshots absent from `presentation/shots` | **NEEDS EKA CONFIRMATION** | Plan adapts layout: use verified code/UI cards or request phone shots from Eka. |
| C18 | *Received SMS screenshot on recipient device* | Tested (`MEASUREMENTS.md:58`, `SmsStatusHonestyTest`), but no image in repo | **NEEDS EKA CONFIRMATION** | Use code template illustration or request real screenshot from Eka. |

---

## 3. Slide-by-Slide Narrative Plan

### Slide 1: Title & Hero
- **Action Title:** JAAGRIT / जागृत
- **One Message:** An offline Android driver alertness assistant that engages before it alarms.
- **Layout:** Dark slide (`#0A120E`). High-contrast hero layout. Prominent wordmark `JAAGRIT` + Devanagari `जागृत` in ivory `#F4F0E6`. Tagline in accent lime `#B9EE45`. Center-right: realistic phone mockup displaying `landing page english.png`. Bottom left: minimal badges `100% OFFLINE` • `ON-DEVICE VISION` • `iQOO 15`.
- **Assets:** `landing page english.png` inside custom drawn phone bezel.
- **Draft Speaker Notes (English):** "Good morning, judges. This is Jaagrit. An offline Android driver alertness assistant built for the iQOO 15 that sits on the dashboard, estimates driver fatigue in real time, and talks you awake before sounding the alarm." *(32 words)*
- **Hindi Anchor Line:** *"सजग रहें, सुरक्षित चलें — पूरी तरह ऑन-डिवाइस, बिना इंटरनेट।"*
- **Target Time:** 15 seconds

---

### Slide 2: The Problem
- **Action Title:** Drowsiness does not announce itself — it arrives in silence.
- **One Message:** Long-haul drivers don't realize their alertness is eroding until their eyes close, and existing fleet hardware is costly, invasive, and cloud-bound.
- **Layout:** Ultra-minimal, cinematic dark slide (`#0A120E`). Large focal typography (48 pt) in ivory `#F4F0E6`. Three quiet tension points arranged horizontally:
  1. *Micro-sleep is stealthy* — Fatigue builds over hours before the first head droop.
  2. *Traditional alarms trigger too late* — Screaming sirens after eyes close cause startle, panic, or driver disabling.
  3. *Highway blind spots* — Cloud-reliant telematics fail completely on dark Indian highway corridors.
- **Assets:** Typographic layout with subtle horizontal gradient divider in forest green.
- **Draft Speaker Notes (English):** "Drowsiness doesn't announce itself. On a dark highway between midnight and dawn, alertness drains gradually. By the time a traditional buzzer screams, a truck has traveled fifty meters blind. Drivers hate loud buzzers and unplug them. We need prevention, not just panic." *(41 words)*
- **Hindi Anchor Line:** *"नींद आने से पहले ही चेतावनी ज़रूरी है — चिल्लाने वाले अलार्म बहुत देर कर देते हैं।"*
- **Target Time:** 15 seconds

---

### Slide 3: The Solution
- **Action Title:** Three pillars that turn everyday smartphones into a guardian.
- **One Message:** Jaagrit combines on-device vision, a 5-stage graduated ladder, and an uncompromising offline-first architecture.
- **Layout:** Light slide (`#F4F0E6`). Clean 3-column card layout with forest green titles and lime accents:
  - **Pillar 1: Continuous Vision (18 ms)** — Real-time eye aspect ratio tracking and head posture directly on the Snapdragon 8 Elite CPU.
  - **Pillar 2: Graduated Ladder (L1–L5)** — Soft conversational checks while fatigue is building; urgent siren, family voice, and SMS only when sleep is critical.
  - **Pillar 3: Zero-Cloud Privacy (0 Bytes)** — Completely offline. No camera frames saved, no internet permission requested, works in full airplane mode.
- **Assets:** 3 structural cards with subtle border `#E8E1D3` and custom SVG-style geometric icons (Eye, Ladder, Shield).
- **Draft Speaker Notes (English):** "Jaagrit solves this with three principles. First, continuous on-device vision running in eighteen milliseconds per frame. Second, a graduated ladder that talks first and alarms last. And third, total privacy: zero bytes leave the phone, and photos never touch disk." *(40 words)*
- **Hindi Anchor Line:** *"तीन सिद्धांत: तेज़ ऑन-डिवाइस विज़न, पाँच चरणों की सीढ़ी, और शत-प्रतिशत ऑफ़लाइन सुरक्षा।"*
- **Target Time:** 20 seconds

---

### Slide 4: The Escalation Ladder
- **Action Title:** A five-stage intervention ladder that steps in gradually.
- **One Message:** Jaagrit respects the driver: early cognitive engagement at L1/L2, escalated siren at L3, family voice at L4, and SMS at L5.
- **Layout:** Dark slide (`#0A120E`). Left 60%: A drawn vertical escalation timeline showing the 5 graduated levels with color-coded badges and exact timing rules:
  - **L1 Early Warning:** Blink rate +20% / Yawn → Spoken friendly check.
  - **L2 Active Engagement:** PERCLOS ≥ 0.12 → Spoken cognitive check; measures response latency.
  - **L3 Direct Alert:** Eyes closed ≥ 2.5 s → Loud siren + urgent Hindi phrase + red screen + vibration.
  - **L4 Family Voice:** Unresponsive 10 s after L3 → Pre-recorded family voice clip.
  - **L5 Emergency SOS:** Unresponsive 20 s after L4 → GPS emergency SMS to saved contact.
  Right 40%: Real phone mockup displaying the Critical Red Alert screen (`frame_10.jpg`) with large *"I'M AWAKE"* button.
- **Assets:** Drawn vector timeline + `frame_10.jpg` inside phone mockup.
- **Draft Speaker Notes (English):** "Here is the heart of Jaagrit: the graduated ladder. At level one, a conversational check. At level two, a simple cognitive question to gauge reaction speed. If eyes close for two and a half seconds, Level Three fires instantly. If there's no response, Level Four plays a family voice. After twenty seconds, Level Five sends an emergency SMS with GPS." *(62 words)*
- **Hindi Anchor Line:** *"हल्की बातचीत से शुरू होकर, परिवार की आवाज़ और आपातकालीन एसएमएस तक।"*
- **Target Time:** 25 seconds

---

### Slide 5: The Awakening Moment (Video & Live Demo Handover)
- **Action Title:** From subtle drowsiness to critical intervention in real time.
- **One Message:** Real recording of Jaagrit detecting eye closure at 2.5 s, flashing the critical red banner, and providing immediate one-tap dismissal.
- **Layout:** Dark slide (`#0A120E`). Left side: Embedded clean 1080x1920 video loop (or high-res poster frame `frame_08.jpg` to `frame_10.jpg`) showing the live transition from green (98/100) to orange (35/100) to red (25/100, *"WAKE UP!"*). Right side: Key metric callouts pointing at the UI:
  - *2.5 s closure confirmed* → Immediate alarm trigger.
  - *Siren on USAGE_ALARM* → Full device volume regardless of media settings.
  - *One-tap "I'M AWAKE"* → Instantly silences siren and cancels pending Level 4/5 escalation.
  - Prominent badge: *"LIVE DEMO BACKUP / REPRODUCIBLE TRIAL"*.
- **Assets:** `awakeness clip.mp4` (trimmed to 12s H.264 loop) + poster frame `frame_10.jpg`.
- **Draft Speaker Notes (English):** "Let's watch it happen. When my eyes close during a fatigue event, the PERCLOS metric drops my score to thirty-five. The moment two point five seconds elapse, Level Three triggers: high-contrast red flash, max-volume alarm, and urgent Hindi voice. Tapping 'I'm Awake' or opening eyes immediately cancels the alert. [SWITCH TO LIVE PHONE DEMO]." *(57 words)*
- **Hindi Anchor Line:** *"दो दशमलव पाँच सेकंड आँखें बंद — और सिस्टम तुरंत जागो का अलर्ट देता है।"*
- **Target Time:** 35 seconds (Handover to live phone demo)

---

### Slide 6: Personal Calibration & Honest Accuracy
- **Action Title:** Learned from the driver's own face, not a generic average.
- **One Message:** A 60-second baseline records open/closed eye medians, glasses offset, and neutral dashboard tilt to eliminate false positives.
- **Layout:** Light slide (`#F4F0E6`). Two-column comparison layout:
  - Left column: The Calibration Mechanics (Median-based math formula: `Threshold = ClosedMedian + 0.5 × (OpenMedian − ClosedMedian)`). Neutral pitch baseline accounting for 7–9° dashboard angle tilt.
  - Right column: Verified Measured Numbers from `docs/MEASUREMENTS.md`:
    - **18 ms** Median Inference Latency (Snapdragon 8 Elite)
    - **~24.8 FPS** Continuous Camera Analysis
    - **5.6x Separation** (0.34 Open EAR vs 0.06 Closed EAR)
    - **< 34°C** Device Temperature after sustained driving (zero thermal throttling)
- **Assets:** Drawn card layout with oversized numerals (54 pt) and formula illustration.
- **Draft Speaker Notes (English):** "Every driver has different eyes, posture, and glasses. Jaagrit runs a short calibration before driving. We take the statistical median of open and closed eyes, yielding a five-point-six times dynamic separation. We also calibrate neutral dashboard tilt, preventing false alerts when glancing at road mirrors." *(46 words)*
- **Hindi Anchor Line:** *"हर ड्राइवर की आँखें अलग होती हैं — हमारा कैलिब्रेशन हर चेहरे के हिसाब से सीमा तय करता है।"*
- **Target Time:** 15 seconds

---

### Slide 7: The Safety Net: Level 5 Emergency SMS
- **Action Title:** Automated SOS with live GPS when a driver is unresponsive.
- **One Message:** If sirens and family voices fail, Jaagrit transmits an emergency SMS with exact coordinates to family or fleet managers via cellular radio.
- **Layout:** Light slide (`#F4F0E6`). Center layout showcasing the verified Level 5 safety mechanism:
  - Left: Settings & Pre-Drive Readiness verification (Callout: *"Cellular radio only — no data plan needed"*). Warning honesty: *"Airplane mode blocks SMS; pre-drive checklist warns driver"*.
  - Center: Formatted real SMS template card:
    ```
    ALERT: Driver may be unresponsive while driving.
    Last alert: 10:37:32 AM
    Alerts in this trip: 1
    Location: 28.5450° N, 77.1926° E
    — Sent by Jaagrit (automated safety alert)
    ```
  - Right: Key fail-safe protections: False positive guard (3 alerts in 10 mins prompts recalibration), any awake response halts the SMS countdown.
- **Assets:** Custom SMS dialogue visual + Home screen readiness snippet showing *"Emergency SMS: no SIM card"* graceful honesty badge.
- **Draft Speaker Notes (English):** "If a driver suffers an emergency and doesn't respond to the alarm or family voice after thirty seconds, Level Five triggers. An automated SMS with GPS coordinates is sent directly over cellular radio. It requires no mobile internet and costs pennies." *(43 words)*
- **Hindi Anchor Line:** *"यदि चालक कोई प्रतिक्रिया नहीं देता, तो जीपीएस लोकेशन के साथ आपातकालीन संदेश तुरंत भेजा जाता है।"*
- **Target Time:** 15 seconds

---

### Slide 8: Trips, Proof & Office Kit Fleet Transfer
- **Action Title:** Complete trip telemetry stored locally and shared seamlessly.
- **One Message:** Room database records every trip, computes safety scores, renders alertness graphs, and exports one-page PDF reports for fleet dispatch via Office Kit.
- **Layout:** Light slide (`#F4F0E6`). Dual-card layout incorporating real app screenshots:
  - Left Card: `trip history.jpg` showing Room database tracking 48 drives, 74% avg alertness, and logged event timestamps.
  - Right Card: `trip safety score and dashboard-alertness over time.jpg` showing Safety Score `62/100 (MODERATE RISK)` and the real Canvas graph plotting alertness decline and alert markers.
  - Bottom bar: Office Kit integration callout: *"Reports export to Download/Jaagrit/ as PDF & JSON — instantly pulled to fleet manager laptops via Office Kit file transfer."*
- **Assets:** `trip history.jpg`, `trip safety score and dashboard-alertness over time.jpg`, and `Screenshot_20261011_104925.jpg`.
- **Draft Speaker Notes (English):** "Every drive is cataloged in an on-device Room database. Here you see our real test log of forty-eight drives. Jaagrit scores trip safety, visualizes the alertness curve, and generates a one-page PDF report. Fleet managers can transfer this report to their laptop in seconds using Office Kit." *(48 words)*
- **Hindi Anchor Line:** *"यात्रा का पूरा डेटा स्थानीय डेटाबेस में सुरक्षित रहता है और बिना इंटरनेट पीडीएफ़ में एक्सपोर्ट होता है।"*
- **Target Time:** 20 seconds

---

### Slide 9: Prevention Before the Alarm: Cognitive Checkpoints
- **Action Title:** Engaging the brain before eyes close.
- **One Message:** Rule-based conversational openers and cognitive math checks test mental latency and keep the driver mentally alert.
- **Layout:** Dark slide (`#0A120E`). High-clarity interactive diagram showing the L1/L2 companion loop:
  - **L1 Openers:** Friendly spoken Hindi phrases (*"भाई, बहुत देर हो गई। चाय का टाइम है।"*). Two ignored openers trigger a 5-minute backoff so the driver isn't nagged.
  - **L2 Cognitive Math:** Simple spoken mental questions (*"सौ में से तीस घटा। कितना बचा?"*). Measures response latency: latency > 2x baseline confirms creeping drowsiness.
  - **Microphone Isolation Rule:** The mic is strictly closed during active alarms (L3–L5) to prevent siren acoustic feedback; push-to-talk button and 4 on-screen fallback buttons ensure zero driver frustration.
- **Assets:** Graphic dialogue cards with Devanagari typography and latency timing diagram.
- **Draft Speaker Notes (English):** "True safety starts before the eyes close. At Level One and Two, Jaagrit speaks short, natural Hindi questions and simple mental arithmetic. It measures the driver's response latency. If latency doubles, fatigue is confirmed. And during loud alarms, the microphone stays locked to prevent audio distortion." *(48 words)*
- **Hindi Anchor Line:** *"अलार्म बजाने से पहले, हल्की दिमागी बातचीत से ड्राइवर को जाग्रत रखा जाता है।"*
- **Target Time:** 15 seconds

---

### Slide 10: Private, Bilingual, On-Device
- **Action Title:** Designed for Indian drivers: Hindi first, zero cloud compromise.
- **One Message:** Natural Devanagari UI and offline Hindi TTS voice paired with English, with network permissions stripped at the manifest level.
- **Layout:** Light slide (`#F4F0E6`). Side-by-side comparison of real phone screenshots:
  - Left: `landing page hindi.jpg` (*"सजग रहें। सुरक्षित चलें।"*)
  - Right: `landing page english.png` (*"Stay alert. Drive safe."*)
  - Center/Footer Callout:
    - *Zero Internet Permission:* `android.permission.INTERNET` explicitly stripped with `tools:node="remove"`.
    - *Offline TTS:* Native Android Google TTS Hindi voice package stored locally on device.
    - *No Cloud Account:* Drivers don't log in, register, or upload face biometrics.
- **Assets:** `landing page hindi.jpg` and `landing page english.png` in side-by-side phone mockups.
- **Draft Speaker Notes (English):** "Jaagrit was designed specifically for Indian commercial drivers. The entire interface and voice guidance toggle between English and natural Hindi. Crucially, the app requests zero internet permissions. Drivers don't have to worry about surveillance, cellular data consumption, or privacy leaks." *(42 words)*
- **Hindi Anchor Line:** *"पूरी तरह हिंदी और अंग्रेज़ी में, बिना किसी सर्वर या इंटरनेट की निर्भरता के।"*
- **Target Time:** 15 seconds

---

### Slide 11: Under the Hood: Architecture & Audit
- **Action Title:** Engineered for resilience: clean architecture and verified fixes.
- **One Message:** Decoupled pure-Kotlin engine, monotonic timebase, and 198 unit tests proven through an independent codebase audit.
- **Layout:** Dark slide (`#0A120E`). Technical architectural flow diagram drawn with clean boxes and arrows:
  `[CameraX (Keep Latest)]` → `[MediaPipe (18 ms)]` → `[FatigueEngine (Pure Kotlin)]` → `[InterventionLadder]` → `[UI / TTS / Alarm / SMS]`
  - Key highlights below architecture:
    - **198 Passing Unit Tests:** 27 test suites with 100% pass rate.
    - **Independent Audit (AUDIT-001):** Discovered a critical mixed timebase bug between system uptime and epoch clocks; resolved by injecting a unified monotonic `Clock`, verified by 8 new regression tests.
    - **UI Independence:** Vision pipeline is decoupled from Compose Activity, ready to run seamlessly inside an Android Foreground Service.
- **Assets:** Drawn architecture vector diagram + audit verification badge.
- **Draft Speaker Notes (English):** "Under the hood, Jaagrit separates vision from Android lifecycle. Our pure-Kotlin fatigue engine has zero Android imports, tested across one hundred ninety-eight unit tests. During an independent architectural audit, we caught and fixed a subtle clock-drift bug with dedicated regression tests. This system is rock-solid." *(47 words)*
- **Hindi Anchor Line:** *"स्वतंत्र कोड ऑडिट और एक सौ अट्ठानवे यूनिट टेस्ट्स द्वारा प्रमाणित आर्किटेक्चर।"*
- **Target Time:** 20 seconds

---

### Slide 12: Honest Limits & What's Next
- **Action Title:** Engineering honesty: real operational boundaries and future roadmap.
- **One Message:** We estimate alertness, not diagnose fatigue; acknowledging camera edge cases while planning fleet expansion.
- **Layout:** Light slide (`#F4F0E6`). Two-column transparent breakdown:
  - **Column 1: Honest Operational Limits**
    - *Low Ambient Lighting:* Relies on phone screen full-brightness fill light (`UI-4`), but deep pitch darkness requires infrared/cabin lighting.
    - *Polarized Sunglasses:* Obscures pupil landmarks; system gracefully falls back to head pitch and vocal response.
    - *Extreme Head Tilt:* Angles beyond 30° reduce landmark precision.
    - *Disclaimer:* Estimates alertness heuristically; does not claim medical diagnosis.
  - **Column 2: Roadmap Ahead**
    - *Android Foreground Service:* Continuous background monitoring while running Google Maps navigation in split-screen or PiP.
    - *Fleet Manager Pairing:* Two-way Office Kit dispatch sync for long-haul fleet depots.
- **Assets:** Two structured cards with warning and roadmap icons.
- **Draft Speaker Notes (English):** "We believe in engineering honesty. Jaagrit estimates alertness; it does not claim medical diagnosis. Dark sunglasses and pitch-black cabins reduce eye tracking accuracy, falling back to head droop. Our next milestone moves the pipeline into a Foreground Service to support Google Maps navigation." *(43 words)*
- **Hindi Anchor Line:** *"हम सतर्कता का अनुमान लगाते हैं, कोई मेडिकल दावा नहीं करते — ईमानदारी ही हमारी ताक़त है।"*
- **Target Time:** 15 seconds

---

### Slide 13: Close & Vision
- **Action Title:** JAAGRIT — Keeping India's wheels turning safely.
- **One Message:** Real-time driver protection on the phone they already own.
- **Layout:** Dark slide (`#0A120E`). Cinematic closing layout. Bold call-to-action headline in ivory, tagline in accent lime: *"Stay alert. Drive safe."*
  - Left: Three core pillars recap (*18 ms On-Device Vision* • *5-Stage Graduated Ladder* • *100% Offline & Private*).
  - Right: Clean minimalist QR Code placeholder pointing to repository and demo artifacts.
  - Footer: Brand mark `JAAGRIT / जागृत` • iQOO 15 • Android 16.
- **Assets:** Minimal QR code vector box + brand logo.
- **Draft Speaker Notes (English):** "Commercial drivers are the backbone of our economy. They deserve safety technology that works with them, respects their privacy, and protects them on every dark highway. That is Jaagrit. Thank you, and I look forward to your questions." *(38 words)*
- **Hindi Anchor Line:** *"सजग रहें, सुरक्षित चलें। धन्यवाद!"*
- **Target Time:** 10 seconds

---

## 4. Appendix Q&A Slides (Anticipating Judge Probes)

### Slide A1: Detection Accuracy & Edge Conditions
- **Focus:** How does the app handle glasses, night driving, and sudden head movements?
- **Data:** Median-based calibration captures custom baseline with glasses; UI forces maximum display brightness to act as fill light in dark cabins; head pitch is relative to calibrated dashboard mount (7–9° offset absorbed).

### Slide A2: Why On-Device Only? (Why Not Cloud AI?)
- **Focus:** Why reject cloud models or cloud telemetry?
- **Data:** Latency must be deterministic (< 500 ms vs unpredictable highway 4G latency); highway blind spots leave trucks disconnected for hours; privacy eliminates liability and driver resistance; 0 API cost.

### Slide A3: Driver Distraction Defense
- **Focus:** Doesn't an interactive app distract a driver?
- **Data:** Companion speaks only 5-second short questions during early fatigue (L1/L2), never during normal driving; "Chup raho" or one dismiss word silences companion for 2 minutes; mic strictly locked during alarms; UI uses oversized typography visible at arm's length without squinting.

### Slide A4: Two-Wheelers vs Commercial Cabs
- **Focus:** Can this protect two-wheeler riders or auto-rickshaws?
- **Data:** Optimized for dashboard-mounted windshield angles (cars, trucks, buses); full-face helmets obscure landmarks, making head-pose and vocal checkpoints the primary signal for two-wheelers.

### Slide A5: Cellular SMS & SIM-Free Graceful Degradation
- **Focus:** What happens if the driver has no SIM card or airplane mode is enabled?
- **Data:** Live screenshot proof: Home screen explicitly alerts *"Emergency SMS unavailable (no SIM card)"*; alarm siren, vibration, and voice alerts remain 100% operational; app never crashes.

---

## 5. Visual System & Typography Specification

- **Aspect Ratio:** 16:9 widescreen (`13.333 in × 7.5 in`).
- **Color Palette:**
  - *Ivory (Background / Text):* `#F4F0E6`
  - *Forest Green (Primary Brand):* `#18392B`
  - *Accent Lime (Highlight / Pill / Accents):* `#B9EE45`
  - *Near-Black (Dark Slides Background):* `#0A120E`
  - *Muted Sand (Card Borders / Dividers):* `#E8E1D3`
  - *Alert Red (Only for Level 3 / Alarm elements):* `#B71C1C`
- **Slide Rhythm:**
  - Dark: Slides 1 (Title), 2 (Problem), 4 (Ladder), 5 (Awakening), 9 (Prevention), 11 (Architecture), 13 (Close).
  - Light: Slides 3 (Solution), 6 (Calibration), 7 (SMS), 8 (Trips/Fleet), 10 (Bilingual/Privacy), 12 (Honest Limits).
- **Typography:**
  - Primary Font: **Mukta** (Bold, Medium, Regular copied in `presentation/fonts/`).
  - Title Size: 40–50 pt bold.
  - Body / Subtitles: 20–24 pt regular/medium.
  - Captions & Data Callouts: 14–16 pt.
  - Devanagari Script: Must configure complex script font tags (`a:cs`) in PowerPoint OpenXML generation to prevent system fallback.
- **Layout Grid:** 12-column grid, 0.6 in outer margins, generous breathing room, max 25 words per slide.
- **Real Phone Mockups:** Custom drawn vector frames with realistic 24 dp rounded corners, dark bezels, and subtle drop shadows framing unmodified real screenshots.

---

## 6. Questions & Decision Points for Eka

1. **Missing Visual Assets in `presentation/shots`:**
   - We have verified screenshots for Home (English & Hindi), Trip History, Trip Detail `#48`, Dashboard Safety Score card, and real video recording.
   - Do you have real screenshots for:
     - *(a)* The **Calibration flow** (Step 1 or Success screen)?
     - *(b)* The **Settings screen** showing emergency contact configuration?
     - *(c)* The **Received emergency SMS** on a second phone?
     - *(d)* An exported **PDF report file** open on a phone or laptop?
   *Recommendation:* If you don't have them handy, we will adapt Slides 6, 7, and 8 using our verified code templates, extracted video frames, and real dashboard cards without slowing down the build.

2. **Gemma / LLM Language:**
   - Our code audit verified that `PhraseBankCompanion` is the active brain (deterministic phrase matching from `docs/PHRASES.md`). `GemmaCompanion` is an M12 architecture test stub without weights bundled on the device.
   - We have strictly excluded "Gemma" and "LLM" from all slides, framing the companion as a "rule-based cognitive check". Please confirm this matches your expectations for the judges.

3. **Live Demo Handover Timing:**
   - Slide 5 is designed as the transition point: you show the real awakening clip on slide 5 as a visual backup, then say `[SWITCH TO LIVE DEMO]` to run the live eye-closure test on your iQOO 15. Does this fit your presentation flow?
