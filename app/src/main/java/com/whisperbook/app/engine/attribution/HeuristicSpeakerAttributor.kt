package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.AttributedPublication
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.SpeakerAttributor
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.Passage

enum class AttributionRule(val evidenceName: String) {
    NARRATION("narration-outside-dialogue"),
    EXPLICIT_AFTER("explicit-after-dialogue"),
    EXPLICIT_BEFORE("explicit-before-dialogue"),
    EXPLICIT_LABEL("explicit-speaker-label"),
    EXPLICIT_EM_DASH("explicit-em-dash-tag"),
    TWO_SPEAKER_CARRY_OVER("two-speaker-carry-over"),
    NARRATOR_FALLBACK("narrator-fallback"),
}

data class SpeakerEvidence(
    val speakerId: String,
    val confidence: Float,
    val rule: AttributionRule,
    val evidence: String,
) {
    fun serializedRule(): String = "${rule.evidenceName}:$evidence"
}

/**
 * Fast, offline character attribution intended as a conservative first pass. Explicit speech tags
 * win. Alternation is used only after exactly two distinct speakers have been established in the
 * current chapter and only across adjacent dialogue paragraphs. Everything else stays Narrator.
 */
class HeuristicSpeakerAttributor(
    private val seeds: List<KnownCharacterSeed> = emptyList(),
) : SpeakerAttributor {
    override suspend fun attribute(
        bookId: String,
        publication: ExtractedPublication,
    ): AttributedPublication {
        val registry = KnownCharacterRegistry(bookId, seeds)
        publication.chapters
            .asSequence()
            .flatMap { it.paragraphs.asSequence() }
            .forEach { paragraph -> SpeechTagMatcher.discover(paragraph).forEach(registry::register) }

        val chapters = publication.chapters.mapIndexed { chapterOrdinal, sourceChapter ->
            val chapterId = "$bookId-chapter-${chapterOrdinal + 1}"
            val scene = ChapterSceneState()
            val passages = mutableListOf<Passage>()

            sourceChapter.paragraphs.forEachIndexed { paragraphIndex, rawParagraph ->
                val paragraph = rawParagraph.trim()
                if (paragraph.isBlank()) return@forEachIndexed
                val spans = DialogueScanner.scan(paragraph)
                if (spans.isEmpty()) {
                    passages += passage(
                        chapterId = chapterId,
                        ordinal = passages.size,
                        text = paragraph,
                        speaker = narratorNarration(),
                    )
                    return@forEachIndexed
                }

                var cursor = 0
                spans.forEach { span ->
                    addNarrationIfPresent(passages, chapterId, paragraph.substring(cursor, span.startInclusive))
                    val explicit = SpeechTagMatcher.attribute(
                        before = paragraph.substring(0, span.startInclusive).takeLast(180),
                        after = paragraph.substring(span.endExclusive).take(180),
                        dialogue = span.content(paragraph),
                        delimiter = span.delimiter,
                        registry = registry,
                    )
                    val evidence = explicit ?: scene.carryOver(paragraphIndex) ?: narratorFallback()
                    val dialogue = span.content(paragraph).trim()
                    if (dialogue.isNotBlank()) {
                        passages += passage(chapterId, passages.size, dialogue, evidence)
                        registry.incrementDialogue(evidence.speakerId)
                        scene.onDialogue(paragraphIndex, evidence)
                    }
                    cursor = span.endExclusive
                }
                addNarrationIfPresent(passages, chapterId, paragraph.substring(cursor))
            }

            Chapter(
                id = chapterId,
                bookId = bookId,
                ordinal = chapterOrdinal,
                title = sourceChapter.title,
                passages = passages,
            )
        }

        return AttributedPublication(chapters = chapters, characters = registry.characters())
    }

    private fun addNarrationIfPresent(
        passages: MutableList<Passage>,
        chapterId: String,
        rawText: String,
    ) {
        val text = rawText.trim().trimStart(',', ';').trim()
        if (text.isNotBlank()) passages += passage(chapterId, passages.size, text, narratorNarration())
    }

    private fun passage(
        chapterId: String,
        ordinal: Int,
        text: String,
        speaker: SpeakerEvidence,
    ) = Passage(
        id = "$chapterId-passage-${ordinal + 1}",
        chapterId = chapterId,
        ordinal = ordinal,
        text = text,
        speakerId = speaker.speakerId,
        confidence = speaker.confidence.coerceIn(0f, 1f),
        attributionRule = speaker.serializedRule(),
    )

    private fun narratorNarration() = SpeakerEvidence(
        speakerId = BuiltInCharacters.NARRATOR_ID,
        confidence = 1f,
        rule = AttributionRule.NARRATION,
        evidence = "prose",
    )

    private fun narratorFallback() = SpeakerEvidence(
        speakerId = BuiltInCharacters.NARRATOR_ID,
        confidence = 0.30f,
        rule = AttributionRule.NARRATOR_FALLBACK,
        evidence = "no-reliable-speaker",
    )
}

private class ChapterSceneState {
    private val explicitSpeakers = linkedSetOf<String>()
    private var previousDialogueSpeaker: String? = null
    private var previousDialogueParagraph: Int? = null

