package com.frameglass.playerui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.AutoSubtitleOutcome
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.api.SkipSegment
import com.frameglass.data.api.SubtitleResult
import com.frameglass.data.api.TrackPrefs
import com.frameglass.data.api.WatchTarget
import com.frameglass.data.api.autoFetch
import com.frameglass.data.api.frameServices
import com.frameglass.data.api.segmentsAfterFetch
import com.frameglass.data.api.sites
import com.frameglass.engine.CueTimeline
import com.frameglass.engine.DecoderLadder
import com.frameglass.engine.DecoderProbe
import com.frameglass.engine.EngineConfig
import com.frameglass.engine.FramePlayer
import com.frameglass.engine.FrameRateFix
import com.frameglass.engine.HdrReport
import com.frameglass.engine.LiveSubtitle
import com.frameglass.engine.SubtitleCues
import com.frameglass.engine.SubtitleEncoding
import com.frameglass.engine.SubtitleSync
import com.frameglass.engine.SyncAligner
import com.frameglass.engine.SyncOutcome
import com.frameglass.engine.SyncProblem
import com.frameglass.engine.VideoFacts
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Facts for the info panel (F-T5, F-P20). Read once a second while the panel is open. */
internal data class StreamFacts(
    val videoCodec: String?,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Long,
    val hdr: HdrReport,
    val videoDecoder: String,
    val hardware: Boolean,
    val droppedFrames: Int,
    val audioCodec: String?,
    val channels: Int,
    val sampleRate: Int,
    val audioDecoder: String,
)

/**
 * The player without any screen: engine, position saving, track memory, skip and next episode, timers and
 * decoder fallback. It lives in [PlaybackHost], so it outlives the activity for background audio.
 */
@OptIn(UnstableApi::class)
internal class PlayerSession(context: Context) {
    private val app: Context = context.applicationContext
    private val services = app.frameServices
    private val repository = services.playback
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Never cancelled, so the last save of a closing session still reaches the disk. */
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val localPrefs = app.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    private val videoLadder = DecoderLadder.video()
    private val audioLadder = DecoderLadder.audio()

    val engine = FramePlayer(app, EngineConfig(playbackBehaviour = true))
    val player: ExoPlayer get() = engine.player
    val state = PlayerState()
    val previews = ScrubPreviews(app, main)

    private var window: PlayerWindow? = null
    private var secure = false

    private var prefs = TrackPrefs()
    private var files: List<SubtitleFile> = emptyList()

    /** Text tracks as Media3 reports them, before the live ones join for the sheet. */
    private var textRows: List<TrackRow> = emptyList()

    /** Subtitles loaded during this play, which Media3 does not know about until the next reopen. */
    private var liveFiles: List<SubtitleFile> = emptyList()
    private var liveFile: SubtitleFile? = null
    private var liveClock: Job? = null
    private var liveSwitch: Job? = null

    private var syncJob: Job? = null
    private var syncUndo: SyncUndo? = null

    /** A file added from the picker is synced once the reopen has selected it. */
    private var pendingSyncPath: String? = null

    private var openJob: Job? = null
    private var onlineJob: Job? = null
    private var autoJob: Job? = null

    /** The automatic subtitle check runs once per open, on the first tracks. */
    private var autoChecked = false

    /** The owner picked a subtitle during this play, so an automatic one arriving late must not replace it. */
    private var ownerChoseText = false
    private var vaultWatch: Job? = null
    private var ticker: Job? = null
    private var countdown: Job? = null
    private var tracksApplied = false
    private var positionKnown = false

