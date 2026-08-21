package com.whisperbook.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.whisperbook.app.domain.SettingsRepository
import com.whisperbook.app.domain.NarrationTextChunker
import com.whisperbook.app.domain.model.AppSettings
import com.whisperbook.app.domain.model.NarrationLanguage
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(SettingsPreferencesCodec::decode)

    /** One-time compatibility input for books created before language belonged to each book. */
    val legacyNarrationLanguageCode: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(SettingsPreferencesCodec::decodeLegacyNarrationLanguageCode)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { current ->
            current.toMutablePreferences().also { mutable ->
                SettingsPreferencesCodec.write(mutable, transform(SettingsPreferencesCodec.decode(current)))
            }.toPreferences()
        }
    }
}

internal object SettingsPreferencesCodec {
    private val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    private val narrationLanguageCode = stringPreferencesKey("narration_language_code")
    private val installedLanguagePackCodes = stringSetPreferencesKey("installed_language_pack_codes")
    private val narrationChunkChars = intPreferencesKey("narration_chunk_chars")
    private val speakingSpeed = floatPreferencesKey("speaking_speed")
    private val sleepTimerMinutes = intPreferencesKey("sleep_timer_minutes")
    private val keepScreenAwake = booleanPreferencesKey("keep_screen_awake")
    private val largerText = booleanPreferencesKey("larger_text")
    private val autoScroll = booleanPreferencesKey("auto_scroll")
    private val audioCacheLimitBytes = longPreferencesKey("audio_cache_limit_bytes")

    fun decode(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        val installedPacks = normalizeInstalledPacks(
            preferences[installedLanguagePackCodes] ?: defaults.installedLanguagePackCodes,
        )
        return AppSettings(
            onboardingComplete = preferences[onboardingComplete] ?: defaults.onboardingComplete,
            installedLanguagePackCodes = installedPacks,
            narrationChunkChars = NarrationTextChunker.normalizeMaxChars(
                preferences[narrationChunkChars] ?: defaults.narrationChunkChars,
            ),
            speakingSpeed = normalizeSpeakingSpeed(preferences[speakingSpeed] ?: defaults.speakingSpeed),
            sleepTimerMinutes = (preferences[sleepTimerMinutes] ?: defaults.sleepTimerMinutes)
                .coerceIn(MIN_SLEEP_TIMER_MINUTES, MAX_SLEEP_TIMER_MINUTES),
            keepScreenAwake = preferences[keepScreenAwake] ?: defaults.keepScreenAwake,
            largerText = preferences[largerText] ?: defaults.largerText,
            autoScroll = preferences[autoScroll] ?: defaults.autoScroll,
            audioCacheLimitBytes = (preferences[audioCacheLimitBytes] ?: defaults.audioCacheLimitBytes)
                .coerceAtLeast(MIN_AUDIO_CACHE_BYTES),
        )
    }

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[onboardingComplete] = settings.onboardingComplete
        val installedPacks = normalizeInstalledPacks(settings.installedLanguagePackCodes)
        preferences[installedLanguagePackCodes] = installedPacks
        preferences[narrationChunkChars] = NarrationTextChunker.normalizeMaxChars(settings.narrationChunkChars)
        preferences[speakingSpeed] = normalizeSpeakingSpeed(settings.speakingSpeed)
        preferences[sleepTimerMinutes] = settings.sleepTimerMinutes
            .coerceIn(MIN_SLEEP_TIMER_MINUTES, MAX_SLEEP_TIMER_MINUTES)
        preferences[keepScreenAwake] = settings.keepScreenAwake
        preferences[largerText] = settings.largerText
        preferences[autoScroll] = settings.autoScroll
        preferences[audioCacheLimitBytes] = settings.audioCacheLimitBytes.coerceAtLeast(MIN_AUDIO_CACHE_BYTES)
    }

    fun decodeLegacyNarrationLanguageCode(preferences: Preferences): String =
        preferences[narrationLanguageCode]
            ?.takeIf { it in NarrationLanguage.supportedCodes }
            ?: NarrationLanguage.ENGLISH.code

    private const val MIN_SPEAKING_SPEED = 0.5f
    private const val MAX_SPEAKING_SPEED = 2f
    private const val MIN_SLEEP_TIMER_MINUTES = 0
    private const val MAX_SLEEP_TIMER_MINUTES = 24 * 60
    private const val MIN_AUDIO_CACHE_BYTES = 64L * 1024L * 1024L

    private fun normalizeSpeakingSpeed(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_SPEAKING_SPEED, MAX_SPEAKING_SPEED) else AppSettings().speakingSpeed

    private fun normalizeInstalledPacks(codes: Set<String>): Set<String> = codes
        .filterTo(linkedSetOf()) { it in NarrationLanguage.supportedCodes }
        .apply { add(NarrationLanguage.ENGLISH.code) }
}
