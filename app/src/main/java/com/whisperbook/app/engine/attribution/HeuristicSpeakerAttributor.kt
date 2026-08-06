package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.AttributedPublication
import com.whisperbook.app.domain.ExtractedChapter
import com.whisperbook.app.domain.ExtractedPublication
import com.whisperbook.app.domain.PassageTextChunker
import com.whisperbook.app.domain.SpeakerAttributor
import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.Chapter
import com.whisperbook.app.domain.model.Passage
import com.whisperbook.app.domain.model.StoryCharacter

enum class AttributionRule(val evidenceName: String) {
    NARRATION("narration-outside-dialogue"),
    EXPLICIT_AFTER("explicit-after-dialogue"),
    EXPLICIT_BEFORE("explicit-before-dialogue"),
    EXPLICIT_LABEL("explicit-speaker-label"),
    EXPLICIT_EM_DASH("explicit-em-dash-tag"),
    FIRST_PERSON_NARRATOR("first-person-narrator-tag"),
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
        val paragraphs = publication.chapters
            .asSequence()
            .flatMap { it.paragraphs.asSequence() }
            .toList()
        val narration = prepareRegistry(registry, paragraphs)

        val chapters = publication.chapters.mapIndexed { chapterOrdinal, sourceChapter ->
            attributeChapter(
                bookId = bookId,
                chapterId = "$bookId-chapter-${chapterOrdinal + 1}",
                chapterOrdinal = chapterOrdinal,
                sourceChapter = sourceChapter,
                registry = registry,
                narration = narration,
            )
        }

