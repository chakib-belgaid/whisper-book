package com.whisperbook.app.domain

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextCleanerTest {
    @Test
    fun `plain ascii text is unchanged`() {
        val text = "A short opening line, with nothing unusual in it."
        assertEquals(text, SpokenTextCleaner.clean(text))
    }

    @Test
    fun `footnote markers are removed`() {
        assertEquals("The treaty was signed in Vienna.", SpokenTextCleaner.clean("The treaty[12] was signed in Vienna.[a]"))
        assertEquals("Il partit le soir.", SpokenTextCleaner.clean("Il partit¹ le soir.²"))
    }

    @Test
    fun `stage directions in brackets and unit superscripts are kept`() {
        assertEquals("He paused [Laughs] then went on.", SpokenTextCleaner.clean("He paused [Laughs] then went on."))
        assertEquals("A room of 20 m2.", SpokenTextCleaner.clean("A room of 20 m²."))
    }

    @Test
    fun `ligatures and ellipsis become characters the model knows`() {
        assertEquals("The final flight...", SpokenTextCleaner.clean("The ﬁnal ﬂight…"))
        assertEquals("Wait...", SpokenTextCleaner.clean("Wait. . ."))
        assertEquals("Wait...", SpokenTextCleaner.clean("Wait......"))
    }

    @Test
    fun `french typographic spaces become plain spaces`() {
        assertEquals(
            nfkd("« Quoi ? » dit-elle : « Viens ! »"),
            SpokenTextCleaner.clean("« Quoi ? » dit-elle : « Viens ! »"),
        )
    }

    @Test
    fun `accented letters are decomposed for the model vocabulary`() {
        val cleaned = SpokenTextCleaner.clean("L'été à Noël, ÿ")
        assertEquals(nfkd("L'été à Noël, ÿ"), cleaned)
        assertEquals("L'été à Noël, ÿ", Normalizer.normalize(cleaned, Normalizer.Form.NFC))
    }

    @Test
    fun `repeated punctuation is collapsed`() {
        assertEquals("No! Really? What?!", SpokenTextCleaner.clean("No!!! Really??? What?!?!"))
    }

    @Test
    fun `zero width characters and soft hyphens are removed`() {
        assertEquals("extraordinary", SpokenTextCleaner.clean("extra­ordi​nary"))
    }

    private fun nfkd(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKD)
}
