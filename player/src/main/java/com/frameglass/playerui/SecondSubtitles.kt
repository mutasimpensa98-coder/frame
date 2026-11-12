package com.frameglass.playerui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.frameglass.engine.CueTimeline
import com.frameglass.engine.FrameRateFix
import com.frameglass.engine.SubtitleCues
import com.frameglass.engine.SubtitleEncoding
import com.frameglass.engine.SubtitleSync
import com.frameglass.engine.SyncAligner
import com.frameglass.engine.SyncOutcome
import com.frameglass.engine.SyncProblem
import com.frameglass.engine.TextTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/** Where a second subtitle comes from: a text file, or a text track inside the video. */
internal sealed interface SecondSource {
    data class FileSource(val path: String, val mimeType: String) : SecondSource
    data class Embedded(val video: Uri, val ordinal: Int) : SecondSource
}

/**
 * Dual subtitles (waiver of 2026-11-12). Media3 renders one text track, so the second one is read by the app and
 * drawn on a cue overlay of its own, smaller, at the top or just above the main one. Its clock follows the main
 * subtitle delay, or runs on its own delay, which its own sync can set. Main thread only, like [PlayerSession].
 */
@OptIn(UnstableApi::class)
internal class SecondSubtitles(
    private val app: Context,
    private val scope: CoroutineScope,
    private val player: ExoPlayer,
    private val state: PlayerState,
    private val prefs: SharedPreferences,
    private val window: () -> PlayerWindow?,
) {
    /** As read, before a frame rate fix. */
    private var base: CueTimeline<Cue>? = null
    private var timeline: CueTimeline<Cue>? = null
    private var ratio = 1.0
    private var loadJob: Job? = null
    private var syncJob: Job? = null
    private var clock: Job? = null
    private var redraw = true

    init {
        state.secondPlacement = SecondPlacement.entries.firstOrNull { it.name == prefs.getString(KEY_PLACEMENT, null) }
            ?: SecondPlacement.TOP
    }

    /** A new window has no cues yet. */
    fun redraw() {
        redraw = true
    }

    /** The second subtitle stops and nothing is forgotten, as when a new item opens. */
    fun clear() {
        loadJob?.cancel()
        syncJob?.cancel()
        clock?.cancel()
        clock = null
        if (state.second != null) window()?.showSecondCues(emptyList())
        base = null
        timeline = null
        ratio = 1.0
        state.second = null
        state.secondSync = null
        state.secondFollowsMain = true
        state.secondDelayMs = 0L
    }

    /** The owner's choice from the sheet. It is remembered for the series by language (F-S6). */
    fun choose(item: SecondTarget, row: TrackRow, source: SecondSource, memory: String, encoding: String?) {
        prefs.edit().putString(memoryKey(item), memory).apply()
        show(item, row, source, encoding, syncAfter = source is SecondSource.FileSource)
    }

    /** Off from the sheet: the next episode starts with no second subtitle either. */
    fun turnOff(item: SecondTarget) {
        prefs.edit().remove(memoryKey(item)).apply()
        clear()
    }

    /** What this series showed last time, by language, or null. */
    fun storedFor(item: SecondTarget): String? = prefs.getString(memoryKey(item), null)

    private fun memoryKey(item: SecondTarget): String = SecondPicker.memoryKey(item.titleId, item.playableKey)

    fun show(item: SecondTarget, row: TrackRow, source: SecondSource, encoding: String?, syncAfter: Boolean) {
        val key = SecondKey.of(row) ?: return
        clear()
        val isFile = source is SecondSource.FileSource
        state.second = SecondShown(key, row.language, row.label, loading = true)
        state.secondFollowsMain = SecondTiming.followsMainAtStart(isFile)
        loadJob = scope.launch {
            when (source) {
                is SecondSource.FileSource -> {
                    val read = withContext(Dispatchers.IO) {
                        runCatching {
                            TextTracks.readFile(SubtitleEncoding.toUtf8(app, File(source.path), encoding), source.mimeType)
                        }.getOrNull()
                    }
                    ensureActive()
                    if (read == null) {
                        state.second = state.second?.copy(loading = false, failed = true)
                        return@launch
                    }
                    setBase(read)
                    state.second = state.second?.copy(loading = false)
                    startClock()
                    if (syncAfter) startSync(item, source, encoding)
                }
                is SecondSource.Embedded -> {
                    startClock()
                    TextTracks.readEmbedded(app, source.video, source.ordinal)
                        .catch { /* Read errors end the flow; what was found stays. */ }
                        .collect { setBase(it) }
                    ensureActive()
                    state.second = state.second?.copy(loading = false, failed = base?.isEmpty != false)
                }
            }
        }
    }

    private fun setBase(read: CueTimeline<Cue>) {
        base = read
        timeline = if (ratio == 1.0) read else CueTimeline(SecondTiming.stretched(read.entries, ratio))
    }

    fun setPlacement(placement: SecondPlacement) {
        state.secondPlacement = placement
        prefs.edit().putString(KEY_PLACEMENT, placement.name).apply()
        redraw = true
    }

    /** Leaving the main delay starts the own delay where the main one is, so nothing jumps. */
    fun setFollowsMain(follows: Boolean) {
        if (state.secondFollowsMain == follows) return
        if (!follows) state.secondDelayMs = state.subtitleDelayMs
        state.secondFollowsMain = follows
        cancelSync()
    }

    fun setOwnDelay(ms: Long) {
        cancelSync()
        state.secondFollowsMain = false
        state.secondDelayMs = ms.coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
    }

    private fun cancelSync() {
        if (state.secondSync == SyncState.Running) {
            syncJob?.cancel()
            state.secondSync = null
        }
    }

    /** Positive delay shows later. A seek shows within [MAX_WAIT_MS], as for the live main subtitle. */
    private fun startClock() {
        if (clock?.isActive == true) return
        clock = scope.launch {
            var shown: List<Cue>? = null
            while (isActive) {
                val current = timeline
                val delayMs = SecondTiming.delayMs(state.secondFollowsMain, state.subtitleDelayMs, state.secondDelayMs)
                val positionUs = SecondTiming.lookupUs(player.currentPosition, delayMs)
                val cues = current?.at(positionUs) ?: emptyList()
                if (cues !== shown || redraw) {
                    shown = cues
                    redraw = false
                    window()?.showSecondCues(cues)
                }
                val nextUs = current?.nextChange(positionUs)
                val waitMs = if (nextUs == null) {
                    MAX_WAIT_MS
                } else {
                    ((nextUs - positionUs) / 1000 / player.playbackParameters.speed).toLong()
                }
                delay(waitMs.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS))
            }
        }
    }

    /** "Sync with the speech" for the second file: its own delay, and a frame rate fix stretches it in memory. */
    fun syncNow(item: SecondTarget, source: SecondSource.FileSource, encoding: String?) = startSync(item, source, encoding)

    private fun startSync(item: SecondTarget, source: SecondSource.FileSource, encoding: String?) {
        if (SubtitleCues.kindOf(source.mimeType) == null) return
        syncJob?.cancel()
        state.secondSync = SyncState.Running
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val audio = audioGroups.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
        val fps = player.videoFormat?.frameRate?.takeIf { it > 0f }
        val duration = max(state.durationMs, item.durationMs)
        val video = item.video
        val key = state.second?.key
        syncJob = scope.launch {
            val outcome = try {
                val readable = withContext(Dispatchers.IO) { SubtitleEncoding.toUtf8(app, File(source.path), encoding) }
                SubtitleSync.run(app, video, readable, source.mimeType, duration, fps, audio)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Even an Error must stay here: an escape from this scope would end playback.
                SyncOutcome.Unavailable(SyncProblem.NO_CUES)
            }
            ensureActive()
            if (state.second?.key != key) return@launch
            state.secondSync = when (outcome) {
                is SyncOutcome.Fit -> applyFit(outcome)
                SyncOutcome.Unsure -> SyncState.Unsure
                is SyncOutcome.Unavailable -> SyncState.Unavailable(outcome.problem)
            }
        }
    }

    private fun applyFit(fit: SyncOutcome.Fit): SyncState {
        val target = ((fit.offsetMs / SYNC_STEP_MS.toDouble()).roundToLong() * SYNC_STEP_MS).coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
        if (fit.fix != FrameRateFix.NONE) {
            ratio = fit.fix.ratio
            base?.let(::setBase)
        }
        state.secondFollowsMain = false
        state.secondDelayMs = target
        return SyncState.Applied(target, fit.fix)
    }

    fun release() {
        clear()
    }

    private companion object {
        const val KEY_PLACEMENT = "second_placement"
        const val MIN_WAIT_MS = 15L
        const val MAX_WAIT_MS = 200L
        const val SYNC_STEP_MS = 10L
        const val MAX_DELAY_MS = SyncAligner.MAX_OFFSET_MS
    }
}

/** What [SecondSubtitles] needs of the playing item. */
internal data class SecondTarget(val titleId: Long?, val playableKey: String, val durationMs: Long, val video: Uri)