        return AttributedPublication(chapters = chapters, characters = registry.characters())
    }

    override suspend fun attributeChapter(
        bookId: String,
        chapterId: String,
        chapterOrdinal: Int,
        chapter: ExtractedChapter,
        knownCharacters: List<StoryCharacter>,
    ): AttributedPublication {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(chapterOrdinal >= 0) { "chapterOrdinal must be non-negative" }

        val persistedSeeds = knownCharacters
            .asSequence()
            .filter { it.bookId == bookId }
            .sortedBy(StoryCharacter::id)
            .map(KnownCharacterSeed::from)
            .toList()
        val registry = KnownCharacterRegistry(bookId, persistedSeeds + seeds)
        val paragraphs = chapter.paragraphs.toList()
        val narration = prepareRegistry(registry, paragraphs)
        val attributedChapter = attributeChapter(
            bookId = bookId,
            chapterId = chapterId,
            chapterOrdinal = chapterOrdinal,
            sourceChapter = chapter,
            registry = registry,
            narration = narration,
        )
        return AttributedPublication(
            chapters = listOf(attributedChapter),
            characters = registry.characters(),
        )
    }

    private fun prepareRegistry(
        registry: KnownCharacterRegistry,
        paragraphs: List<String>,
    ): NarrationAnalysis {
        val narration = CharacterProfileInferencer.analyzeNarration(paragraphs)
        narration.narratorIdentity?.let(registry::addNarratorAlias)
        paragraphs.forEach { paragraph ->
            SpeechTagMatcher.discover(paragraph).forEach(registry::register)
        }
        val profileTargets = registry.profileTargets()
        val inferenceTargets = if (registry.narratorId == BuiltInCharacters.NARRATOR_ID) {
            profileTargets
        } else {
            profileTargets.map { target ->
                if (target.id == registry.narratorId) {
                    target.copy(id = BuiltInCharacters.NARRATOR_ID)
                } else {
                    target
                }
            }
        }
        CharacterProfileInferencer.infer(paragraphs, inferenceTargets, narration).forEach { (id, profile) ->
            registry.applyProfile(
                id = if (id == BuiltInCharacters.NARRATOR_ID) registry.narratorId else id,
                profile = profile,
            )
        }
        return narration
    }

    private fun attributeChapter(
        bookId: String,
        chapterId: String,
        chapterOrdinal: Int,
        sourceChapter: ExtractedChapter,
        registry: KnownCharacterRegistry,
        narration: NarrationAnalysis,
    ): Chapter {
        val scene = ChapterSceneState()
        val passages = mutableListOf<Passage>()

        sourceChapter.paragraphs.forEachIndexed { paragraphIndex, rawParagraph ->
            val paragraph = rawParagraph.trim()
            if (paragraph.isBlank()) return@forEachIndexed
            val spans = DialogueScanner.scan(paragraph)
            if (spans.isEmpty()) {
                addPassages(
                    passages = passages,
                    chapterId = chapterId,
                    rawText = paragraph,
                    speaker = narratorNarration(registry.narratorId),
                )
                return@forEachIndexed
            }

            var cursor = 0
            spans.forEachIndexed { dialogueIndex, span ->
                addNarrationIfPresent(
                    passages,
                    chapterId,
                    paragraph.substring(cursor, span.startInclusive),
                    registry.narratorId,
                )
                val explicit = SpeechTagMatcher.attribute(
                    before = paragraph.substring(0, span.startInclusive).takeLast(180),
                    after = paragraph.substring(span.endExclusive).take(180),
                    dialogue = span.content(paragraph),
                    delimiter = span.delimiter,
                    registry = registry,
                    firstPersonNarrator = narration.perspective ==
                        com.whisperbook.app.domain.model.NarrationPerspective.FIRST_PERSON,
                )
                val evidence = explicit ?: scene.carryOver(paragraphIndex)
                    ?: narratorFallback(registry.narratorId)
                val dialogue = span.content(paragraph).trim()
                if (dialogue.isNotBlank()) {
                    // One dialogue span can be split into several bounded passages. Persist a
                    // shared deterministic unit marker so per-chapter metadata can recover the
                    // original dialogue-line count without double-counting those chunks.
                    val persistedEvidence = evidence.copy(
                        evidence = "${evidence.evidence};dialogue-unit-$paragraphIndex-$dialogueIndex",
                    )
                    addPassages(passages, chapterId, dialogue, persistedEvidence)
                    registry.incrementDialogue(evidence.speakerId)
                    scene.onDialogue(paragraphIndex, evidence)
                }
                cursor = span.endExclusive
            }
            addNarrationIfPresent(
                passages,
                chapterId,
                paragraph.substring(cursor),
                registry.narratorId,
            )
        }

        return Chapter(
            id = chapterId,
            bookId = bookId,
            ordinal = chapterOrdinal,
            title = sourceChapter.title,
            passages = passages,
        )
    }

    private fun addNarrationIfPresent(
        passages: MutableList<Passage>,
        chapterId: String,
        rawText: String,
        narratorId: String,
    ) {
        val text = rawText.trim().trimStart(',', ';').trim()
        if (text.isNotBlank()) addPassages(passages, chapterId, text, narratorNarration(narratorId))
    }

    private fun addPassages(
        passages: MutableList<Passage>,
        chapterId: String,
        rawText: String,
        speaker: SpeakerEvidence,
    ) {
        PassageTextChunker.split(rawText).forEach { text ->
            val ordinal = passages.size
            passages += Passage(
                id = "$chapterId-passage-${ordinal + 1}",
                chapterId = chapterId,
                ordinal = ordinal,
                text = text,
                speakerId = speaker.speakerId,
                confidence = speaker.confidence.coerceIn(0f, 1f),
                attributionRule = speaker.serializedRule(),
            )
        }
    }

    private fun narratorNarration(narratorId: String) = SpeakerEvidence(
        speakerId = narratorId,
        confidence = 1f,
        rule = AttributionRule.NARRATION,
        evidence = "prose",
    )

    private fun narratorFallback(narratorId: String) = SpeakerEvidence(
        speakerId = narratorId,
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
            AttributionRule.FIRST_PERSON_NARRATOR,
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
    private val rightFirstPersonVerb = Regex("^\\s*[,;:.!?—-]*\\s*I\\s+(?i:$VERB)\\b")
    private val rightVerbFirstPerson = Regex("^\\s*[,;:.!?—-]*\\s*(?i:$VERB)\\s+I\\b")
    private val leftFirstPersonVerb = Regex("I\\s+(?i:$VERB)\\s*[,;:]?\\s*$")
    private val leftVerbFirstPerson = Regex("(?i:$VERB)\\s+I\\s*[,;:]?\\s*$")

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
        firstPersonNarrator: Boolean,
    ): SpeakerEvidence? {
        if (
            firstPersonNarrator &&
            (
                rightFirstPersonVerb.containsMatchIn(after) ||
                    rightVerbFirstPerson.containsMatchIn(after) ||
                    leftFirstPersonVerb.containsMatchIn(before) ||
                    leftVerbFirstPerson.containsMatchIn(before)
            )
        ) {
            return SpeakerEvidence(
                speakerId = registry.narratorId,
                confidence = 0.98f,
                rule = AttributionRule.FIRST_PERSON_NARRATOR,
                evidence = "first-person-speech-tag",
            )
        }
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
