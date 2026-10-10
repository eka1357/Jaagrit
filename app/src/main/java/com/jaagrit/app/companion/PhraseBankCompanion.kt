package com.jaagrit.app.companion

import com.jaagrit.app.engine.CompanionQuestion
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.Lang
import com.jaagrit.app.speech.Phrases
import kotlin.random.Random

/**
 * Deterministic companion brain backed by the PHRASES.md phrase bank (COM-2). No LLM.
 * - Random opener, never the same twice in a row.
 * - The "last time you got sleepy around now" line only when real history exists (COM-6),
 *   and it is used first because it is the most relevant thing to say.
 * - Math questions carry their own max wait (D2) plus on-screen quick-answer choices.
 */
class PhraseBankCompanion(
    private val config: Config = Config.DEFAULT,
    private val random: Random = Random.Default
) : CompanionBrain {

    private var lastOpenerIndex = -1
    private var lastQuestionIndex = -1
    private var patternLineUsed = false

    override fun opener(ctx: CompanionContext): String {
        val index = if (ctx.hasSleepyHistoryNow && !patternLineUsed) {
            patternLineUsed = true
            Phrases.PATTERN_OPENER_INDEX
        } else {
            val candidates = Phrases.OPENERS.indices.filter {
                it != Phrases.PATTERN_OPENER_INDEX && it != lastOpenerIndex
            }
            candidates[random.nextInt(candidates.size)]
        }
        lastOpenerIndex = index
        return Phrases.OPENERS[index].pick(ctx.lang)
    }

    override fun question(ctx: CompanionContext): CompanionQuestion {
        val candidates = Phrases.MATH_QUESTIONS.indices.filter { it != lastQuestionIndex }
        val index = candidates[random.nextInt(candidates.size)]
        lastQuestionIndex = index
        val item = Phrases.MATH_QUESTIONS[index]
        return CompanionQuestion(
            id = item.id,
            prompt = item.prompt.pick(ctx.lang),
            answer = item.answer.toString(),
            maxWaitMs = item.maxWaitMs.takeIf { it > 0L } ?: config.defaultMathMaxWaitMs,
            choices = choicesFor(item.answer)
        )
    }

    /** Correct answer plus nearby distractors (common slips of 5 and 10), shuffled. */
    private fun choicesFor(answer: Int): List<String> {
        val distractors = listOf(answer + 10, answer - 10, answer + 5, answer - 5)
            .filter { it >= 0 && it != answer }
            .distinct()
            .take((config.mathChoiceCount - 1).coerceAtLeast(0))
        return (distractors + answer).shuffled(random).map { it.toString() }
    }
}

fun Phrases.Line.pick(lang: Lang): String = if (lang == Lang.EN) en else hi
