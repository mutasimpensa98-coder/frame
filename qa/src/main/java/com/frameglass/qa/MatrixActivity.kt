package com.frameglass.qa

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.SubtitleView
import com.frameglass.engine.EngineConfig
import com.frameglass.engine.FramePlayer
import com.frameglass.engine.SubtitleEncoding
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Phase 0 harness. Started over adb:
 *   am start -n com.frameglass.player.qa/com.frameglass.qa.MatrixActivity
 *     --es mode matrix|census|soak|delay --es dir /sdcard/Movies/FrameGlassTest
 *     [--es only V1,A2] [--es keys V1,S1@.utf8.srt] [--ei playSec 8] [--es file name] [--ei minutes 30]
 * only picks rows by id prefix, keys by exact result key. Results go to <dir>/results as JSON. tools/matrix.py
 * pulls them.
 *
 * A row needs the screen, so using the phone must not cost one. Stopping the activity cancels the running
 * measurement, and starting it again redoes that measurement from nothing and carries on. Each result
 * says how many times it was interrupted, and PAUSED.txt shows the wait to tools/matrix.py.
 */
@OptIn(UnstableApi::class)
class MatrixActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var dir: File
    private lateinit var out: File
    private val rowsDone = HashSet<String>()
    private val interruptions = HashMap<String, Int>()

    private val onScreen: Boolean get() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(STATE_DONE)?.let(rowsDone::addAll)
        savedInstanceState?.getBundle(STATE_INTERRUPTIONS)?.let { saved ->
            saved.keySet().forEach { interruptions[it] = saved.getInt(it) }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        dir = File(intent.getStringExtra("dir") ?: "/sdcard/Movies/FrameGlassTest")
        out = File(dir, "results").apply { mkdirs() }
        val mode = intent.getStringExtra("mode") ?: "matrix"
        markPaused(true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                markPaused(false)
                runMode(mode)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) markPaused(true)
    }

    /** The system can end the process while the harness waits. Reopened, it goes on from here. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_DONE, ArrayList(rowsDone))
        outState.putBundle(STATE_INTERRUPTIONS, bundleOf(*interruptions.toList().toTypedArray()))
    }

    private fun markPaused(paused: Boolean) {
        val mark = File(out, PAUSED_MARK)
        if (paused) mark.writeText("paused") else mark.delete()
    }

    private suspend fun runMode(mode: String) {
        try {
            when (mode) {
                "census" -> census()
                "soak" -> soak()
                "delay" -> delayProof()
                else -> matrix()
            }
            File(out, "DONE-$mode.txt").writeText("ok")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "harness failed", t)
            File(out, "DONE-$mode.txt").writeText("error: ${t.stackTraceToString()}")
        }
        finish()
    }

    // ---- shared rig ---------------------------------------------------------

    private inner class Rig(cfg: EngineConfig = EngineConfig()) {
        val engine = FramePlayer(this@MatrixActivity, cfg)
        val surface = SurfaceView(this@MatrixActivity)
        val assView = engine.createAssView(this@MatrixActivity)
        val cueView = SubtitleView(this@MatrixActivity)
        val stage = FrameLayout(this@MatrixActivity)
        val player get() = engine.player
        var videoDecoder = ""
        var audioDecoder = ""
        var firstFrameAt = 0L
        var errorText: String? = null
        var incomplete = false
        var droppedEvents = 0
        var cueLog = JSONArray()
        @Volatile var lastPtsUs = -1L
        var videoSize: VideoSize? = null
        val firstFrame = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        var frameListener: ((Long, Long) -> Unit)? = null

        init {
            root.removeAllViews()
            stage.addView(surface, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            stage.addView(assView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            stage.addView(cueView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            root.addView(stage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage("ar").setSelectUndeterminedTextLanguage(true).build()
            player.setVideoSurfaceView(surface)
            player.setVideoFrameMetadataListener(VideoFrameMetadataListener { pts, release, _, _ ->
                lastPtsUs = pts
                frameListener?.invoke(pts, release)
            })
            player.addListener(object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    cueView.setCues(cueGroup.cues)
                    if (cueGroup.cues.isNotEmpty() && cueLog.length() < 200) {
                        cueLog.put(JSONObject().put("posMs", player.currentPosition).put("text", cueGroup.cues[0].text?.toString() ?: ""))
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) ready.complete(Unit)
                }
                override fun onPlayerError(error: PlaybackException) {
                    errorText = "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
                    incomplete = FramePlayer.isIncomplete(error)
                    firstFrame.complete(Unit)
                    ready.complete(Unit)
                }
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                    if (size.width > 0 && size.height > 0) {
                        surface.holder.setFixedSize(size.width, size.height)
                        fitStage(size.width * size.pixelWidthHeightRatio / size.height)
                    }
                }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(e: AnalyticsListener.EventTime, name: String, a: Long, b: Long) { videoDecoder = name }
                override fun onAudioDecoderInitialized(e: AnalyticsListener.EventTime, name: String, a: Long, b: Long) { audioDecoder = name }
                override fun onRenderedFirstFrame(e: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                    if (firstFrameAt == 0L) firstFrameAt = SystemClock.elapsedRealtime()
                    firstFrame.complete(Unit)
                }
                override fun onDroppedVideoFrames(e: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) { droppedEvents += count }
            })
            assView.post { engine.installSubtitleDelay() }
        }

        /** The same fit box as VideoStage in the app. The buffer keeps the video's size for the frame reads. */
        private fun fitStage(aspect: Float) = root.doOnLayout {
            val wider = aspect >= root.width.toFloat() / root.height
            val width = if (wider) root.width else (root.height * aspect).roundToInt()
            val height = if (wider) (root.width / aspect).roundToInt() else root.height
            stage.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        }

        fun release() {
            engine.release()
            root.removeAllViews()
        }
    }

    /** A fresh rig for one measurement. Stopping the activity ends the measurement and counts against [key]. */
    private inline fun <T> withRig(key: String, config: EngineConfig = EngineConfig(), block: (Rig) -> T): T {
        val rig = Rig(config)
        try {
            return block(rig)
        } catch (t: Throwable) {
            if (t !is CancellationException && onScreen) throw t
            interruptions.merge(key, 1, Int::plus)
            throw t as? CancellationException ?: CancellationException("stopped during $key", t)
        } finally {
            rig.release()
        }
    }

    private fun item(row: JSONObject, sidecarSuffix: String? = null): MediaItem {
        val f = File(dir, row.getString("file"))
        val b = MediaItem.Builder().setUri(Uri.fromFile(f))
        if (sidecarSuffix != null) {
            val side = File(dir, f.nameWithoutExtension + sidecarSuffix)
            val mime = when (side.extension.lowercase()) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            b.setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(SubtitleEncoding.toUtf8(this, side)))
                    .setMimeType(mime).setLanguage("ar").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
            ))
        }
        return b.build()
    }

    private suspend fun capture(view: SurfaceView, w: Int, h: Int): Bitmap? = suspendCoroutine { c ->
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(view, bmp, { r -> c.resume(if (r == PixelCopy.SUCCESS) bmp else null) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            c.resume(null)
        }
    }

    /** Reads the 16-bit frame number strip burned into the test clips. */
    private fun readStrip(bmp: Bitmap): Int {
        val b = maxOf(bmp.width / 24, 16)
        var v = 0
        for (i in 0 until 16) {
            val p = bmp.getPixel((i * b + b / 2).coerceAtMost(bmp.width - 1), (b / 2).coerceAtMost(bmp.height - 1))
            val luma = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
            if (luma > 128) v = v or (1 shl i)
        }
        return v
    }

    private fun Format?.brief(): JSONObject? = this?.let {
        JSONObject().put("mime", it.sampleMimeType).put("codecs", it.codecs).put("w", it.width).put("h", it.height)
            .put("fps", it.frameRate.toDouble()).put("ch", it.channelCount).put("rate", it.sampleRate).put("lang", it.language)
            .put("hdr", it.colorInfo?.colorTransfer ?: -1)
    }

    private fun tracksJson(t: Tracks): JSONArray {
        val a = JSONArray()
        for (g in t.groups) for (i in 0 until g.length) {
            a.put(JSONObject().put("type", g.type).put("selected", g.isTrackSelected(i)).put("supported", g.isTrackSupported(i)).put("format", g.getTrackFormat(i).brief()))
        }
        return a
    }

    // ---- matrix -------------------------------------------------------------

    private suspend fun matrix() {
        val manifest = JSONArray(File(dir, "manifest.json").readText())
        val only = intent.getStringExtra("only")?.split(",")?.filter { it.isNotBlank() }
        val keys = intent.getStringExtra("keys")?.split(",")?.filter { it.isNotBlank() }?.toSet()
        val playSec = intent.getIntExtra("playSec", 0)
        suspend fun runOnce(row: JSONObject, key: String, sidecar: String?) {
            if (key in rowsDone || (keys != null && key !in keys)) return
            runRow(row, key, sidecar, playSec)
            rowsDone += key
        }
        for (i in 0 until manifest.length()) {
            val row = manifest.getJSONObject(i)
            val id = row.getString("id")
            if (only != null && only.none { id.startsWith(it) }) continue
            val sidecars = row.optJSONArray("sidecars")
            val variants = buildList {
                add(null as String?)
                if (sidecars != null) for (k in 0 until sidecars.length()) add(sidecars.getString(k))
            }
            val extra = row.optJSONArray("extra")
            for ((v, suffix) in variants.withIndex()) {
                runOnce(row, if (v == 0) id else "$id@$suffix", suffix)
            }
            if (extra != null) for (k in 0 until extra.length()) {
                val r2 = JSONObject(row.toString()).put("file", extra.getString(k))
                r2.remove("sidecars")
                runOnce(r2, "$id+$k", null)
            }
        }
    }

    private suspend fun runRow(row: JSONObject, key: String, sidecar: String?, playSec: Int) {
        val res = JSONObject().put("key", key).put("id", row.getString("id")).put("file", row.getString("file"))
        val config = EngineConfig(assRenderType = AssRenderType.OVERLAY_OPEN_GL, maxRenderPixels = intent.getIntExtra("maxRenderPixels", 0))
        withRig(key, config) { rig ->
            Log.i(TAG, "row $key")
            try {
                measure(rig, row, sidecar, playSec, res)
            } catch (t: Throwable) {
                if (t is CancellationException || !onScreen) throw t
                res.put("exception", t.stackTraceToString().take(1500))
            }
            res.put("error", rig.errorText ?: JSONObject.NULL).put("incomplete", rig.incomplete)
        }
        res.put("interruptions", interruptions[key] ?: 0)
        File(out, "${safe(key)}.json").writeText(res.toString(1))
    }

    // Lint reads the Dispatchers.IO block in subtitleShots as if it ran on the main thread.
    @SuppressLint("ThreadConstraint")
    private suspend fun measure(rig: Rig, row: JSONObject, sidecar: String?, playSec: Int, res: JSONObject) {
        val p = rig.player
        rig.engine.addBundledFont()
        p.setMediaItem(item(row, sidecar))
        val t0 = SystemClock.elapsedRealtime()
        p.prepare()
        p.play()
        withTimeoutOrNull(FIRST_FRAME_TIMEOUT) { rig.firstFrame.await() }
        res.put("firstFrameMs", if (rig.firstFrameAt > 0) rig.firstFrameAt - t0 else -1)
        val dur = withTimeoutOrNull(3_000) { rig.ready.await(); p.duration } ?: C.TIME_UNSET
        res.put("durationMs", dur)
        if (rig.errorText == null) {
            rig.engine.tap.resetLevel()
            val fullMs = if (dur != C.TIME_UNSET) dur else 30_000
            val playMs = if (playSec > 0) playSec * 1000L else minOf(fullMs, MAX_PLAY_MS)
            val deadline = SystemClock.elapsedRealtime() + playMs
            while (SystemClock.elapsedRealtime() < deadline && rig.errorText == null && p.playbackState != Player.STATE_ENDED) delay(200)
            val c = p.videoDecoderCounters
            if (c != null) {
                c.ensureUpdated()
                res.put("renderedFrames", c.renderedOutputBufferCount).put("droppedFrames", c.droppedBufferCount).put("droppedToKeyframe", c.droppedToKeyframeCount)
            }
            res.put("audioRmsDbfs", rig.engine.tap.rmsDbfs())
            res.put("position", p.currentPosition)
        }
        res.put("videoDecoder", rig.videoDecoder).put("audioDecoder", rig.audioDecoder)
        res.put("tracks", tracksJson(p.currentTracks)).put("videoFormat", p.videoFormat.brief()).put("audioFormat", p.audioFormat.brief())
        res.put("cues", rig.cueLog.length()).put("cueSample", rig.cueLog.optJSONObject(0))
        res.put("assTrack", rig.engine.hasAssTrack)

        val fps = row.optDouble("fps", 0.0)
        if (rig.errorText == null && fps > 0 && row.getString("id") !in NO_STRIP) seeks(rig, row, res, fps)
        val hasSubs = row.has("subs") || sidecar != null || row.getString("file").contains("movtext")
        if (rig.errorText == null && hasSubs) subtitleShots(rig, res)
        res.put("assTrackAfter", rig.engine.hasAssTrack)
        if (rig.engine.hasAssTrack) res.put("layout", layoutJson(rig))
    }

    /** Where the overlay ended up, so tools/matrix.py can fail a libass frame that is not the picture. */
    private fun layoutJson(rig: Rig): JSONObject {
        val overlay = rig.engine.assHandler.overlaySize
        return JSONObject().put("windowW", root.width).put("windowH", root.height)
            .put("stageL", rig.stage.left).put("stageT", rig.stage.top).put("stageW", rig.stage.width).put("stageH", rig.stage.height)
            .put("overlayW", overlay.width).put("overlayH", overlay.height)
    }

    /** Saves what the subtitle layers drew, on black, because PixelCopy of a window skips the video surface. */
    private suspend fun subtitleShots(rig: Rig, res: JSONObject) {
        val p = rig.player
        var n = 0
        for (ms in listOf(1200L, 4200L, 7200L, 10200L, 13200L, 16200L)) {
            p.seekTo(ms)
            p.play()
            delay(800)
            p.pause()
            delay(120)
            val w = root.width
            val h = root.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val ok = suspendCoroutine<Boolean> { c ->
                PixelCopy.request(window, bmp, { r -> c.resume(r == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
            }
            if (ok) {
                val shot = File(out, "${safe(res.getString("key"))}-sub$n.png")
                withContext(Dispatchers.IO) { shot.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                n++
            }
            bmp.recycle()
        }
        res.put("subShots", n)
    }

    private suspend fun seeks(rig: Rig, row: JSONObject, res: JSONObject, fps: Double) {
        val p = rig.player
        val size = rig.videoSize ?: return
        val dur = p.duration
        if (dur == C.TIME_UNSET || size.width == 0) return
        p.pause()
        val rnd = Random(row.getString("id").hashCode())
        val list = JSONArray()
        var savedShot = false
        repeat(5) {
            val target = (dur * (0.05 + 0.85 * rnd.nextDouble())).toLong()
            val start = SystemClock.elapsedRealtime()
            p.seekTo(target)
            delay(30)
            var waited = 0
            while (p.playbackState != Player.STATE_READY && waited < 8000) { delay(10); waited += 10 }
            val readyMs = SystemClock.elapsedRealtime() - start
            delay(150)
            val bmp = capture(rig.surface, size.width, size.height)
            val entry = JSONObject().put("targetMs", target).put("readyMs", readyMs).put("lastPtsUs", rig.lastPtsUs)
            if (bmp != null) {
                val read = readStrip(bmp)
                val expected = (target * fps / 1000.0)
                val raw = abs(read - expected % 65536.0)
                entry.put("frameRead", read).put("frameExpected", expected).put("frameError", minOf(raw, 65536.0 - raw))
                if (!savedShot) {
                    File(out, "${safe(res.getString("key"))}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    res.put("shotFrame", read)
                    savedShot = true
                }
                bmp.recycle()
            } else {
                entry.put("capture", "failed")
            }
            list.put(entry)
        }
        res.put("seeks", list)
    }

    // ---- census -------------------------------------------------------------

    // SDK_INT_FULL needs API 36 and getHdrSdrRatio API 34; older phones record null for them.
    private fun census() {
        val j = JSONObject()
        val d = display ?: windowManager.defaultDisplay
        j.put("model", android.os.Build.MODEL).put("sdkInt", android.os.Build.VERSION.SDK_INT)
            .put("sdkIntFull", if (android.os.Build.VERSION.SDK_INT >= 36) android.os.Build.VERSION.SDK_INT_FULL else JSONObject.NULL).put("incremental", android.os.Build.VERSION.INCREMENTAL)
            .put("security", android.os.Build.VERSION.SECURITY_PATCH).put("fingerprint", android.os.Build.FINGERPRINT)
        val modes = JSONArray()
        for (m in d.supportedModes) modes.put(JSONObject().put("w", m.physicalWidth).put("h", m.physicalHeight).put("hz", m.refreshRate.toDouble()).put("alt", m.alternativeRefreshRates.joinToString()))
        j.put("modes", modes).put("currentHz", d.refreshRate.toDouble())
        val hdr = d.hdrCapabilities
        j.put("hdrTypes", JSONArray(hdr?.supportedHdrTypes?.toList() ?: emptyList<Int>()))
            .put("hdrMaxNits", hdr?.desiredMaxLuminance?.toDouble() ?: 0.0).put("hdrAvgNits", hdr?.desiredMaxAverageLuminance?.toDouble() ?: 0.0)
            .put("isHdr", d.isHdr).put("hdrSdrRatio", if (android.os.Build.VERSION.SDK_INT >= 34) d.hdrSdrRatio.toDouble() else JSONObject.NULL)
        val codecs = JSONArray()
        for (info in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) codecs.put(codecJson(info))
        j.put("codecs", codecs)
        File(out, "census.json").writeText(j.toString(1))
    }

    private fun codecJson(info: MediaCodecInfo): JSONObject {
        val types = JSONArray()
        for (t in info.supportedTypes) {
            val o = JSONObject().put("mime", t)
            try {
                val caps = info.getCapabilitiesForType(t)
                caps.videoCapabilities?.let {
                    o.put("maxW", it.supportedWidths.upper).put("maxH", it.supportedHeights.upper).put("maxFps", it.supportedFrameRates.upper)
                }
                caps.audioCapabilities?.let { o.put("maxCh", it.maxInputChannelCount) }
                o.put("profiles", JSONArray(caps.profileLevels.map { "${it.profile}/${it.level}" }.distinct()))
            } catch (_: Exception) {
            }
            types.put(o)
        }
        return JSONObject().put("name", info.name).put("encoder", info.isEncoder).put("hw", info.isHardwareAccelerated)
            .put("sw", info.isSoftwareOnly).put("vendor", info.isVendor).put("types", types)
    }

    // ---- soak ---------------------------------------------------------------

    private suspend fun soak() {
        val minutes = intent.getIntExtra("minutes", 30)
        val file = intent.getStringExtra("file") ?: "S4_ass_heavy_over_v5.mkv"
        withRig(SOAK) { rig ->
            val p = rig.player
            rig.engine.addBundledFont()
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(dir, file))))
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.prepare()
            p.play()
            val res = JSONObject().put("file", file).put("minutes", minutes).put("interruptions", interruptions[SOAK] ?: 0)
            val samples = JSONArray()
            val end = SystemClock.elapsedRealtime() + minutes * 60_000L
            while (SystemClock.elapsedRealtime() < end && rig.errorText == null) {
                delay(60_000)
                val c = p.videoDecoderCounters
                c?.ensureUpdated()
                samples.put(JSONObject().put("rendered", c?.renderedOutputBufferCount ?: -1).put("dropped", c?.droppedBufferCount ?: -1)
                    .put("nativeHeapMb", Debug.getNativeHeapAllocatedSize() shr 20))
                File(out, "soak.json").writeText(res.put("samples", samples).put("error", rig.errorText ?: JSONObject.NULL).put("decoder", rig.videoDecoder).toString(1))
            }
        }
    }

    // ---- delay proof (step 0.6) ---------------------------------------------

    private suspend fun delayProof() {
        val res = JSONObject()
        res.put("audio", audioDelayProof())
        res.put("subtitle", subtitleDelayProof())
        res.put("interruptions", interruptions[DELAY] ?: 0)
        File(out, "delay.json").writeText(res.toString(1))
    }

    private suspend fun gap(rig: Rig, ms: Long): Double {
        val sink = rig.engine.delaySink ?: return Double.NaN
        val gaps = ArrayList<Long>()
        rig.frameListener = { pts, release ->
            val raw = sink.rawPositionUs
            val ahead = (release - System.nanoTime()) / 1000
            if (raw > 0) gaps.add(pts - raw - ahead)
        }
        delay(ms)
        rig.frameListener = null
        val s = synchronized(gaps) { gaps.toList().sorted() }
        return if (s.isEmpty()) Double.NaN else s[s.size / 2] / 1000.0
    }

    private suspend fun audioDelayProof(): JSONObject = withRig(DELAY) { rig ->
        val p = rig.player
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(dir, "V11_60fps.mkv"))))
        p.prepare()
        p.play()
        withTimeoutOrNull(FIRST_FRAME_TIMEOUT) { rig.firstFrame.await() }
        delay(1500)
        val base = gap(rig, 2000)
        val out = JSONObject().put("baseGapMs", base)
        val steps = JSONArray()
        for (d in listOf(500L, -500L, 1500L, -1500L, 50L, 0L)) {
            val before = p.currentPosition
            rig.engine.setAudioDelayMs(d)
            delay(2500)
            val g = gap(rig, 1500)
            steps.put(JSONObject().put("delayMs", d).put("gapMs", g).put("gapChangeMs", g - base).put("errorMs", abs(g - base - d)).put("posBefore", before).put("posAfter", p.currentPosition))
        }
        out.put("steps", steps).put("error", rig.errorText ?: JSONObject.NULL)
    }

    private suspend fun subtitleDelayProof(): JSONObject {
        val out = JSONObject()
        for ((name, delayMs) in listOf("srt0" to 0L, "srt1000" to 1000L, "srtMinus800" to -800L)) {
            withRig(DELAY) { rig ->
                val manifestRow = JSONObject().put("file", "S1_srt_encodings.mp4")
                rig.player.setMediaItem(item(manifestRow, ".utf8.srt"))
                rig.engine.setSubtitleDelayMs(delayMs)
                rig.player.prepare()
                rig.player.play()
                delay(11_000)
                out.put(name, rig.cueLog)
            }
        }
        return out
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_.+@-]"), "_")

    companion object {
        private const val TAG = "FrameGlassQA"
        private const val PAUSED_MARK = "PAUSED.txt"
        private const val STATE_DONE = "rowsDone"
        private const val STATE_INTERRUPTIONS = "interruptions"
        private const val SOAK = "soak"
        private const val DELAY = "delay"
        private const val FIRST_FRAME_TIMEOUT = 20_000L
        private const val MAX_PLAY_MS = 30_000L
        private val NO_STRIP = setOf("V10a", "V12", "V13", "V14", "Y1", "C2", "C3")
    }
}
