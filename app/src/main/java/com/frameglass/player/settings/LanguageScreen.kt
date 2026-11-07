package com.frameglass.player.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.frameglass.glass.FrameColors
import com.frameglass.player.R

private const val Arabic = "ar"
private const val English = "en"

private fun Context.locales(): LocaleManager = getSystemService(LocaleManager::class.java)

/** The language chosen for this app, or null when it follows the phone. */
private fun LocaleManager.chosenLanguage(): String? = applicationLocales.get(0)?.language

/** The system keeps this choice and recreates the activity, so there is no restart logic here. */
private fun LocaleManager.choose(language: String?) {
    applicationLocales = if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
}

@Composable
private fun rememberChosenLanguage(): String? {
    val manager = LocalContext.current.locales()
    var chosen by remember(manager) { mutableStateOf(manager.chosenLanguage()) }
    // The phone's own per-app language page can change this too.
    LifecycleResumeEffect(manager) {
        chosen = manager.chosenLanguage()
        onPauseOrDispose { }
    }
    return chosen
}

@Composable
private fun languageName(language: String?): String = stringResource(
    when (language) {
        Arabic -> R.string.app_language_arabic
        English -> R.string.app_language_english
        else -> R.string.app_language_system
    },
)

/** The value shown on the main Settings list. */
@Composable
internal fun rememberLanguageLabel(): String = languageName(rememberChosenLanguage())

@Composable
internal fun LanguageScreen() {
    val manager = LocalContext.current.locales()
    val chosen = rememberChosenLanguage()

    SettingsPage(stringResource(R.string.app_settings_language)) {
        item(key = "languages") {
            SettingsSection(stringResource(R.string.app_language_section)) {
                listOf(null, Arabic, English).forEachIndexed { index, language ->
                    if (index > 0) SettingsDivider()
                    SettingRow(
                        title = languageName(language),
                        description = if (language == null) stringResource(R.string.app_language_system_hint) else null,
                        onClick = { if (language != chosen) manager.choose(language) },
                        trailing = {
                            if (language == chosen) {
                                Icon(Icons.Rounded.Check, null, tint = FrameColors.Accent)
                            }
                        },
                    )
                }
            }
        }
    }
}
