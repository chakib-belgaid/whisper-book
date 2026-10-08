package com.whisperbook.app.domain

import java.text.Normalizer

/**
 * Prepares the text that is sent to speech synthesis. The reader keeps showing the original
 * publication text; only the spoken copy is cleaned.
 *
 * sherpa-onnx already maps curly quotes and dashes to ASCII and decomposes French accents before
 * looking characters up in Supertonic's vocabulary. This pass handles what it does not:
 *
 * - footnote markers (`[12]`, `[a]`, superscript digits) that would otherwise be read aloud,
 * - PDF ligatures (`ﬁ`, `ﬂ`) and the `…` character, which are missing from the vocabulary,
 * - non-breaking and thin spaces used by French typography, which the model cannot see,
 * - zero-width characters and soft hyphens left behind by EPUB and PDF extraction,
 * - repeated punctuation (`!!!`, `?!?!`, `.....`) that makes the voice stumble.
 */
object SpokenTextCleaner {
    fun clean(text: String): String {
        if (text.isBlank()) return text
        var result = text
            .replace(zeroWidth, "")
            .replace(bracketedFootnote, "")
            .replace(superscriptFootnote, "")
        // NFKD expands ligatures, compatibility spaces and "…", and splits every accented letter
        // into base + combining mark, which is how Supertonic's vocabulary stores them (sherpa-onnx
        // only decomposes a fixed list of Latin letters, so "ÿ" or "ő" would otherwise be dropped).
        result = Normalizer.normalize(result, Normalizer.Form.NFKD)
        result = result
            .replace(oddSpaces, " ")
            .replace(longEllipsis, "...")
            .replace(repeatedBang, "!")
            .replace(repeatedQuestion, "?")
            .replace(mixedInterrobang, "?!")
            .replace(multipleSpaces, " ")
            .trim()
        return result.ifBlank { text.trim() }
    }

    private val zeroWidth = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00AD]")

    /** `[12]`, `[iv]`, `[a]` directly attached to a word or punctuation; not `[Laughs]`. */
    private val bracketedFootnote = Regex("(?<=[\\p{L}\\p{N}.,;:!?\\u00BB\\u201D\"')])\\[(?:\\d{1,3}|[a-z]|[ivx]{1,5})]")

    /**
     * Superscript digits or daggers attached to a word of three or more letters or to punctuation:
     * `word¹²`, `fin.†`. Short words are left alone so units such as `m²` and `km³` survive.
     */
    private val superscriptFootnote = Regex("(?:(?<=\\p{L}{3})|(?<=[.,;:!?\\u00BB\\u201D\"')]))[\\u00B9\\u00B2\\u00B3\\u2070\\u2074-\\u2079\\u2020\\u2021]+")

    private val oddSpaces = Regex("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000\\t]")
    private val longEllipsis = Regex("\\.{4,}|(?:\\.\\s){2,}\\.")
    private val repeatedBang = Regex("!{2,}")
    private val repeatedQuestion = Regex("\\?{2,}")
    private val mixedInterrobang = Regex("[?!]*(?:\\?!|!\\?)[?!]*")
    private val multipleSpaces = Regex(" {2,}")
}
