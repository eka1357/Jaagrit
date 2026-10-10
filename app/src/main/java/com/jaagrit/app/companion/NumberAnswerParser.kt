package com.jaagrit.app.companion

import com.jaagrit.app.speech.IntentParser

/**
 * Extracts a spoken number from recognizer text (PHRASES.md §5 answer matching).
 * Accepts digits ("70"), Devanagari digits ("७०"), Hindi number words ("सत्तर", 0-100),
 * a few common romanized Hindi forms, and English number words ("seventy five").
 * A wrong or unparseable answer still counts as a response; only latency matters (COM-3).
 * Pure Kotlin — zero Android dependencies.
 */
object NumberAnswerParser {

    fun parse(text: String): Int? {
        val normalized = IntentParser.normalize(toLatinDigits(text))
        if (normalized.isBlank()) return null

        Regex("\\d+").find(normalized)?.let { return it.value.toIntOrNull() }

        val tokens = normalized.split(" ")
        for (token in tokens) {
            HINDI_WORDS[token]?.let { return it }
            ROMANIZED_WORDS[token]?.let { return it }
        }
        return parseEnglish(tokens)
    }

    private fun toLatinDigits(text: String): String = buildString(text.length) {
        for (ch in text) append(if (ch in '०'..'९') '0' + (ch - '०') else ch)
    }

    private fun parseEnglish(tokens: List<String>): Int? {
        var total = 0
        var current = 0
        var matched = false
        for (token in tokens) {
            val unit = ENGLISH_UNITS[token]
            val ten = ENGLISH_TENS[token]
            when {
                unit != null -> { current += unit; matched = true }
                ten != null -> { current += ten; matched = true }
                token == "hundred" -> { current = (if (current == 0) 1 else current) * 100; matched = true }
                token == "and" && matched -> Unit
                matched -> break
            }
        }
        total += current
        return if (matched) total else null
    }

    private val ENGLISH_UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
        "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )

    private val ENGLISH_TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    // Romanized forms the recognizer may emit for the PHRASES.md §5 answers
    private val ROMANIZED_WORDS = mapOf(
        "sattar" to 70, "sau" to 100, "saintis" to 37, "sentis" to 37,
        "pachattar" to 75, "pachhattar" to 75, "bayalis" to 42, "bayaalis" to 42,
        "chaurasi" to 84, "chourasi" to 84
    )

    // Hindi number words 0-100. Keys are normalized like IntentParser (nukta stripped).
    private val HINDI_WORDS: Map<String, Int> = listOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
        "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
        "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
        "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
        "पचास", "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
        "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
        "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी",
        "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
        "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे",
        "सौ"
    ).mapIndexed { value, word -> IntentParser.normalize(word) to value }.toMap() +
        mapOf(IntentParser.normalize("पांच") to 5, IntentParser.normalize("छः") to 6)
}
