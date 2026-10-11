# Jaagrit — 90-Second Pitch

**Length:** about 245 words, roughly 95–100 seconds at a steady pace (~150 words/minute). Rehearse with a timer; if you run long, cut section 5's last line.
**Style:** simple English, short sentences. Pause at every `/`.

---

## The script

### 1. Hook (0:00 – 0:12)
It's 3 a.m. on a highway. / A truck driver has driven for nine hours. / Their eyes close — just for two seconds. / At 60 kilometres an hour, / that's over 30 metres with no one driving.

### 2. Problem (0:12 – 0:25)
Driver-monitoring systems exist, / but they're expensive hardware in new cars. / Truck and bus drivers on long night routes need them most — / and don't have them. / But every one of them has a phone.

### 3. Solution (0:25 – 0:45)
This is **Jaagrit**. / It means *"awake"* in Hindi. / Put the phone on the dashboard. / The front camera watches your eyes and your head, / 25 times a second, / and gives an alertness score from 0 to 100. / First, it learns *your* face in one minute — / your eyes, your glasses, your lighting.

### 4. How it helps (0:45 – 1:12)
But it doesn't just scream. / It's like a friend in the passenger seat. / When you start getting tired, / it talks to you — a quick question, a little math. / If your eyes close for two and a half seconds — / a loud alarm, a Hindi voice, a red screen. / Still no response? / It plays a recorded message from your family. / Still nothing? / It sends an SMS with your location to your emergency contact.

### 5. Trust (1:12 – 1:25)
Everything runs on the phone. / No internet. No cloud. No photos saved — only numbers. / The safety alarms never depend on AI or the network, / so they always work.

### 6. Close (1:25 – 1:35)
Jaagrit doesn't replace a rested driver. / But on a long night road, / it makes sure *someone* is always awake. / **Jaagrit. Stay awake. Reach home.**

---

## The one line to remember
> "A phone on the dashboard that talks you awake first, and sounds the alarm only if talking fails — fully offline."

## Delivery tips
- **Hold up the phone** at "Put the phone on the dashboard." Show it running if you can.
- **Slow down on the ladder** (section 4). It's the heart of the pitch. Count the steps on your fingers: talk → alarm → family → SMS.
- **Pause after the hook.** Let "30 metres with no one driving" land before you go on.
- **Smile on the last line.** End on "Reach home," not on a fact.
- If you demo the companion questions, **turn on the companion in Settings first**. It's off by default.
- If you use `DEMO_TIMERS` (shorter L4/L5 timers), **say so** if anyone asks.

## If the judges ask (short, honest answers)
| Question | Answer |
|---|---|
| How accurate is it? | "Face detection takes 18 ms on this phone, at about 25 frames a second. Open and closed eyes are clearly separated — 0.34 vs 0.06 on our eye-openness measure. We're still measuring accuracy in different conditions, so we only quote numbers we've measured." |
| What about night or sunglasses? | "Low light and sunglasses reduce accuracy. The screen works as a fill light, the app warns in low light, and if it can't see your face it says so. It never raises a false alarm because the face is missing." |
| Does it need the internet? | "No. We removed the internet permission completely. Only the emergency SMS needs mobile signal." |
| Is it a medical device? | "No. It *estimates* alertness. It doesn't diagnose anything." |
| Isn't this already out there? | "Pieces of it exist. We haven't found one that combines personal calibration, a talk-first ladder, a family voice, and fully offline privacy on a normal phone." |
| What's next? | "Run it as a background service, test with real truck drivers, and add a fleet dashboard for transport companies." |

## Do not say
- "No app does this" → say "we haven't found one that combines these."
- "Medically proven" or "detects drowsiness 100%" → say "estimates alertness."
- Any number that isn't in `docs/MEASUREMENTS.md`.
