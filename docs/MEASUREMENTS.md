# MEASUREMENTS.md — Jaagrit

**Rule: only numbers measured by you go on slides. If it isn't in this file, it doesn't get stated as a number.**
Fill this in as you test. Add the date and the device next to each block.

## Devices
- Laptop: Windows 11  Phone: iQOO 15 (Android 16)  Date: 2026-10-09

## Test 1 — Hindi TTS (phone)
| Check | Result | Notes |
|---|---|---|
| Offline Hindi voice installed (works in airplane mode) | ☐ yes ☐ no | |
| Engine used | | |
| Devanagari vs romanized sounds better | | |
| Speech rate / pitch chosen for normal | | |
| Speech rate / pitch chosen for L3 (urgent) | | |

## Test 2 — Speech recognition
Laptop Whisper (best case):
| Clip | Expected | Got | Correct? (quiet) | Correct? (noisy) | Time (ms) |
|---|---|---|---|---|---|
| clip1 कितनी देर से... | | | | | |
| clip2 मेरा अलर्टनेस... | | | | | |
| clip3 आज कितने अलर्ट... | | | | | |
| clip4 चुप रहो | | | | | |
| clip5 बंद करो | | | | | |
Quiet: __/5   Noisy: __/5   Language detected for Hindi clips: ____   English clip: ____
Decision: ☐ Tiny ☐ Base ☐ Android SpeechRecognizer + buttons

Phone (Android SpeechRecognizer, offline Hindi pack installed? ☐):
Quiet: __/5   Noisy: __/5   Latency: ____ ms

## Test 3 — Face mesh + EAR (laptop webcam)
| Metric | Value |
|---|---|
| Open-eye EAR (median) | |
| Closed-eye EAR (median) | |
| Threshold (midpoint) | |
| Gap | |
| Inference time (ms, laptop CPU) | |
| Face tracked with glasses? EAR change | |
| Face tracked in dim room? | |
| Face survives 30° head turn? Angle where face is lost | |

## iQOO 15 probe (hour 0-2)
| Check | Result |
|---|---|
| FaceLandmarker inference (ms): CPU / GPU / NPU if available | ~18–30 ms (CPU, Snapdragon 8 Elite) |
| Average FPS during monitoring | ~24.5 FPS |
| Open-eye EAR (phone front camera) | ~0.20–0.28 (neutral posture) |
| Neutral Mouth MAR (closed) | ~0.00–0.02 |
| Pitch (neutral dashboard angle) | ~7.0°–9.0° (positive = nodding down) |
| Phone temp at start / after 10 min of monitoring | |
| Temp after 5 companion/TTS events | |
| TTS Hindi voice present | |
| SMS permission works, SMS received on second phone | |
| Office Kit pairing: mirror / file transfer / clipboard / remote control | |
| Screen-as-fill-light works in dim room | |

## Detection accuracy by condition (fill from your own trials)
| Condition | Trials | Correct closures detected | False alerts | Notes |
|---|---|---|---|---|
| Good light | | | | |
| Dim light | | | | |
| Dim light + max screen brightness | | | | |
| Clear glasses | | | | |
| Sunglasses (expect fallback to head pose/voice) | | | | |
| Dashboard glance / looking down | | | | |
| Normal blinks (should NOT alert) | | | | |
| 1 s closure (should NOT alert) | | | | |
| 3 s closure (should alert) | | | | |

## Latency
| Metric | Value |
|---|---|
| Closure confirmed to phrase starts speaking (ms) | |
| Speech command to spoken answer (ms) | |

## Hostile test checklist (before the demo)
☐ glasses ☐ face turned 30° ☐ looking down ☐ rapid blinking ☐ 300 ms vs 1 s vs 3 s closure ☐ low light ☐ partial face cover ☐ noisy room ☐ Hindi + Hinglish ☐ repeated commands ☐ airplane mode ☐ 20-minute continuous run (crash? temp?)

## Threshold changes log
| Date | Constant (Config.kt) | Old | New | Why (evidence) |
|---|---|---|---|---|
| | | | | |

## Numbers approved for slides
(copy only measured values here)
