package com.jaagrit.app.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [NumberAnswerParser] verifying recognition of spoken and text numbers
 * in English, Hindi words, Latin digits, and Devanagari numerals.
 */
class NumberAnswerParserTest {

    @Test
    fun parseLatinDigits() {
        assertEquals(70, NumberAnswerParser.parse("70"))
        assertEquals(100, NumberAnswerParser.parse("100"))
        assertEquals(37, NumberAnswerParser.parse("37"))
        assertEquals(75, NumberAnswerParser.parse("75"))
        assertEquals(42, NumberAnswerParser.parse("42"))
        assertEquals(84, NumberAnswerParser.parse("84"))
    }

    @Test
    fun parseDevanagariNumerals() {
        assertEquals(70, NumberAnswerParser.parse("७०"))
        assertEquals(100, NumberAnswerParser.parse("१००"))
        assertEquals(37, NumberAnswerParser.parse("३७"))
        assertEquals(75, NumberAnswerParser.parse("७५"))
        assertEquals(42, NumberAnswerParser.parse("४२"))
        assertEquals(84, NumberAnswerParser.parse("८४"))
    }

    @Test
    fun parseHindiNumberWords() {
        assertEquals(70, NumberAnswerParser.parse("सत्तर"))
        assertEquals(100, NumberAnswerParser.parse("सौ"))
        assertEquals(37, NumberAnswerParser.parse("सैंतीस"))
        assertEquals(75, NumberAnswerParser.parse("पचहत्तर"))
        assertEquals(42, NumberAnswerParser.parse("बयालीस"))
        assertEquals(84, NumberAnswerParser.parse("चौरासी"))
        assertEquals(5, NumberAnswerParser.parse("पांच"))
        assertEquals(5, NumberAnswerParser.parse("पाँच"))
        assertEquals(10, NumberAnswerParser.parse("दस"))
    }

    @Test
    fun parseRomanizedHindiWords() {
        assertEquals(70, NumberAnswerParser.parse("sattar"))
        assertEquals(100, NumberAnswerParser.parse("sau"))
        assertEquals(37, NumberAnswerParser.parse("saintis"))
        assertEquals(75, NumberAnswerParser.parse("pachattar"))
        assertEquals(42, NumberAnswerParser.parse("bayalis"))
        assertEquals(84, NumberAnswerParser.parse("chaurasi"))
    }

    @Test
    fun parseEnglishNumberWords() {
        assertEquals(70, NumberAnswerParser.parse("seventy"))
        assertEquals(100, NumberAnswerParser.parse("one hundred"))
        assertEquals(37, NumberAnswerParser.parse("thirty seven"))
        assertEquals(75, NumberAnswerParser.parse("seventy five"))
        assertEquals(42, NumberAnswerParser.parse("forty two"))
        assertEquals(84, NumberAnswerParser.parse("eighty four"))
    }

    @Test
    fun parseEmbeddedInSentences() {
        assertEquals(70, NumberAnswerParser.parse("mera jawab 70 hai"))
        assertEquals(37, NumberAnswerParser.parse("bhai saintis bacha"))
        assertEquals(100, NumberAnswerParser.parse("it is one hundred"))
    }

    @Test
    fun parseInvalidInput_returnsNull() {
        assertNull(NumberAnswerParser.parse(""))
        assertNull(NumberAnswerParser.parse("kuch nahi"))
        assertNull(NumberAnswerParser.parse("theek hoon"))
    }
}
