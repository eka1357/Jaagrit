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

    // 10. System messages
    // "Camere se chehra dikhayi nahi de raha. Phone adjust karo." (ENG-4, face lost 30 s)
    const val FACE_LOST_30S = "कैमरे से चेहरा दिखाई नहीं दे रहा। फोन एडजस्ट करो।"

    // "Jaagrit chaalu hai. Surakshit chalao."
    const val DRIVE_STARTED = "जागृत चालू है। सुरक्षित चलाओ।"

    // "Kayi baar galat alert aaye. Recalibrate karna chahoge?"
    const val TOO_MANY_FALSE_ALERTS = "कई बार गलत अलर्ट आए। रीकैलिब्रेट करना चाहोगे?"
}
