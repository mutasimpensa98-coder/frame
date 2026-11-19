package com.frameglass.glass

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

/** How much glass and motion the phone can afford right now (brief 7.9). */
enum class EffectsLevel {
    /** Everything on. */
    Full,

    /** Battery saver, Remove Animations or SEVERE heat: no refraction, tilt or heavy motion. */
    Degraded,
}

internal val LocalEffectsLevel = compositionLocalOf<EffectsLevel?> { null }

/** What this phone's Android version can draw, whatever its battery or heat (waiver of 2026-11-19). */
internal object GlassSupport {
    /**
     * Haze's glass and the app's own AGSL both need RuntimeShader, API 33. Below it no surface asks Haze for
     * anything: every one keeps the tint and rim of brief 7.9, and sheets and menus turn solid.
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val liquid: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * The light tick of a slider step, a tab change or an armed pull. Android's own segment tick arrives in API 34, and an
 * older phone answers it with nothing, so those get the text handle tick, the nearest they have.
 */
val SegmentTickHaptic: HapticFeedbackType
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackType.SegmentTick
    } else {
        HapticFeedbackType.TextHandleMove
    }

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
        // The export flag arrives in API 33. Below it a plain registration is as safe here: only the system may
        // send this broadcast. ContextCompat is not used, because its fallback needs a permission the manifest removes.
        val saverChanged = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, saverChanged, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, saverChanged)
        }
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
