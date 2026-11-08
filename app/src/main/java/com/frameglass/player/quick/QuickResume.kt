package com.frameglass.player.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import com.frameglass.data.api.LibraryRepository
import com.frameglass.data.api.ResumeEntry
import com.frameglass.data.api.frameServices
import com.frameglass.glass.bidiIsolate
import com.frameglass.player.MainActivity
import com.frameglass.player.R
import com.frameglass.playerui.PlayerRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FrameQuick"

/** A shortcut needs an action. The player reads only the extras, as for any other open. */
private const val ACTION_RESUME = "com.frameglass.player.action.RESUME"

/**
 * Progress is saved every 5 s while a video plays, so the shortcuts and the widget wait for a quiet moment: they
 * change after a pause, a stop or a scan, and cost nothing while the video runs.
 */
private const val QUIET_MS = 8_000L

private const val ICON_PX = 192
private const val COVER_W = 120
private const val COVER_H = 180

/** Launcher shortcuts and the Continue watching widget, from one list of items to resume. */
internal object QuickResume {
    @OptIn(FlowPreview::class)
    fun start(context: Context, library: LibraryRepository, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch {
            library.resumeChanges.debounce(QUIET_MS).collectLatest { publish(app, library.quickResume()) }
        }
    }

    suspend fun publish(context: Context, entries: List<ResumeEntry>) = withContext(Dispatchers.IO) {
        runCatching { publishShortcuts(context, entries) }.onFailure { Log.w(TAG, "shortcuts not updated", it) }
        runCatching { publishWidgets(context, entries) }.onFailure { Log.w(TAG, "widget not updated", it) }
    }

    fun playIntent(context: Context, entry: ResumeEntry): Intent =
        PlayerRoute.intent(context, entry.play).setAction(ACTION_RESUME)

    private fun publishShortcuts(context: Context, entries: List<ResumeEntry>) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val limit = min(ShortcutPlan.MAX, manager.maxShortcutCountPerActivity)
        val shortcuts = ShortcutPlan.of(entries, limit).map { spec ->
            val entry = spec.entry
            val name = isolate(entry.name)
            val long = entry.episodeLabel?.let { context.getString(R.string.app_shortcut_resume_episode, name, isolate(it)) }
                ?: context.getString(R.string.app_shortcut_resume, name)
            ShortcutInfo.Builder(context, spec.id)
                .setShortLabel(context.getString(R.string.app_shortcut_resume, name))
                .setLongLabel(long)
                .setIcon(coverIcon(entry.coverPath) ?: Icon.createWithResource(context, R.mipmap.app_icon))
                .setIntent(playIntent(context, entry))
                .setRank(spec.rank)
                .build()
        }
        manager.dynamicShortcuts = shortcuts
    }

    private fun publishWidgets(context: Context, entries: List<ResumeEntry>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, ContinueWidget::class.java))
        if (ids.isEmpty()) return
        val views = widgetViews(context, entries)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun widgetViews(context: Context, entries: List<ResumeEntry>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_continue)
        val slots = WidgetSlots.of(entries)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_header, open)
        views.setOnClickPendingIntent(R.id.widget_empty, open)
        val empty = slots.all { it == null }
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        slots.forEachIndexed { index, entry ->
            val row = ROWS[index]
            if (entry == null) {
                views.setViewVisibility(row.root, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(row.root, View.VISIBLE)
            views.setTextViewText(row.title, isolate(entry.name))
            val label = entry.episodeLabel
            views.setViewVisibility(row.subtitle, if (label == null) View.GONE else View.VISIBLE)
            if (label != null) views.setTextViewText(row.subtitle, isolate(label))
            val cover = entry.coverPath?.let { decode(it, COVER_W, COVER_H) }
            if (cover != null) views.setImageViewBitmap(row.cover, cover) else views.setImageViewResource(row.cover, R.drawable.widget_cover_frame)
            val progress = WidgetSlots.progressOf(entry)
            views.setViewVisibility(row.progress, if (progress > 0) View.VISIBLE else View.INVISIBLE)
            views.setProgressBar(row.progress, WidgetSlots.PROGRESS_MAX, progress, false)
            val tap = PendingIntent.getActivity(
                context, index + 1, playIntent(context, entry).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(row.root, tap)
        }
        return views
    }

    private class Row(val root: Int, val cover: Int, val title: Int, val subtitle: Int, val progress: Int)

    private val ROWS = listOf(
        Row(R.id.widget_row0, R.id.widget_cover0, R.id.widget_title0, R.id.widget_sub0, R.id.widget_progress0),
        Row(R.id.widget_row1, R.id.widget_cover1, R.id.widget_title1, R.id.widget_sub1, R.id.widget_progress1),
        Row(R.id.widget_row2, R.id.widget_cover2, R.id.widget_title2, R.id.widget_sub2, R.id.widget_progress2),
    )

    /** A small square from the middle of the cover. Decoding one poster this size is cheap. */
    private fun coverIcon(path: String?): Icon? {
        val bitmap = path?.let { decode(it, ICON_PX, ICON_PX) } ?: return null
        return Icon.createWithBitmap(bitmap)
    }

    /** Scaled and cropped to fill [width] by [height], or null when the file is gone or not an image. */
    private fun decode(path: String, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val cropW = min(source.width, (width / scale).toInt().coerceAtLeast(1))
        val cropH = min(source.height, (height / scale).toInt().coerceAtLeast(1))
        val cropped = Bitmap.createBitmap(source, (source.width - cropW) / 2, (source.height - cropH) / 2, cropW, cropH)
        return cropped.scale(width, height)
    }

    /** Keeps an English title from pulling Arabic text around it. */
    private fun isolate(text: String) = bidiIsolate(text)
}

/** The home-screen widget. Android asks for an update on its own period too, and this reads the library then. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = runCatching { app.frameServices.library.quickResume() }.getOrDefault(emptyList())
                val views = QuickResume.widgetViews(app, entries)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.w(TAG, "widget update failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
