# PRODUCT.md — Jaagrit (जागृत)

## One-liner
A phone on the dashboard that watches your face, talks you awake first, and only sounds the alarm if talking fails. Fully offline.

## Who it's for
Indian truck/bus drivers on long hauls, and the fleet managers who worry about them. Existing driver-monitoring hardware is expensive (verify exact figures before putting numbers on a slide); Jaagrit runs on the phone the driver already has.

## What makes it different (say it carefully)
- **Prevention first:** conversation and engagement while fatigue is *building*, alarm only as a later step.
- **Personal calibration:** thresholds learned from the driver's own face, glasses, lighting.
- **Graduated ladder** including a family voice and an emergency SMS.
- **Offline & private:** no cloud, no stored images or audio.
- **Fleet report** via Office Kit.
Do NOT claim "no app does this" or "medically validated". Say "we haven't found one that combines these".

## The intervention ladder
| Level | Trigger | What happens |
|---|---|---|
| L0 | Normal | Silent. Green UI. |
| L1 Early warning | Blink rate ~20% above the driver's baseline | Companion asks a short friendly question |
| L2 Active engagement | PERCLOS approaching 0.12 (60 s window) | Companion asks a short mental question; response time is measured; slow = fatigue confirmed |
| L3 Direct alert | Eyes closed ≥ 2.5 s, OR head drooped and no response to a spoken check | Loud hardcoded Hindi phrase, red flashing screen, strong vibration |
| L4 Family voice | No response 10 s after L3 (face must be detected) | Pre-recorded family audio |
| L5 Emergency | No response 20 s after L4 | SMS with GPS to emergency contact + fleet dashboard goes red. Any response cancels. |

Companion questions fire only at L1/L2, never during normal driving, never while eyes are closed. "Chup raho" silences them.

## Screens
1. **Home** — "Start Drive", privacy badge "ON-DEVICE".
2. **Pre-drive checklist** — phone mounted, face visible, mic ready.
3. **Calibration** (~60 s) — look normally, close eyes 3 s, yawn once, look left/right/down, answer one quick math question (sets response-time baseline).
4. **Monitoring** — small camera preview, huge alertness number, state (Focused / Fatigued / Critical / Face not visible), drive time, alert count, "Ask Jaagrit" button, "I'm awake" button (red state).
5. **Why this alert?** — plain-language reasons (eye closure, blink rate, head pose, drive time).
6. **Dashboard / Drive report** — alertness-over-time graph, alert timeline, trip safety score, export PDF. Built to be mirrored to a laptop via Office Kit.
7. **Settings / Privacy** — language, emergency contact, recalibrate, privacy summary.

## Visual states
Green = focused. Amber = fatigue building. Red = critical (flashing, big "I'M AWAKE" button). Grey = face not visible (never escalates). Blue = calibrating.

## Voice (keep tiny)
Queries: how long driving, alertness now, alerts today, send report, recalibrate.
Dismiss: "chup raho", "band karo", "bas karo", "rehne de", "stop". (**Not** "theek hoon". A drowsy driver says that on autopilot.)
Languages: Hindi / Hinglish / English. Switch by voice or settings.

## Demo script (~90 s)
Calibrate → ask drive time (Hindi) → ask alertness (English) → act tired, companion asks a math question, slow answer is noticed → close eyes 3 s, L3 alert → keep closed, L4 family voice → open eyes, recovery message → show laptop dashboard, transfer the PDF report. Timers for L4/L5 may be shortened with the `DEMO_TIMERS` flag and this must be disclosed when asked.

## Honest limits (state them in the pitch)
Low light, sunglasses, extreme head angles reduce accuracy. Show measured numbers only, never invented ones.
