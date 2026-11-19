package com.frameglass.player.settings

import androidx.activity.compose.LocalActivity
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
import com.frameglass.data.api.AppLocale
import com.frameglass.glass.FrameColors
import com.frameglass.player.R

private const val Arabic = "ar"
private const val English = "en"

/** The language chosen for this app, or null when it follows the phone. */
@Composable
private fun rememberChosenLanguage(): String? {
    val context = LocalContext.current.applicationContext
    var chosen by remember(context) { mutableStateOf(AppLocale.chosen(context)) }
    // From Android 13 the phone's own per-app language page can change this too.
    LifecycleResumeEffect(context) {
        chosen = AppLocale.chosen(context)
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

/**
 * From Android 13 the system keeps the choice and recreates the activity. Android 10 to 12 have no per-app language,
 * so AppLocale keeps it there and this screen recreates its own activity, which comes back on this page in the new
 * language (waiver of 2026-11-19).
 */
@Composable
internal fun LanguageScreen() {
    val context = LocalContext.current.applicationContext
    val activity = LocalActivity.current
    val chosen = rememberChosenLanguage()

    SettingsPage(stringResource(R.string.app_settings_language)) {
        item(key = "languages") {
            SettingsSection(stringResource(R.string.app_language_section)) {
                listOf(null, Arabic, English).forEachIndexed { index, language ->
                    if (index > 0) SettingsDivider()
                    SettingRow(
                        title = languageName(language),
                        description = if (language == null) stringResource(R.string.app_language_system_hint) else null,
                        onClick = {
                            if (language != chosen) {
                                AppLocale.choose(context, language)
                                if (!AppLocale.keptBySystem) activity?.recreate()
                            }
                        },
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
