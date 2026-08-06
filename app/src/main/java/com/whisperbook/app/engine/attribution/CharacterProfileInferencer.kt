package com.whisperbook.app.engine.attribution

import com.whisperbook.app.domain.model.BuiltInCharacters
import com.whisperbook.app.domain.model.CharacterAgeGroup
import com.whisperbook.app.domain.model.CharacterGender
import com.whisperbook.app.domain.model.NarrationPerspective
import java.util.Locale

internal data class CharacterProfileTarget(
    val id: String,
    val displayName: String,
    val aliases: Set<String>,
)

internal data class InferredCharacterProfile(
    val gender: CharacterGender = CharacterGender.UNKNOWN,
    val genderConfidence: Float = 0f,
    val ageGroup: CharacterAgeGroup = CharacterAgeGroup.UNKNOWN,
    val ageConfidence: Float = 0f,
    val narrationPerspective: NarrationPerspective = NarrationPerspective.UNKNOWN,
    val perspectiveConfidence: Float = 0f,
    val narratorIdentity: String? = null,
)

internal data class NarrationAnalysis(
    val perspective: NarrationPerspective,
    val confidence: Float,
    val narratorIdentity: String?,
    val proseParagraphs: List<String>,
)

/**
 * Conservative, English-language metadata inference for automatic voice casting.
 *
 * Names never imply gender or age. A single weak pronoun is insufficient; explicit self
 * descriptions, ages, relationship nouns, or repeated unambiguous pronouns must support a label.
 */
internal object CharacterProfileInferencer {
    fun analyzeNarration(paragraphs: List<String>): NarrationAnalysis {
        val prose = sampleNarrationProse(paragraphs)
        val proseText = prose.joinToString("\n")
        val identity = narratorIdentity(proseText)
        val firstPersonCount = FIRST_PERSON_TOKEN.findAll(proseText).count()
        val thirdPersonCount = THIRD_PERSON_SUBJECT.findAll(proseText).count()
        val wordCount = WORD.findAll(proseText).count()
        val strongFirstPerson = identity != null || FIRST_PERSON_SELF_DESCRIPTION.containsMatchIn(proseText) ||
            FIRST_PERSON_SPEECH_TAG.containsMatchIn(proseText)

        val perspective = when {
            strongFirstPerson || firstPersonCount >= 2 -> NarrationPerspective.FIRST_PERSON
            wordCount >= 20 && firstPersonCount == 0 && thirdPersonCount >= 1 -> NarrationPerspective.THIRD_PERSON
            wordCount >= 40 && firstPersonCount == 0 -> NarrationPerspective.THIRD_PERSON
            else -> NarrationPerspective.UNKNOWN
        }
        val confidence = when (perspective) {
            NarrationPerspective.FIRST_PERSON -> (
                0.68f + (firstPersonCount.coerceAtMost(6) * 0.045f) + if (identity != null) 0.08f else 0f
                ).coerceAtMost(0.97f)
            NarrationPerspective.THIRD_PERSON ->
                (0.66f + thirdPersonCount.coerceAtMost(5) * 0.04f).coerceAtMost(0.88f)
            NarrationPerspective.UNKNOWN -> 0f
        }
        return NarrationAnalysis(perspective, confidence, identity, prose)
    }

    private fun sampleNarrationProse(paragraphs: List<String>): List<String> {
        val prose = ArrayList<String>(NARRATION_SAMPLE_MAX_PARAGRAPHS)
        var sampledCharacters = 0
        for (paragraph in paragraphs) {
            if (
                prose.size >= NARRATION_SAMPLE_MAX_PARAGRAPHS ||
                sampledCharacters >= NARRATION_SAMPLE_MAX_CHARACTERS
            ) {
                break
            }
            val candidate = proseOnly(paragraph)
            if (candidate.isBlank()) continue
            val remainingCharacters = NARRATION_SAMPLE_MAX_CHARACTERS - sampledCharacters
            val bounded = candidate.take(remainingCharacters)
            if (bounded.isBlank()) continue
            prose += bounded
            sampledCharacters += bounded.length
        }
        return prose
    }

