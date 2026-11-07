package com.frameglass.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameglass.data.api.AppSettings
import com.frameglass.glass.FrameTheme
import com.frameglass.player.nav.FrameRoot
import com.frameglass.player.ui.toFrame

/** A FragmentActivity because androidx.biometric shows BiometricPrompt through a fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
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
                FrameRoot()
            }
        }
    }
}
