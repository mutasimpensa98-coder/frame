package com.frameglass.glass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs

private const val SamplingPeriodUs = 33_333
private const val MinEventNanos = 33_333_000L
private const val RestRate = 0.02f
private const val Smoothing = 0.25f
private const val FullTilt = 0.35f
private const val DeadZone = 0.01f

/** Device tilt away from how it is being held, from -1 to 1 on each axis, at 30 Hz at most. */
@Stable
internal class GlassTilt(private val sensors: SensorManager?) : SensorEventListener {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    private var consumers = 0
    private var resumed = false
    private var listening = false
    private var lastEventNanos = 0L
    private var restX = Float.NaN
    private var restY = Float.NaN
    private val matrix = FloatArray(9)

    fun setResumed(value: Boolean) {
        resumed = value
        sync()
    }

    fun acquire() {
        consumers++
        sync()
    }

    fun release() {
        consumers--
        sync()
    }

    // Registered only while the app is resumed and a blurred surface wants it.
    private fun sync() {
        val manager = sensors ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: return
        val wanted = resumed && consumers > 0
        if (wanted == listening) return
        listening = wanted
        if (wanted) {
            manager.registerListener(this, sensor, SamplingPeriodUs)
        } else {
            manager.unregisterListener(this)
            restX = Float.NaN
            restY = Float.NaN
            x = 0f
            y = 0f
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.timestamp - lastEventNanos < MinEventNanos) return
        lastEventNanos = event.timestamp
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        // The last row is world up in device axes, which unlike pitch and roll has no gimbal lock.
        val upX = matrix[6]
        val upY = matrix[7]
        if (restX.isNaN()) {
            restX = upX
            restY = upY
        }
        restX += (upX - restX) * RestRate
        restY += (upY - restY) * RestRate
        x = smoothed(x, (upX - restX) / FullTilt)
        y = smoothed(y, (upY - restY) / FullTilt)
    }

    private fun smoothed(current: Float, target: Float): Float {
        val next = current + (target.coerceIn(-1f, 1f) - current) * Smoothing
        return if (abs(next - current) < DeadZone) current else next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

internal val LocalGlassTilt = staticCompositionLocalOf { GlassTilt(null) }

@Composable
internal fun rememberGlassTilt(): GlassTilt {
    val context = LocalContext.current
    val tilt = remember(context) { GlassTilt(context.getSystemService(SensorManager::class.java)) }
    LifecycleResumeEffect(tilt) {
        tilt.setResumed(true)
        onPauseOrDispose { tilt.setResumed(false) }
    }
    return tilt
}
