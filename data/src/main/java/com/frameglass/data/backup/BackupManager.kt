package com.frameglass.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.AudioDeviceDelay
import com.frameglass.data.db.EnrichState
import com.frameglass.data.db.ExcludedFolder
import com.frameglass.data.db.FilePrefs
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.GroupAlias
import com.frameglass.data.db.IntroSegment
import com.frameglass.data.db.LookupJob
import com.frameglass.data.db.LookupQueue
import com.frameglass.data.db.LookupState
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.RootFolder
import com.frameglass.data.db.SeriesPrefs
import com.frameglass.data.db.Title
import com.frameglass.data.db.Volume
import com.frameglass.data.db.WatchState
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.SegmentRules
import com.frameglass.data.meta.Keys
import com.frameglass.data.meta.SearchIndexer
import com.frameglass.data.meta.TitleOps
import com.frameglass.data.repo.SettingsStore
import com.frameglass.data.scan.CoverColors
import com.frameglass.data.scan.ImageStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** F-L5. A zip of versioned JSON plus the owner's own covers. It holds no keys and no TMDB images. */
internal class BackupManager(
    private val context: Context,
    private val db: FrameDb,
    private val settings: SettingsStore,
    private val images: ImageStore,
    private val ops: TitleOps,
    private val index: SearchIndexer,
    private val clock: Clock,
) {
    suspend fun export(target: Uri): Result<Unit> = runCatching {
        val stream = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("cannot open target")
        stream.use { write(it) }
    }

    private suspend fun collect(): Pair<BackupDoc, Map<String, File>> {
        val scan = db.scan()
        val watch = db.watch()
        val covers = HashMap<String, File>()
        val titles = db.titles().allTitles().map { title ->
            val file = title.coverPath?.takeIf { title.manualCover }?.let(::File)?.takeIf { it.exists() }
            val entry = file?.let { BackupCodec.COVER_DIR + "t${title.id}." + ParsePaths.extension(it.name).ifEmpty { "jpg" } }
            if (entry != null) covers[entry] = file
            BackupTitle(
                id = title.id, kind = title.kind.name, groupKey = title.groupKey, groupName = title.groupName,
                localName = title.localName, localYear = title.localYear, tmdbId = title.tmdbId,
                tmdbIsMovie = title.tmdbIsMovie, anilistId = title.anilistId, malId = title.malId, imdbId = title.imdbId,
                matchState = title.matchState.name, matchConfidence = title.matchConfidence, manualMatch = title.manualMatch,
                coverFile = entry, removed = title.removed, createdAt = title.createdAt, favorite = title.favorite,
            )
        }
        val files = scan.allFiles()
        val full = BackupDoc(
            exportedAt = clock.now(),
            settings = BackupSettings.from(settings.current()),
            volumes = scan.volumes().map { BackupVolume(it.uuid, it.label, it.isRemovable, it.lastPath) },
            roots = scan.roots().map { BackupRoot(it.volumeUuid, it.relPath, it.addedAt) },
            titles = titles,
            aliases = db.titles().aliases().map { BackupAlias(it.groupKey, it.titleId) },
            files = files.map(::backupFile),
            watch = watch.all().map { BackupWatch(it.playableKey, it.positionMs, it.durationMs, it.watched, it.lastPlayedAt, it.titleId) },
            seriesPrefs = watch.allSeriesPrefs().map { BackupSeriesPrefs(it.titleId, it.audioLang, it.subLang, it.subsOff, it.speed, it.aspectMode) },
            filePrefs = watch.allFilePrefs().map {
                BackupFilePrefs(it.fileId, it.audioTrack, it.subtitleTrack, it.subtitleUri, it.subtitleEncoding, it.subtitleDelayMs, it.subsOff, it.speed, it.aspectMode)
            },
            audioDelays = watch.allDeviceDelays().map { BackupDelay(it.deviceKey, it.delayMs) },
            intros = db.meta().segmentsBySource(SegmentRules.MANUAL).map { BackupIntro(it.titleId, it.season, it.kind.name, it.startMs, it.endMs) },
            excluded = scan.excluded().map { BackupExcluded(it.volumeUuid, it.relPath, it.addedAt) },
        )
        // Private files have no rows here, but a title and preferences left behind would still name them.
        val vault = db.vault()
        val doc = BackupPrivacy.strip(
            full,
            titles = vault.titleIds().toSet() - files.mapNotNullTo(HashSet()) { it.titleId },
            files = vault.fileIds().toSet() - files.mapTo(HashSet()) { it.fileId },
        )
        val kept = doc.titles.mapNotNullTo(HashSet()) { it.coverFile }
        return doc to covers.filterKeys { it in kept }
    }

    private fun backupFile(f: MediaFile) = BackupFile(
        fileId = f.fileId, volumeUuid = f.volumeUuid, relPath = f.relPath, size = f.size, mtime = f.mtime,
        durationMs = f.durationMs, width = f.width, height = f.height, videoCodec = f.videoCodec, hdrType = f.hdrType,
        audioSummary = f.audioSummary, osHash = f.osHash, firstSeenAt = f.firstSeenAt, titleId = f.titleId,
        releaseTags = f.releaseTags, hasSidecarSub = f.hasSidecarSub, hasEmbeddedSub = f.hasEmbeddedSub,
        playableKey = f.playableKey, parsedSeason = f.parsedSeason, parsedEpisode = f.parsedEpisode,
        parsedEpisodeEnd = f.parsedEpisodeEnd, parsedAbsolute = f.parsedAbsolute, nameKind = f.nameKind,
    )

    private suspend fun write(out: OutputStream) {
        val (doc, covers) = collect()
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupCodec.JSON_ENTRY))
            zip.write(BackupCodec.encode(doc).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, file) in covers) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun import(source: Uri): Result<Unit> = runCatching {
        val folder = File(context.cacheDir, "restore-${clock.now()}").apply { mkdirs() }
        try {
            val stream = context.contentResolver.openInputStream(source) ?: throw BackupException("cannot open backup")
            val archive = stream.use { BackupArchive.read(it, folder) }
            safetyCopy()
            restore(archive)
        } finally {
            folder.deleteRecursively()
        }
    }

    private suspend fun safetyCopy() {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        File(dir, "safety-${clock.now()}.zip").outputStream().use { write(it) }
        dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(KEEP_SAFETY_COPIES).forEach { it.delete() }
    }

    /** Keeps a title's id when it is free, so a wiped phone comes back with the same keys. */
    private suspend fun restoreTitle(item: BackupTitle, cover: File?): Long {
        val titles = db.titles()
        val colors = cover?.let { CoverColors.fromFile(it.path) }
        val kind = TitleKind.valueOf(item.kind)
        val state = MatchState.valueOf(item.matchState)
        val existing = titles.byGroupKey(item.groupKey)
        if (existing != null) {
            val sameMatch = existing.tmdbId == item.tmdbId
            titles.update(
                existing.copy(
                    tmdbId = item.tmdbId, tmdbIsMovie = item.tmdbIsMovie, anilistId = item.anilistId, malId = item.malId,
                    imdbId = item.imdbId, matchState = state, matchConfidence = item.matchConfidence,
                    manualMatch = item.manualMatch, removed = item.removed, favorite = item.favorite,
                    coverPath = cover?.path ?: existing.coverPath, manualCover = cover != null || existing.manualCover,
                    dominantColor = colors?.dominant ?: existing.dominantColor,
                    coverLuma = colors?.luma ?: existing.coverLuma,
                    fetchedAt = if (sameMatch) existing.fetchedAt else null,
                    metaLanguage = if (sameMatch) existing.metaLanguage else null,
                ),
            )
            return existing.id
        }
        val wanted = if (titles.title(item.id) == null) item.id else 0
        return titles.insert(
            Title(
                id = wanted, kind = kind, groupKey = item.groupKey, groupName = item.groupName,
                localName = item.localName, localYear = item.localYear, name = item.localName, year = item.localYear,
                tmdbId = item.tmdbId, tmdbIsMovie = item.tmdbIsMovie, anilistId = item.anilistId, malId = item.malId,
                imdbId = item.imdbId, matchState = state, matchConfidence = item.matchConfidence,
                manualMatch = item.manualMatch, coverPath = cover?.path, manualCover = cover != null,
                dominantColor = colors?.dominant, coverLuma = colors?.luma,
                sortKey = Keys.normalize(item.localName), removed = item.removed, createdAt = item.createdAt,
                favorite = item.favorite,
            ),
        )
    }

    private suspend fun restoreFile(item: BackupFile, ids: Map<Long, Long>, stamp: Long, vaulted: Set<String>) {
        // A backup older than a move must not bring the file back into view.
        if (item.fileId in vaulted) return
        val scan = db.scan()
        val titleId = item.titleId?.let(ids::get)
        val key = PlayableKeys.remap(item.playableKey, ids)
        val existing = scan.byPath(item.volumeUuid, item.relPath)
        if (existing != null) {
            if (titleId != null) scan.relink(existing.rowId, titleId, null, key)
            return
        }
        // Available stays false until the rescan that follows sees the file.
        scan.insertFile(
            MediaFile(
                fileId = item.fileId, volumeUuid = item.volumeUuid, relPath = item.relPath, size = item.size,
                mtime = item.mtime, durationMs = item.durationMs, width = item.width, height = item.height,
                videoCodec = item.videoCodec, hdrType = item.hdrType, audioSummary = item.audioSummary,
                osHash = item.osHash, available = false, firstSeenAt = item.firstSeenAt, lastSeenAt = stamp,
                titleId = titleId, episodeId = null, releaseTags = item.releaseTags, hasSidecarSub = item.hasSidecarSub,
                playableKey = key, parsedSeason = item.parsedSeason, parsedEpisode = item.parsedEpisode,
                parsedEpisodeEnd = item.parsedEpisodeEnd, parsedAbsolute = item.parsedAbsolute, nameKind = item.nameKind,
                hasEmbeddedSub = item.hasEmbeddedSub, enrichState = EnrichState.PENDING,
            ),
        )
    }

    private suspend fun restore(archive: BackupArchive) {
        val doc = archive.doc
        val stamp = clock.now()
        val covers = doc.titles.mapNotNull { title ->
            val source = title.coverFile?.let(archive.covers::get) ?: return@mapNotNull null
            val target = images.coverFile(title.id, stamp, ParsePaths.extension(source.name).ifEmpty { "jpg" })
            source.copyTo(target, overwrite = true)
            title.id to target
        }.toMap()

        val ids = HashMap<Long, Long>()
        val scan = db.scan()
        val titles = db.titles()
        val watch = db.watch()
        val meta = db.meta()
        val vaulted = db.vault().fileIds().toSet()
        db.withTransaction {
            for (volume in doc.volumes) {
                if (scan.volumes().none { it.uuid == volume.uuid }) {
                    scan.upsertVolume(Volume(volume.uuid, volume.label, volume.isRemovable, stamp, volume.lastPath))
                }
            }
            doc.roots.forEach { scan.insertRoot(RootFolder(volumeUuid = it.volumeUuid, relPath = it.relPath, addedAt = it.addedAt)) }
            doc.excluded.forEach { scan.insertExcluded(ExcludedFolder(it.volumeUuid, it.relPath, it.addedAt)) }
            for (item in doc.titles.sortedBy { it.id }) ids[item.id] = restoreTitle(item, covers[item.id])
            doc.aliases.forEach { titles.upsertAlias(GroupAlias(it.groupKey, ids.getValue(it.titleId))) }
            doc.files.forEach { restoreFile(it, ids, stamp, vaulted) }
            for (item in doc.watch) {
                val key = PlayableKeys.remap(item.playableKey, ids)
                watch.upsert(WatchState(key, item.positionMs, item.durationMs, item.watched, item.lastPlayedAt, item.titleId?.let(ids::get)))
            }
            for (item in doc.seriesPrefs) {
                watch.upsertSeriesPrefs(SeriesPrefs(ids.getValue(item.titleId), item.audioLang, item.subLang, item.subsOff, item.speed, item.aspectMode))
            }
            for (item in doc.filePrefs) {
                watch.upsertFilePrefs(
                    FilePrefs(item.fileId, item.audioTrack, item.subtitleTrack, item.subtitleUri, item.subtitleEncoding, item.subtitleDelayMs, item.subsOff, item.speed, item.aspectMode),
                )
            }
            doc.audioDelays.forEach { watch.upsertDeviceDelay(AudioDeviceDelay(it.deviceKey, it.delayMs)) }
            for (item in doc.intros) {
                val titleId = ids.getValue(item.titleId)
                meta.deleteSeasonSegments(titleId, item.season, SegmentRules.MANUAL)
                meta.insertSegment(
                    IntroSegment(
                        titleId = titleId, season = item.season, episodeId = null, kind = SegmentKind.valueOf(item.kind),
                        startMs = item.startMs, endMs = item.endMs, source = SegmentRules.MANUAL, confidence = 1.0,
                    ),
                )
            }
            for (item in doc.titles) {
                val id = ids.getValue(item.id)
                val title = titles.title(id) ?: continue
                ops.linkMissingEpisodes(id, title.kind)
                index.index(id)
                queue(title, stamp)
            }
        }
        val font = doc.settings.subtitleFontPath
        val fontExists = font != null && File(font).exists()
        settings.update { doc.settings.toAppSettings(fontExists) }
    }

    private suspend fun queue(title: Title, stamp: Long) {
        val job = when {
            title.tmdbId != null && title.fetchedAt == null -> LookupJob.REFRESH
            title.tmdbId == null && title.matchState in REQUEUE_STATES -> LookupJob.MATCH
            else -> return
        }
        db.meta().upsertQueue(LookupQueue(title.id, LookupState.QUEUED, 0, stamp, job))
    }

    private companion object {
        const val KEEP_SAFETY_COPIES = 3
        val REQUEUE_STATES = setOf(MatchState.UNMATCHED, MatchState.QUEUED)
    }
}
