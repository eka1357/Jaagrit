package com.jaagrit.app.speech

/**
 * Hardcoded spoken phrases stored in Devanagari with romanized comments.
 * Requirements: docs/PHRASES.md & AGENTS.md Rule 8.
 * Pure Kotlin — zero Android dependencies.
 */
object Phrases {

    // 1. Level 3 — Direct alerts (hardcoded, urgent voice)
    val L3_ALERTS = listOf(
        // "Bhai! Aankhen khol! Gaadi dheere karo!"
        "भाई! आँखें खोल! गाड़ी धीरे करो!",
        // "Uth ja bhai! So mat jaana!"
        "उठ जा भाई! सो मत जाना!",
        // "Aankhen band ho gayi thi! Alert ho ja!"
        "आँखें बंद हो गई थीं! अलर्ट हो जा!",
        // "Safe jagah dekh ke ruk ja bhai!"
        "सेफ जगह देख के रुक जा भाई!",
        // "Bhai dhyan se! Aankhen khol!"
        "भाई ध्यान से! आँखें खोल!",
        // "Agle dhaba pe ruk ja, neend aa rahi hai!"
        "अगले ढाबे पे रुक जा, नींद आ रही है!"
    )

    // 7. Soft prompt (head droop check, LAD-2)
    val SOFT_PROMPTS = listOf(
        // "Sab theek hai? Bol de."
        "सब ठीक है? बोल दे।",
        // "Bhai, sar neeche ja raha tha. Theek hai?"
        "भाई, सिर नीचे जा रहा था। ठीक है?",
        // "Alert hai na? Ek baar bol de."
        "अलर्ट है ना? एक बार बोल दे।"
    )

    /** A companion line with the Hindi (TTS source of truth) and English variants. */
    data class Line(val hi: String, val en: String)

    /** A PHRASES.md §5 cognitive question; [maxWaitMs] is stored with the question (D2). */
    data class MathItem(val id: Int, val prompt: Line, val answer: Int, val maxWaitMs: Long)

    // 4. Level 1-2 — Companion openers (COM-1, COM-2). Index 4 (#5) is the history-only pattern line (COM-6).
    val OPENERS = listOf(
        // "Bhai, bohot der ho gayi. Ghar pe kaun wait kar raha hai?"
        Line("भाई, बहुत देर हो गई। घर पे कौन इंतज़ार कर रहा है?", "Been a long drive. Who's waiting at home?"),
        // "Ek sawaal — tera favorite cricketer kaun hai?"
        Line("एक सवाल — तेरा फेवरेट क्रिकेटर कौन है?", "Quick one — who's your favorite cricketer?"),
        // "Aaj ka din kaisa raha?"
        Line("आज का दिन कैसा रहा?", "How was your day?"),
        // "Chal ek game khelte hain. Main number bolunga, tu double karke bata."
        Line("चल एक गेम खेलते हैं। मैं नंबर बोलूँगा, तू डबल करके बता।", "Let's play a game. I say a number, you double it."),
        // "Pichli baar bhi is waqt neend aayi thi. Chai ka time hai."
        Line("पिछली बार भी इस वक़्त नींद आई थी। चाय का टाइम है।", "Last time you got sleepy around now too. Chai time."),
        // "Ek baat bata — agar ek din chutti milti toh kya karta?"
        Line("एक बात बता — अगर एक दिन छुट्टी मिलती तो क्या करता?", "If you got a day off, what would you do?"),
        // "Bhai, thodi thakan lag rahi hai. Chal baat karte hain."
        Line("भाई, थोड़ी थकान लग रही है। चल बात करते हैं।", "Feeling a bit tired? Let's talk."),
        // "Koi gaana suna? Main ga nahi sakta lekin tu toh ga sakta hai!"
        Line("कोई गाना सुना? मैं गा नहीं सकता लेकिन तू तो गा सकता है!", "Heard any good songs? I can't sing but you can!"),
        // "Agla dhaba kitni door hoga? Wahan ruk ke chai pi le."
        Line("अगला ढाबा कितनी दूर होगा? वहाँ रुक के चाय पी ले।", "How far is the next dhaba? Stop for chai."),
        // "Tu kitne saal se drive kar raha hai? Longest trip kaunsa tha?"
        Line("तू कितने साल से ड्राइव कर रहा है? सबसे लंबा ट्रिप कौन सा था?", "How long have you been driving? Longest trip?")
    )
    const val PATTERN_OPENER_INDEX = 4