    fun onDialogue(paragraphIndex: Int, evidence: SpeakerEvidence) {
        if (evidence.rule in explicitRules) explicitSpeakers += evidence.speakerId
        previousDialogueSpeaker = evidence.speakerId
        previousDialogueParagraph = paragraphIndex
    }

    fun carryOver(paragraphIndex: Int): SpeakerEvidence? {
        if (explicitSpeakers.size != 2) return null
        val previous = previousDialogueSpeaker ?: return null
        val previousParagraph = previousDialogueParagraph ?: return null
        if (paragraphIndex - previousParagraph > 1 || previous !in explicitSpeakers) return null
        val alternate = explicitSpeakers.firstOrNull { it != previous } ?: return null
        return SpeakerEvidence(
            speakerId = alternate,
            confidence = 0.62f,
            rule = AttributionRule.TWO_SPEAKER_CARRY_OVER,
            evidence = "alternates-after-two-explicit-speakers",
        )
    }

    companion object {
        private val explicitRules = setOf(
            AttributionRule.EXPLICIT_AFTER,
            AttributionRule.EXPLICIT_BEFORE,
            AttributionRule.EXPLICIT_LABEL,
            AttributionRule.EXPLICIT_EM_DASH,
        )
    }
}

private object SpeechTagMatcher {
    private const val VERB = "(?:said|asked|replied|answered|whispered|murmured|cried|called|shouted|yelled|exclaimed|added|continued|declared|remarked|sighed|growled|hissed|stammered|began)"
    private const val NAME_WORD = "[\\p{Lu}][\\p{L}\\p{M}'’\\-]*"
    private const val ARTICLE_NAME = "[Tt]he\\s+[\\p{L}][\\p{L}\\p{M}'’\\-]*"
    private const val NAME = "(?:$ARTICLE_NAME|$NAME_WORD(?:\\s+$NAME_WORD){0,2})"
    private val afterVerb = Regex("(?i:$VERB)\\s+($NAME)")
    private val beforeVerb = Regex("($NAME)\\s+(?i:$VERB)\\b")
    private val rightVerbName = Regex("^\\s*[,;:.!?—-]*\\s*(?i:$VERB)\\s+($NAME)")
    private val rightNameVerb = Regex("^\\s*[,;:.!?—-]*\\s*($NAME)\\s+(?i:$VERB)\\b")
    private val leftNameVerb = Regex("($NAME)\\s+(?i:$VERB)\\s*[,;:]?\\s*$")
    private val leftVerbName = Regex("(?i:$VERB)\\s+($NAME)\\s*[,;:]?\\s*$")
    private val speakerLabel = Regex("($NAME)\\s*:\\s*$")

    fun discover(text: String): List<String> = buildList {
        afterVerb.findAll(text).forEach { add(it.groupValues[1]) }
        beforeVerb.findAll(text).forEach { add(it.groupValues[1]) }
    }.distinct()

    fun attribute(
        before: String,
        after: String,
        dialogue: String,
        delimiter: DialogueDelimiter,
        registry: KnownCharacterRegistry,
    ): SpeakerEvidence? {
        resolveMatch(rightVerbName.find(after), registry)?.let { (id, mention) ->
            return explicit(id, AttributionRule.EXPLICIT_AFTER, "speech-verb-before-$mention")
        }
        resolveMatch(rightNameVerb.find(after), registry)?.let { (id, mention) ->
            return explicit(id, AttributionRule.EXPLICIT_AFTER, "$mention-before-speech-verb")
        }
        resolveMatch(leftNameVerb.find(before), registry)?.let { (id, mention) ->
            return explicit(id, AttributionRule.EXPLICIT_BEFORE, "$mention-before-speech-verb")
        }
        resolveMatch(leftVerbName.find(before), registry)?.let { (id, mention) ->
            return explicit(id, AttributionRule.EXPLICIT_BEFORE, "speech-verb-before-$mention")
        }
        resolveMatch(speakerLabel.find(before), registry)?.let { (id, mention) ->
            return explicit(id, AttributionRule.EXPLICIT_LABEL, "label-$mention")
        }
        if (delimiter == DialogueDelimiter.EM_DASH) {
            val embedded = afterVerb.findAll(dialogue).lastOrNull() ?: beforeVerb.findAll(dialogue).lastOrNull()
            resolveMatch(embedded, registry)?.let { (id, mention) ->
                return SpeakerEvidence(id, 0.90f, AttributionRule.EXPLICIT_EM_DASH, "embedded-tag-$mention")
            }
        }
        return null
    }

    private fun resolveMatch(
        match: MatchResult?,
        registry: KnownCharacterRegistry,
    ): Pair<String, String>? {
        val mention = match?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) ?: return null
        val id = registry.resolve(mention) ?: registry.register(mention) ?: return null
        return id to (registry.displayName(id) ?: mention)
    }

    private fun explicit(id: String, rule: AttributionRule, evidence: String) = SpeakerEvidence(
        speakerId = id,
        confidence = 0.98f,
        rule = rule,
        evidence = evidence,
    )
}
