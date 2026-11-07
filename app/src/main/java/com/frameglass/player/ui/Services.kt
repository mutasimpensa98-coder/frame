package com.frameglass.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.frameServices
import com.frameglass.player.model.ServiceState
import com.frameglass.player.model.ServiceSwitch

/** The TMDB and IMDb switches with their keys, or null until all of it has loaded, so nothing flashes. */
@Composable
internal fun rememberServiceState(): ServiceState? {
    val loaded by rememberUiModel().settings.collectAsStateWithLifecycle()
    val store = LocalContext.current.frameServices.settings
    val tmdbKey by remember(store) { store.hasKey(ApiKeyKind.TMDB) }.collectAsStateWithLifecycle<Boolean?>(null)
    val omdbKey by remember(store) { store.hasKey(ApiKeyKind.OMDB) }.collectAsStateWithLifecycle<Boolean?>(null)
    val settings = loaded ?: return null
    return ServiceState(
        online = settings.onlineFeatures,
        tmdb = ServiceSwitch(tmdbKey ?: return null, settings.tmdbEnabled),
        imdb = ServiceSwitch(omdbKey ?: return null, settings.imdbEnabled),
    )
}