    fun infer(
        paragraphs: List<String>,
        targets: List<CharacterProfileTarget>,
        narration: NarrationAnalysis,
    ): Map<String, InferredCharacterProfile> {
        val accumulators = targets.associate { target -> target.id to ProfileAccumulator() }
        val namedProfileIndex = NamedProfileIndex.build(targets)
        paragraphs.forEach { paragraph ->
            inferNamedProfiles(paragraph, namedProfileIndex, accumulators)
        }

        if (narration.perspective == NarrationPerspective.FIRST_PERSON) {
            val narrator = accumulators[BuiltInCharacters.NARRATOR_ID]
            narration.proseParagraphs.forEach { paragraph -> inferFirstPersonProfile(paragraph, narrator) }
        }

        return targets.associate { target ->
            val resolved = accumulators.getValue(target.id).resolve()
            target.id to if (target.id == BuiltInCharacters.NARRATOR_ID) {
                resolved.copy(
                    narrationPerspective = narration.perspective,
                    perspectiveConfidence = narration.confidence,
                    narratorIdentity = narration.narratorIdentity,
                )
            } else {
                resolved
            }
        }
    }

    private fun inferNamedProfiles(
        paragraph: String,
        index: NamedProfileIndex,
        accumulators: Map<String, ProfileAccumulator>,
    ) {
        var lastSingleMention: IndexedTarget? = null
        splitSentences(paragraph).forEach { sentence ->
            val mentioned = index.findMentions(sentence)
            if (mentioned.size == 1) {
                val target = mentioned.single()
                val accumulator = accumulators.getValue(target.target.target.id)
                inferDirectNamedDescription(sentence, target.mentions, accumulator)
                inferDeclaredPronouns(sentence, target.firstMentionEnd, accumulator)
                inferWeakPronounsAfterMention(sentence, target.firstMentionEnd, accumulator)
                lastSingleMention = target.target
            } else if (mentioned.isEmpty()) {
                val previous = lastSingleMention
                if (previous != null) {
                    val accumulator = accumulators.getValue(previous.target.id)
                    when {
                        STARTS_FEMININE_PRONOUN.containsMatchIn(sentence) ->
                            accumulator.gender.add(CharacterGender.FEMALE, 0.52f)
                        STARTS_MASCULINE_PRONOUN.containsMatchIn(sentence) ->
                            accumulator.gender.add(CharacterGender.MALE, 0.52f)
                    }
                }
            } else {
                lastSingleMention = null
            }
        }
    }

    private fun inferDirectNamedDescription(
        sentence: String,
        mentions: Set<AliasMention>,
        accumulator: ProfileAccumulator,
    ) {
        mentions.forEach { mention ->
            val before = sentence.substring(0, mention.startInclusive)
            val after = sentence.substring(mention.endExclusive)
            listOf(
                DESCRIPTION_AFTER_NAME_COPULA.find(after)?.groupValues?.getOrNull(1),
                DESCRIPTION_AFTER_NAME_APPOSITION.find(after)?.groupValues?.getOrNull(1),
                DESCRIPTION_BEFORE_NAME.find(before)?.groupValues?.getOrNull(1),
            ).filterNotNull().forEach { description ->
                inferDescription(description, accumulator, 0.92f)
            }
            EXPLICIT_AGE_AFTER_NAME.firstNotNullOfOrNull { pattern ->
                pattern.find(after)?.groupValues?.getOrNull(1)?.let(::parseAge)
            }?.let { age ->
                accumulator.age.add(age.toAgeGroup(), 0.98f)
            }
        }
    }

    private fun inferDeclaredPronouns(
        sentence: String,
        firstMentionEnd: Int,
        accumulator: ProfileAccumulator,
    ) {
        val tail = sentence.substring(firstMentionEnd)
        when {
            DECLARED_FEMININE_PRONOUNS.containsMatchIn(tail) ->
                accumulator.gender.add(CharacterGender.FEMALE, 0.99f)
            DECLARED_MASCULINE_PRONOUNS.containsMatchIn(tail) ->
                accumulator.gender.add(CharacterGender.MALE, 0.99f)
            DECLARED_NON_BINARY.containsMatchIn(tail) ->
                accumulator.gender.add(CharacterGender.NON_BINARY, 0.99f)
        }
    }

