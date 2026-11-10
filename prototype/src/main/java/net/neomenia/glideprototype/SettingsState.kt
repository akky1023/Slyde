package net.neomenia.glideprototype

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.*

/** Observe the original preference file, including changes from the IME and editors. */
@Stable
internal class SettingsState(val preferences: SharedPreferences) {
    private var revision by mutableIntStateOf(0)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }

    fun boolean(key: String, fallback: Boolean = false): Boolean {
        revision
        return preferences.getBoolean(key, fallback)
    }

    fun int(key: String, fallback: Int): Int {
        revision
        return preferences.getInt(key, fallback)
    }

    fun setBoolean(key: String, value: Boolean) {
        preferences.edit {
            putBoolean(key, value)
            if (key == "rememberInputMode" && !value) {
                remove("savedJapaneseInputMode")
                remove("inputModeHiddenAt")
            }
        }
    }

    fun setInt(key: String, value: Int) { preferences.edit { putInt(key, value) } }

    fun halfWidthParentheses(): Boolean {
        revision
        return InputPreferences.halfWidthParentheses(preferences)
    }
}

@Composable
internal fun rememberSettingsState(preferences: SharedPreferences): SettingsState {
    val state = remember(preferences) { SettingsState(preferences) }
    DisposableEffect(state) {
        preferences.registerOnSharedPreferenceChangeListener(state.listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(state.listener) }
    }
    return state
}