    // 5. Cognitive questions (L2, COM-3) — per-question max wait from PHRASES.md
    val MATH_QUESTIONS = listOf(
        // "Sau mein se tees ghata. Kitna bacha?"
        MathItem(1, Line("सौ में से तीस घटा। कितना बचा?", "100 minus 30. What's left?"), 70, 5000L),
        // "Pachaas ko double kar. Kitna hua?"
        MathItem(2, Line("पचास को डबल कर। कितना हुआ?", "Double 50. What do you get?"), 100, 4000L),
        // "Pandrah aur baaees jod. Kitna hua?"
        MathItem(3, Line("पंद्रह और बाईस जोड़। कितना हुआ?", "15 plus 22. What do you get?"), 37, 5000L),
        // "Do sau mein se ek sau pachchees ghata. Kitna bacha?"
        MathItem(4, Line("दो सौ में से एक सौ पच्चीस घटा। कितना बचा?", "200 minus 125. What's left?"), 75, 5000L),
        // "Solah ko double kar, phir das jod. Kitna hua?"
        MathItem(5, Line("सोलह को डबल कर, फिर दस जोड़। कितना हुआ?", "Double 16, then add 10."), 42, 6000L),
        // "Ek sau bayalis mein se atthawan ghata. Kitna bacha?"
        MathItem(6, Line("एक सौ बयालीस में से अट्ठावन घटा। कितना बचा?", "142 minus 58. What's left?"), 84, 6000L)
    )

    // Short acknowledgements after a companion answer (spoken, not safety path)
    // "Shabaash! Dhyan se chalate raho."
    val ACK_GOOD = Line("शाबाश! ध्यान से चलाते रहो।", "Nice! Keep driving carefully.")
    // "Thoda dheere jawab aaya. Chai break le lo."
    val ACK_SLOW = Line("थोड़ा धीरे जवाब आया। चाय ब्रेक ले लो।", "That answer was a bit slow. Take a chai break.")
    // "Theek hai, do minute chup rehta hoon. Nazar rakhunga."
    val ACK_DISMISS = Line("ठीक है, दो मिनट चुप रहता हूँ। नज़र रखूँगा।", "Okay, I'll stay quiet for two minutes. Still watching.")

    // 2. Level 4 — Family voice fallback (D8)
    // "Papa, jaldi ghar aao. Hum intezaar kar rahe hain."
    const val L4_FALLBACK = "पापा, जल्दी घर आओ। हम इंतज़ार कर रहे हैं।"

    // 3. Level 5 — Emergency SMS templates (PHRASES.md Section 3)
    const val L5_SMS_TEMPLATE_EN = "ALERT: %s may be unresponsive while driving.\nLast alert: %s\nAlerts in this trip: %d\nLocation: %s\n— Sent by Jaagrit (automated safety alert)"
    const val L5_SMS_TEMPLATE_HI = "अलर्ट: %s गाड़ी चलाते समय जवाब नहीं दे रहे हैं।\nआख़िरी अलर्ट: %s\nइस ट्रिप में अलर्ट: %d\nलोकेशन: %s\n— जागृत (ऑटोमैटिक सेफ्टी अलर्ट)"

    // 10. System messages
    // "Camere se chehra dikhayi nahi de raha. Phone adjust karo." (ENG-4, face lost 30 s)
    const val FACE_LOST_30S = "कैमरे से चेहरा दिखाई नहीं दे रहा। फोन एडजस्ट करो।"

    // "Jaagrit chaalu hai. Surakshit chalao."
    const val DRIVE_STARTED = "जागृत चालू है। सुरक्षित चलाओ।"

    // "Kayi baar galat alert aaye. Recalibrate karna chahoge?"
    const val TOO_MANY_FALSE_ALERTS = "कई बार गलत अलर्ट आए। रीकैलिब्रेट करना चाहोगे?"
}
