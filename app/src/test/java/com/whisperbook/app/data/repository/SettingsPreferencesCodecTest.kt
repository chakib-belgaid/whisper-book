package com.whisperbook.app.data.repository

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.whisperbook.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesCodecTest {
    @Test
    fun `empty preferences decode to domain defaults`() {
        assertEquals(AppSettings(), SettingsPreferencesCodec.decode(mutablePreferencesOf()))
    }

    @Test
    fun `settings survive a preferences round trip`() {
        val expected = AppSettings(
            onboardingComplete = true,
            defaultNarratorVoiceId = "willow",
            narrationLanguageCode = "fr",
            installedLanguagePackCodes = setOf("en", "fr", "ar"),
            speakingSpeed = 1.25f,
            sleepTimerMinutes = 45,
            keepScreenAwake = true,
            largerText = true,
            autoScroll = false,
            audioCacheLimitBytes = 512L * 1024L * 1024L,
        )
        val preferences = mutablePreferencesOf()

        SettingsPreferencesCodec.write(preferences, expected)

        assertEquals(expected, SettingsPreferencesCodec.decode(preferences))
    }

    @Test
    fun `unsafe settings are normalized before persistence`() {
        val preferences = mutablePreferencesOf()
        SettingsPreferencesCodec.write(
            preferences,
            AppSettings(
                defaultNarratorVoiceId = "",
                speakingSpeed = 9f,
                sleepTimerMinutes = -4,
                audioCacheLimitBytes = 1L,
            ),
        )

        val decoded = SettingsPreferencesCodec.decode(preferences)

        assertEquals("bella", decoded.defaultNarratorVoiceId)
        assertEquals(2f, decoded.speakingSpeed)
        assertEquals(0, decoded.sleepTimerMinutes)
        assertEquals(64L * 1024L * 1024L, decoded.audioCacheLimitBytes)
    }

    @Test
    fun `non finite speaking speed falls back to the domain default`() {
        val preferences = mutablePreferencesOf()
        SettingsPreferencesCodec.write(preferences, AppSettings(speakingSpeed = Float.NaN))

        assertEquals(1f, SettingsPreferencesCodec.decode(preferences).speakingSpeed)
    }

    @Test
    fun `language packs keep English installed and reject an uninstalled selection`() {
        val preferences = mutablePreferencesOf()
        SettingsPreferencesCodec.write(
            preferences,
            AppSettings(
                narrationLanguageCode = "fr",
                installedLanguagePackCodes = setOf("ar", "unsupported"),
            ),
        )

        val decoded = SettingsPreferencesCodec.decode(preferences)

        assertEquals("en", decoded.narrationLanguageCode)
        assertEquals(setOf("en", "ar"), decoded.installedLanguagePackCodes)
    }
}
