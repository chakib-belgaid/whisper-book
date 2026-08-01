package com.whisperbook.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whisperbook.app.domain.model.CharacterColorRole
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.integration.WhisperbookUiSnapshot

interface WhisperbookUiActions {
    fun importBook(uri: Uri)
    fun retryPreparation()
    fun selectBook(bookId: String)
    fun selectChapter(chapterId: String)
    fun playOrPause()
    fun seekByFraction(delta: Float)
    fun seekToFraction(fraction: Float)
    fun seekToPassage(passageId: String)
    fun cycleSpeed()
    fun cycleDefaultNarratorVoice()
    fun cycleSleepTimer()
    fun cycleVoice(characterId: String)
    fun previewCharacter(characterId: String)
    fun setAutoScroll(enabled: Boolean)
    fun setKeepScreenAwake(enabled: Boolean)
    fun setLargerText(enabled: Boolean)
    fun completeOnboarding()
}

@Immutable
data class LibraryBookUi(
    val id: String,
    val title: String,
    val author: String,
    val chapter: Int,
    val totalChapters: Int,
    val progress: Float,
)

@Immutable
data class ChapterUi(
    val number: Int,
    val title: String,
    val selected: Boolean = false,
    val id: String = number.toString(),
)

@Immutable
data class CastMemberUi(
    val id: String,
    val character: String,
    val voice: String,
    val confidence: Int,
    val lines: Int,
    val portraitRes: Int,
    val role: SpeakerRole,
)

enum class SpeakerRole { Narrator, Elara, Fox }

@Immutable
data class PassageUi(
    val id: String,
    val speaker: SpeakerRole,
    val text: String,
    val speakerId: String = "",
    val speakerName: String = speaker.name,
)

/**
 * UI-facing integration seam. Production repositories and the Media3 gateway can drive this
 * holder without coupling screens to storage, parsing, synthesis, or playback implementations.
 */
@Stable
class WhisperbookAppState(private val productionActions: WhisperbookUiActions? = null) {
    private val demoMode = productionActions == null

    val books = mutableStateListOf<LibraryBookUi>().apply {
        if (demoMode) addAll(
            listOf(
                LibraryBookUi("moonlit", "The Moonlit Wood", "E. Wren", 7, 18, .58f),
                LibraryBookUi("alice", "Alice's Adventures", "Lewis Carroll", 1, 12, .08f),
                LibraryBookUi("garden", "The Secret Garden", "Frances Hodgson Burnett", 1, 27, 0f),
            ),
        )
    }
    val chapters = mutableStateListOf<ChapterUi>().apply {
        if (demoMode) addAll(
            listOf(
                ChapterUi(7, "The Hidden Glade", true),
                ChapterUi(8, "A Lantern in the Rain"),
                ChapterUi(9, "The Fox's Promise"),
                ChapterUi(10, "Under the Rowan Tree"),
            ),
        )
    }
    val cast = mutableStateListOf<CastMemberUi>().apply {
        if (demoMode) addAll(
            listOf(
                CastMemberUi("narrator", "Narrator", "Arthur", 98, 426, com.whisperbook.app.R.drawable.portrait_narrator, SpeakerRole.Narrator),
                CastMemberUi("elara", "Elara", "Celeste", 91, 84, com.whisperbook.app.R.drawable.portrait_elara, SpeakerRole.Elara),
                CastMemberUi("fox", "Fox", "Rowan", 87, 39, com.whisperbook.app.R.drawable.portrait_fox, SpeakerRole.Fox),
            ),
        )
    }
    val passages = mutableStateListOf<PassageUi>().apply {
        if (demoMode) addAll(
            listOf(
                PassageUi("p1", SpeakerRole.Narrator, "The trees leaned close as the path narrowed beneath the moon."),
                PassageUi("p2", SpeakerRole.Elara, "We should turn back before the lantern fades."),
                PassageUi("p3", SpeakerRole.Fox, "The woods remember every traveler."),
                PassageUi("p4", SpeakerRole.Narrator, "Beyond the trees, a glimmer of silver called to them, soft as a secret."),
            ),
        )
    }

