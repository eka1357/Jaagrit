package com.jaagrit.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Table-driven unit tests for [IntentParser] (M8, VOI-2, VOI-3).
 * Verifies Devanagari, Hinglish, English, spelling variants, nukta stripping,
 * dismiss exclusions, and negative noise cases.
 */
class IntentParserTest {

    @Test
    fun testPhrasesDocSection8_DriveTime() {
        val testCases = listOf(
            "कितनी देर से ड्राइव कर रहा हूँ?" to DriverIntent.DRIVE_TIME,
            "कितनी देर" to DriverIntent.DRIVE_TIME,
            "कितना टाइम हो गया" to DriverIntent.DRIVE_TIME,
            "ड्राइव टाइम कितना है" to DriverIntent.DRIVE_TIME,
            "kitni der se drive kar raha hoon" to DriverIntent.DRIVE_TIME,
            "kitni der" to DriverIntent.DRIVE_TIME,
            "kitna time ho gaya" to DriverIntent.DRIVE_TIME,
            "drive time" to DriverIntent.DRIVE_TIME,
            "How long have I been driving?" to DriverIntent.DRIVE_TIME,
            "how long" to DriverIntent.DRIVE_TIME,
            "driving time" to DriverIntent.DRIVE_TIME,
            "how long have i been driving" to DriverIntent.DRIVE_TIME
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testPhrasesDocSection8_Alertness() {
        val testCases = listOf(
            "मेरा अलर्टनेस कैसा है?" to DriverIntent.ALERTNESS,
            "अलर्टनेस कैसा है" to DriverIntent.ALERTNESS,
            "अलर्टनेस स्कोर क्या है" to DriverIntent.ALERTNESS,
            "सतर्कता कैसी है" to DriverIntent.ALERTNESS,
            "mera alertness kaisa hai" to DriverIntent.ALERTNESS,
            "alertness kaisa hai" to DriverIntent.ALERTNESS,
            "alertness" to DriverIntent.ALERTNESS,
            "How's my alertness?" to DriverIntent.ALERTNESS,
            "how is my alertness" to DriverIntent.ALERTNESS,
            "my alertness level" to DriverIntent.ALERTNESS,
            "alertness score" to DriverIntent.ALERTNESS
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testPhrasesDocSection8_AlertCount() {
        val testCases = listOf(
            "आज कितने अलर्ट आए?" to DriverIntent.ALERT_COUNT,
            "कितने अलर्ट आए" to DriverIntent.ALERT_COUNT,
            "आज कितने अलर्ट" to DriverIntent.ALERT_COUNT,
            "आज के अलर्ट" to DriverIntent.ALERT_COUNT,
            "अलर्ट काउंट कितना है" to DriverIntent.ALERT_COUNT,
            "aaj kitne alert aaye" to DriverIntent.ALERT_COUNT,
            "kitne alert aaye" to DriverIntent.ALERT_COUNT,
            "kitne alerts" to DriverIntent.ALERT_COUNT,
            "alert count" to DriverIntent.ALERT_COUNT,
            "How many alerts today?" to DriverIntent.ALERT_COUNT,
            "how many alerts" to DriverIntent.ALERT_COUNT,
            "alerts today" to DriverIntent.ALERT_COUNT,
            "total alerts today" to DriverIntent.ALERT_COUNT
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testPhrasesDocSection8_LastAlert() {
        val testCases = listOf(
            "आख़िरी अलर्ट कब आया?" to DriverIntent.LAST_ALERT,
            "आखिरी अलर्ट कब आया" to DriverIntent.LAST_ALERT, // without nukta
            "आखिरी अलर्ट" to DriverIntent.LAST_ALERT,
            "पिछला अलर्ट कब आया" to DriverIntent.LAST_ALERT,
            "पिछला अलर्ट" to DriverIntent.LAST_ALERT,
            "aakhiri alert kab aaya" to DriverIntent.LAST_ALERT,
            "akhiri alert kab aaya" to DriverIntent.LAST_ALERT,
            "last alert kab aaya" to DriverIntent.LAST_ALERT,
            "last alert" to DriverIntent.LAST_ALERT,
            "When was the last alert?" to DriverIntent.LAST_ALERT,
            "time since last alert" to DriverIntent.LAST_ALERT,
            "latest alert" to DriverIntent.LAST_ALERT
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testPhrasesDocSection8_Recalibrate() {
        val testCases = listOf(
            "रीकैलिब्रेट करो" to DriverIntent.RECALIBRATE,
            "रीकैलिब्रेट" to DriverIntent.RECALIBRATE,
            "रीकेलिब्रेट" to DriverIntent.RECALIBRATE,
            "कैलिब्रेट करो" to DriverIntent.RECALIBRATE,
            "कैलिब्रेशन करो" to DriverIntent.RECALIBRATE,
            "recalibrate karo" to DriverIntent.RECALIBRATE,
            "recalibrate" to DriverIntent.RECALIBRATE,
            "re-calibrate" to DriverIntent.RECALIBRATE,
            "calibrate please" to DriverIntent.RECALIBRATE,
            "Recalibrate" to DriverIntent.RECALIBRATE
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testPhrasesDocSection6_Dismiss() {
        val testCases = listOf(
            "चुप रहो" to DriverIntent.DISMISS,
            "चुप हो जा" to DriverIntent.DISMISS,
            "चुप बैठो" to DriverIntent.DISMISS,
            "चुप" to DriverIntent.DISMISS,
            "बंद करो" to DriverIntent.DISMISS,
            "बस करो" to DriverIntent.DISMISS,
            "रहने दे" to DriverIntent.DISMISS,
            "रहने दो" to DriverIntent.DISMISS,
            "मत बोल" to DriverIntent.DISMISS,
            "शांत रहो" to DriverIntent.DISMISS,
            "chup raho" to DriverIntent.DISMISS,
            "chup ho ja" to DriverIntent.DISMISS,
            "chup" to DriverIntent.DISMISS,
            "band karo" to DriverIntent.DISMISS,
            "bas karo" to DriverIntent.DISMISS,
            "rehne de" to DriverIntent.DISMISS,
            "rehne do" to DriverIntent.DISMISS,
            "rahne de" to DriverIntent.DISMISS,
            "mat bol" to DriverIntent.DISMISS,
            "stop" to DriverIntent.DISMISS,
            "quiet" to DriverIntent.DISMISS,
            "shut up" to DriverIntent.DISMISS,
            "leave it" to DriverIntent.DISMISS,
            "be quiet" to DriverIntent.DISMISS,
            "silence" to DriverIntent.DISMISS
        )

        for ((input, expected) in testCases) {
            assertEquals("Failed for: '$input'", expected, IntentParser.parse(input))
        }
    }

    @Test
    fun testHardRule_TheekHoonIsNeverDismiss() {
        // Hard rule from PHRASES.md section 6:
        // "theek hoon / ठीक हूँ / I'm fine" is NEVER a dismiss word.
        val nonDismissCases = listOf(
            "theek hoon",
            "theek hu",
            "theek hun",
            "thik hoon",
            "thik hu",
            "main theek hoon",
            "bhai theek hoon",
            "ठीक हूँ",
            "ठीक हु",
            "मैं ठीक हूँ",
            "सब ठीक है",
            "theek hai",
            "thik hai",
            "I'm fine",
            "im fine",
            "i am fine",
            "all good",
            // Even if a dismiss word is combined with theek hoon:
            "theek hoon chup raho",
            "chup theek hoon"
        )

        for (input in nonDismissCases) {
            val parsed = IntentParser.parse(input)
            assertNotEquals("Input '$input' must NEVER parse as DISMISS", DriverIntent.DISMISS, parsed)
            assertEquals("Input '$input' should be UNKNOWN", DriverIntent.UNKNOWN, parsed)
        }
    }

    @Test
    fun testNegativeCases_ReturnUnknown() {
        val unknownCases = listOf(
            "",
            "   ",
            "hmm",
            "haan",
            "accha",
            "kuch nahi",
            "what is the weather today",
            "play music",
            "call home",
            "nonstop driving", // "nonstop" must not match "stop"
            "quietly moving"   // partial word must not match "quiet"
        )

        for (input in unknownCases) {
            assertEquals("Failed for: '$input'", DriverIntent.UNKNOWN, IntentParser.parse(input))
        }
    }

    @Test
    fun testPunctuationAndNuktaNormalization() {
        // With nukta: ख़ (U+0959) or combining (ख + U+093C)
        val withNukta = "आख़िरी अलर्ट कब आया??!"
        assertEquals(DriverIntent.LAST_ALERT, IntentParser.parse(withNukta))

        val withPunctuation = "\"Kitni der?\", how long?!"
        assertEquals(DriverIntent.DRIVE_TIME, IntentParser.parse(withPunctuation))
    }
}
