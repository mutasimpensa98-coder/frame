package com.frameglass.playerui

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.util.Rational
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.frameglass.data.api.AppSettings
import com.frameglass.engine.CueBidi
import com.frameglass.engine.FrameGrabber
import com.frameglass.engine.FramePlayer
import com.google.common.util.concurrent.ListenableFuture
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The player window. It owns what only an activity can own: the view stack of brief 4.4, the window flags,
 * brightness, orientation and picture-in-picture. Playback itself lives in [PlayerSession].
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), PlayerActions, PlayerWindow {
    private lateinit var session: PlayerSession
    private lateinit var stage: VideoStage
    private lateinit var assView: AssSubtitleView
    private lateinit var cueView: SubtitleView

    /** Dual subtitles: the second one, smaller, drawn by the app beside Media3's own text output. */
    private lateinit var secondView: SubtitleView
    private var secondCues: List<Cue> = emptyList()
    private lateinit var blurHook: VideoBlurHook
    private lateinit var audioManager: AudioManager
    private val ui = PlayerUiState()

    private var controller: ListenableFuture<MediaController>? = null
    private var rotated = false
    private var videoAspect = 0f
    private var volumeLevel = NO_LEVEL
    private var cueSizePx = 0f
    private var defaultTypeface: Typeface? = null

    private val subtitlePicker = registerForActivityResult(OpenAtFolder()) { uri ->
        if (uri != null) session.addSubtitle(uri) { ui.show(getString(R.string.player_subtitle_rejected)) }
    }

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) session.setCustomFont(uri)
    }

    private val playerListener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) {
            if (!session.showsLiveCues) cueView.setCues(cueGroup.cues.map(CueBidi::fix))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = applyVideoSize(videoSize)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            stage.keepScreenOn = isPlaying
            updatePictureInPicture()
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_BACK -> session.seekBy(-SEEK_STEP_MS)
                ACTION_PIP_FORWARD -> session.seekBy(SEEK_STEP_MS)
                ACTION_PIP_TOGGLE -> session.togglePlay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        volumeControlStream = AudioManager.STREAM_MUSIC
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        audioManager = getSystemService(AudioManager::class.java)

        session = PlaybackHost.attachActivity(this)
        session.attachWindow(this)
        buildStack()
        connectService()
        registerReceiver(
            pipReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_BACK)
                addAction(ACTION_PIP_FORWARD)
                addAction(ACTION_PIP_TOGGLE)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        onBackPressedDispatcher.addCallback(this) {
            if (ui.locked) ui.unlockVisible = true else exit()
        }
        open(intent)
        observe()
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun buildStack() {
        val engine = session.engine
        stage = VideoStage(this)
        assView = engine.createAssView(this)
        cueView = SubtitleView(this).apply {
            setApplyEmbeddedStyles(true)
            setApplyEmbeddedFontSizes(false)
        }
        secondView = SubtitleView(this).apply {
            setApplyEmbeddedStyles(true)
            setApplyEmbeddedFontSizes(false)
        }
        val controls = ComposeView(this).apply {
            setContent { PlayerTheme { ControlsLayer(session, ui, this@PlayerActivity) } }
        }
        val sheets = ComposeView(this).apply {
            setContent { PlayerTheme { SheetsLayer(session, ui, this@PlayerActivity) } }
        }
        stage.setLayers(controls, listOf(assView, cueView, secondView), sheets)
        stage.topBarPx = (TOP_BAR_DP * resources.displayMetrics.density).roundToInt()
        stage.onBoxChanged = {
            val cutout = stage.rootWindowInsets?.getInsets(WindowInsets.Type.displayCutout())?.top ?: 0
            stage.topBarPx = (TOP_BAR_DP * resources.displayMetrics.density).roundToInt() + cutout
            updateCueSize()
            updatePictureInPicture()
        }
        setContentView(stage)
        blurHook = VideoBlurHook(stage)
        assView.post { engine.installSubtitleDelay() }

        val player = session.player
        player.setVideoSurfaceView(stage.surface)
        player.addListener(playerListener)
        stage.keepScreenOn = player.isPlaying
        applyVideoSize(player.videoSize)
    }

    /** Binding through a controller is what brings the session into the service and its notification. */
    private fun connectService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    private fun open(intent: Intent) {
        val ref = PlayerRoute.refOf(intent)
        when {
            ref != null -> {
                keepUriAccess(intent)
                session.open(ref)
            }
            session.state.item == null -> session.openLastSession()
        }
    }

    private fun keepUriAccess(intent: Intent) {
        val uri = intent.data ?: return
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    private fun observe() {
        val state = session.state
        lifecycleScope.launch {
            snapshotFlow { ui.subtitleClearance }.collect { stage.liftSubtitles(it) }
        }
        lifecycleScope.launch {
            snapshotFlow { Picture(state.aspectMode, state.zoom, state.panX, state.panY) }
                .collect { stage.setPicture(it.mode, it.zoom, it.panX, it.panY) }
        }
        lifecycleScope.launch {
            snapshotFlow { state.finished }.collect { if (it) finish() }
        }
        lifecycleScope.launch {
            snapshotFlow { state.refusedSeeks }.drop(1).collect { ui.show(getString(R.string.player_no_seek)) }
        }
        // The sheet usually closes as a subtitle is chosen, so the result of its sync is said over the video.
        lifecycleScope.launch {
            snapshotFlow { state.sync }.drop(1).collect { sync ->
                if (sync != null && sync != SyncState.Running && ui.sheet == null) {
                    ui.show(SyncText.summary(resources, resources.configuration.locales[0], sync), longer = true)
                }
            }
        }
        if (VideoBlurHook.ENABLED) {
            val density = resources.displayMetrics.density
            lifecycleScope.launch {
                snapshotFlow { ui.blurRects.values.toList() }.collect { rects ->
                    val regions = rects.map { RectF(it.left, it.top, it.right, it.bottom) }
                    blurHook.update(regions, BLUR_CORNER_DP * density, BLUR_RADIUS_DP * density)
                }
            }
        }
        // Only what shapes a cue, so a theme change in the app behind this never touches the subtitles.
        lifecycleScope.launch {
            snapshotFlow { CueLook.of(state.settings, state.subtitleOutline) }.collect(::applySubtitleStyle)
        }
        lifecycleScope.launch {
            snapshotFlow { state.secondPlacement }.collect { placement ->
                stage.setTopView(if (placement == SecondPlacement.TOP) secondView else null)
                applySecondLayout()
                showSecondCues(secondCues)
            }
        }
    }

    private data class Picture(val mode: AspectMode, val zoom: Float, val panX: Float, val panY: Float)

    private data class CueLook(
        val color: Int,
        val background: Boolean,
        val fontPath: String?,
        val bottomOffset: Float,
        val scale: Float,
        val outline: Boolean,
    ) {
        companion object {
            fun of(settings: AppSettings, outline: Boolean) = CueLook(
                settings.subtitleColor,
                settings.subtitleBackground,
                settings.subtitleFontPath,
                settings.subtitleBottomOffset,
                settings.subtitleScale,
                outline,
            )
        }
    }

    /** A size of zero only means the next file has not shown a frame yet, so the box stays as it is. */
    private fun applyVideoSize(size: VideoSize) {
        if (size.width <= 0 || size.height <= 0) return
        videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        stage.setVideoAspect(videoAspect)
        applyOrientation()
        updatePictureInPicture()
    }

    private fun applyOrientation() {
        val portraitVideo = videoAspect in MIN_ASPECT..<1f
        requestedOrientation = if (portraitVideo != rotated) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun updateCueSize() {
        val shortSide = min(stage.width, stage.height)
        cueSizePx = shortSide * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
        applyCueSize(session.state.settings.subtitleScale)
    }

    private fun applyCueSize(scale: Float) {
        if (cueSizePx <= 0f) return
        cueView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, cueSizePx * scale)
        secondView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, cueSizePx * scale * SECOND_SCALE)
        applySecondLayout()
    }

    /**
     * Above the main subtitle means room for [MAIN_LINES_KEPT] lines of it over its own bottom padding. The main
     * text can be one line or three, so this is a fixed reserve: a line that moves with every cue would be worse.
     */
    private fun applySecondLayout() {
        val settings = session.state.settings
        val mainPadding = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + settings.subtitleBottomOffset
        val height = secondView.height.takeIf { it > 0 } ?: stage.box.height
        val reserve = if (session.state.secondPlacement == SecondPlacement.ABOVE_MAIN && height > 0) {
            MAIN_LINES_KEPT * LINE_HEIGHT * cueSizePx * settings.subtitleScale / height
        } else {
            0f
        }
        secondView.setBottomPaddingFraction((mainPadding + reserve).coerceAtMost(MAX_SECOND_PADDING))
    }

    /** F-P9. This styles cue subtitles only. For ASS the script's own styling always wins. */
    private fun applySubtitleStyle(look: CueLook) {
        val custom = look.fontPath?.let { path -> runCatching { Typeface.createFromFile(path) }.getOrNull() }
        val typeface = custom ?: defaultTypeface ?: Typeface.createFromAsset(assets, FramePlayer.SUBTITLE_FONT_ASSET).also {
            defaultTypeface = it
        }
        val style = CaptionStyleCompat(
            look.color,
            if (look.background) CUE_BACKGROUND else Color.TRANSPARENT,
            Color.TRANSPARENT,
            if (look.outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            typeface,
        )
        cueView.setStyle(style)
        secondView.setStyle(style)
        cueView.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + look.bottomOffset)
        applyCueSize(look.scale)
    }

    // ---- PlayerWindow ---------------------------------------------------------------------------

    /**
     * One activity plays everything, so this runs whenever a private item and a public one trade places.
     * Picture-in-picture has no title to hide: no PictureInPictureParams title is ever set.
     */
    override fun setSecure(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        excludeFromRecents(secure)
    }

    /**
     * The flag sits on the task, which this activity shares with the library. It comes off every task of the app,
     * because picture-in-picture moves this activity to a task of its own and leaves the flag on the old one.
     */
    private fun excludeFromRecents(exclude: Boolean) {
        for (task in getSystemService(ActivityManager::class.java).appTasks) {
            runCatching { if (!exclude || task.taskInfo?.taskId == taskId) task.setExcludeFromRecents(exclude) }
        }
    }

    override fun showLiveCues(cues: List<Cue>) = cueView.setCues(cues)

    /**
     * Cues that overlap in time join into one block, which the view cannot stack by itself, and at the top they hang
     * from a line near the top edge of the picture.
     */
    override fun showSecondCues(cues: List<Cue>) {
        secondCues = cues
        val texts = cues.mapNotNull { it.text }
        if (texts.isEmpty()) {
            secondView.setCues(emptyList())
            return
        }
        val joined = SpannableStringBuilder()
        texts.forEachIndexed { index, text ->
            if (index > 0) joined.append('\n')
            joined.append(text)
        }
        val builder = Cue.Builder().setText(joined)
        if (session.state.secondPlacement == SecondPlacement.TOP) {
            builder.setLine(SECOND_TOP_LINE, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START)
        }
        secondView.setCues(listOf(builder.build()))
    }

    // ---- Picture in picture ---------------------------------------------------------------------

    private fun updatePictureInPicture() {
        if (!::stage.isInitialized || isFinishing) return
        setPictureInPictureParams(pictureInPictureParams())
    }

    private fun pictureInPictureParams(): PictureInPictureParams {
        val playing = session.player.playWhenReady
        val builder = PictureInPictureParams.Builder()
            .setAutoEnterEnabled(session.player.isPlaying)
            .setSeamlessResizeEnabled(false)
            .setActions(
                listOf(
                    pipAction(R.drawable.player_ic_rewind, R.string.player_back_10, ACTION_PIP_BACK, REQUEST_PIP_BACK),
                    if (playing) {
                        pipAction(R.drawable.player_ic_pause, R.string.player_pause, ACTION_PIP_TOGGLE, REQUEST_PIP_TOGGLE)
                    } else {
                        pipAction(R.drawable.player_ic_play, R.string.player_play, ACTION_PIP_TOGGLE, REQUEST_PIP_TOGGLE)
                    },
                    pipAction(R.drawable.player_ic_forward, R.string.player_forward_10, ACTION_PIP_FORWARD, REQUEST_PIP_FORWARD),
                ),
            )
        if (videoAspect > 0f) {
            val clamped = videoAspect.coerceIn(MIN_PIP_ASPECT, MAX_PIP_ASPECT)
            builder.setAspectRatio(Rational((clamped * PIP_RATIO_BASE).roundToInt(), PIP_RATIO_BASE))
            val hint = stage.visibleVideoRect()
            if (!hint.isEmpty) builder.setSourceRectHint(hint)
        }
        return builder.build()
    }

    private fun pipAction(icon: Int, title: Int, action: String, request: Int): RemoteAction {
        val label = getString(title)
        val intent = PendingIntent.getBroadcast(
            this,
            request,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), label, label, intent)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            ui.sheet = null
            ui.controlsVisible = false
            ui.subtitleClearance = 0
        }
        // Leaving picture-in-picture while stopped means it was dismissed, not expanded.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) exit()
    }

    // ---- Lifecycle ------------------------------------------------------------------------------

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        session.onShown()
    }

    override fun onPause() {
        super.onPause()
        session.savePosition()
    }

    /** Picture-in-picture stopped means the screen went off, and a private item must not play on behind it. */
    override fun onStop() {
        super.onStop()
        val keepsPlaying = isInPictureInPictureMode && session.state.item?.isPrivate != true
        if (!keepsPlaying && !isFinishing) session.onHidden()
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        val player = session.player
        player.removeListener(playerListener)
        player.clearVideoSurfaceView(stage.surface)
        controller?.let(MediaController::releaseFuture)
        session.detachWindow(this)
        if (isFinishing && PlaybackHost.isLastActivity) {
            session.close()
            stopService(Intent(this, PlaybackService::class.java))
            excludeFromRecents(false)
        }
        PlaybackHost.detachActivity()
        super.onDestroy()
    }

    // ---- PlayerActions --------------------------------------------------------------------------

    override fun exit() {
        session.player.pause()
        session.savePosition()
        finish()
    }

    override fun lock(locked: Boolean) {
        ui.locked = locked
        ui.unlockVisible = locked
        ui.controlsVisible = !locked
        ui.touch()
    }

    override fun cycleAspect() {
        val next = session.state.aspectMode.next()
        session.setAspect(next)
        ui.show(getString(next.label))
    }

    override fun rotate() {
        rotated = !rotated
        applyOrientation()
    }

    override fun adjustBrightness(delta: Float): Float {
        val attributes = window.attributes
        val current = if (attributes.screenBrightness >= 0f) attributes.screenBrightness else systemBrightness()
        val next = (current + delta).coerceIn(MIN_BRIGHTNESS, 1f)
        attributes.screenBrightness = next
        window.attributes = attributes
        return next
    }

    private fun systemBrightness(): Float =
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, SYSTEM_BRIGHTNESS_MAX / 2) /
            SYSTEM_BRIGHTNESS_MAX.toFloat()

    override fun adjustVolume(delta: Float): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volumeLevel < 0f) volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * max).roundToInt(), 0)
        return volumeLevel
    }

    override fun endLevelGesture() {
        volumeLevel = NO_LEVEL
    }

    override fun pinch(zoomChange: Float, panX: Float, panY: Float) {
        session.pinch(zoomChange, panX, panY)
        // The stored pan stays at the limit, so a change of direction moves the picture at once.
        val state = session.state
        val shown = VideoGeometry.transform(stage.width, stage.height, stage.box, state.aspectMode, state.zoom, state.panX, state.panY)
        state.panX = shown.translationX
        state.panY = shown.translationY
    }

    override fun pinchEnded() = session.saveAspect()

    override fun pickSubtitleFile() {
        subtitlePicker.launch(session.state.item?.uri?.let(::documentFolderOf))
    }

    /** The document id of the folder holding [path], so the picker opens beside the video (F-P7). */
    private fun documentFolderOf(path: String): Uri? {
        val folder = File(path).parent ?: return null
        val match = STORAGE_PATH.matchEntire(folder) ?: return null
        val volume = match.groupValues[1].let { if (it == PRIMARY_MOUNT) PRIMARY_VOLUME else it }
        return DocumentsContract.buildDocumentUri(EXTERNAL_DOCUMENTS, "$volume:${match.groupValues[2]}")
    }

    override fun pickFont() {
        fontPicker.launch(FONT_TYPES)
    }

    override fun screenshot(withSubtitles: Boolean) {
        if (session.state.item?.isPrivate == true) return
        val positionMs = session.player.currentPosition
        val uri = session.currentUri() ?: return
        lifecycleScope.launch {
            val saved = runCatching {
                val bitmap = if (withSubtitles) {
                    Screenshots.compose(stage, assView, listOf(cueView, secondView))
                } else {
                    FrameGrabber.fullFrame(this@PlayerActivity, uri, positionMs)
                }
                bitmap != null && Screenshots.save(this@PlayerActivity, bitmap)
            }.getOrDefault(false)
            ui.show(getString(if (saved) R.string.player_screenshot_saved else R.string.player_screenshot_failed))
        }
    }

    override fun openElsewhere() {
        val item = session.state.item?.takeUnless { it.isPrivate } ?: return
        val uri = if (item.uri.startsWith("/")) {
            FileProvider.getUriForFile(this, "$packageName$FILE_AUTHORITY", File(item.uri))
        } else {
            Uri.parse(item.uri)
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(view, getString(R.string.player_open_elsewhere))) }
    }

    /** OpenDocument, starting in the folder given as input. */
    private class OpenAtFolder : ActivityResultContract<Uri?, Uri?>() {
        override fun createIntent(context: Context, input: Uri?): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .apply { input?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) } }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data.takeIf { resultCode == RESULT_OK }
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
        const val NO_LEVEL = -1f
        const val MIN_BRIGHTNESS = 0.01f
        const val SYSTEM_BRIGHTNESS_MAX = 255
        const val MIN_ASPECT = 0.01f
        const val CUE_BACKGROUND = 0x99000000.toInt()
        /** Dual subtitles: the second is this much of the main size, hangs from this line, and keeps 2 main lines clear. */
        const val SECOND_SCALE = 0.8f
        const val SECOND_TOP_LINE = 0.04f
        const val MAIN_LINES_KEPT = 2f
        const val LINE_HEIGHT = 1.25f
        const val MAX_SECOND_PADDING = 0.6f

        /** Back and lock, with their padding, as the controls lay them out. */
        const val TOP_BAR_DP = 68f
        const val BLUR_CORNER_DP = 24f
        const val BLUR_RADIUS_DP = 48f

        const val MIN_PIP_ASPECT = 0.42f
        const val MAX_PIP_ASPECT = 2.38f
        const val PIP_RATIO_BASE = 1000

        const val ACTION_PIP_BACK = "com.frameglass.playerui.PIP_BACK"
        const val ACTION_PIP_FORWARD = "com.frameglass.playerui.PIP_FORWARD"
        const val ACTION_PIP_TOGGLE = "com.frameglass.playerui.PIP_TOGGLE"
        const val REQUEST_PIP_BACK = 1
        const val REQUEST_PIP_TOGGLE = 2
        const val REQUEST_PIP_FORWARD = 3

        const val FILE_AUTHORITY = ".playerfiles"
        const val EXTERNAL_DOCUMENTS = "com.android.externalstorage.documents"
        const val PRIMARY_MOUNT = "emulated/0"
        const val PRIMARY_VOLUME = "primary"
        val STORAGE_PATH = Regex("/storage/(emulated/0|[^/]+)/?(.*)")

        val FONT_TYPES = arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")
    }
}