    var importedUri by mutableStateOf<Uri?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var preparationProgress by mutableFloatStateOf(if (demoMode) .62f else 0f)
        private set
    var preparationStage by mutableIntStateOf(if (demoMode) 1 else 0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var chapterProgress by mutableFloatStateOf(if (demoMode) .44f else 0f)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var sleepMinutes by mutableIntStateOf(if (demoMode) 30 else 0)
        private set
    var activePassageId by mutableStateOf(if (demoMode) "p2" else "")
        private set
    var autoScroll by mutableStateOf(true)
        private set
    var keepScreenAwake by mutableStateOf(false)
        private set
    var largerText by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var preparationFailed by mutableStateOf(false)
        private set
    var currentBookTitle by mutableStateOf(if (demoMode) "The Moonlit Wood" else "")
        private set
    var currentAuthor by mutableStateOf(if (demoMode) "E. Wren" else "")
        private set
    var currentChapterTitle by mutableStateOf(if (demoMode) "The Hidden Glade" else "")
        private set
    var currentChapterNumber by mutableIntStateOf(if (demoMode) 7 else 0)
        private set
    var totalChapters by mutableIntStateOf(if (demoMode) 18 else 0)
        private set
    var chapterPositionMs by mutableLongStateOf(if (demoMode) 18L * 60_000L + 42_000L else 0L)
        private set
    var chapterDurationMs by mutableLongStateOf(if (demoMode) 42L * 60_000L + 58_000L else 0L)
        private set
    var activePassagePositionMs by mutableLongStateOf(if (demoMode) 6_400L else 0L)
        private set
    var activePassageDurationMs by mutableLongStateOf(if (demoMode) 10_000L else 0L)
        private set
    var localStorageBytes by mutableLongStateOf(if (demoMode) 1_800_000_000L else 0L)
        private set
    var storageLimitBytes by mutableLongStateOf(2L * 1024L * 1024L * 1024L)
        private set
    var defaultNarratorVoice by mutableStateOf(if (demoMode) "Arthur" else "Bella")
        private set

    val isProductionBacked: Boolean get() = productionActions != null

    fun synchronize(snapshot: WhisperbookUiSnapshot) {
        if (snapshot.selectedBook == null) {
            currentBookTitle = ""
            currentAuthor = ""
        }
        snapshot.selectedBook?.let { selectedBook ->
            currentBookTitle = selectedBook.title
            currentAuthor = selectedBook.author ?: "Unknown author"
        }
        if (snapshot.selectedChapter == null) {
            currentChapterTitle = ""
            currentChapterNumber = 0
        }
        snapshot.selectedChapter?.let { selectedChapter ->
            currentChapterTitle = selectedChapter.title
            currentChapterNumber = selectedChapter.ordinal + 1
        }
        totalChapters = snapshot.chapters.size
        books.clear()
        books.addAll(snapshot.books.map { book ->
            val chapterIndex = snapshot.chapters.indexOfFirst { it.id == book.currentChapterId }
            LibraryBookUi(
                id = book.id,
                title = book.title,
                author = book.author ?: "Unknown author",
                chapter = (chapterIndex + 1).coerceAtLeast(1),
                totalChapters = snapshot.chapters.size.coerceAtLeast(1),
                progress = book.progressFraction,
            )
        })
        chapters.clear()
        chapters.addAll(snapshot.chapters.map { chapter ->
                ChapterUi(
                    number = chapter.ordinal + 1,
                    title = chapter.title,
                    selected = chapter.id == snapshot.selectedChapter?.id,
                    id = chapter.id,
                )
            })
        cast.clear()
        cast.addAll(snapshot.characters.map { character ->
                val role = when (character.colorRole) {
                    CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> SpeakerRole.Narrator
                    CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> SpeakerRole.Elara
                    CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> SpeakerRole.Fox
                }
                val assignment = snapshot.voiceAssignments[character.id]
                val voice = snapshot.voices.firstOrNull { it.id == assignment?.voiceId }
                CastMemberUi(
                    id = character.id,
                    character = character.displayName,
                    voice = voice?.displayName ?: "Automatic",
                    confidence = 90,
                    lines = character.dialogueLineCount,
                    portraitRes = when (character.colorRole) {
                        CharacterColorRole.NARRATOR, CharacterColorRole.BLUE -> com.whisperbook.app.R.drawable.portrait_narrator
                        CharacterColorRole.ELARA_BURGUNDY, CharacterColorRole.BURGUNDY -> com.whisperbook.app.R.drawable.portrait_elara
                        CharacterColorRole.FOX_ORANGE, CharacterColorRole.ORANGE -> com.whisperbook.app.R.drawable.portrait_fox
                    },
                    role = role,
                )
            }.sortedWith(compareBy<CastMemberUi>({ it.role != SpeakerRole.Narrator }, { -it.lines }, { it.character })))
        passages.clear()
        snapshot.selectedChapter?.passages?.let { domainPassages ->
            val characterById = cast.associateBy(CastMemberUi::id)
            passages.addAll(domainPassages.map { passage ->
                val character = characterById[passage.speakerId]
                PassageUi(
                    id = passage.id,
                    speaker = character?.role ?: SpeakerRole.Narrator,
                    text = passage.text,
                    speakerId = passage.speakerId,
                    speakerName = character?.character ?: "Narrator",
                )
            })
        }
        snapshot.preparation?.let { preparation ->
            preparationProgress = preparation.overallProgress()
            preparationStage = when (preparation.stage) {
                PreparationStage.COPY_AND_VALIDATE, PreparationStage.READING_CHAPTERS -> 0
                PreparationStage.FINDING_CHARACTERS -> 1
                PreparationStage.ASSIGNING_VOICES, PreparationStage.PREPARING_AUDIO -> 2
                PreparationStage.READY -> 4
                PreparationStage.FAILED -> 0
            }
            preparationFailed = preparation.stage == PreparationStage.FAILED
        } ?: run {
            preparationProgress = 0f
            preparationStage = 0
            preparationFailed = false
        }
        chapterProgress = snapshot.chapterProgress
        snapshot.playback?.let { playback ->
            isPlaying = playback.isPlaying
            speed = playback.speed
            activePassageId = playback.passageId
            chapterPositionMs = playback.chapterPositionMs
            chapterDurationMs = playback.chapterDurationMs
            activePassagePositionMs = playback.segmentPositionMs
            activePassageDurationMs = playback.segmentDurationMs
        } ?: run {
            isPlaying = false
            chapterPositionMs = 0L
            chapterDurationMs = 0L
            activePassagePositionMs = 0L
            activePassageDurationMs = 0L
        }
        autoScroll = snapshot.settings.autoScroll
        keepScreenAwake = snapshot.settings.keepScreenAwake
        largerText = snapshot.settings.largerText
        speed = snapshot.settings.speakingSpeed
        sleepMinutes = snapshot.settings.sleepTimerMinutes
        localStorageBytes = snapshot.localStorageBytes.coerceAtLeast(0L)
        storageLimitBytes = snapshot.settings.audioCacheLimitBytes.coerceAtLeast(1L)
        defaultNarratorVoice = snapshot.voices
            .firstOrNull { it.id == snapshot.settings.defaultNarratorVoiceId }
            ?.displayName
            ?: "Bella"
        importError = snapshot.errorMessage
        isBusy = snapshot.isBusy
        statusMessage = snapshot.statusMessage
    }

    private fun com.whisperbook.app.domain.model.PreparationState.overallProgress(): Float {
        val local = progressFraction.coerceIn(0f, 1f)
        return when (stage) {
            PreparationStage.COPY_AND_VALIDATE -> 0.08f * local
            PreparationStage.READING_CHAPTERS -> 0.08f + 0.37f * local
            PreparationStage.FINDING_CHARACTERS -> 0.45f + 0.25f * local
            PreparationStage.ASSIGNING_VOICES -> 0.70f + 0.12f * local
            PreparationStage.PREPARING_AUDIO -> 0.82f + 0.18f * local
            PreparationStage.READY -> 1f
            PreparationStage.FAILED -> local
        }.coerceIn(0f, 1f)
    }

    fun imported(uri: Uri) {
        importedUri = uri
        importError = null
        preparationProgress = .12f
        preparationStage = 0
        productionActions?.importBook(uri)
    }

    fun importFailed(message: String) {
        importError = message
    }

    fun retryPreparation() {
        importError = null
        preparationFailed = false
        productionActions?.retryPreparation()
    }

    fun advancePreparation() {
        if (productionActions != null) return
        preparationStage = (preparationStage + 1).coerceAtMost(4)
        preparationProgress = when (preparationStage) {
            0 -> .22f
            1 -> .62f
            2 -> .82f
            else -> 1f
        }
    }

    fun togglePlayback() {
        isPlaying = !isPlaying
        productionActions?.playOrPause()
    }

    fun seekBy(delta: Float) {
        chapterProgress = (chapterProgress + delta).coerceIn(0f, 1f)
        if (passages.isNotEmpty()) {
            val passageIndex = (chapterProgress * passages.size).toInt().coerceIn(0, passages.lastIndex)
            activePassageId = passages[passageIndex].id
        }
        productionActions?.seekByFraction(delta)
    }

    fun seekTo(fraction: Float) {
        chapterProgress = fraction.coerceIn(0f, 1f)
        if (passages.isNotEmpty()) {
            val passageIndex = (chapterProgress * passages.size).toInt().coerceIn(0, passages.lastIndex)
            activePassageId = passages[passageIndex].id
        }
        productionActions?.seekToFraction(chapterProgress)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)
        speed = speeds[(speeds.indexOf(speed).takeIf { it >= 0 } ?: 0).plus(1) % speeds.size]
        productionActions?.cycleSpeed()
    }

