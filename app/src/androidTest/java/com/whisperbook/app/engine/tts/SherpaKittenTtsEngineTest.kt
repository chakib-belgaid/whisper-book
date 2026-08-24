package com.whisperbook.app.engine.tts

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whisperbook.app.domain.SynthesisRequest
import com.whisperbook.app.engine.compute.NeuralExecutionBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaKittenTtsEngineTest {
    @Test
    fun embeddedModelWarmsUpAndSynthesizesPcm() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        try {
            engine.warmUp().getOrThrow()
            val voices = engine.voices()
            assertEquals(8, voices.size)

            val audio = engine.synthesize(
                SynthesisRequest(
                    text = BACKEND_SMOKE_TEXT,
                    voice = voices.first(),
                    speed = 1f,
                    cacheKey = "instrumented-smoke-test",
                ),
            ).getOrThrow()

            assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
            assertTrue(audio.pcm16.isNotEmpty())
            assertTrue(audio.durationMs > 0)

            // On devices where partial NNAPI offload is slower than the guarded threshold, the
            // first call retires that runtime and this follow-up exercises the automatic CPU path.
            // Faster devices keep NNAPI; both paths must preserve the same synthesis contract.
            val followUp = engine.synthesize(
                SynthesisRequest(
                    text = BACKEND_SMOKE_TEXT,
                    voice = voices.first(),
                    speed = 1f,
                    cacheKey = "instrumented-backend-follow-up",
                ),
            ).getOrThrow()
            assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, followUp.sampleRate)
            assertTrue(followUp.pcm16.isNotEmpty())
        } finally {
            engine.close()
        }
    }

    @Test
    fun cpuProviderFallbackSynthesizesPcm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = SherpaKittenTtsEngine(
            workerDispatcher = null,
            androidSdk = 28,
            runtimeFactory = SupertonicRuntimeFactory { backend ->
                assertEquals(NeuralExecutionBackend.CPU, backend)
                OrtSupertonicRuntime(context, backend)
            },
        )
        try {
            val audio = engine.synthesize(
                SynthesisRequest(
                    text = BACKEND_SMOKE_TEXT,
                    voice = engine.voices().first(),
                    speed = 1f,
                    cacheKey = "instrumented-cpu-fallback-test",
                ),
            ).getOrThrow()

            assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
            assertTrue(audio.pcm16.isNotEmpty())
            assertTrue(audio.durationMs > 0)
        } finally {
            engine.close()
        }
    }

    @Test
    fun brunoAndJasperUseReachableMasculinePresets() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        try {
            engine.warmUp().getOrThrow()
            val voices = engine.voices().associateBy { it.id }

            listOf("bruno", "jasper").forEach { voiceId ->
                val voice = requireNotNull(voices[voiceId])
                assertTrue("$voiceId should use an M1-M5 preset", voice.speakerIndex in 5..9)
                val audio = engine.synthesize(
                    SynthesisRequest(
                        text = "The lantern is ready.",
                        voice = voice,
                        speed = 1f,
                        cacheKey = "instrumented-$voiceId-voice-test",
                    ),
                ).getOrThrow()

                assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
                assertTrue(audio.pcm16.isNotEmpty())
            }
        } finally {
            engine.close()
        }
    }

    @Test
    fun frenchAndArabicLanguagePacksSynthesizeWithTheirLanguageCodes() = runBlocking {
        val engine = SherpaKittenTtsEngine(ApplicationProvider.getApplicationContext())
        try {
            engine.warmUp().getOrThrow()
            val voice = engine.voices().first()
            listOf(
                "fr" to "Il était une fois une bibliothèque silencieuse.",
                "ar" to "كان يا ما كان، في مكتبة هادئة.",
            ).forEach { (languageCode, text) ->
                val audio = engine.synthesize(
                    SynthesisRequest(
                        text = text,
                        voice = voice,
                        speed = 1f,
                        cacheKey = "instrumented-$languageCode-pack-test",
                        languageCode = languageCode,
                    ),
                ).getOrThrow()

                assertEquals(SherpaKittenTtsEngine.EXPECTED_SAMPLE_RATE, audio.sampleRate)
                assertTrue(audio.pcm16.isNotEmpty())
            }
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val BACKEND_SMOKE_TEXT = "The lantern is ready for tonight."
    }
}
