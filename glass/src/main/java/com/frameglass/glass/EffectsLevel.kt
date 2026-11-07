package com.frameglass.glass

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** How much glass and motion the phone can afford right now (brief 7.9). */
enum class EffectsLevel {
    /** Everything on. */
    Full,

    /** Battery saver, Remove Animations or SEVERE heat: no refraction, tilt or heavy motion. */
    Degraded,
}

internal val LocalEffectsLevel = compositionLocalOf<EffectsLevel?> { null }

/** The current [EffectsLevel], kept up to date. */
@Composable
fun rememberEffectsLevel(): EffectsLevel = LocalEffectsLevel.current ?: observeEffectsLevel()

@Composable
internal fun observeEffectsLevel(): EffectsLevel {
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val resolver = context.contentResolver
    var saver by remember { mutableStateOf(power.isPowerSaveMode) }
    var noAnimations by remember { mutableStateOf(animationsRemoved(resolver)) }
    var hot by remember { mutableStateOf(isSevere(power.currentThermalStatus)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                saver = power.isPowerSaveMode
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                noAnimations = animationsRemoved(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val thermal = PowerManager.OnThermalStatusChangedListener { hot = isSevere(it) }
        power.addThermalStatusListener(context.mainExecutor, thermal)
        saver = power.isPowerSaveMode
        noAnimations = animationsRemoved(resolver)
        onDispose {
            context.unregisterReceiver(receiver)
            resolver.unregisterContentObserver(observer)
            power.removeThermalStatusListener(thermal)
        }
    }
    return if (saver || noAnimations || hot) EffectsLevel.Degraded else EffectsLevel.Full
}

private fun animationsRemoved(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun isSevere(status: Int): Boolean = status >= PowerManager.THERMAL_STATUS_SEVERE