    fun cycleDefaultNarratorVoice() {
        if (productionActions == null) {
            val voices = listOf("Bella", "Jasper", "Luna", "Bruno", "Rosie", "Hugo", "Kiki", "Leo")
            val index = voices.indexOf(defaultNarratorVoice).coerceAtLeast(0)
            defaultNarratorVoice = voices[(index + 1) % voices.size]
        }
        productionActions?.cycleDefaultNarratorVoice()
    }

    fun cycleSleepTimer() {
        val timers = listOf(15, 30, 45, 60, 0)
        sleepMinutes = timers[(timers.indexOf(sleepMinutes).takeIf { it >= 0 } ?: 0).plus(1) % timers.size]
        productionActions?.cycleSleepTimer()
    }

    fun selectPassage(id: String) {
        activePassageId = id
        val index = passages.indexOfFirst { it.id == id }
        if (index >= 0) chapterProgress = index / passages.size.toFloat()
        productionActions?.seekToPassage(id)
    }

    fun updateAutoScroll(value: Boolean) {
        autoScroll = value
        productionActions?.setAutoScroll(value)
    }

    fun updateKeepScreenAwake(value: Boolean) {
        keepScreenAwake = value
        productionActions?.setKeepScreenAwake(value)
    }

    fun updateLargerText(value: Boolean) {
        largerText = value
        productionActions?.setLargerText(value)
    }

