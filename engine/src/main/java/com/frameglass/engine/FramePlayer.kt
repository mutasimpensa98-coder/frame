package com.frameglass.engine

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.ui.SubtitleView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.media.AssBidi
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.render.AssRenderer
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException

data class EngineConfig(
    val assRenderType: AssRenderType = AssRenderType.OVERLAY_OPEN_GL,
    val maxRenderPixels: Int = 0,
    val cacheSizeMb: Int = 128,
    /** Audio focus, pause on headphone loss and the wake lock. The app turns this on, the harness does not. */
    val playbackBehaviour: Boolean = false,
)

/**
 * Builds the player: Media3 with NextLib's FFmpeg renderers and libass.
 *
 * This repeats what ass-media's buildWithAssSupport does, because that helper keeps the
 * AssHandler private, and the app needs it for fonts and subtitle delay.
 */
@OptIn(UnstableApi::class)
class FramePlayer(context: Context, val config: EngineConfig = EngineConfig()) {
    private val appContext = context.applicationContext
    private val subtitleFont: ByteArray by lazy {
        appContext.assets.open(SUBTITLE_FONT_ASSET).use { it.readBytes() }
    }

    private var customFont: ByteArray? = null

    val tap = RmsTapProcessor()
    val night = NightAudioProcessor()
    val assHandler = AssHandler(config.assRenderType, AssHandlerConfig(cacheSize = config.cacheSizeMb, maxRenderPixels = config.maxRenderPixels))
    val decoderManager = DecoderManager()
    var delaySink: DelayAudioSink? = null
        private set

    val player: ExoPlayer

    init {
        val renderers = object : NextRenderersFactory(appContext) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink {
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(night, tap))
                    .build()
                return DelayAudioSink(sink).also { delaySink = it }
            }

            // DecoderManager requires the player's renderer list to equal the one it wrapped, so
            // the ASS renderer joins here and not through AssRenderersFactory, which appends it later.
            override fun buildMiscellaneousRenderers(context: Context, eventHandler: Handler, extensionRendererMode: Int, out: ArrayList<Renderer>) {
                super.buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, out)
                out.add(AssRenderer(assHandler))
            }
        }.setDecoderManager(decoderManager)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val parser = AssSubtitleParserFactory(assHandler)
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext), extractors(parser))
        sources.setSubtitleParserFactory(parser)

        player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(sources)
            .setRenderersFactory(renderers)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
        if (config.playbackBehaviour) {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            player.setAudioAttributes(attributes, true)
            player.setHandleAudioBecomingNoisy(true)
            player.setWakeMode(C.WAKE_MODE_LOCAL)
        }
        assHandler.init(player)
        decoderManager.attach(player)

        // AssHandler clears its fonts on every media change, so the bundled font goes back in.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                addBundledFont()
            }
        })
    }

    /** What ass-media's withAssMkvSupport does, with Matroska also ready for a file that stops early. */
    private fun extractors(parser: AssSubtitleParserFactory): ExtractorsFactory {
        val defaults = DefaultExtractorsFactory()
        return ExtractorsFactory {
            val extractors = defaults.createExtractors()
            extractors.forEachIndexed { index, extractor ->
                if (extractor is MatroskaExtractor) {
                    extractors[index] = PartialFileExtractor { flags -> AssMatroskaExtractor(parser, assHandler, flags) }
                }
            }
            extractors
        }
    }

    /** A chosen font goes first, so libass reaches it before the bundled one when a font is missing. */
    fun addBundledFont() {
        customFont?.let { assHandler.addFont(CUSTOM_FONT_FAMILY, it) }
        assHandler.addFont(SUBTITLE_FONT_FAMILY, subtitleFont)
    }

    /** F-P9: the owner's font as the libass fallback. It applies from the next media change. */
    fun setCustomSubtitleFont(bytes: ByteArray?) {
        customFont = bytes
    }

    val hasAssTrack: Boolean get() = assHandler.track != null

    /** Creates the libass overlay. Attach it over the video, then call [installSubtitleDelay]. */
    fun createAssView(context: Context): AssSubtitleView = AssSubtitleView(context, assHandler)

    fun withSubtitleView(view: SubtitleView) {
        view.withAssSupport(assHandler)
    }

    @Volatile private var assDelayUs = 0L
    private var delayedClock: ((Long) -> Unit)? = null

    /**
     * Wraps the overlay's clock. Call after the AssSubtitleView is attached to the window, and again for
     * each new overlay: a player kept alive for background audio outlives the activity that made the old one.
     */
    fun installSubtitleDelay() {
        val original = assHandler.videoTimeCallback ?: return
        if (original === delayedClock) return
        val delayed: (Long) -> Unit = { t -> original(t - assDelayUs) }
        delayedClock = delayed
        assHandler.videoTimeCallback = delayed
    }

    /**
     * Positive values show subtitles later. Applies live, with no re-prepare, to the cue renderer through NextLib and
     * to the libass overlay through its clock. NextLib's subtitleSpeed is not used for a frame rate fix: it multiplies
     * the renderer position, which carries Media3's 10^12 µs offset, so any speed but 1 moves cues by hours.
     */
    fun setSubtitleDelayMs(ms: Long) {
        player.subtitleDelayMilliseconds = ms
        assDelayUs = ms * 1000
        // A paused video sends the overlay no clock, so it would keep the old timing until play.
        if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
            assHandler.videoTimeCallback?.invoke(player.currentPosition * 1000)
        }
    }

    fun setAudioDelayMs(ms: Long) {
        delaySink?.delayUs = ms * 1000
    }

    private var liveScript: AssTrack? = null

    /**
     * Hands a whole ASS file to libass beside the player's own tracks. AssRenderer feeds the overlay's clock
     * whatever is selected, so this draws with no re-prepare, and the subtitle delay applies as usual.
     */
    suspend fun showLiveScript(script: ByteArray) {
        val track = withContext(Dispatchers.IO) {
            val fixed = AssBidi.fixScript(script, 0, script.size)
            assHandler.createTrack(LIVE_SCRIPT).also { it.readBuffer(fixed, 0, fixed.size) }
        }
        val old = liveScript
        liveScript = track
        assHandler.render?.setTrack(track)
        old?.release()
        // A paused video sends no clock, so the first line would otherwise wait for play.
        assHandler.videoTimeCallback?.invoke(player.currentPosition * 1000)
    }

    /** Call after every track change: AssHandler knows nothing of this track and drops whatever is set. */
    fun keepLiveScript() {
        liveScript?.let { assHandler.render?.setTrack(it) }
    }

    /** Must come before a media change, which throws the renderer away. */
    fun clearLiveScript() {
        val track = liveScript ?: return
        liveScript = null
        assHandler.render?.setTrack(null)
        track.release()
    }

    fun release() {
        clearLiveScript()
        decoderManager.detach()
        player.release()
        assHandler.release()
    }

    companion object {
        const val SUBTITLE_FONT_ASSET = "fonts/NotoNaskhArabic-Regular.ttf"
        const val SUBTITLE_FONT_FAMILY = "Noto Naskh Arabic"
        const val CUSTOM_FONT_FAMILY = "Frame Glass Custom"
        const val SEEK_STEP_MS = 10_000L

        private val LIVE_SCRIPT = Format.Builder().setId("live").setSampleMimeType(MimeTypes.TEXT_SSA).build()

        /** The file ends before its own index or header says it should, as an unfinished download does. */
        fun isIncomplete(error: PlaybackException): Boolean =
            error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                generateSequence(error.cause) { it.cause }.any { it is TruncatedFileException || it is EOFException }
    }
}
