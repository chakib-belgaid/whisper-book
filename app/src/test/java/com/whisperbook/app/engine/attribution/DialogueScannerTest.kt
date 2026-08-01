package com.whisperbook.app.engine.attribution

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueScannerTest {
    @Test
    fun `scans straight smart guillemet and em dash dialogue`() {
        val fixtures = listOf(
            "She said \"come home\"." to "come home",
            "She said “come home”." to "come home",
            "Elle dit « reviens vite »." to " reviens vite ",
            "— Come home before moonrise." to "Come home before moonrise.",
        )

        fixtures.forEach { (text, expected) ->
            assertEquals(expected, DialogueScanner.scan(text).single().content(text))
        }
    }

    @Test
    fun `ignores unmatched and escaped straight quotes`() {
        assertEquals(emptyList<DialogueSpan>(), DialogueScanner.scan("An unmatched \" is prose"))
        assertEquals(emptyList<DialogueSpan>(), DialogueScanner.scan("A \\\"quoted word\\\" in source code"))
    }
}
