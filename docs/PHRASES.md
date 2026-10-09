# PHRASES.md — Jaagrit phrase bank

**Rules for this file**
- Devanagari is the source of truth for TTS and for matching speech output. The romanized line is only for reading and editing.
- Level 3, 4, 5 phrases are hardcoded. They never depend on an LLM.
- Spoken phrases stay short (L3 under 4 s, companion lines under 8 s). Never repeat the same phrase twice in a row.
- Read every line aloud and edit anything you wouldn't say to a truck driver.
- Section numbers (6 = dismiss, 8 = voice commands) are referenced by `docs/PROMPTS.md`. Don't renumber.

---

## 1. Level 3 — Direct alerts (hardcoded, urgent voice)
Also used when a head droop gets no response.

| # | Devanagari (TTS) | Romanized | English |
|---|---|---|---|
| 1 | भाई! आँखें खोल! गाड़ी धीरे करो! | Bhai! Aankhen khol! Gaadi dheere karo! | Wake up! Slow down! |
| 2 | उठ जा भाई! सो मत जाना! | Uth ja bhai! So mat jaana! | Stay awake! Don't fall asleep! |
| 3 | आँखें बंद हो गई थीं! अलर्ट हो जा! | Aankhen band ho gayi thi! Alert ho ja! | Your eyes were closed! Stay alert! |
| 4 | सेफ जगह देख के रुक जा भाई! | Safe jagah dekh ke ruk ja bhai! | Find a safe spot and stop! |
| 5 | भाई ध्यान से! आँखें खोल! | Bhai dhyan se! Aankhen khol! | Pay attention! Open your eyes! |
| 6 | अगले ढाबे पे रुक जा, नींद आ रही है! | Agle dhaba pe ruk ja, neend aa rahi hai! | Stop at the next dhaba, you're sleepy! |

Note: #2 changed from "Soja mat" to "So mat jaana" (clearer). #4 replaces "Ruk ja side pe" (stopping on the shoulder isn't safe).

---

## 2. Level 4 — Family voice (pre-recorded audio, NOT TTS)
Record real audio clips. Ask the person for permission, and for the demo use a friend or your own voice.

| # | Devanagari (script for the recording) | Romanized | Who records |
|---|---|---|---|
| 1 | पापा, जल्दी घर आओ। हम इंतज़ार कर रहे हैं। | Papa, jaldi ghar aao. Hum intezaar kar rahe hain. | A child's voice |
| 2 | संभल के चलो। घर पे सब तुम्हारा इंतज़ार कर रहे हैं। | Sambhal ke chalo. Ghar pe sab tumhara intezaar kar rahe hain. | An adult voice |
| 3 | भाई, रुक जा थोड़ी देर। हम चाहते हैं तू सेफ घर आए। | Bhai, ruk ja thodi der. Hum chahte hain tu safe ghar aaye. | A warm, concerned voice |

File names: `family_1.mp3`, `family_2.mp3`, `family_3.mp3`. Minimum for the demo: one clip.

---

## 3. Level 5 — SMS template (hardcoded)
English:
```
ALERT: {driver_name} may be unresponsive while driving.
Last alert: {timestamp}
Alerts in this trip: {count}
Location: {gps_lat}, {gps_lon}
— Sent by Jaagrit (automated safety alert)
```
Hindi:
```
अलर्ट: {driver_name} गाड़ी चलाते समय जवाब नहीं दे रहे हैं।
आख़िरी अलर्ट: {timestamp}
इस ट्रिप में अलर्ट: {count}
लोकेशन: {gps_lat}, {gps_lon}
— जागृत (ऑटोमैटिक सेफ्टी अलर्ट)
```
Use the English version by default (Devanagari SMS can split into more segments). If no GPS fix, write "Location: unavailable".

---