    private fun inferWeakPronounsAfterMention(
        sentence: String,
        firstMentionEnd: Int,
        accumulator: ProfileAccumulator,
    ) {
        val tail = sentence.substring(firstMentionEnd)
        FEMININE_PRONOUN.findAll(tail).take(2).forEach {
            accumulator.gender.add(CharacterGender.FEMALE, 0.34f)
        }
        MASCULINE_PRONOUN.findAll(tail).take(2).forEach {
            accumulator.gender.add(CharacterGender.MALE, 0.34f)
        }
    }

    private fun inferFirstPersonProfile(paragraph: String, accumulator: ProfileAccumulator?) {
        if (accumulator == null) return
        splitSentences(paragraph).forEach { sentence ->
            SELF_DESCRIPTION.findAll(sentence).forEach { match ->
                inferDescription(match.groupValues[1], accumulator, 0.96f)
            }
            SELF_AGE.findAll(sentence).forEach { match ->
                parseAge(match.groupValues[1])?.let { age -> accumulator.age.add(age.toAgeGroup(), 0.99f) }
            }
            AT_AGE.findAll(sentence).forEach { match ->
                parseAge(match.groupValues[1])?.let { age -> accumulator.age.add(age.toAgeGroup(), 0.96f) }
            }
            when {
                SELF_NON_BINARY.containsMatchIn(sentence) ->
                    accumulator.gender.add(CharacterGender.NON_BINARY, 0.99f)
                SELF_FEMININE_PRONOUNS.containsMatchIn(sentence) ->
                    accumulator.gender.add(CharacterGender.FEMALE, 0.99f)
                SELF_MASCULINE_PRONOUNS.containsMatchIn(sentence) ->
                    accumulator.gender.add(CharacterGender.MALE, 0.99f)
            }
        }
    }

    private fun inferDescription(
        rawDescription: String,
        accumulator: ProfileAccumulator,
        explicitWeight: Float,
    ) {
        val description = rawDescription.lowercase(Locale.ROOT)
        genderFromDescription(description)?.let { accumulator.gender.add(it, explicitWeight) }
        ageFromDescription(description)?.let { accumulator.age.add(it, explicitWeight) }
    }

    private fun genderFromDescription(description: String): CharacterGender? {
        if (NON_BINARY_TERM.containsMatchIn(description) && !NEGATED_NON_BINARY_TERM.containsMatchIn(description)) {
            return CharacterGender.NON_BINARY
        }
        val feminine = FEMININE_TERM.containsMatchIn(description) && !NEGATED_FEMININE_TERM.containsMatchIn(description)
        val masculine = MASCULINE_TERM.containsMatchIn(description) && !NEGATED_MASCULINE_TERM.containsMatchIn(description)
        return when {
            feminine && !masculine -> CharacterGender.FEMALE
            masculine && !feminine -> CharacterGender.MALE
            else -> null
        }
    }

    private fun ageFromDescription(description: String): CharacterAgeGroup? {
        AGE_YEARS_OLD.find(description)?.groupValues?.getOrNull(1)?.let(::parseAge)?.let { return it.toAgeGroup() }
        return when {
            TEEN_TERM.containsMatchIn(description) -> CharacterAgeGroup.TEEN
            CHILD_TERM.containsMatchIn(description) -> CharacterAgeGroup.CHILD
            OLDER_TERM.containsMatchIn(description) -> CharacterAgeGroup.OLDER_ADULT
            YOUNG_ADULT_TERM.containsMatchIn(description) -> CharacterAgeGroup.YOUNG_ADULT
            ADULT_TERM.containsMatchIn(description) -> CharacterAgeGroup.ADULT
            else -> null
        }
    }