    fun cycleVoice(characterId: String) {
        val choices = when (characterId) {
            "narrator" -> listOf("Arthur", "Storyteller", "James")
            "elara" -> listOf("Celeste", "Luna", "Bella")
            else -> listOf("Rowan", "Jasper", "Leo")
        }
        val index = cast.indexOfFirst { it.id == characterId }
        if (index >= 0) {
            val member = cast[index]
            val next = choices[(choices.indexOf(member.voice).takeIf { it >= 0 } ?: 0).plus(1) % choices.size]
            cast[index] = member.copy(voice = next)
        }
        productionActions?.cycleVoice(characterId)
    }

    fun previewCharacter(characterId: String) {
        productionActions?.previewCharacter(characterId)
        if (productionActions == null) togglePlayback()
    }

    fun selectBook(bookId: String) {
        productionActions?.selectBook(bookId)
    }

    fun selectChapter(chapterId: String) {
        if (chapterId.isBlank()) return
        if (productionActions == null) {
            val chapterIndex = chapters.indexOfFirst { it.id == chapterId }
            if (chapterIndex >= 0) {
                chapters.indices.forEach { index ->
                    chapters[index] = chapters[index].copy(selected = index == chapterIndex)
                }
                val chapter = chapters[chapterIndex]
                currentChapterNumber = chapter.number
                currentChapterTitle = chapter.title
                chapterProgress = 0f
                activePassageId = passages.firstOrNull()?.id.orEmpty()
            }
        }
        productionActions?.selectChapter(chapterId)
    }

    fun completeOnboarding() {
        productionActions?.completeOnboarding()
    }
}
