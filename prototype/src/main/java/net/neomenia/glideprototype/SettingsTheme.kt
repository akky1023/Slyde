package net.neomenia.glideprototype

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Share the existing day/night palette with the View-based editor screens. */
@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val mode = rememberSettingsState(InputPreferences.get(context)).int(ThemeMode.KEY, ThemeMode.SYSTEM)
    val palette = remember(context, configuration, mode) { ThemeMode.colors(context) }
    fun color(resource: Int) = Color(palette.getColor(resource))
    val dark = palette.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = base.copy(
        primary = color(R.color.settings_primary),
        onPrimary = color(R.color.settings_on_primary),
        primaryContainer = color(R.color.settings_primary_container),
        onPrimaryContainer = color(R.color.settings_on_primary_container),
        secondary = color(R.color.settings_secondary),
        onSecondary = color(R.color.settings_on_secondary),
        secondaryContainer = color(R.color.settings_secondary_container),
        onSecondaryContainer = color(R.color.settings_on_secondary_container),
        tertiary = color(R.color.settings_tertiary),
        onTertiary = color(R.color.settings_on_tertiary),
        tertiaryContainer = color(R.color.settings_tertiary_container),
        onTertiaryContainer = color(R.color.settings_on_tertiary_container),
        error = color(R.color.settings_error),
        onError = color(R.color.settings_on_error),
        errorContainer = color(R.color.settings_error_container),
        onErrorContainer = color(R.color.settings_on_error_container),
        background = color(R.color.settings_background),
        onBackground = color(R.color.settings_on_background),
        surface = color(R.color.settings_surface),
        onSurface = color(R.color.settings_on_surface),
        surfaceVariant = color(R.color.settings_surface_variant),
        onSurfaceVariant = color(R.color.settings_on_surface_variant),
        outline = color(R.color.settings_outline),
        outlineVariant = color(R.color.settings_outline_variant),
        inverseSurface = color(R.color.settings_inverse_surface),
        inverseOnSurface = color(R.color.settings_inverse_on_surface),
        inversePrimary = color(R.color.settings_inverse_primary),
        surfaceDim = color(R.color.settings_surface_dim),
        surfaceBright = color(R.color.settings_surface_bright),
        surfaceContainerLowest = color(R.color.settings_surface_container_lowest),
        surfaceContainerLow = color(R.color.settings_surface_container_low),
        surfaceContainer = color(R.color.settings_surface_container),
        surfaceContainerHigh = color(R.color.settings_surface_container_high),
        surfaceContainerHighest = color(R.color.settings_surface_container_highest),
    ), content = content)
}
