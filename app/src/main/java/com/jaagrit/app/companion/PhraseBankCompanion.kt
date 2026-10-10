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

    private val recentFactIndices = ArrayDeque<Int>()

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
        val facts = Phrases.AWAKENING_FACTS
        val maxRecent = (facts.size / 2).coerceAtLeast(1)
        val candidates = facts.indices.filter { it !in recentFactIndices }
        val index = if (candidates.isNotEmpty()) {
            candidates[random.nextInt(candidates.size)]
        } else {
            recentFactIndices.clear()
            val fallbackCandidates = facts.indices.filter { it != lastQuestionIndex }
            if (fallbackCandidates.isNotEmpty()) {
                fallbackCandidates[random.nextInt(fallbackCandidates.size)]
            } else {
                0
            }
        }

        recentFactIndices.addLast(index)
        if (recentFactIndices.size > maxRecent) {
            recentFactIndices.removeFirst()
        }
        lastQuestionIndex = index

        val item = facts[index]
        val promptText = item.prompt.pick(ctx.lang)
        val defaultAck = item.defaultAck.pick(ctx.lang)
        val secondaryAck = item.secondaryAck.pick(ctx.lang)
        val choices = listOf(defaultAck, secondaryAck)

        return CompanionQuestion(
            id = item.id,
            prompt = promptText,
            answer = defaultAck,
            maxWaitMs = item.maxWaitMs.takeIf { it > 0L } ?: config.defaultMathMaxWaitMs,
            choices = choices
        )
    }
}

fun Phrases.Line.pick(lang: Lang): String = if (lang == Lang.EN) en else hi
