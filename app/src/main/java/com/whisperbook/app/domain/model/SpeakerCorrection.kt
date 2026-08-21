package com.whisperbook.app.domain.model

import java.util.Locale

/** Defines how far a manual read-along speaker correction should propagate. */
enum class SpeakerCorrectionScope {
    THIS_PASSAGE,
    MATCHING_PHRASES,
}

/**
 * Produces a conservative key for repeated-phrase corrections.
 *
 * Case, punctuation, and whitespace differences are ignored, but the words must otherwise match.
 * Callers also keep the original attributed speaker equal so a common phrase used by two real
 * characters is not reassigned across the book.
 */
fun speakerPhraseMatchKey(text: String): String = buildString(text.length) {
    var pendingSpace = false
    text.lowercase(Locale.ROOT).forEach { character ->
        when {
            character.isLetterOrDigit() -> {
                if (pendingSpace && isNotEmpty()) append(' ')
                append(character)
                pendingSpace = false
            }
            isNotEmpty() -> pendingSpace = true
        }
    }
}
