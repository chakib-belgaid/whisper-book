package com.whisperbook.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Process-wide preferences store. Access this property from the application context. */
val Context.whisperBookSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "whisperbook_settings",
)
