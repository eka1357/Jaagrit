package com.jaagrit.app.speech

/**
 * Driver intents recognized from voice commands or button fallback (M8, VOI-2, VOI-3).
 * Pure Kotlin — zero Android dependencies (AGENTS.md Rule 2).
 */
enum class DriverIntent {
    DRIVE_TIME,
    ALERTNESS,
    ALERT_COUNT,
    LAST_ALERT,
    RECALIBRATE,
    DISMISS,
    UNKNOWN
}

/**
 * Deterministic keyword-set matching parser for driver voice input.
 * Accepts Devanagari, romanized (Hinglish), and English phrases per docs/PHRASES.md (sections 6 & 8).
 * Pure Kotlin — zero Android dependencies.
 */
object IntentParser {

    /**
     * Normalizes input speech text:
     * - Lowercases text
     * - Unifies Devanagari nukta forms (precomposed and combining nukta U+093C)
     * - Strips punctuation and non-alphanumeric symbols
     * - Collapses whitespace
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val s = text.lowercase()

        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when (ch) {
                // Precomposed nukta forms -> base Devanagari consonants
                '\u0958' -> sb.append('क')
                '\u0959' -> sb.append('ख')
                '\u095A' -> sb.append('ग')
                '\u095B' -> sb.append('ज')
                '\u095C' -> sb.append('ड')
                '\u095D' -> sb.append('ढ')
                '\u095E' -> sb.append('फ')
                '\u095F' -> sb.append('य')
                // Combining nukta mark U+093C -> skip
                '\u093C' -> { /* skip */ }
                else -> {
                    if (ch.isLetterOrDigit() || ch.isWhitespace()) {
                        sb.append(ch)
                    } else {
                        sb.append(' ')
                    }
                }
            }
            i++
        }

        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Parses normalized or raw voice recognition text into a [DriverIntent].
     */
    fun parse(text: String): DriverIntent {
        val normalized = normalize(text)
        if (normalized.isBlank()) return DriverIntent.UNKNOWN

        // 1. RECALIBRATE
        if (matchesAny(normalized, RECALIBRATE_PATTERNS)) {
            return DriverIntent.RECALIBRATE
        }

        // 2. LAST_ALERT (Checked before ALERT_COUNT so "last alert" takes precedence over generic "alert")
        if (matchesAny(normalized, LAST_ALERT_PATTERNS)) {
            return DriverIntent.LAST_ALERT
        }

        // 3. ALERT_COUNT
        if (matchesAny(normalized, ALERT_COUNT_PATTERNS)) {
            return DriverIntent.ALERT_COUNT
        }

        // 4. DRIVE_TIME
        if (matchesAny(normalized, DRIVE_TIME_PATTERNS)) {
            return DriverIntent.DRIVE_TIME
        }

        // 5. ALERTNESS
        if (matchesAny(normalized, ALERTNESS_PATTERNS)) {
            return DriverIntent.ALERTNESS
        }

        // 6. DISMISS (with explicit safety exclusion: "theek hoon" is NEVER a dismiss)
        if (matchesAny(normalized, DISMISS_PATTERNS)) {
            if (isExcludedFromDismiss(normalized)) {
                return DriverIntent.UNKNOWN
            }
            return DriverIntent.DISMISS
        }

        return DriverIntent.UNKNOWN
    }

    /**
     * Checks if text contains reassurance phrases like "theek hoon / I'm fine".
     * Per docs/PHRASES.md Section 6: "theek hoon / ठीक हूँ / I'm fine" is NEVER a dismiss word.
     */
    private fun isExcludedFromDismiss(normalized: String): Boolean {
        return matchesAny(normalized, NOT_DISMISS_PATTERNS)
    }

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        val padded = " $text "
        for (pattern in patterns) {
            val normalizedPattern = normalize(pattern)
            if (normalizedPattern.isBlank()) continue
            if (padded.contains(" $normalizedPattern ")) {
                return true
            }
        }
        return false
    }

    // --- Pattern Definitions from docs/PHRASES.md (Sections 6 & 8) ---

    private val RECALIBRATE_PATTERNS = listOf(
        "रीकैलिब्रेट करो",
        "रीकैलिब्रेट",
        "रीकेलिब्रेट",
        "कैलिब्रेट करो",
        "कैलिब्रेशन करो",
        "कैलिब्रेट",
        "कैलिब्रेशन",
        "recalibrate",
        "re calibrate",
        "recalibrate karo",
        "calibrate karo",
        "calibrate",
        "calibration"
    )

    private val LAST_ALERT_PATTERNS = listOf(
        "आखिरी अलर्ट कब आया",
        "आखिरी अलर्ट कब",
        "आखिरी अलर्ट",
        "पिछला अलर्ट कब आया",
        "पिछला अलर्ट",
        "आखिरी बार अलर्ट",
        "aakhiri alert kab aaya",
        "akhiri alert kab aaya",
        "aakhri alert kab aaya",
        "akhri alert kab aaya",
        "aakhiri alert",
        "akhiri alert",
        "aakhri alert",
        "akhri alert",
        "pichhla alert",
        "pichla alert",
        "last alert",
        "latest alert",
        "previous alert",
        "when was the last alert",
        "time since last alert"
    )

    private val ALERT_COUNT_PATTERNS = listOf(
        "आज कितने अलर्ट आए",
        "कितने अलर्ट आए",
        "कितने अलर्ट",
        "आज के अलर्ट",
        "अलर्ट काउंट",
        "कुल अलर्ट",
        "aaj kitne alert aaye",
        "kitne alert aaye",
        "kitne alert",
        "kitne alerts",
        "aaj ke alert",
        "alert count",
        "how many alerts today",
        "how many alerts",
        "alerts today",
        "alert count",
        "number of alerts",
        "total alerts"
    )

    private val DRIVE_TIME_PATTERNS = listOf(
        "कितनी देर से ड्राइव कर रहा हूँ",
        "कितनी देर से ड्राइव",
        "कितनी देर",
        "कितना टाइम हो गया",
        "कितना टाइम",
        "कितने समय से",
        "ड्राइव टाइम",
        "ड्राइव का समय",
        "kitni der se drive kar raha hoon",
        "kitni der se drive",
        "kitni der",
        "kitna time ho gaya",
        "kitna time",
        "kitne samay",
        "drive time",
        "driving time",
        "how long have i been driving",
        "how long have i driven",
        "how long",
        "been driving",
        "time driving"
    )

    private val ALERTNESS_PATTERNS = listOf(
        "मेरा अलर्टनेस कैसा है",
        "अलर्टनेस कैसा है",
        "अलर्टनेस कैसा",
        "अलर्टनेस स्कोर",
        "अलर्टनेस",
        "सतर्कता कैसी है",
        "सतर्कता",
        "mera alertness kaisa hai",
        "alertness kaisa hai",
        "alertness kaisa",
        "alertness level",
        "alertness score",
        "alertness",
        "hows my alertness",
        "how is my alertness",
        "my alertness",
        "alertness status"
    )

    private val NOT_DISMISS_PATTERNS = listOf(
        "theek hoon",
        "theek hu",
        "theek hun",
        "thik hoon",
        "thik hu",
        "thik hun",
        "theek hai",
        "thik hai",
        "theek",
        "thik",
        "ठीक हूँ",
        "ठीक हु",
        "ठीक हू",
        "ठीक है",
        "सब ठीक है",
        "सब ठीक",
        "sab theek hai",
        "sab theek",
        "i m fine",
        "im fine",
        "i am fine",
        "all good",
        "fine"
    )

    private val DISMISS_PATTERNS = listOf(
        // Hindi (PHRASES.md Section 6)
        "चुप रहो",
        "चुप हो जा",
        "चुप होजा",
        "चुप बैठो",
        "चुप",
        "बंद करो",
        "बस करो",
        "रहने दे",
        "रहने दो",
        "मत बोल",
        "शांत रहो",
        "शांत हो जाओ",
        // Hinglish
        "chup raho",
        "chup ho ja",
        "chup hoja",
        "chup",
        "band karo",
        "bas karo",
        "rehne de",
        "rahne de",
        "rehne do",
        "rahne do",
        "mat bol",
        "shant raho",
        // English
        "stop",
        "quiet",
        "shut up",
        "leave it",
        "silence",
        "be quiet"
    )
}
