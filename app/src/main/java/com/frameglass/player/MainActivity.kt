package com.frameglass.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AppLocale
import com.frameglass.data.api.AppSettings
import com.frameglass.glass.FrameTheme
import com.frameglass.player.nav.AppLinks
import com.frameglass.player.nav.FrameRoot
import com.frameglass.player.nav.Route
import com.frameglass.player.ui.toFrame

/** A FragmentActivity because androidx.biometric shows BiometricPrompt through a fragment. */
class MainActivity : FragmentActivity() {
    /** A page asked for by an intent, such as the subtitle keys from the player. Cleared once FrameRoot opened it. */
    private var link by mutableStateOf<Route?>(null)

    /** Android 10 to 12 have no per-app language, so the app applies its own choice here. A no-op from Android 13. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // After a recreation the restored back stack already holds the page.
        if (savedInstanceState == null) link = AppLinks.routeOf(intent?.action)
        val ui = (application as FrameApp).ui
        setContent {
            val loaded by ui.settings.collectAsStateWithLifecycle()
            val settings = loaded ?: AppSettings()
            FrameTheme(
                mode = settings.themeMode.toFrame(),
                accent = settings.accent.toFrame(),
                coverTint = settings.coverTint,
            ) {
                // Behind every screen change, so a light theme shows no dark flash between pages.
                val canvas = FrameTheme.colors.canvas
                LaunchedEffect(canvas) { window.setBackgroundDrawable(canvas.toArgb().toDrawable()) }
                FrameRoot(link = link, onLinkOpened = { link = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppLinks.routeOf(intent.action)?.let { link = it }
    }
}
