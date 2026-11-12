package com.frameglass.data.repo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.PlaybackRepository
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.SegmentSource
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.api.SkipSegment
import com.frameglass.data.api.TrackPrefs
import com.frameglass.data.db.AudioDeviceDelay
import com.frameglass.data.db.FilePrefs
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.IntroSegment
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.PlaySession
import com.frameglass.data.db.SeriesPrefs
import com.frameglass.data.db.Title
import com.frameglass.data.logic.Assembler
import com.frameglass.data.logic.CleanName
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.Labels
import com.frameglass.data.logic.NaturalOrder
import com.frameglass.data.logic.NextUp
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.PathRange
import com.frameglass.data.logic.Playable
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.SegmentRules
import com.frameglass.data.logic.VaultRefs
import com.frameglass.data.logic.Versions
import com.frameglass.data.logic.WatchRules
import com.frameglass.data.meta.MetadataPipeline
import com.frameglass.data.meta.SegmentTarget
import com.frameglass.data.scan.ImageStore
import com.frameglass.data.scan.SidecarDisk
import com.frameglass.data.scan.Volumes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal class PlaybackRepositoryImpl(
    private val context: Context,
    private val db: FrameDb,
    private val volumes: Volumes,
    private val labels: Labels,
    private val pipeline: MetadataPipeline,
    private val images: ImageStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val sidecarLabels: SidecarLabels,
    private val vault: Lazy<VaultPlayback>,
    private val logger: WatchLogger,
) : PlaybackRepository, SegmentSource {
    private val scan get() = db.scan()
    private val watch get() = db.watch()

    @Volatile
    private var prefetch: Job? = null

    override suspend fun open(ref: PlayRef): PlaybackItem? = withContext(Dispatchers.IO) {
        when {
            ref.fileRowId != null -> scan.file(ref.fileRowId)?.takeIf { it.available && onDisk(it) }?.let { build(ref, it) }
            ref.playableKey != null && VaultRefs.isVault(ref.playableKey) -> vault.value.open(ref) { prefs(null, it) }
            ref.playableKey != null -> openPlayable(ref, ref.playableKey)
            ref.uri != null -> openUri(ref, ref.uri)
            else -> null
        }
    }

    /** A file can vanish between scans. The next scan marks it, and until then it must not reach the player. */
    private fun onDisk(file: MediaFile): Boolean = pathOf(file)?.let { File(it).isFile } == true

    private suspend fun openPlayable(ref: PlayRef, key: String): PlaybackItem? {
        val files = scan.byPlayable(key).filter { it.available && onDisk(it) }
        if (files.isNotEmpty()) return build(ref, Versions.pick(files))
        // An Open with item has no file row, so its key is all that is left of it.
        val session = watch.session()
        return if (session?.watchKey == key && session.uri != null) openUri(PlayRef(uri = session.uri), session.uri) else null
    }

    private fun pathOf(file: MediaFile): String? = volumes.pathMap()[file.volumeUuid]?.let { File(it, file.relPath).path }

    private suspend fun build(ref: PlayRef, file: MediaFile): PlaybackItem? {
        val path = pathOf(file) ?: return null
        val title = file.titleId?.let { db.titles().title(it) }
        val all = file.titleId?.let { scan.filesOfTitle(it) }.orEmpty()
        val states = file.titleId?.let { id -> watch.ofTitle(id) }.orEmpty().associateBy { it.playableKey }
        val playables = file.titleId?.let { id -> Assembler.playables(id, all, db.titles().episodeRows(id)) { states[it] } }.orEmpty()
        val current = playables.firstOrNull { it.key == file.playableKey }
        val next = current?.let { NextUp.after(playables, it.key) }
        val state = watch.get(file.playableKey)
        val session = watch.session()
        val sessionLive = session?.watchKey == file.playableKey
        val season = current?.season?.takeIf { current.numbered && title?.kind != com.frameglass.data.api.TitleKind.MOVIE }
        val episode = current?.number?.takeIf { season != null }
        val nextFile = if (next == null && needsFolderOrder(title)) nextInFolder(file) else null
        prefetchSegments(title, current, next)
        return PlaybackItem(
            ref = ref,
            playableKey = file.playableKey,
            fileRowId = file.rowId,
            titleId = file.titleId,
            uri = path,
            title = title?.name ?: ParsePaths.baseName(file.relPath),
            subtitle = subtitle(current, season, episode),
            season = season,
            episode = episode,
            posterPath = title?.coverPath ?: title?.framePath ?: file.framePath,
            resumeMs = WatchRules.resumeMs(state, sessionLive),
            durationMs = file.durationMs.takeIf { it > 0 } ?: state?.durationMs ?: 0,
            sidecars = sidecars(File(path), file.fileId),
            segments = file.titleId?.let { segments(it, season, current?.episode?.id) }.orEmpty(),
            prefs = prefs(file.titleId, file.fileId),
            next = next?.let { PlayRef(playableKey = it.key) } ?: nextFile?.let { PlayRef(fileRowId = it.rowId) },
            nextLabel = next?.let { nextLabel(it) } ?: nextFile?.let { CleanName.of(it.relPath, it.releaseTags) },
        )
    }

    private fun subtitle(current: Playable?, season: Int?, episode: Int?): String? {
        if (current == null || season == null || episode == null) return null
        return labels.withTitle(labels.episode(season, episode), current.episode?.name)
    }

    private fun nextLabel(p: Playable): String =
        if (p.numbered) labels.withTitle(labels.episode(p.season, p.number), p.episode?.name)
        else CleanName.of(p.best.relPath, p.best.releaseTags)

    /** F-S2: a file with no metadata continues with the next file in its folder. An OMDb match is metadata too. */
    private fun needsFolderOrder(title: Title?): Boolean = title == null || (title.tmdbId == null && title.imdbId == null)

    private suspend fun nextInFolder(file: MediaFile): MediaFile? {
        val folder = ParsePaths.folderOf(file.relPath)
        val siblings = scan.filesUnder(file.volumeUuid, PathRange.low(folder), PathRange.high(folder))
            .filter { ParsePaths.folderOf(it.relPath) == folder && it.nameKind != NameKinds.EXTRA }
            .sortedWith { a, b -> NaturalOrder.compare(a.relPath, b.relPath) }
        val index = siblings.indexOfFirst { it.rowId == file.rowId }
        if (index < 0) return null
        return siblings.drop(index + 1).firstOrNull { it.available }
    }

    private fun prefetchSegments(title: Title?, current: Playable?, next: Playable?) {
        val id = title?.id ?: return
        val targets = listOfNotNull(current, next).mapNotNull { p ->
            p.episode?.let { SegmentTarget(it.id, it.season, it.number, p.durationMs) }
        }
        if (targets.isNotEmpty()) prefetch = scope.launch { runCatching { pipeline.prefetchSegments(id, targets) } }
    }

    override suspend fun segmentsAfterFetch(item: PlaybackItem): List<SkipSegment> = withContext(Dispatchers.IO) {
        prefetch?.join()
        val titleId = item.titleId ?: return@withContext item.segments
        segments(titleId, item.season, item.fileRowId?.let { scan.file(it)?.episodeId })
    }

    private suspend fun segments(titleId: Long, season: Int?, episodeId: Long?) =
        SegmentRules.pick(db.meta().segments(titleId), season, episodeId)

    private suspend fun prefs(titleId: Long?, fileKey: String): TrackPrefs {
        val series = titleId?.let { watch.seriesPrefs(it) }
        val file = watch.filePrefs(fileKey)
        return TrackPrefs(
            audioLanguage = file?.audioTrack ?: series?.audioLang,
            subtitleLanguage = file?.subtitleTrack ?: series?.subLang,
            subtitlesOff = series?.subsOff ?: file?.subsOff ?: false,
            speed = series?.speed ?: file?.speed ?: 1f,
            subtitleUri = file?.subtitleUri,
            subtitleEncoding = file?.subtitleEncoding,
            subtitleDelayMs = file?.subtitleDelayMs ?: 0,
            aspectMode = series?.aspectMode ?: file?.aspectMode,
        )
    }

    /** F-S7, plus subtitles downloaded for this file into app storage. */
    private fun sidecars(video: File, fileKey: String): List<SidecarSubtitle> =
        SidecarDisk.matching(video).map(sidecarLabels::of) + sidecarLabels.saved(fileKey)

    /** Open with. A path that resolves to a library row plays as that row, so its progress is shared. */
    private suspend fun openUri(ref: PlayRef, uri: String): PlaybackItem? {
        val parsed = Uri.parse(uri)
        val path = resolvePath(parsed)
        // A private file opens through the vault or not at all, whatever path reaches this.
        if (path != null && vault.value.owns(path)) return null
        if (path != null) {
            volumes.locate(path)?.let { (volume, rel) ->
                scan.byPath(volume.uuid, rel)?.let { return build(ref, it.copy(available = true)) }
            }
        }
        val key = PlayableKeys.uri(sha256(uri))
        val name = displayName(parsed) ?: path?.let { File(it).name } ?: uri.substringAfterLast('/')
        val state = watch.get(key)
        val session = watch.session()
        val file = path?.let(::File)?.takeIf { it.exists() }
        val tag = SidecarDisk.tagOf(file?.parentFile ?: File(""), name)
        return PlaybackItem(
            ref = ref,
            playableKey = key,
            fileRowId = null,
            titleId = null,
            uri = file?.path ?: uri,
            title = CleanName.of(name, null),
            subtitle = null,
            season = tag?.season,
            episode = tag?.episode,
            posterPath = null,
            resumeMs = WatchRules.resumeMs(state, session?.watchKey == key),
            durationMs = state?.durationMs ?: 0,
            sidecars = file?.let { sidecars(it, key) }.orEmpty(),
            segments = emptyList(),
            prefs = prefs(null, key),
            next = null,
            nextLabel = null,
        )
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun resolvePath(uri: Uri): String? = when (uri.scheme) {
        null, "file" -> uri.path
        "content" -> documentPath(uri) ?: mediaStorePath(uri)
        else -> null
    }

    private fun documentPath(uri: Uri): String? {
        if (uri.authority != EXTERNAL_DOCUMENTS || !DocumentsContract.isDocumentUri(context, uri)) return null
        val id = DocumentsContract.getDocumentId(uri)
        val volume = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        val base = volumes.pathMap()[if (volume == "primary") Volumes.PRIMARY else volume] ?: return null
        return ParsePaths.join(base, rel)
    }

    @Suppress("DEPRECATION")
    private fun mediaStorePath(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    override suspend fun savePosition(item: PlaybackItem, positionMs: Long, durationMs: Long, speed: Float) = withContext(Dispatchers.IO) {
        // Private time counts in the totals only, so the log never holds a private title.
        logger.onPosition(item.playableKey, item.titleId.takeUnless { item.isPrivate }, positionMs, speed)
        if (item.isPrivate) return@withContext vault.value.savePosition(item, positionMs, durationMs)
        val now = clock.now()
        val reached = db.withTransaction {
            val key = item.playableKey
            val before = watch.get(key)
            val after = WatchRules.onPosition(before, key, item.titleId, positionMs, durationMs, now)
            watch.upsert(after)
            watch.upsertSession(PlaySession(playableKey = item.ref.playableKey, fileRowId = item.ref.fileRowId, uri = item.ref.uri, watchKey = key, updatedAt = now))
            if (item.fileRowId != null && durationMs > 0) scan.fillDuration(item.fileRowId, durationMs)
            after.watched && before?.watched != true
        }
        if (reached) WatchlistKeeper.settle(db, item.titleId)
    }

    override suspend fun markFinished(item: PlaybackItem) = withContext(Dispatchers.IO) {
        logger.onFinished(item.playableKey)
        if (item.isPrivate) return@withContext vault.value.markFinished(item)
        val now = clock.now()
        val file = item.fileRowId?.let { scan.file(it) }
        val titleId = item.titleId
        val keys = if (file != null && titleId != null) {
            PlayableKeys.covered(file.playableKey, titleId, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedEpisodeEnd)
        } else {
            listOf(item.playableKey)
        }
        db.withTransaction {
            keys.forEach { key -> watch.upsert(WatchRules.onFinished(watch.get(key), key, titleId, item.durationMs, now)) }
            watch.clearSession()
        }
        WatchlistKeeper.settle(db, titleId)
    }

    override suspend fun savePrefs(item: PlaybackItem, prefs: TrackPrefs) = withContext(Dispatchers.IO) {
        val fileKey = if (item.isPrivate) {
            vault.value.fileKey(item) ?: return@withContext
        } else {
            item.fileRowId?.let { scan.file(it)?.fileId } ?: item.playableKey
        }
        db.withTransaction {
            item.titleId?.let { titleId ->
                watch.upsertSeriesPrefs(
                    SeriesPrefs(titleId, prefs.audioLanguage, prefs.subtitleLanguage, prefs.subtitlesOff, prefs.speed, prefs.aspectMode),
                )
            }
            watch.upsertFilePrefs(
                FilePrefs(
                    fileId = fileKey,
                    audioTrack = prefs.audioLanguage,
                    subtitleTrack = prefs.subtitleLanguage,
                    subtitleUri = prefs.subtitleUri,
                    subtitleEncoding = prefs.subtitleEncoding,
                    subtitleDelayMs = prefs.subtitleDelayMs,
                    subsOff = prefs.subtitlesOff,
                    speed = prefs.speed,
                    aspectMode = prefs.aspectMode,
                ),
            )
        }
    }

    override suspend fun saveManualIntro(item: PlaybackItem, startMs: Long, endMs: Long, wholeSeries: Boolean) = withContext(Dispatchers.IO) {
        val titleId = item.titleId ?: return@withContext
        val season = if (wholeSeries) null else item.season
        val meta = db.meta()
        db.withTransaction {
            meta.deleteSeasonSegments(titleId, season, SegmentRules.MANUAL)
            meta.insertSegment(
                IntroSegment(
                    titleId = titleId, season = season, episodeId = null, kind = SegmentKind.OP,
                    startMs = startMs, endMs = endMs, source = SegmentRules.MANUAL, confidence = 1.0,
                ),
            )
        }
        Unit
    }

    override suspend fun bluetoothDelayMs(deviceKey: String): Long = withContext(Dispatchers.IO) {
        watch.deviceDelay(deviceKey)?.delayMs ?: 0
    }

    override suspend fun saveBluetoothDelay(deviceKey: String, ms: Long) = withContext(Dispatchers.IO) {
        watch.upsertDeviceDelay(AudioDeviceDelay(deviceKey, ms))
    }

    override suspend fun lastSession(): PlayRef? = withContext(Dispatchers.IO) {
        watch.session()?.let { PlayRef(it.playableKey, it.fileRowId, it.uri) }
    }

    private companion object {
        const val EXTERNAL_DOCUMENTS = "com.android.externalstorage.documents"
    }
}
