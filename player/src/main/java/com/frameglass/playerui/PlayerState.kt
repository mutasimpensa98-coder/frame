package com.frameglass.playerui

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.media3.common.text.Cue
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.SkipSegment

internal enum class FailureKind { MISSING, UNSUPPORTED, DAMAGED, INCOMPLETE, OTHER }

/** A plain reason for the failure screen, never a raw exception. [codec] names what could not play. */
internal data class PlayerFailure(val kind: FailureKind, val codec: String? = null)

/** F-S2. [seconds] is null when auto-play is off or was cancelled, so nothing counts down. */
internal data class NextCard(val label: String?, val seconds: Int?, val fromCredits: Boolean)

/** F-T6. [endsAtElapsed] is on the elapsed realtime clock, and null means end of episode. */
internal data class SleepTimer(val endsAtElapsed: Long?)

/** Playback as the screens see it. Written by [PlayerSession] on the main thread only. */
@Stable
internal class PlayerState {
    var item by mutableStateOf<PlaybackItem?>(null)
    var failure by mutableStateOf<PlayerFailure?>(null)

    /** Nothing is left to show, so the activity should close. */
    var finished by mutableStateOf(false)

    var isPlaying by mutableStateOf(false)

    /** Wanted, not actual: stays true through buffering, so the button does not flicker. */
    var playWhenReady by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var heldSpeed by mutableStateOf(false)

    /** Counts seeks this file could not honour, so the activity can say why nothing moved. */
    var refusedSeeks by mutableIntStateOf(0)

    var audioTracks by mutableStateOf(emptyList<TrackRow>())
    var textTracks by mutableStateOf(emptyList<TrackRow>())
    var hasTextFiles by mutableStateOf(false)
    var subtitleEncoding by mutableStateOf<String?>(null)
    var subtitleDelayMs by mutableLongStateOf(0L)
    var audioDelayMs by mutableLongStateOf(0L)

    /** Product name of the Bluetooth output, when one is connected. */
    var audioDevice by mutableStateOf<String?>(null)

    /** A subtitle key is set and online features are on. */
    var onlineAvailable by mutableStateOf(false)
    var online by mutableStateOf(OnlineSearch())

    var segment by mutableStateOf<SkipSegment?>(null)
    var next by mutableStateOf<NextCard?>(null)
    var sleep by mutableStateOf<SleepTimer?>(null)

    var aspectMode by mutableStateOf(AspectMode.FIT)
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    var settings by mutableStateOf(AppSettings())

    /** Text outline for cue subtitles. AppSettings has no field for it, so :player stores it. */
    var subtitleOutline by mutableStateOf(true)

    var videoDecoder by mutableStateOf("")
    var audioDecoder by mutableStateOf("")

    /** Set by the manual mark in the More sheet, so the row can offer the opposite. */
    var markedWatched by mutableStateOf(false)

    fun clearForNewItem() {
        failure = null
        finished = false
        isPlaying = false
        buffering = true
        positionMs = 0L
        bufferedMs = 0L
        durationMs = 0L
        heldSpeed = false
        audioTracks = emptyList()
        textTracks = emptyList()
        hasTextFiles = false
        online = OnlineSearch()
        subtitleEncoding = null
        segment = null
        next = null
        panX = 0f
        panY = 0f
        videoDecoder = ""
        audioDecoder = ""
        markedWatched = false
    }
}

internal enum class PlayerSheet(@StringRes val title: Int, val parent: PlayerSheet? = null) {
    SUBTITLES(R.string.player_subtitles),
    SUBTITLE_STYLE(R.string.player_subtitle_style, SUBTITLES),
    SUBTITLE_ENCODING(R.string.player_subtitle_encoding, SUBTITLES),
    SUBTITLE_ONLINE(R.string.player_online_title, SUBTITLES),
    AUDIO(R.string.player_audio),
    SPEED(R.string.player_speed_title),
    EPISODES(R.string.player_episodes),
    MORE(R.string.player_more),
    INFO(R.string.player_info, MORE),
    SLEEP(R.string.player_sleep_timer, MORE),
    INTRO(R.string.player_set_intro, MORE),
}

/** What a gesture is telling the viewer right now, as minimal as image 09. */
internal sealed interface Feedback {
    data class Skip(val seconds: Int, val forward: Boolean) : Feedback
    data class Seek(val deltaMs: Long) : Feedback
    data class Brightness(val level: Float) : Feedback
    data class Volume(val level: Float) : Feedback
    data class Zoom(val zoom: Float) : Feedback
    data class Message(val text: String) : Feedback
}

/** Seeking in progress. Nothing reaches the player until the finger lifts. */
internal data class Scrub(val positionMs: Long, val fromGesture: Boolean)

/** State that only the two Compose layers share. Survives nothing but the activity. */
@Stable
internal class PlayerUiState {
    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)

    /** A tap while locked brings the unlock button back for a moment. */
    var unlockVisible by mutableStateOf(false)

    var sheet by mutableStateOf<PlayerSheet?>(null)
    var feedback by mutableStateOf<Feedback?>(null)
    var scrub by mutableStateOf<Scrub?>(null)
    var inPip by mutableStateOf(false)

    /** Bumped by every touch on the controls, to restart the auto-hide timer. */
    var interaction by mutableIntStateOf(0)

    /** Pixels from the bottom edge up to the top of the controls, measured even while they fade. */
    var chromeHeight by mutableIntStateOf(0)

    /** What subtitles must clear right now: [chromeHeight] while the controls show, otherwise 0. */
    var subtitleClearance by mutableIntStateOf(0)

    /** Where the seek bar is, for the preview above it. In the root of the controls layer. */
    var seekBarBounds by mutableStateOf<Rect?>(null)

    /** Control clusters in window pixels, filled only while [VideoBlurHook.ENABLED] is on. */
    val blurRects = mutableStateMapOf<String, Rect>()

    var introStartMs by mutableStateOf<Long?>(null)
    var introEndMs by mutableStateOf<Long?>(null)

    fun touch() {
        interaction++
    }

    fun toggleControls() {
        controlsVisible = !controlsVisible
        touch()
    }

    fun openSheet(sheet: PlayerSheet) {
        this.sheet = sheet
    }

    fun closeSheet() {
        sheet = null
        controlsVisible = true
        touch()
    }

    fun show(message: String) {
        feedback = Feedback.Message(message)
    }
}

/** What the session needs from the window showing it. */
internal interface PlayerWindow {
    /** Keeps the window out of screenshots and recents. Turned on before a private frame can show. */
    fun setSecure(secure: Boolean)

    fun showLiveCues(cues: List<Cue>)
}

/** What the composables need from the activity. */
internal interface PlayerActions {
    fun exit()
    fun lock(locked: Boolean)
    fun cycleAspect()
    fun rotate()

    /** Each returns the new level, from 0 to 1. */
    fun adjustBrightness(delta: Float): Float
    fun adjustVolume(delta: Float): Float
    fun endLevelGesture()

    fun pinch(zoomChange: Float, panX: Float, panY: Float)
    fun pinchEnded()

    fun pickSubtitleFile()
    fun pickFont()
    fun screenshot(withSubtitles: Boolean)
    fun openElsewhere()
}
