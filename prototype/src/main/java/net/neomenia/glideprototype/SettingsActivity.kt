package net.neomenia.glideprototype

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Opening settings never initializes the conversion engines. */
class SettingsActivity : AppCompatActivity() {
    private var inputStatus by mutableStateOf("")

    override fun attachBaseContext(newBase: Context) {
        delegate.localNightMode = ThemeMode.nightMode(newBase)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = InputPreferences.get(this)
        setContent {
            SettingsTheme {
                SettingsScreen(rememberSettingsState(preferences), intent.getStringExtra("page"), inputStatus)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeMode.apply(this)
        val id = ComponentName(this, GlideInputMethodService::class.java).flattenToShortString()
        val enabled = getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.id == id }
        val selected = id == Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        inputStatus = when {
            selected -> "使用中です"
            enabled -> "有効です。キーボードを切り替えてください。"
            else -> "キーボードを有効にして切り替えてください。"
        }
    }
}
