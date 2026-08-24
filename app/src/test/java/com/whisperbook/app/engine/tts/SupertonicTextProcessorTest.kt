package com.whisperbook.app.engine.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupertonicTextProcessorTest {
    private val identityIndexer = IntArray(2_048) { it }
    private val processor = SupertonicTextProcessor(identityIndexer)

    @Test
    fun `normalizes symbols removes emoji and adds language tags`() {
        assertEquals(
            "<en>Hello at moon.</en>",
            processor.preprocess(" Hello @ moon 🌙 ", "en"),
        )
    }

    @Test
    fun `encodes normalized code points through the bundled indexer`() {
        val expected = "<fr>Bonjour!</fr>".codePoints().mapToLong(Int::toLong).toArray()

        assertArrayEquals(expected, processor.encode("Bonjour!", "fr"))
    }

    @Test
    fun `rejects a language outside the model catalog`() {
        assertThrows(IllegalArgumentException::class.java) {
            processor.encode("Hei", "no")
        }
    }
}