    private fun proseOnly(paragraph: String): String {
        val spans = DialogueScanner.scan(paragraph)
        if (spans.isEmpty()) return paragraph.trim()
        return buildString {
            var cursor = 0
            spans.forEach { span ->
                append(paragraph.substring(cursor, span.startInclusive))
                append(' ')
                cursor = span.endExclusive
            }
            append(paragraph.substring(cursor))
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun narratorIdentity(proseText: String): String? = NARRATOR_IDENTITY_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.find(proseText)?.groupValues?.getOrNull(1)?.let(KnownCharacterRegistry::cleanMention)
    }

    private fun splitSentences(text: String): List<String> = text
        .split(SENTENCE_BOUNDARY)
        .map(String::trim)
        .filter(String::isNotBlank)

    /**
     * Alias matching is indexed once per book. The previous implementation compiled one regular
     * expression for every character alias in every sentence, which made large books effectively
     * quadratic and produced sustained allocation/GC pressure on Android.
     */
    private class NamedProfileIndex private constructor(
        private val targetsById: Map<String, IndexedTarget>,
        private val aliasesByFirstToken: Map<String, List<IndexedAlias>>,
    ) {
        fun findMentions(sentence: String): List<TargetMention> {
            val sentenceTokens = WORD.findAll(sentence).map { match ->
                IndexedWord(
                    normalized = normalizeToken(match.value),
                    startInclusive = match.range.first,
                    endExclusive = match.range.last + 1,
                )
            }.toList()
            if (sentenceTokens.isEmpty()) return emptyList()

            val mentions = linkedMapOf<String, MutableTargetMention>()
            sentenceTokens.forEachIndexed { tokenIndex, token ->
                aliasesByFirstToken[token.normalized].orEmpty().forEach { alias ->
                    if (!alias.matches(sentenceTokens, tokenIndex)) return@forEach
                    val mention = mentions.getOrPut(alias.targetId) {
                        MutableTargetMention(targetsById.getValue(alias.targetId))
                    }
                    val startInclusive = sentenceTokens[tokenIndex].startInclusive
                    val endExclusive = sentenceTokens[tokenIndex + alias.tokens.lastIndex].endExclusive
                    mention.mentions += AliasMention(startInclusive, endExclusive)
                    mention.firstMentionEnd = minOf(mention.firstMentionEnd, endExclusive)
                }
            }
            return mentions.values.map { mention ->
                TargetMention(
                    target = mention.target,
                    mentions = mention.mentions,
                    firstMentionEnd = mention.firstMentionEnd,
                )
            }
        }

        companion object {
            fun build(targets: List<CharacterProfileTarget>): NamedProfileIndex {
                val indexedTargets = targets.asSequence()
                    .filter { it.id != BuiltInCharacters.NARRATOR_ID }
                    .map { target ->
                        val aliases = target.aliases.asSequence()
                            .mapNotNull { alias -> IndexedAlias.compile(target.id, alias) }
                            .distinctBy { it.tokens }
                            .toList()
                        IndexedTarget(target, aliases)
                    }
                    .filter { it.aliases.isNotEmpty() }
                    .associateBy { it.target.id }
                val aliasesByFirstToken = indexedTargets.values
                    .flatMap { it.aliases }
                    .groupBy { it.tokens.first() }
                return NamedProfileIndex(indexedTargets, aliasesByFirstToken)
            }
        }
    }

    private data class IndexedWord(
        val normalized: String,
        val startInclusive: Int,
        val endExclusive: Int,
    )

    private data class IndexedAlias(
        val targetId: String,
        val tokens: List<String>,
    ) {
        fun matches(sentenceTokens: List<IndexedWord>, startIndex: Int): Boolean {
            if (startIndex + tokens.size > sentenceTokens.size) return false
            return tokens.indices.all { offset ->
                sentenceTokens[startIndex + offset].normalized == tokens[offset]
            }
        }

        companion object {
            fun compile(targetId: String, raw: String): IndexedAlias? {
                val tokens = WORD.findAll(raw).map { normalizeToken(it.value) }.toList()
                if (tokens.isEmpty()) return null
                return IndexedAlias(targetId, tokens)
            }
        }
    }

    private data class IndexedTarget(
        val target: CharacterProfileTarget,
        val aliases: List<IndexedAlias>,
    )

    private data class AliasMention(
        val startInclusive: Int,
        val endExclusive: Int,
    )

    private data class TargetMention(
        val target: IndexedTarget,
        val mentions: Set<AliasMention>,
        val firstMentionEnd: Int,
    )

    private data class MutableTargetMention(
        val target: IndexedTarget,
        val mentions: MutableSet<AliasMention> = linkedSetOf(),
        var firstMentionEnd: Int = Int.MAX_VALUE,
    )

    private fun normalizeToken(token: String): String = token
        .lowercase(Locale.ROOT)
        .replace('’', '\'')

    private fun parseAge(raw: String): Int? {
        raw.trim().toIntOrNull()?.let { return it.takeIf { age -> age in 0..120 } }
        val words = raw.lowercase(Locale.ROOT).replace('-', ' ').trim().split(Regex("\\s+"))
        var total = 0
        var current = 0
        words.forEach { word ->
            when (word) {
                "hundred" -> current = current.coerceAtLeast(1) * 100
                else -> {
                    val value = AGE_WORDS[word] ?: return null
                    current += value
                }
            }
        }
        total += current
        return total.takeIf { it in 0..120 }
    }

    private fun Int.toAgeGroup(): CharacterAgeGroup = when (this) {
        in 0..12 -> CharacterAgeGroup.CHILD
        in 13..17 -> CharacterAgeGroup.TEEN
        in 18..29 -> CharacterAgeGroup.YOUNG_ADULT
        in 30..59 -> CharacterAgeGroup.ADULT
        in 60..120 -> CharacterAgeGroup.OLDER_ADULT
        else -> CharacterAgeGroup.UNKNOWN
    }

    private class ProfileAccumulator {
        val gender = EvidenceAccumulator<CharacterGender>()
        val age = EvidenceAccumulator<CharacterAgeGroup>()

        fun resolve(): InferredCharacterProfile {
            val resolvedGender = gender.resolve(CharacterGender.UNKNOWN)
            val resolvedAge = age.resolve(CharacterAgeGroup.UNKNOWN)
            return InferredCharacterProfile(
                gender = resolvedGender.value,
                genderConfidence = resolvedGender.confidence,
                ageGroup = resolvedAge.value,
                ageConfidence = resolvedAge.confidence,
            )
        }
    }

    private class EvidenceAccumulator<T> {
        private val evidence = linkedMapOf<T, MutableList<Float>>()

        fun add(value: T, confidence: Float) {
            evidence.getOrPut(value) { mutableListOf() } += confidence.coerceIn(0f, 0.999f)
        }

        fun resolve(unknown: T): ResolvedEvidence<T> {
            val ranked = evidence.map { (value, weights) ->
                value to (1f - weights.fold(1f) { remaining, weight -> remaining * (1f - weight) })
            }.sortedByDescending { it.second }
            val top = ranked.firstOrNull() ?: return ResolvedEvidence(unknown, 0f)
            val second = ranked.getOrNull(1)?.second ?: 0f
            val adjusted = top.second * (1f - second * 0.5f)
            if (top.second < MIN_PROFILE_CONFIDENCE || (second >= 0.55f && top.second - second < 0.25f)) {
                return ResolvedEvidence(unknown, 0f)
            }
            return ResolvedEvidence(top.first, adjusted.coerceIn(0f, 0.99f))
        }
    }

    private data class ResolvedEvidence<T>(val value: T, val confidence: Float)

    private const val MIN_PROFILE_CONFIDENCE = 0.60f
    private const val NARRATION_SAMPLE_MAX_PARAGRAPHS = 400
    private const val NARRATION_SAMPLE_MAX_CHARACTERS = 80_000
    private const val AGE_TOKEN_PATTERN =
        "(?:\\d{1,3}|zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|" +
            "fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty(?:[- ](?:one|two|three|four|five|six|" +
            "seven|eight|nine))?|thirty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|" +
            "forty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|fifty(?:[- ](?:one|two|three|four|five|" +
            "six|seven|eight|nine))?|sixty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|" +
            "seventy(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|eighty(?:[- ](?:one|two|three|four|five|" +
            "six|seven|eight|nine))?|ninety(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|one hundred)"

    private val WORD = Regex("(?iu)\\b[\\p{L}\\p{N}'’]+\\b")
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+|[\\r\\n]+")
    private val DESCRIPTION_AFTER_NAME_COPULA = Regex(
        "(?iu)^\\s*(?:,\\s*)?(?:is|was|seems|seemed|looks|looked|became|remains|remained)\\s+" +
            "(?:an?\\s+|the\\s+)?([^,.;!?]{1,48})",
    )
    private val DESCRIPTION_AFTER_NAME_APPOSITION = Regex(
        "(?iu)^\\s*,\\s*(?:an?\\s+|the\\s+)?([^,.;!?]{1,40})",
    )
    private val DESCRIPTION_BEFORE_NAME = Regex(
        "(?iu)\\b(?:an?|the)\\s+([^,.;!?]{1,36})\\s+$",
    )
    private val EXPLICIT_AGE_AFTER_NAME = listOf(
        Regex(
            "(?iu)^.{0,18}\\b(?:is|was|aged|turned)\\s+(?:an?\\s+)?($AGE_TOKEN_PATTERN)\\b",
        ),
        Regex("(?iu)^.{0,18}\\b($AGE_TOKEN_PATTERN)[ -]year(?:s)?[ -]old\\b"),
    )
    private val DECLARED_FEMININE_PRONOUNS = Regex(
        "(?iu)^.{0,32}\\b(?:uses?|pronouns? (?:are|were))\\s+she\\s*/\\s*her\\b",
    )
    private val DECLARED_MASCULINE_PRONOUNS = Regex(
        "(?iu)^.{0,32}\\b(?:uses?|pronouns? (?:are|were))\\s+he\\s*/\\s*him\\b",
    )
    private val DECLARED_NON_BINARY = Regex(
        "(?iu)^.{0,36}\\b(?:non[ -]?binary|genderqueer|agender)\\b",
    )
    private val FIRST_PERSON_TOKEN = Regex("(?iu)\\b(?:I|me|my|mine|myself|we|us|our|ours|ourselves)\\b")
    private val THIRD_PERSON_SUBJECT = Regex("(?iu)\\b(?:he|she|they)\\b")
    private val FIRST_PERSON_SELF_DESCRIPTION = Regex(
        "(?iu)\\bI\\s+(?:am|was|became|remain(?:ed)?)\\s+(?:an?\\s+|the\\s+)?" +
            "(?:girl|boy|woman|man|child|teen(?:ager)?|mother|father|grandmother|grandfather|non[ -]?binary)\\b",
    )
    private val FIRST_PERSON_SPEECH_TAG = Regex("(?iu)\\b(?:I\\s+(?:said|asked|replied)|(?:said|asked|replied)\\s+I)\\b")
    private val NARRATOR_IDENTITY_PATTERNS = listOf(
        Regex("(?u)\\b[Cc]all me\\s+([\\p{Lu}][\\p{L}\\p{M}'’\\-]*(?:\\s+[\\p{Lu}][\\p{L}\\p{M}'’\\-]*){0,2})"),
        Regex("(?u)\\b[Mm]y name is\\s+([\\p{Lu}][\\p{L}\\p{M}'’\\-]*(?:\\s+[\\p{Lu}][\\p{L}\\p{M}'’\\-]*){0,2})"),
        Regex("(?u)\\bI,\\s*([\\p{Lu}][\\p{L}\\p{M}'’\\-]*(?:\\s+[\\p{Lu}][\\p{L}\\p{M}'’\\-]*){0,2})\\s*,"),
    )

    private val SELF_DESCRIPTION = Regex(
        "(?iu)\\bI\\s+(?:am|was|became|remain(?:ed)?)\\s+(?:an?\\s+|the\\s+)?([^,.;!?]{1,52})",
    )
    private val SELF_AGE = Regex(
        "(?iu)\\bI\\s+(?:am|was|had just turned|had turned|turned)\\s+(?:an?\\s+)?($AGE_TOKEN_PATTERN)" +
            "(?:\\s+years?\\s+old)?\\b",
    )
    private val AT_AGE = Regex("(?iu)\\b(?:at (?:the )?age of|at age)\\s+($AGE_TOKEN_PATTERN)\\b")
    private val SELF_NON_BINARY = Regex("(?iu)\\bI\\s+(?:am|was)\\s+(?:non[ -]?binary|genderqueer|agender)\\b")
    private val SELF_FEMININE_PRONOUNS = Regex("(?iu)\\bmy pronouns (?:are|were) she\\s*/\\s*her\\b")
    private val SELF_MASCULINE_PRONOUNS = Regex("(?iu)\\bmy pronouns (?:are|were) he\\s*/\\s*him\\b")

    private val AGE_YEARS_OLD = Regex("(?iu)\\b($AGE_TOKEN_PATTERN)[ -]years?[ -]old\\b")
    private val NON_BINARY_TERM = Regex("(?iu)\\b(?:non[ -]?binary|genderqueer|agender)\\b")
    private val FEMININE_TERM = Regex(
        "(?iu)\\b(?:girl|woman|lady|mother|daughter|sister|wife|queen|princess|actress|aunt|grandmother|niece|heroine)\\b",
    )
    private val MASCULINE_TERM = Regex(
        "(?iu)\\b(?:boy|man|gentleman|father|son|brother|husband|king|prince|actor|uncle|grandfather|nephew|hero)\\b",
    )
    private val NEGATED_NON_BINARY_TERM = Regex("(?iu)\\b(?:not|never)\\s+(?:non[ -]?binary|genderqueer|agender)\\b")
    private val NEGATED_FEMININE_TERM = Regex(
        "(?iu)\\b(?:not|never)\\s+(?:an?\\s+|the\\s+)?" +
            "(?:girl|woman|lady|mother|daughter|sister|wife|queen|princess|actress|aunt|grandmother|niece|heroine)\\b",
    )
    private val NEGATED_MASCULINE_TERM = Regex(
        "(?iu)\\b(?:not|never)\\s+(?:an?\\s+|the\\s+)?" +
            "(?:boy|man|gentleman|father|son|brother|husband|king|prince|actor|uncle|grandfather|nephew|hero)\\b",
    )
    private val TEEN_TERM = Regex("(?iu)\\b(?:teen|teenager|teenage|adolescent)\\b")
    private val CHILD_TERM = Regex("(?iu)\\b(?:baby|infant|toddler|child|schoolgirl|schoolboy|girl|boy)\\b")
    private val OLDER_TERM = Regex(
        "(?iu)\\b(?:(?:old|older|elderly|aged|ancient)\\s+(?:woman|man|lady|gentleman|adult)|grandmother|grandfather)\\b",
    )
    private val YOUNG_ADULT_TERM = Regex("(?iu)\\b(?:young adult|young woman|young man)\\b")
    private val ADULT_TERM = Regex("(?iu)\\b(?:adult|woman|man|lady|gentleman|mother|father|wife|husband)\\b")

    private val FEMININE_PRONOUN = Regex("(?iu)\\b(?:she|her|hers|herself)\\b")
    private val MASCULINE_PRONOUN = Regex("(?iu)\\b(?:he|him|his|himself)\\b")
    private val STARTS_FEMININE_PRONOUN = Regex("(?iu)^\\s*(?:[“\"'‘]?\\s*)?she\\b")
    private val STARTS_MASCULINE_PRONOUN = Regex("(?iu)^\\s*(?:[“\"'‘]?\\s*)?he\\b")

    private val AGE_WORDS = mapOf(
        "zero" to 0,
        "one" to 1,
        "two" to 2,
        "three" to 3,
        "four" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8,
        "nine" to 9,
        "ten" to 10,
        "eleven" to 11,
        "twelve" to 12,
        "thirteen" to 13,
        "fourteen" to 14,
        "fifteen" to 15,
        "sixteen" to 16,
        "seventeen" to 17,
        "eighteen" to 18,
        "nineteen" to 19,
        "twenty" to 20,
        "thirty" to 30,
        "forty" to 40,
        "fifty" to 50,
        "sixty" to 60,
        "seventy" to 70,
        "eighty" to 80,
        "ninety" to 90,
    )
}
