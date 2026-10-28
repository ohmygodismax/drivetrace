package dev.drivetrace.data.preferences

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore

val Application.preferences by preferencesDataStore("settings")