## 4. Level 1-2 — Companion openers
Fire only when the fatigue score crosses L1. The app only checks that the driver responded (it can't grade open-ended answers).

| # | Devanagari (TTS) | Romanized | English | Notes |
|---|---|---|---|---|
| 1 | भाई, बहुत देर हो गई। घर पे कौन इंतज़ार कर रहा है? | Bhai, bohot der ho gayi. Ghar pe kaun wait kar raha hai? | Been a long drive. Who's waiting at home? | Personal |
| 2 | एक सवाल — तेरा फेवरेट क्रिकेटर कौन है? | Ek sawaal — tera favorite cricketer kaun hai? | Quick one — who's your favorite cricketer? | Light |
| 3 | आज का दिन कैसा रहा? | Aaj ka din kaisa raha? | How was your day? | Personal |
| 4 | चल एक गेम खेलते हैं। मैं नंबर बोलूँगा, तू डबल करके बता। | Chal ek game khelte hain. Main number bolunga, tu double karke bata. | Let's play a game. I say a number, you double it. | Cognitive |
| 5 | पिछली बार भी इस वक़्त नींद आई थी। चाय का टाइम है। | Pichli baar bhi is waqt neend aayi thi. Chai ka time hai. | Last time you got sleepy around now too. Chai time. | **Only if real trip history exists** |
| 6 | एक बात बता — अगर एक दिन छुट्टी मिलती तो क्या करता? | Ek baat bata — agar ek din chutti milti toh kya karta? | If you got a day off, what would you do? | Personal |
| 7 | भाई, थोड़ी थकान लग रही है। चल बात करते हैं। | Bhai, thodi thakan lag rahi hai. Chal baat karte hain. | Feeling a bit tired? Let's talk. | Direct |
| 8 | कोई गाना सुना? मैं गा नहीं सकता लेकिन तू तो गा सकता है! | Koi gaana suna? Main ga nahi sakta lekin tu toh ga sakta hai! | Heard any good songs? I can't sing but you can! | Light |
| 9 | अगला ढाबा कितनी दूर होगा? वहाँ रुक के चाय पी ले। | Agla dhaba kitni door hoga? Wahan ruk ke chai pi le. | How far is the next dhaba? Stop for chai. | Practical (a question to the driver, the app doesn't know the distance) |
| 10 | तू कितने साल से ड्राइव कर रहा है? सबसे लंबा ट्रिप कौन सा था? | Tu kitne saal se drive kar raha hai? Longest trip kaunsa tha? | How long have you been driving? Longest trip? | Personal |

---

## 5. Cognitive questions (L2) — response time is measured
Keep the math simple. The point is engagement, not difficulty.

| # | Devanagari (TTS) | English | Answer | Spoken answer (Hindi) | Max wait |
|---|---|---|---|---|---|
| 1 | सौ में से तीस घटा। कितना बचा? | 100 minus 30. What's left? | 70 | सत्तर | 5 s |
| 2 | पचास को डबल कर। कितना हुआ? | Double 50. What do you get? | 100 | सौ | 4 s |
| 3 | पंद्रह और बाईस जोड़। कितना हुआ? | 15 plus 22. What do you get? | 37 | सैंतीस | 5 s |
| 4 | दो सौ में से एक सौ पच्चीस घटा। कितना बचा? | 200 minus 125. What's left? | 75 | पचहत्तर | 5 s |
| 5 | सोलह को डबल कर, फिर दस जोड़। कितना हुआ? | Double 16, then add 10. | 42 | बयालीस | 6 s |
| 6 | एक सौ बयालीस में से अट्ठावन घटा। कितना बचा? | 142 minus 58. What's left? | 84 | चौरासी | 6 s |

**Answer matching:** accept digits ("70"), Hindi number words, and English number words ("seventy"). Also show on-screen quick-answer buttons as a fallback if recognition fails. A wrong answer still counts as a response; only the latency matters for the fatigue signal.

**Latency rules:** baseline comes from the calibration question. Current latency above 2× baseline = fatigue confirmation. No answer within the max wait → escalate to L3.

---

## 6. Dismiss keywords
The companion goes silent for **2 minutes**. Matching is deterministic (keyword matching), accept Devanagari and romanized output.

| Hindi | Hinglish | English |
|---|---|---|
| चुप रहो / चुप हो जा | chup raho / chup ho ja | stop |
| बंद करो / बस करो | band karo / bas karo | quiet |
| रहने दे / मत बोल | rehne de / mat bol | leave it |

**Not a dismiss word:** "ठीक हूँ / theek hoon / I'm fine". A drowsy driver says that on autopilot. It counts as a normal *response* but does not silence anything.

**Behavior on dismiss:**
- Companion silent for 2 minutes. Face monitoring continues.
- If eyes are closed for 2.5 s or more during the silent period, L3 still fires (safety overrides dismiss).

---

## 7. Soft prompt (head droop check)
Fires when head pitch is over 15° for 1.5 s or more. 30 s cooldown. If there's no reply within 5 s, it escalates to L3. A reply like "ठीक है / theek hai" counts as a response.

| # | Devanagari (TTS) | Romanized | English |
|---|---|---|---|
| 1 | सब ठीक है? बोल दे। | Sab theek hai? Bol de. | Everything okay? Say something. |
| 2 | भाई, सिर नीचे जा रहा था। ठीक है? | Bhai, sar neeche ja raha tha. Theek hai? | Your head was dropping. You okay? |
| 3 | अलर्ट है ना? एक बार बोल दे। | Alert hai na? Ek baar bol de. | Still alert? Just say something. |

---

## 8. Voice commands
Normalize the recognizer output (lowercase, strip punctuation) and match by keyword sets. Accept Devanagari, romanized and English.

| Intent | Hindi | English | Keyword hints | Spoken response |
|---|---|---|---|---|
| DRIVE_TIME | कितनी देर से ड्राइव कर रहा हूँ? | How long have I been driving? | कितनी देर, kitni der, how long, driving | "{X} घंटे {Y} मिनट हो गए" |
| ALERTNESS | मेरा अलर्टनेस कैसा है? | How's my alertness? | अलर्टनेस, alertness, कैसा | "अलर्टनेस {X} परसेंट है। {status}" |
| ALERT_COUNT | आज कितने अलर्ट आए? | How many alerts today? | कितने अलर्ट, kitne alert, how many alerts | "आज {X} अलर्ट आए हैं" |
| LAST_ALERT (optional) | आख़िरी अलर्ट कब आया? | When was the last alert? | आख़िरी, last alert, kab | "आख़िरी अलर्ट {X} मिनट पहले आया था" |
| REPORT | रिपोर्ट भेजो | Send report | रिपोर्ट, report | "रिपोर्ट सेव हो गई है" |
| RECALIBRATE | रीकैलिब्रेट करो | Recalibrate | रीकैलिब्रेट, recalibrate, calibrate | "ठीक है, कैमरे की तरफ देखो" |
| DISMISS | see section 6 | | | silent |

Changed: REPORT says the report is *saved*, not "sent to laptop". The transfer happens through Office Kit, so don't claim it did.

**Fallback if recognition fails:** a 4-button panel `[Drive Time] [Alertness] [Alerts] [Report]`. Never let the demo stall on a recognition failure. Reply "दोबारा बोलो?" (say again) once, then show the buttons.

---

## 9. Status messages (on screen)
| State | Text | Hindi | Color |
|---|---|---|---|
| Normal | ✅ Alert — {drive_time} | अलर्ट | Green |
| L1 | 😐 Fatigue building — companion active | थकान बढ़ रही है | Yellow |
| L2 | ⚠️ Respond to stay alert | जवाब दो, जागते रहो | Orange |
| L3 | 🔴 DROWSY — WAKE UP | जागो! | Red, flashing |
| Face lost | 📷 Face not visible — adjust phone | चेहरा नहीं दिख रहा | Grey |
| Calibrating | 📐 Look at camera... | कैमरे की तरफ देखो | Blue |
| Post-dismiss | 🔇 Companion paused — monitoring active | कंपैनियन रुका है, निगरानी चालू | Grey |

---

## 10. System messages (spoken)
| Situation | Devanagari (TTS) | English |
|---|---|---|
| Face lost for 30 s | कैमरे से चेहरा दिखाई नहीं दे रहा। फोन एडजस्ट करो। | Face not visible. Adjust the phone. |
| Too many false alerts | कई बार गलत अलर्ट आए। रीकैलिब्रेट करना चाहोगे? | Several false alerts. Recalibrate? |
| Recognition failed | दोबारा बोलो? | Say again? |
| Drive started | जागृत चालू है। सुरक्षित चलाओ। | Jaagrit is on. Drive safe. |
