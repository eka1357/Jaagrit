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
        // "Kya aap jaante hain? Jaagte samay aapka dimaag ek bulb jalaane jitni bijli paida karta hai."
        Line("क्या आप जानते हैं? जागते समय आपका दिमाग एक बल्ब जलाने जितनी बिजली पैदा करता है।", "Did you know? While awake, your brain produces enough electricity to power a small bulb."),
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

    /** A cognitive awakening fact item for L2 companion engagement. */
    data class AwakeningFact(
        val id: Int,
        val prompt: Line,
        val defaultAck: Line = Line("अलर्ट हूँ", "I'm alert"),
        val secondaryAck: Line = Line("सुना", "Got it"),
        val maxWaitMs: Long = 5000L
    )

    // 5. Cognitive awakening facts (L2, COM-3) — scientifically accurate, short, stimulating facts
    val AWAKENING_FACTS = listOf(
        // Fact 1: Sunlight to Earth (Space)
        AwakeningFact(
            id = 1,
            prompt = Line(
                // "Surya ka prakash prithvi tak pahunchne mein lagbhag 8 minute 20 second leta hai."
                "सूर्य का प्रकाश पृथ्वी तक पहुँचने में लगभग 8 मिनट 20 सेकंड लेता है।",
                "Sunlight takes about 8 minutes and 20 seconds to reach Earth."
            )
        ),
        // Fact 2: Brain oxygen and energy consumption (Human Brain)
        AwakeningFact(
            id = 2,
            prompt = Line(
                // "Dimaag shareer ka sirf 2% vajan rakhta hai, lekin 20% oxygen aur energy istemaal karta hai."
                "दिमाग शरीर का सिर्फ 2% वजन रखता है, लेकिन 20% ऑक्सीजन और ऊर्जा इस्तेमाल करता है।",
                "Your brain is only 2% of your body weight, but consumes 20% of your oxygen and energy."
            )
        ),
        // Fact 3: Lightning temperature (Nature / Physics)
        AwakeningFact(
            id = 3,
            prompt = Line(
                // "Aakashiy bijli ka taapmaan 30,000 degree Celsius tak ho sakta hai — sooraj ki satah se paanch guna zyada garm."
                "आकाशीय बिजली का तापमान 30,000 डिग्री सेल्सियस तक हो सकता है — सूरज की सतह से पाँच गुना ज़्यादा गर्म।",
                "A lightning bolt can reach 30,000 degrees Celsius — five times hotter than the surface of the Sun."
            )
        ),
        // Fact 4: Sound in water (Everyday Physics)
        AwakeningFact(
            id = 4,
            prompt = Line(
                // "Paani ke andar aawaaz hawa ke mukaable chaar guna zyada tezi se safar karti hai."
                "पानी के अंदर आवाज़ हवा के मुकाबले चार गुना ज़्यादा तेज़ी से सफ़र करती है।",
                "Sound travels more than four times faster through water than through air."
            )
        ),
        // Fact 5: Brain electrical power (Human Brain)
        AwakeningFact(
            id = 5,
            prompt = Line(
                // "Jaagte samay aapka dimaag 12 se 25 watt bijli paida karta hai — ek chhote LED bulb ke barabar."
                "जागते समय आपका दिमाग 12 से 25 वॉट बिजली पैदा करता है — एक छोटे एलईडी बल्ब के बराबर।",
                "While awake, your brain produces 12 to 25 watts of electricity — enough to power a small LED bulb."
            )
        ),
        // Fact 6: Venus rotation (Space)
        AwakeningFact(
            id = 6,
            prompt = Line(
                // "Shukra grah par ek din uske poore ek saal se bhi lamba hota hai."
                "शुक्र ग्रह पर एक दिन उसके पूरे एक साल से भी लंबा होता है।",
                "A single day on Venus is longer than its entire year."
            )
        ),
        // Fact 7: Blue whale heartbeat (Nature / Biology)
        AwakeningFact(
            id = 7,
            prompt = Line(
                // "Blue whale ka dil ek chhoti car jitna bada hota hai, aur uski dhadkan 3 kilometer door tak suni ja sakti hai."
                "ब्लू व्हेल का दिल एक छोटी कार जितना बड़ा होता है, और उसकी धड़कन 3 किलोमीटर दूर तक सुनी जा सकती है।",
                "A blue whale's heart is the size of a small car, and its beat can be heard 3 kilometers away."
            )
        ),
        // Fact 8: Pure honey preservation (Everyday Phenomena / Chemistry)
        AwakeningFact(
            id = 8,
            prompt = Line(
                // "Shuddh shahad kabhi kharab nahi hota. Hazaaron saal puraana shahad bhi bilkul theek rehta hai."
                "शुद्ध शहद कभी खराब नहीं होता। हज़ारों साल पुराना शहद भी बिल्कुल ठीक रहता है।",
                "Pure honey never spoils. Honey preserved for thousands of years remains completely edible."
            )
        ),
        // Fact 9: Blinking resets visual cortex (Human Body / Focus)
        AwakeningFact(
            id = 9,
            prompt = Line(
                // "Palak jhapkaane se dimaag visual cortex ko reset karta hai taaki dhyan bana rahe."
                "पलक झपकाने से दिमाग विजुअल कॉर्टेक्स को रीसेट करता है ताकि ध्यान बना रहे।",
                "Every time you blink, your brain briefly resets your visual cortex to keep your focus sharp."
            )
        ),
        // Fact 10: Earth trees vs galaxy stars (Earth / Nature)
        AwakeningFact(
            id = 10,
            prompt = Line(
                // "Prithvi par kareeb 3 lakh crore ped hain, jo aakashganga ke taaron se bhi zyada hain."
                "पृथ्वी पर करीब 3 लाख करोड़ पेड़ हैं, जो आकाशगंगा के तारों से भी ज़्यादा हैं।",
                "There are roughly 3 trillion trees on Earth, outnumbering stars in the Milky Way."
            )
        ),
        // Fact 11: Moon footprints (Space)
        AwakeningFact(
            id = 11,
            prompt = Line(
                // "Chaand par hawa aur paani na hone se antariksh yaatriyon ke kadmon ke nishaan laakhon saal rahenge."
                "चाँद पर हवा और पानी न होने से अंतरिक्ष यात्रियों के कदमों के निशान लाखों साल रहेंगे।",
                "With no wind or water on the Moon, astronaut footprints will stay for millions of years."
            )
        ),
        // Fact 12: Octopus anatomy (Nature / Marine Biology)
        AwakeningFact(
            id = 12,
            prompt = Line(
                // "Octopus ke paas 3 dil aur 9 dimaag hote hain, aur uska khoon neele rang ka hota hai."
                "ऑक्टोपस के पास 3 दिल और 9 दिमाग होते हैं, और उसका खून नीले रंग का होता है।",
                "An octopus has 3 hearts and 9 brains, and its blood is naturally blue."
            )
        )
    )

    // Legacy reference mapped to awakening facts for compatibility
    val MATH_QUESTIONS = AWAKENING_FACTS.map {
        MathItem(it.id, it.prompt, 70, it.maxWaitMs)
    }

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