    /** From a request until the new item is in the player, whose events still belong to the old one. */
    private var switching = false
    private var finishedMarked = false
    private var nextCancelled = false
    private var videoHidden = false
    private var audioOff = false
    private var resumeOnShow = false
    private var lastSaveAt = 0L
    private var speedBeforeHold = 1f
    private var pinchZoom = 0f
    private var bluetoothKey: String? = null
    private var loadedFontPath: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.isPlaying = isPlaying
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                syncPosition()
                savePosition()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            state.playWhenReady = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            state.buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY && !switching) {
                positionKnown = true
                syncPosition()
                // A private frame stays on the surface until the new item's first frame replaces it.
                if (state.item?.isPrivate == false) setSecure(false)
            }
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = syncPosition()

        override fun onTracksChanged(tracks: Tracks) = onTracks(tracks)

        override fun onPlayerError(error: PlaybackException) = onError(error)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (!state.heldSpeed) state.speed = playbackParameters.speed
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            state.videoDecoder = decoderName
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            state.audioDecoder = decoderName
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshAudioRoute()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshAudioRoute()
    }

    init {
        state.subtitleOutline = localPrefs.getBoolean(KEY_OUTLINE, true)
        player.addListener(playerListener)
        player.addAnalyticsListener(analytics)
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        refreshAudioRoute()
        main.launch { services.settings.settings.collect { applySettings(it) } }
        main.launch { services.subtitles.available.collect { state.onlineAvailable = it } }
        main.launch { services.subtitles.sites.collect { state.onlineSites = it } }
    }

    // ---- Window --------------------------------------------------------------------------------

    /** A window opened on a private item that is already playing starts out secure. */
    fun attachWindow(window: PlayerWindow) {
        this.window = window
        if (secure) window.setSecure(true)
    }

    /** A new player can open while the last one is still closing, so only the current window lets go. */
    fun detachWindow(window: PlayerWindow) {
        if (this.window === window) this.window = null
    }

    private fun setSecure(value: Boolean) {
        if (secure == value) return
        secure = value
        window?.setSecure(value)
    }

    // ---- Opening -------------------------------------------------------------------------------

    fun open(ref: PlayRef) {
        val alreadyOpen = state.item?.ref == ref && state.failure == null && player.currentMediaItem != null
        if (alreadyOpen && player.playbackState != Player.STATE_ENDED) {
            player.play()
            return
        }
        savePosition()
        openJob?.cancel()
        countdown?.cancel()
        prepareForNewItem()
        openJob = main.launch {
            val item = runCatching { repository.open(ref) }.getOrNull()
            if (item == null) {
                player.stop()
                switching = false
                state.item = null
                state.failure = PlayerFailure(FailureKind.MISSING)
            } else {
                start(item)
            }
        }
    }

    /** F-P15: nothing named in the intent, so this picks up what was playing when the app was killed. */
    fun openLastSession(): Job = main.launch {
        val ref = runCatching { repository.lastSession() }.getOrNull()
        if (ref == null) state.finished = true else open(ref)
    }

    private fun prepareForNewItem() {
        onlineJob?.cancel()
        autoJob?.cancel()
        autoChecked = false
        ownerChoseText = false
        forgetSync()
        pendingSyncPath = null
        clearLive()
        textRows = emptyList()
        state.clearForNewItem()
        stopTicker()
        previews.close()
        tracksApplied = false
        positionKnown = false
        switching = true
        finishedMarked = false
        nextCancelled = false
        audioOff = false
        videoLadder.reset()
        audioLadder.reset()
        engine.decoderManager.selectVideoDecoder(DecoderMode.AUTO)
        engine.decoderManager.selectAudioDecoder(DecoderMode.AUTO)
    }

    private suspend fun start(item: PlaybackItem) {
        if (item.isPrivate) setSecure(true)
        watchVault(item)
        prefs = item.prefs
        state.item = item
        state.durationMs = item.durationMs
        state.positionMs = item.resumeMs
        state.subtitleEncoding = prefs.subtitleEncoding
        val memory = AspectMemory.decode(prefs.aspectMode)
        state.aspectMode = memory.mode
        state.zoom = memory.zoom

        val configurations = withContext(Dispatchers.IO) { subtitleConfigurations(item) }
        state.hasTextFiles = files.isNotEmpty()
        player.trackSelectionParameters = baseTrackParameters()
        engine.setSubtitleDelayMs(prefs.subtitleDelayMs)
        state.subtitleDelayMs = prefs.subtitleDelayMs
        state.speed = prefs.speed
        player.setPlaybackSpeed(prefs.speed)
        engine.addBundledFont()
        player.setMediaItem(mediaItem(item, configurations), item.resumeMs)
        player.prepare()
        player.playWhenReady = true
        switching = false
        previews.bind(playbackUri(item), hash(item.uri), item.durationMs, keepOnDisk = !item.isPrivate)
        refreshSegments(item)
    }

    /** Runs only for a private item, so a public one reads nothing from the vault. */
    private fun watchVault(item: PlaybackItem) {
        vaultWatch?.cancel()
        vaultWatch = null
        if (!item.isPrivate) return
        vaultWatch = main.launch {
            services.vault.state.first { !it.unlocked }
            close()
            state.finished = true
        }
    }

    /** Skip times looked up for this play arrive after it started. A first watch still gets the button (F-S4). */
    private fun refreshSegments(item: PlaybackItem) {
        main.launch {
            val fresh = runCatching { repository.segmentsAfterFetch(item) }.getOrNull() ?: return@launch
            val current = state.item ?: return@launch
            if (current.playableKey == item.playableKey && fresh != current.segments) state.item = current.copy(segments = fresh)
        }
    }

    private fun baseTrackParameters(): TrackSelectionParameters =
        player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setPreferredTextLanguage(ARABIC)
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, prefs.subtitlesOff)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, videoHidden)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()

    private fun mediaItem(item: PlaybackItem, subtitles: List<MediaItem.SubtitleConfiguration>): MediaItem {
        val metadata = if (item.isPrivate) {
            BLANK_METADATA
        } else {
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setSubtitle(item.subtitle)
                .setArtist(item.subtitle)
                .setArtworkUri(item.posterPath?.let { Uri.fromFile(File(it)) })
                .build()
        }
        return MediaItem.Builder()
            .setUri(playbackUri(item))
            .setMediaId(item.playableKey)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    private fun playbackUri(item: PlaybackItem): Uri =
        if (item.uri.startsWith("/")) Uri.fromFile(File(item.uri)) else Uri.parse(item.uri)

    /** Runs off the main thread: text sidecars may need transcoding to UTF-8 (F-P8). */
    private fun subtitleConfigurations(item: PlaybackItem): List<MediaItem.SubtitleConfiguration> {
        val all = item.sidecars.map { SubtitleFile(it.path, it.label, it.language, it.mimeType) }.toMutableList()
        val chosen = prefs.subtitleUri?.takeIf { File(it).isFile }
        val copied = subtitleFolder(item).listFiles()?.filter { it.isFile && subtitleMime(it.name) != null }?.map { it.path }.orEmpty()
        for (path in CopiedSubtitles.extra(all.map { it.path }, chosen, copied)) {
            all += SubtitleFile(path, File(path).name, null, subtitleMime(path) ?: MimeTypes.APPLICATION_SUBRIP)
        }
        files = all
        return all.mapIndexed { index, file ->
            val source = File(file.path)
            val readable = runCatching { SubtitleEncoding.toUtf8(app, source, prefs.subtitleEncoding) }.getOrDefault(source)
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(readable))
                // AssHandler needs an id, and its track lookup strips the "1:" that merging adds.
                .setId("$SIDECAR_ID$index")
                .setMimeType(file.mimeType)
                .setLanguage(file.language)
                .setLabel(file.label)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
    }

    /** Loads the same item again with changed sidecars or encoding, at the same position. */
    private fun reload() {
        val item = state.item ?: return
        val position = player.currentPosition
        val playing = player.playWhenReady
        openJob?.cancel()
        // Track rows are rebuilt by the reopen, so an Undo could no longer find what it would put back.
        forgetSync()
        // The reopen brings any live subtitle back as a normal track, through item.sidecars and prefs.
        clearLive()
        openJob = main.launch {
            val configurations = withContext(Dispatchers.IO) { subtitleConfigurations(item) }
            state.hasTextFiles = files.isNotEmpty()
            tracksApplied = false
            player.trackSelectionParameters = baseTrackParameters()
            player.setMediaItem(mediaItem(item, configurations), position)
            player.prepare()
            player.playWhenReady = playing
        }
    }

    // ---- Position, end of episode, next episode ------------------------------------------------

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = main.launch {
            while (isActive) {
                syncPosition()
                checkSleep()
                if (SystemClock.elapsedRealtime() - lastSaveAt >= SAVE_EVERY_MS) savePosition()
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun syncPosition() {
        val item = state.item ?: return
        val position = player.currentPosition
        state.positionMs = position
        state.bufferedMs = player.bufferedPosition
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0) state.durationMs = duration
        updateSegment(item, position)
    }

    private fun updateSegment(item: PlaybackItem, position: Long) {
        val segment = item.segments.firstOrNull { position >= it.startMs && position < it.endMs }
        if (segment != state.segment) state.segment = segment
        val next = state.next
        if (next?.fromCredits == true && segment?.kind != SegmentKind.ED) {
            countdown?.cancel()
            state.next = null
        } else if (segment?.kind == SegmentKind.ED && next == null && !nextCancelled && item.next != null && autoNext()) {
            showNext(item, fromCredits = true)
        }
    }

    private fun sleepsAtEpisodeEnd(): Boolean = state.sleep?.let { it.endsAtElapsed == null } == true

    private fun autoNext(): Boolean = state.settings.autoPlayNext && !sleepsAtEpisodeEnd()

    /** Writes through [writes] so the save outlives the activity and this session. */
    fun savePosition() {
        val item = state.item ?: return
        if (!positionKnown || finishedMarked || state.failure != null) return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: item.durationMs
        lastSaveAt = SystemClock.elapsedRealtime()
        writes.launch { runCatching { repository.savePosition(item, position, duration) } }
    }

    private fun onEnded() {
        val item = state.item ?: return
        stopTicker()
        markFinished(item)
        when {
            sleepsAtEpisodeEnd() -> {
                state.sleep = null
                state.finished = true
            }
            item.next == null -> state.finished = true
            state.next == null -> showNext(item, fromCredits = false)
        }
    }

    private fun markFinished(item: PlaybackItem) {
        if (finishedMarked) return
        finishedMarked = true
        writes.launch { runCatching { repository.markFinished(item) } }
    }

    private fun showNext(item: PlaybackItem, fromCredits: Boolean) {
        countdown?.cancel()
        val auto = autoNext() && !nextCancelled
        state.next = NextCard(item.nextLabel, if (auto) COUNTDOWN_SECONDS else null, fromCredits)
        if (!auto) return
        countdown = main.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                state.next = state.next?.copy(seconds = seconds)
                delay(1_000)
            }
            playNext()
        }
    }

    fun playNext() {
        val item = state.item ?: return
        val next = item.next ?: return
        countdown?.cancel()
        state.next = null
        markFinished(item)
        open(next)
    }

    /** Stops the countdown. At the true end there is nothing left to show, so the player closes. */
    fun cancelNext() {
        countdown?.cancel()
        state.next = null
        nextCancelled = true
        if (player.playbackState == Player.STATE_ENDED) state.finished = true
    }

    fun skipSegment() {
        val segment = state.segment ?: return
        seekTo(segment.endMs)
    }

    // ---- Transport ------------------------------------------------------------------------------

    fun togglePlay() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    /** False when the file cannot seek, so the caller shows no movement that did not happen. */
    fun seekTo(positionMs: Long): Boolean {
        if (!canSeek()) {
            state.refusedSeeks++
            return false
        }
        val duration = state.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else max(positionMs, 0)
        player.seekTo(target)
        state.positionMs = target
        state.item?.let { updateSegment(it, target) }
        return true
    }

    fun seekBy(deltaMs: Long): Boolean = seekTo(player.currentPosition + deltaMs)

    /** Media3 answers any seek in unseekable media by jumping to the start, as with a partial download. */
    private fun canSeek(): Boolean {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return true
        val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
        return window.isPlaceholder || window.isSeekable
    }

    fun setSpeed(speed: Float) {
        val rounded = ((speed * SPEED_STEPS).roundToInt() / SPEED_STEPS).coerceIn(MIN_SPEED, MAX_SPEED)
        state.speed = rounded
        player.setPlaybackSpeed(rounded)
        savePrefs { copy(speed = rounded) }
    }

    fun beginHoldSpeed() {
        if (state.heldSpeed) return
        speedBeforeHold = state.speed
        state.heldSpeed = true
        player.setPlaybackSpeed(HOLD_SPEED)
    }

    fun endHoldSpeed() {
        if (!state.heldSpeed) return
        state.heldSpeed = false
        player.setPlaybackSpeed(speedBeforeHold)
    }

    fun setSleep(timer: SleepTimer?) {
        state.sleep = timer
    }

    private fun checkSleep() {
        val ends = state.sleep?.endsAtElapsed ?: return
        if (SystemClock.elapsedRealtime() >= ends) {
            state.sleep = null
            player.pause()
        }
    }

    // ---- Tracks ---------------------------------------------------------------------------------

    private fun onTracks(tracks: Tracks) {
        val audio = TrackReader.rows(tracks, C.TRACK_TYPE_AUDIO, emptyList())
        textRows = TrackReader.rows(tracks, C.TRACK_TYPE_TEXT, files)
        state.audioTracks = audio
        publishTextRows()
        engine.keepLiveScript()
        if (tracks.isEmpty) return
        if (!tracksApplied) {
            tracksApplied = true
            applyMemory(audio, textRows)
        }
        if (!autoChecked && !switching) {
            autoChecked = true
            startAutoSubtitle()
        }
        pendingSyncPath?.let { path ->
            val row = textRows.firstOrNull { it.sidecarPath == path && it.selected } ?: return@let
            pendingSyncPath = null
            fileOf(row)?.let(::startSync)
        }
        checkPlayable(tracks)
    }

    private fun publishTextRows() {
        state.textTracks = textRows + liveFiles.mapIndexed { index, file ->
            TrackRow(
                group = LIVE_GROUP,
                track = index,
                language = file.language,
                label = file.label,
                codec = TrackReader.codecName(Format.Builder().setSampleMimeType(file.mimeType).build()),
                channels = 0,
                forced = false,
                signs = false,
                sidecarPath = file.path,
                isDefault = false,
                selected = file == liveFile,
                supported = true,
            )
        }
    }

    private fun applyMemory(audio: List<TrackRow>, text: List<TrackRow>) {
        val tracks = player.currentTracks
        val builder = player.trackSelectionParameters.buildUpon()
        TrackPicker.audio(audio, prefs)?.let { row ->
            tracks.groups.getOrNull(row.group)?.let { group ->
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, row.track))
            }
        }
        // A live subtitle may already show when the first tracks arrive, and two must not show at once.
        when (val choice = if (liveFile != null) SubtitleChoice.Off else TrackPicker.subtitle(text, prefs)) {
            SubtitleChoice.Keep -> Unit
            SubtitleChoice.Off -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            is SubtitleChoice.Track -> tracks.groups.getOrNull(choice.row.group)?.let { group ->
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.row.track))
            }
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Selecting nothing would leave a blank screen, so that counts as a decoder failure (F-P18). */
    private fun checkPlayable(tracks: Tracks) {
        if (state.failure != null) return
        if (!videoHidden && tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
            val next = videoLadder.advance()
            if (next != null) {
                engine.decoderManager.selectVideoDecoder(next)
            } else {
                val format = TrackReader.firstFormat(tracks, C.TRACK_TYPE_VIDEO)
                player.pause()
                state.failure = PlayerFailure(FailureKind.UNSUPPORTED, format?.let(TrackReader::codecName))
            }
            return
        }
        if (!audioOff && tracks.containsType(C.TRACK_TYPE_AUDIO) && !tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
            audioLadder.advance()?.let { engine.decoderManager.selectAudioDecoder(it) }
        }
    }

    /** A choice from the sheet. A subtitle file chosen here is then synced to the speech. */
    fun selectTrack(type: Int, row: TrackRow) {
        if (type != C.TRACK_TYPE_TEXT) {
            applyTrack(type, row)
            return
        }
        ownerChoseText = true
        dropSync()
        val file = fileOf(row)
        if (row.group == LIVE_GROUP) {
            file?.let { live -> selectLive(live) { startSync(live) } }
            return
        }
        applyTrack(type, row)
        file?.let(::startSync)
    }

    private fun applyTrack(type: Int, row: TrackRow) {
        if (row.group == LIVE_GROUP) {
            liveFiles.getOrNull(row.track)?.let { selectLive(it) }
            return
        }
        val group = player.currentTracks.groups.getOrNull(row.group) ?: return
        if (type == C.TRACK_TYPE_AUDIO) audioOff = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, row.track))
            .build()
        if (type == C.TRACK_TYPE_AUDIO) {
            val stored = TrackMemory.encode(state.audioTracks, row)
            savePrefs { copy(audioLanguage = stored) }
        } else {
            stopLive()
            publishTextRows()
            val stored = TrackMemory.encode(state.textTracks, row)
            savePrefs { copy(subtitleLanguage = stored, subtitlesOff = false, subtitleUri = row.sidecarPath) }
        }
    }

    fun audioOff() {
        audioOff = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
    }

    fun subtitlesOff() {
        ownerChoseText = true
        dropSync()
        stopLive()
        publishTextRows()
        disableTextTracks()
        savePrefs { copy(subtitlesOff = true) }
    }

    private fun disableTextTracks() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** The owner's own change. It stops a sync still running, so the result cannot overwrite it. */
    fun setSubtitleDelay(ms: Long) {
        if (state.sync == SyncState.Running) {
            syncJob?.cancel()
            syncJob = null
            state.sync = null
        }
        applyDelay(ms)
    }

    private fun applyDelay(ms: Long) {
        val value = ms.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        state.subtitleDelayMs = value
        engine.setSubtitleDelayMs(value)
        savePrefs { copy(subtitleDelayMs = value) }
    }

    fun setAudioDelay(ms: Long) {
        val value = ms.coerceIn(-MAX_AUDIO_DELAY_MS, MAX_AUDIO_DELAY_MS)
        state.audioDelayMs = value
        engine.setAudioDelayMs(value)
        val key = bluetoothKey ?: return
        writes.launch { runCatching { repository.saveBluetoothDelay(key, value) } }
    }

    fun setSubtitleEncoding(name: String?) {
        state.subtitleEncoding = name
        savePrefs { copy(subtitleEncoding = name) }
        reload()
    }

    /** F-P7. The picked file is copied in, so it survives the document grant ending. */
    fun addSubtitle(uri: Uri, onRejected: () -> Unit) {
        val item = state.item ?: return
        ownerChoseText = true
        main.launch {
            val path = withContext(Dispatchers.IO) { runCatching { copySubtitle(uri, item) }.getOrNull() }
            if (path == null) {
                onRejected()
            } else {
                dropSync()
                savePrefs { copy(subtitleUri = path, subtitleLanguage = null, subtitlesOff = false) }
                reload()
                pendingSyncPath = path
            }
        }
    }

    private fun copySubtitle(uri: Uri, item: PlaybackItem): String? {
        val name = displayName(uri) ?: return null
        subtitleMime(name) ?: return null
        val folder = subtitleFolder(item).apply { mkdirs() }
        val target = File(folder, File(name).name)
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        return target.path
    }

    /** Subtitles copied in or made for one video, in app storage. */
    private fun subtitleFolder(item: PlaybackItem): File = File(app.filesDir, "$SUBTITLE_FOLDER/${hash(item.playableKey)}")

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun subtitleMime(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "srt" -> MimeTypes.APPLICATION_SUBRIP
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> null
    }

    private fun savePrefs(change: TrackPrefs.() -> TrackPrefs) {
        val item = state.item ?: return
        prefs = prefs.change()
        val snapshot = prefs
        writes.launch { runCatching { repository.savePrefs(item, snapshot) } }
    }

    // ---- Online subtitles, F-S9 ------------------------------------------------------------------

    /** The sheet calls this on each tap of its row. Results of this item stay, so only [again] searches twice. */
    fun searchOnline(again: Boolean = false) {
        val item = state.item ?: return
        val current = state.online
        if (current.searching || current.loading != null || (current.results != null && !again)) return
        state.online = OnlineSearch(searching = true)
        onlineJob = main.launch {
            val found = attempt { services.subtitles.search(item) }
            ensureActive()
            state.online = found.fold(
                onSuccess = { OnlineSearch(results = OnlineOrder.arabicFirst(it)) },
                onFailure = { failure ->
                    if (failure.meansNothingFound()) {
                        OnlineSearch(results = emptyList())
                    } else {
                        OnlineSearch(failure = OnlineFailure.of(failure))
                    }
                },
            )
        }
    }

    /** Downloads one result and shows it at once. [onLoaded] runs only if it worked, and a failure stays in the sheet. */
    fun chooseOnline(result: SubtitleResult, onLoaded: () -> Unit) {
        val item = state.item ?: return
        if (state.online.loading != null) return
        ownerChoseText = true
        state.online = state.online.copy(loading = result, failure = null)
        onlineJob = main.launch {
            val loaded = loadOnline(item, result)
            ensureActive()
            state.online = state.online.copy(loading = null, failure = loaded.failure)
            val sidecar = loaded.sidecar ?: return@launch
            onLoaded()
            // A hash match was made from this very file, so its timing is already right.
            if (result.hashMatch) {
                state.sync = SyncState.Exact
            } else {
                state.textTracks.firstOrNull { it.sidecarPath == sidecar.path }?.let(::fileOf)?.let(::startSync)
            }
        }
    }

    /**
     * The waiver of 2026-11-08: a video with no Arabic subtitle gets one by itself, in the background, once per file.
     * It loads beside the playing video as a tapped download does, and every failure stays silent (brief 4.9).
     */
    private fun startAutoSubtitle() {
        val item = state.item ?: return
        autoJob = main.launch {
            // The state copies start at their defaults until the flows answer, so read the stored values.
            val settings = services.settings.settings.first()
            val online = services.subtitles.available.first()
            ensureActive()
            if (ownerChoseText) return@launch
            val languages = AutoSubtitleRule.languages(settings.autoSubtitles, online, item, prefs.subtitlesOff, textRows)
            if (languages.isEmpty()) return@launch
            val outcome = try {
                services.subtitles.autoFetch(item, languages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                AutoSubtitleOutcome.Skipped
            }
            ensureActive()
            val loaded = outcome as? AutoSubtitleOutcome.Loaded ?: return@launch
            fun stillWanted() = state.item?.playableKey == item.playableKey && !ownerChoseText && state.online.loading == null
            if (!stillWanted()) return@launch
            dropSync()
            if (!addLive(loaded.sidecar, ::stillWanted)) return@launch
            state.autoNotice = AutoNotice(loaded.result.provider, loaded.result.language)
            state.textTracks.firstOrNull { it.sidecarPath == loaded.sidecar.path }?.let(::fileOf)?.let(::startSync)
        }
    }

    private class OnlineLoad(val sidecar: SidecarSubtitle? = null, val failure: OnlineFailure? = null)

    private suspend fun loadOnline(item: PlaybackItem, result: SubtitleResult): OnlineLoad {
        val sidecar = attempt { services.subtitles.download(item, result) }.getOrElse { return OnlineLoad(failure = OnlineFailure.of(it)) }
        dropSync()
        return if (addLive(sidecar)) OnlineLoad(sidecar) else OnlineLoad(failure = OnlineFailure.UNREADABLE)
    }

    private suspend fun <T> attempt(call: suspend () -> Result<T>): Result<T> = try {
        call()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        Result.failure(failed)
    }

    // ---- Live subtitles -------------------------------------------------------------------------

    /**
     * A file already saved for this video is a real track of this play, so it needs nothing new. [stillWanted] is asked
     * again after the file is read and before anything on screen changes, so a choice the owner made meanwhile wins.
     */
    private suspend fun addLive(sidecar: SidecarSubtitle, stillWanted: () -> Boolean = { true }): Boolean {
        state.textTracks.firstOrNull { it.sidecarPath == sidecar.path && it.supported }?.let { row ->
            if (!stillWanted()) return false
            applyTrack(C.TRACK_TYPE_TEXT, row)
            return true
        }
        val file = SubtitleFile(sidecar.path, sidecar.label, sidecar.language, sidecar.mimeType)
        if (!playLive(file, stillWanted)) return false
        liveFiles = liveFiles + file
        state.item = state.item?.let { it.copy(sidecars = it.sidecars + sidecar) }
        state.hasTextFiles = true
        publishTextRows()
        rememberLive(file)
        return true
    }

    private fun selectLive(file: SubtitleFile, then: (() -> Unit)? = null) {
        liveSwitch?.cancel()
        liveSwitch = main.launch {
            if (playLive(file)) {
                publishTextRows()
                rememberLive(file)
                then?.invoke()
            }
        }
    }

    /** The next open of this file loads it as a normal track by its path, and the next episode by its language. */
    private fun rememberLive(file: SubtitleFile) {
        val stored = state.textTracks.firstOrNull { it.sidecarPath == file.path }?.let { TrackMemory.encode(state.textTracks, it) }
        savePrefs { copy(subtitleUri = file.path, subtitleLanguage = stored, subtitlesOff = false) }
    }

    private suspend fun playLive(file: SubtitleFile, stillWanted: () -> Boolean = { true }): Boolean {
        val subtitle = withContext(Dispatchers.IO) {
            runCatching {
                LiveSubtitle.read(SubtitleEncoding.toUtf8(app, File(file.path), prefs.subtitleEncoding), file.mimeType)
            }.getOrNull()
        } ?: return false
        if (!stillWanted()) return false
        stopLive()
        disableTextTracks()
        liveFile = file
        when (subtitle) {
            is LiveSubtitle.Cues -> runLiveClock(subtitle.timeline)
            is LiveSubtitle.Script -> engine.showLiveScript(subtitle.bytes)
        }
        return true
    }

    /** Positive delay shows subtitles later, as on the player's own tracks. A seek shows within [LIVE_MAX_WAIT_MS]. */
    private fun runLiveClock(timeline: CueTimeline<Cue>) {
        liveClock = main.launch {
            var shown: List<Cue>? = null
            while (isActive) {
                val positionUs = (player.currentPosition - state.subtitleDelayMs) * 1000
                val cues = timeline.at(positionUs)
                if (cues !== shown) {
                    shown = cues
                    window?.showLiveCues(cues)
                }
                val nextUs = timeline.nextChange(positionUs)
                val waitMs = if (nextUs == null) {
                    LIVE_MAX_WAIT_MS
                } else {
                    ((nextUs - positionUs) / 1000 / player.playbackParameters.speed).toLong()
                }
                delay(waitMs.coerceIn(LIVE_MIN_WAIT_MS, LIVE_MAX_WAIT_MS))
            }
        }
    }

    /** True while the cue view belongs to a live subtitle, so the player's own empty cues must not clear it. */
    val showsLiveCues: Boolean get() = liveClock != null

    private fun stopLive() {
        liveClock?.cancel()
        liveClock = null
        engine.clearLiveScript()
        if (liveFile != null) window?.showLiveCues(emptyList())
        liveFile = null
    }

    private fun clearLive() {
        liveSwitch?.cancel()
        stopLive()
        liveFiles = emptyList()
    }

    // ---- Automatic subtitle sync ------------------------------------------------------------------

    /** What Undo puts back: the delay from before, and the file a frame rate fix replaced with [copy]. */
    private class SyncUndo(val delayMs: Long, val appliedDelayMs: Long, val original: SubtitleFile?, val copy: SubtitleFile?)

    /** True when the subtitle on screen is a file that sync can read. Embedded tracks keep the video's own timing. */
    fun canSync(): Boolean {
        val row = state.textTracks.firstOrNull { it.selected } ?: return false
        val file = fileOf(row) ?: return false
        return SubtitleCues.kindOf(file.mimeType) != null
    }

    /** The row in the subtitle sheet, for a file that was loaded by itself or needs another try. */
    fun syncNow() {
        val row = state.textTracks.firstOrNull { it.selected } ?: return
        fileOf(row)?.let(::startSync)
    }

    private fun fileOf(row: TrackRow): SubtitleFile? {
        val path = row.sidecarPath ?: return null
        return if (row.group == LIVE_GROUP) liveFiles.getOrNull(row.track) else files.firstOrNull { it.path == path }
    }

    private fun startSync(file: SubtitleFile) {
        val item = state.item ?: return
        if (SubtitleCues.kindOf(file.mimeType) == null) return
        syncJob?.cancel()
        state.sync = SyncState.Running
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val audio = audioGroups.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
        val fps = player.videoFormat?.frameRate?.takeIf { it > 0f }
        val duration = max(state.durationMs, item.durationMs)
        val encoding = prefs.subtitleEncoding
        syncJob = main.launch {
            val outcome = try {
                val readable = withContext(Dispatchers.IO) { SubtitleEncoding.toUtf8(app, File(file.path), encoding) }
                SubtitleSync.run(app, playbackUri(item), readable, file.mimeType, duration, fps, audio)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Even an Error must stay here: this scope has no handler, and an escape would end playback.
                SyncOutcome.Unavailable(SyncProblem.NO_CUES)
            }
            ensureActive()
            if (state.item?.playableKey != item.playableKey) return@launch
            when (outcome) {
                is SyncOutcome.Fit -> applyFit(file, outcome)
                SyncOutcome.Unsure -> state.sync = SyncState.Unsure
                is SyncOutcome.Unavailable -> state.sync = SyncState.Unavailable(outcome.problem)
            }
        }
    }

    /**
     * A delay alone goes through the normal delay. A frame rate fix cannot: NextLib's speed is broken by Media3's
     * renderer offset, so a copy with every time stretched is shown live instead, and the delay goes on top of it.
     */
    private suspend fun applyFit(file: SubtitleFile, fit: SyncOutcome.Fit) {
        val target = ((fit.offsetMs / SYNC_STEP_MS.toDouble()).roundToLong() * SYNC_STEP_MS)
            .coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
        val earlier = syncUndo
        val before = earlier?.delayMs ?: state.subtitleDelayMs
        if (fit.fix == FrameRateFix.NONE) {
            if (abs(target - state.subtitleDelayMs) < SYNC_SAME_MS) {
                state.sync = SyncState.AlreadyRight
                return
            }
            syncUndo = SyncUndo(before, target, earlier?.original, earlier?.copy)
        } else {
            val copy = withContext(Dispatchers.IO) { runCatching { writeRetimed(file, fit.fix) }.getOrNull() }
            if (copy == null || !addLive(SidecarSubtitle(copy.path, copy.label, copy.language, copy.mimeType))) {
                state.sync = SyncState.Unavailable(SyncProblem.NO_CUES)
                return
            }
            syncUndo = SyncUndo(before, target, earlier?.original ?: file, copy)
        }
        applyDelay(target)
        state.sync = SyncState.Applied(target, fit.fix)
        state.syncUndoable = true
    }

    /** Beside the video's other copied subtitles in app storage, never next to the video. */
    private fun writeRetimed(file: SubtitleFile, fix: FrameRateFix): SubtitleFile? {
        val item = state.item ?: return null
        val kind = SubtitleCues.kindOf(file.mimeType) ?: return null
        val source = File(file.path)
        val text = SubtitleEncoding.toUtf8(app, source, prefs.subtitleEncoding).readText().removePrefix(BOM)
        // Written in the charset it was read with: a chosen encoding applies to every subtitle of this video.
        val charset = prefs.subtitleEncoding?.takeIf(Charset::isSupported)?.let(Charset::forName) ?: Charsets.UTF_8
        val folder = subtitleFolder(item).apply { mkdirs() }
        val name = source.name
        val target = File(folder, "${name.substringBeforeLast('.')}.${fix.name.lowercase()}.${name.substringAfterLast('.', "srt")}")
        target.writeText(SubtitleCues.retime(text, kind, fix.ratio), charset)
        return SubtitleFile(target.path, app.getString(R.string.player_sync_retimed, file.label), file.language, file.mimeType)
    }

    fun undoSync() {
        val undo = syncUndo ?: return
        forgetSync()
        applyDelay(undo.delayMs)
        val original = undo.original ?: return
        state.textTracks.firstOrNull { it.sidecarPath == original.path }?.let { applyTrack(C.TRACK_TYPE_TEXT, it) }
        val copy = undo.copy ?: return
        if (copy !in liveFiles) return
        liveFiles = liveFiles - copy
        state.item = state.item?.let { it.copy(sidecars = it.sidecars.filterNot { sidecar -> sidecar.path == copy.path }) }
        publishTextRows()
        writes.launch { runCatching { File(copy.path).delete() } }
    }

    /** A new subtitle choice. A delay that sync set for the last file goes back, unless the owner has changed it since. */
    private fun dropSync() {
        val undo = syncUndo
        forgetSync()
        pendingSyncPath = null
        if (undo != null && state.subtitleDelayMs == undo.appliedDelayMs) applyDelay(undo.delayMs)
    }

    private fun forgetSync() {
        syncJob?.cancel()
        syncJob = null
        syncUndo = null
        state.sync = null
        state.syncUndoable = false
    }

    // ---- Aspect and zoom ------------------------------------------------------------------------

    fun setAspect(mode: AspectMode) {
        state.aspectMode = mode
        state.zoom = VideoGeometry.MIN_ZOOM
        state.panX = 0f
        state.panY = 0f
        saveAspect()
    }

    /** The raw product is kept, because snapping every small step near 1x would stop a slow pinch. */
    fun pinch(zoomChange: Float, panX: Float, panY: Float) {
        if (pinchZoom == 0f) pinchZoom = state.zoom
        pinchZoom = (pinchZoom * zoomChange).coerceIn(VideoGeometry.MIN_ZOOM, VideoGeometry.MAX_ZOOM)
        state.zoom = VideoGeometry.clampZoom(pinchZoom)
        state.panX += panX
        state.panY += panY
        if (state.zoom == VideoGeometry.MIN_ZOOM) {
            state.panX = 0f
            state.panY = 0f
        }
    }

    fun saveAspect() {
        pinchZoom = 0f
        val stored = AspectMemory(state.aspectMode, state.zoom).encode()
        savePrefs { copy(aspectMode = stored) }
    }

    // ---- Settings -------------------------------------------------------------------------------

    private suspend fun applySettings(settings: AppSettings) {
        state.settings = settings
        engine.night.enabled = settings.nightAudio
        val path = settings.subtitleFontPath
        if (path != loadedFontPath) {
            loadedFontPath = path
            val bytes = path?.let { withContext(Dispatchers.IO) { runCatching { File(it).readBytes() }.getOrNull() } }
            engine.setCustomSubtitleFont(bytes)
        }
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        writes.launch { runCatching { services.settings.update(change) } }
    }

    fun setOutline(enabled: Boolean) {
        state.subtitleOutline = enabled
        localPrefs.edit().putBoolean(KEY_OUTLINE, enabled).apply()
    }

    /** F-P9. The font is copied in, so removing the source file changes nothing. */
    fun setCustomFont(uri: Uri?) {
        main.launch {
            val path = uri?.let { withContext(Dispatchers.IO) { runCatching { copyFont(it) }.getOrNull() } }
            if (uri == null || path != null) updateSettings { it.copy(subtitleFontPath = path) }
        }
    }

    private fun copyFont(uri: Uri): String? {
        val name = displayName(uri) ?: return null
        val folder = File(app.filesDir, FONT_FOLDER).apply { mkdirs() }
        val target = File(folder, File(name).name)
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        return target.path
    }

    fun saveIntro(startMs: Long, endMs: Long, wholeSeries: Boolean) {
        val item = state.item ?: return
        writes.launch { runCatching { repository.saveManualIntro(item, startMs, endMs, wholeSeries) } }
        val manual = SkipSegment(SegmentKind.OP, startMs, endMs, MANUAL_SOURCE)
        state.item = item.copy(segments = item.segments.filterNot { it.kind == SegmentKind.OP } + manual)
    }

    fun setWatched(watched: Boolean) {
        val item = state.item ?: return
        state.markedWatched = watched
        writes.launch { runCatching { services.library.setWatched(WatchTarget.Playable(item.playableKey), watched) } }
    }

    // ---- Audio route ----------------------------------------------------------------------------

    /** F-P10: each Bluetooth device keeps its own delay. The phone speaker keeps none. */
    private fun refreshAudioRoute() {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in BLUETOOTH_OUTPUTS }
        val key = device?.let { "${it.productName}|${it.type}" }
        if (key == bluetoothKey) return
        bluetoothKey = key
        state.audioDevice = device?.productName?.toString()
        main.launch {
            val stored = key?.let { runCatching { repository.bluetoothDelayMs(it) }.getOrNull() } ?: 0L
            state.audioDelayMs = stored
            engine.setAudioDelayMs(stored)
        }
    }

    // ---- Failures -------------------------------------------------------------------------------

    private fun onError(error: PlaybackException) {
        val position = player.currentPosition
        val mime = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
        val audioFailed = mime != null && MimeTypes.isAudio(mime)
        if (error.errorCode in DECODER_ERRORS) {
            val advanced = if (audioFailed) {
                audioLadder.advance()?.also { engine.decoderManager.selectAudioDecoder(it) }
            } else {
                videoLadder.advance()?.also { engine.decoderManager.selectVideoDecoder(it) }
            }
            if (advanced != null) {
                player.seekTo(position)
                player.prepare()
                return
            }
        }
        savePosition()
        val codec = (error as? ExoPlaybackException)?.rendererFormat?.let(TrackReader::codecName)
        val kind = if (FramePlayer.isIncomplete(error)) FailureKind.INCOMPLETE else failureKind(error.errorCode)
        state.failure = PlayerFailure(kind, codec)
    }

    private fun failureKind(code: Int): FailureKind = when (code) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> FailureKind.MISSING
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> FailureKind.UNSUPPORTED
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> FailureKind.DAMAGED
        else -> FailureKind.OTHER
    }

    // ---- Facts ----------------------------------------------------------------------------------

    fun facts(): StreamFacts {
        val video = player.videoFormat
        val audio = player.audioFormat
        val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
        val path = state.item?.uri?.takeIf { it.startsWith("/") }
        val bitrate = video?.bitrate?.takeIf { it > 0 }?.toLong()
            ?: VideoFacts.estimateBitrate(path?.let { File(it).length() } ?: 0L, state.durationMs)
        return StreamFacts(
            videoCodec = video?.let(TrackReader::codecName),
            width = video?.width?.takeIf { it > 0 } ?: 0,
            height = video?.height?.takeIf { it > 0 } ?: 0,
            frameRate = video?.frameRate?.takeIf { it > 0f } ?: 0f,
            bitrate = bitrate,
            hdr = VideoFacts.hdr(video),
            videoDecoder = state.videoDecoder,
            hardware = DecoderProbe.isHardware(state.videoDecoder),
            droppedFrames = counters?.droppedBufferCount ?: 0,
            audioCodec = audio?.let(TrackReader::codecName),
            channels = audio?.channelCount?.takeIf { it > 0 } ?: 0,
            sampleRate = audio?.sampleRate?.takeIf { it > 0 } ?: 0,
            audioDecoder = state.audioDecoder,
        )
    }

    fun currentUri(): Uri? = state.item?.let(::playbackUri)

    // ---- Background ------------------------------------------------------------------------------

    /** The activity stopped and is not in picture-in-picture. A private item never plays on behind the screen. */
    fun onHidden() {
        savePosition()
        if (state.settings.backgroundAudio && state.item?.isPrivate != true) {
            if (player.isPlaying) setVideoHidden(true)
        } else {
            resumeOnShow = player.playWhenReady
            player.pause()
        }
    }

    fun onShown() {
        setVideoHidden(false)
        if (resumeOnShow) player.play()
        resumeOnShow = false
    }

    private fun setVideoHidden(hidden: Boolean) {
        if (videoHidden == hidden) return
        videoHidden = hidden
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, hidden)
            .build()
    }

    /** Ends playback of the current item. The session stays usable for the next one. */
    fun close() {
        savePosition()
        openJob?.cancel()
        onlineJob?.cancel()
        autoJob?.cancel()
        forgetSync()
        pendingSyncPath = null
        vaultWatch?.cancel()
        countdown?.cancel()
        stopTicker()
        clearLive()
        previews.close()
        player.stop()
        player.clearMediaItems()
        state.item = null
        state.isPlaying = false
        state.finished = false
        resumeOnShow = false
        // No call to the window: it keeps its flag until it closes, with the last frame still on the surface.
        secure = false
    }

    fun release() {
        close()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        player.removeListener(playerListener)
        player.removeAnalyticsListener(analytics)
        main.cancel()
        engine.release()
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(HASH_LENGTH)

    private companion object {
        const val LOCAL_PREFS = "player"
        const val KEY_OUTLINE = "subtitle_outline"
        const val SUBTITLE_FOLDER = "player-subs"
        const val FONT_FOLDER = "player-fonts"
        const val SIDECAR_ID = "side"
        const val MANUAL_SOURCE = "manual"
        const val BOM = "\uFEFF"

        /** A found delay is rounded to this, and one within [SYNC_SAME_MS] of the current one changes nothing. */
        const val SYNC_STEP_MS = 10L
        const val SYNC_SAME_MS = 50L
        const val ARABIC = "ar"
        const val HASH_LENGTH = 24

        /** TrackRow.group of a live subtitle, which has no group in Tracks. */
        const val LIVE_GROUP = -1
        const val LIVE_MIN_WAIT_MS = 15L
        const val LIVE_MAX_WAIT_MS = 200L

        /**
         * For the notification and everything else outside the app. Blank and not null, because the player fills
         * any null field from the tags inside the file, which can name the film and carry its cover.
         */
        val BLANK_METADATA: MediaMetadata = MediaMetadata.Builder()
            .setTitle("")
            .setDisplayTitle("")
            .setSubtitle("")
            .setDescription("")
            .setArtist("")
            .setAlbumTitle("")
            .setAlbumArtist("")
            .setAuthor("")
            .setWriter("")
            .setComposer("")
            .setConductor("")
            .setDiscSubtitle("")
            .setGenre("")
            .setCompilation("")
            .setStation("")
            .setArtworkData(ByteArray(0), null)
            .build()

        const val TICK_MS = 250L
        const val SAVE_EVERY_MS = 5_000L
        const val COUNTDOWN_SECONDS = 10

        const val SPEED_STEPS = 20f
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val HOLD_SPEED = 2f

        /** As far as automatic sync searches, so a found delay can still be nudged by hand. */
        const val MAX_SUBTITLE_DELAY_MS = SyncAligner.MAX_OFFSET_MS
        const val MAX_AUDIO_DELAY_MS = 2_000L

        val BLUETOOTH_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )

        val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        )
    }
}
