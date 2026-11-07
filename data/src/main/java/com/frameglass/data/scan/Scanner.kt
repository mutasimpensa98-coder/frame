package com.frameglass.data.scan

import android.os.Environment
import androidx.room.withTransaction
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.ScanState
import com.frameglass.data.db.CachedFile
import com.frameglass.data.db.EnrichState
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.RootFolder
import com.frameglass.data.db.ScanLog
import com.frameglass.data.db.Title
import com.frameglass.data.db.Volume
import com.frameglass.data.logic.AutoReach
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.EpisodeTag
import com.frameglass.data.logic.Exclusions
import com.frameglass.data.logic.FileIdentity
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.PathRange
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.ScanOrder
import com.frameglass.data.logic.ScanRoots
import com.frameglass.data.logic.ScanRules
import com.frameglass.data.logic.SidecarFile
import com.frameglass.data.logic.SidecarMatcher
import com.frameglass.data.meta.TitleResolver
import com.frameglass.parser.NameKind
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** `matching` is filled in by the repository, which also knows the queue and the key. */
internal class ScanProgress {
    private val mutable = MutableStateFlow(ScanState(running = false, found = 0, matching = 0))

    @Volatile
    private var folderShownAt = Long.MIN_VALUE

    val state: StateFlow<ScanState> = mutable
    val running: Boolean get() = mutable.value.running

    fun begin() {
        folderShownAt = Long.MIN_VALUE
        mutable.update { it.copy(running = true, found = 0, currentFolder = null) }
    }

    /** Counts every video this scan passes, new or known, so a rescan reads the same as a first scan. */
    fun found(count: Int = 1) = mutable.update { it.copy(found = it.found + count) }

    /** A walk passes folders far faster than anyone can read them. */
    fun inFolder(path: String, now: Long) {
        if (folderShownAt != Long.MIN_VALUE && now - folderShownAt < FOLDER_EVERY_MS) return
        folderShownAt = now
        mutable.update { it.copy(currentFolder = path) }
    }

    fun end() = mutable.update { it.copy(running = false, currentFolder = null) }

    private companion object {
        const val FOLDER_EVERY_MS = 200L
    }
}

/**
 * Section 4.6 as changed by the 2026-11-07 waiver. With whole-phone search on, every mounted volume is walked
 * with no folder choice, and folders added by hand keep their own run. One code path serves them all: a first
 * pass over the folders MediaStore names, so videos appear within seconds, then a directory walk with an mtime
 * and size cache for what MediaStore misses. A row is written only when something changed, so an unchanged
 * rescan is reads only. Directory order is fixed and the last finished directory is checkpointed, so a killed
 * scan resumes where it stopped.
 */
internal class Scanner(
    private val db: FrameDb,
    private val volumes: Volumes,
    private val index: MediaIndex,
    private val settings: suspend () -> AppSettings,
    private val resolver: TitleResolver,
    private val clock: Clock,
    private val progress: ScanProgress,
    private val beforeScan: suspend () -> Unit,
    private val onNewRows: () -> Unit,
) {
    private val lock = Mutex()

    val running: Boolean get() = progress.running

    /** Hops to the IO pool itself, because Settings calls this from a screen. */
    suspend fun scan() = withContext(Dispatchers.IO) {
        lock.withLock {
            // Without All Files access every list call fails, and that must not look like deleted files.
            if (!Environment.isExternalStorageManager()) return@withLock
            progress.begin()
            try {
                scanAll()
            } finally {
                progress.end()
            }
        }
    }

    private suspend fun scanAll() {
        beforeScan()
        val dao = db.scan()
        val now = clock.now()
        val mounted = volumes.mounted()
        mounted.forEach { dao.upsertVolume(Volume(it.uuid, it.label, it.removable, now, it.path)) }
        val mountedIds = mounted.mapTo(HashSet()) { it.uuid }
        dao.volumes().filter { it.uuid !in mountedIds }.forEach { dao.markVolumeUnavailable(it.uuid) }
        val opts = settings()
        val added = dao.roots()
        val exclusions = Exclusions(dao.excluded())
        dropExcluded(exclusions, added)
        val open = mounted.map { it.uuid }.filterNot(exclusions::wholeVolume)
        val roots = ScanRoots.plan(added, open, opts.scanWholePhone)
        val seeds = if (opts.scanWholePhone) index.folders(mounted) else emptyMap()
        for (root in roots) {
            val volume = mounted.firstOrNull { it.uuid == root.volumeUuid } ?: continue
            scanRoot(root, volume, roots, opts, seeds[volume.uuid].orEmpty(), exclusions)
        }
        if (!opts.scanWholePhone) dropUncovered(added)
        dao.trimLogs(now - LOG_KEEP_MS)
    }

    /** With whole-phone search off, only added folders count. Titles and progress stay for when it is back on. */
    private suspend fun dropUncovered(added: List<RootFolder>) {
        val dao = db.scan()
        dao.allFiles().filter { !ScanRoots.covers(added, it.volumeUuid, it.relPath) }
            .map { it.rowId }.chunked(BATCH).forEach { dao.deleteFiles(it) }
    }

    /** File rows are derived data. Titles, matches and progress stay, so including the folder again restores all of it. */
    private suspend fun dropExcluded(exclusions: Exclusions, added: List<RootFolder>) {
        if (exclusions.isEmpty) return
        val dao = db.scan()
        dao.allFiles().filter { exclusions.drops(it.volumeUuid, it.relPath, added) }
            .map { it.rowId }.chunked(BATCH).forEach { dao.deleteFiles(it) }
    }

    /** For a change to the exclusions made outside a scan. Call it inside [exclusive]. */
    suspend fun applyExclusions() {
        val dao = db.scan()
        dropExcluded(Exclusions(dao.excluded()), dao.roots())
    }

    /** Runs [block] with no scan in progress, for changes that must not race a walk. */
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }

    /** WorkInfo.getStopReason per section 4.7. Scans cut off by the stop carry the reason too. */
    suspend fun noteStop(worker: String, stopReason: Int) = withContext(NonCancellable) {
        val dao = db.scan()
        dao.stampOpenLogs("work:$stopReason")
        dao.logEvent(clock.now(), worker, "work:$stopReason")
    }

    private suspend fun scanRoot(
        root: RootFolder,
        volume: MountedVolume,
        allRoots: List<RootFolder>,
        opts: AppSettings,
        seeds: List<SeedFolder>,
        exclusions: Exclusions,
    ) {
        val dao = db.scan()
        val key = "${root.volumeUuid}:${root.relPath}"
        val now = clock.now()
        val open = dao.openLog(key)
        val resume = open != null && now - open.startedAt < RESUME_WINDOW_MS
        if (open != null && !resume) dao.finishLog(open.id, now, open.stopReason ?: "abandoned", open.counts.orEmpty())
        val log = if (resume) open else ScanLog(startedAt = now, root = key).let { it.copy(id = dao.insertLog(it)) }

        val cache = HashMap<String, CachedFile>()
        dao.cacheRows(volume.uuid, PathRange.low(root.relPath), PathRange.high(root.relPath))
            .associateByTo(cache) { it.relPath }
        val nested = allRoots
            .filter { it.id != root.id && it.volumeUuid == root.volumeUuid && it.relPath.startsWith(PathRange.low(root.relPath)) }
            .mapTo(HashSet()) { it.relPath + "/" }

        val dir = if (root.relPath.isEmpty()) File(volume.path) else File(volume.path, root.relPath)
        val run = RootRun(root, volume, opts, log, cache, nested, exclusions)
        when {
            !dir.exists() -> run.finish("root-missing", judgeMissing = true)
            dir.list() == null -> run.finish("root-unreadable", judgeMissing = false)
            else -> {
                if (root.relPath.isEmpty()) run.seed(seeds)
                run.visit(dir, "", 0)
                run.finish(null, judgeMissing = true)
            }
        }
    }

    /** One directory's names, sorted by what they are. */
    private class Listing(val videos: List<String>, val subtitles: List<SidecarFile>, val others: List<String>)

    private inner class RootRun(
        private val root: RootFolder,
        private val volume: MountedVolume,
        private val opts: AppSettings,
        private val log: ScanLog,
        private val cache: HashMap<String, CachedFile>,
        private val nestedRoots: Set<String>,
        private val exclusions: Exclusions,
    ) {
        private val dao = db.scan()
        private val paths = volumes.pathMap()
        private val start = log.checkpoint
        private val reach = AutoReach(volume.path, opts)
        private val seen = HashSet<String>()
        private val seeded = HashSet<String>()
        private val unreadable = ArrayList<String>()
        private val restored = ArrayList<Long>()
        private val sidecarOn = ArrayList<Long>()
        private val sidecarOff = ArrayList<Long>()
        private var added = 0
        private var moved = 0
        private var changed = 0
        private var unchanged = 0
        private var errors = 0
        private var lastCheckpointAt = 0L

        private fun counts() = "added=$added moved=$moved changed=$changed unchanged=$unchanged errors=$errors"

        private fun show(relDir: String) {
            val folder = ParsePaths.join(root.relPath, relDir).ifEmpty { volume.label ?: volume.path }
            progress.inFolder(folder, clock.now())
        }

        private fun listing(names: Array<String>): Listing {
            val videos = ArrayList<String>()
            val subtitles = ArrayList<SidecarFile>()
            val others = ArrayList<String>()
            for (name in names) {
                if (ScanRules.isStaged(name) || (name.startsWith('.') && !opts.scanHiddenFolders)) continue
                when (ParsePaths.extension(name)) {
                    in Extensions.VIDEO -> videos += name
                    in SidecarMatcher.EXTENSIONS -> subtitles += SidecarFile("", name)
                    else -> others += name
                }
            }
            return Listing(videos, subtitles, others)
        }

        private fun inNestedRoot(relDir: String): Boolean = nestedRoots.any { "$relDir/".startsWith(it) }

        /** [rel] is relative to the volume. */
        private fun excluded(rel: String): Boolean = exclusions.blocks(volume.uuid, root.relPath, rel)

        /** The fast first pass. It only reads folders, so the walk that follows skips what this one did. */
        suspend fun seed(folders: List<SeedFolder>) {
            for (folder in folders) {
                currentCoroutineContext().ensureActive()
                val relDir = folder.relDir
                if (!reach.reaches(relDir) || inNestedRoot(relDir) || excluded(relDir)) continue
                val dir = if (relDir.isEmpty()) File(volume.path) else File(volume.path, relDir)
                val names = dir.list() ?: continue
                show(relDir)
                processDirectory(dir, relDir, listing(names), folder.durations)
                seeded += relDir
            }
        }

        suspend fun visit(dir: File, relDir: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            val names = dir.list()
            if (names == null) {
                unreadable += ParsePaths.join(root.relPath, relDir)
                return
            }
            val found = listing(names)
            show(relDir)
            if (!ScanOrder.isDone(relDir, start)) {
                if (relDir !in seeded) processDirectory(dir, relDir, found, emptyMap())
                checkpoint(relDir)
            }
            if (depth >= MAX_DEPTH) return
            val folders = found.others.filter { !ScanRules.isPlainFile(it) && File(dir, it).isDirectory }
            for (name in folders.sorted()) {
                val relSub = ParsePaths.join(relDir, name)
                if (ScanOrder.canPrune(relSub, start)) continue
                val sub = File(dir, name)
                if (!admits(sub, ParsePaths.join(root.relPath, relSub), name)) continue
                visit(sub, relSub, depth + 1)
            }
        }

        /** The added folder itself is always scanned. These rules apply only below it. */
        private fun admits(folder: File, rel: String, name: String): Boolean {
            if (!ScanRules.walks(rel, name, opts.scanHiddenFolders) || excluded(rel)) return false
            // A folder added by hand has a run of its own, which may enter what these rules skip.
            if ("$rel/" in nestedRoots) return false
            if (!opts.scanNomediaFolders && File(folder, ".nomedia").exists()) return false
            return !Files.isSymbolicLink(folder.toPath())
        }

        private suspend fun checkpoint(relDir: String) {
            val now = clock.now()
            if (now - lastCheckpointAt < CHECKPOINT_EVERY_MS) return
            lastCheckpointAt = now
            dao.checkpoint(log.id, relDir, counts())
        }

        private suspend fun processDirectory(dir: File, relDir: String, found: Listing, durations: Map<String, Long>) {
            val folderRel = ParsePaths.join(root.relPath, relDir)
            val sameFolder = found.subtitles
            val subFolders = found.others.filter { it.lowercase() in SidecarMatcher.SUB_FOLDERS }.flatMap { folder ->
                File(dir, folder).list().orEmpty().filter(SidecarMatcher::isSubtitle).map { SidecarFile(folder, it) }
            }
            val hasSubtitles = sameFolder.isNotEmpty() || subFolders.isNotEmpty()
            val tags = HashMap<String, EpisodeTag?>()
            val tagOf: (String) -> EpisodeTag? = { name ->
                if (name in tags) tags[name] else parse(ParsePaths.join(folderRel, name)).episodeTag().also { tags[name] = it }
            }
            var known = 0
            for (name in found.videos.sorted()) {
                currentCoroutineContext().ensureActive()
                val file = File(dir, name)
                if (!file.isFile) continue
                val size = file.length()
                if (size <= 0) continue
                val mtime = file.lastModified()
                val rel = ParsePaths.join(folderRel, name)
                seen += rel
                val hasSidecar = hasSubtitles && SidecarMatcher.match(name, sameFolder, subFolders, tagOf).isNotEmpty()
                val cached = cache[rel]
                if (cached != null) known++
                when {
                    cached == null -> addNew(file, rel, size, mtime, hasSidecar, durations[name] ?: 0)
                    cached.size == size && cached.mtime == mtime -> noteUnchanged(cached, hasSidecar)
                    else -> updateChanged(cached, file, rel, size, mtime, hasSidecar)
                }
            }
            if (known > 0) progress.found(known)
            flushBatches()
        }

        private fun noteUnchanged(cached: CachedFile, hasSidecar: Boolean) {
            unchanged++
            if (!cached.available) restored += cached.rowId
            if (cached.hasSidecarSub != hasSidecar) (if (hasSidecar) sidecarOn else sidecarOff) += cached.rowId
        }

        private suspend fun flushBatches() {
            val now = clock.now()
            restored.chunked(BATCH).forEach { dao.markAvailable(it, now) }
            sidecarOn.chunked(BATCH).forEach { dao.setSidecar(it, true) }
            sidecarOff.chunked(BATCH).forEach { dao.setSidecar(it, false) }
            restored.clear()
            sidecarOn.clear()
            sidecarOff.clear()
        }

        private fun identity(file: File) = try {
            FileIdentity.compute(file)
        } catch (_: IOException) {
            errors++
            null
        }

        private fun stillOnDisk(row: MediaFile): Boolean {
            val base = paths[row.volumeUuid] ?: return false
            return File(base, row.relPath).exists()
        }

        private suspend fun addNew(file: File, rel: String, size: Long, mtime: Long, hasSidecar: Boolean, durationMs: Long) {
            val id = identity(file) ?: return
            val now = clock.now()
            val twins = dao.byFileId(id.fileId)
            val gone = twins.firstOrNull { !stillOnDisk(it) }
            if (gone != null) {
                // A rename, a move, or a drive that came back under a new identity. The row keeps everything.
                dao.updatePath(gone.rowId, volume.uuid, rel, size, mtime, now, hasSidecar)
                if (gone.volumeUuid == volume.uuid) cache.remove(gone.relPath)
                moved++
                progress.found()
                return
            }
            val twin = twins.firstOrNull { it.titleId != null }
            val parsed = parse(rel)
            val row = MediaFile(
                fileId = id.fileId,
                volumeUuid = volume.uuid,
                relPath = rel,
                size = size,
                mtime = mtime,
                durationMs = durationMs,
                osHash = id.osHash,
                firstSeenAt = ScanRules.firstSeen(mtime, now),
                lastSeenAt = now,
                titleId = null,
                episodeId = null,
                releaseTags = parsed.releaseTags.joinToString(",").ifEmpty { null },
                hasSidecarSub = hasSidecar,
                playableKey = "",
                parsedSeason = parsed.season,
                parsedEpisode = parsed.episode,
                parsedEpisodeEnd = parsed.episodeEnd,
                parsedAbsolute = parsed.absolute,
                nameKind = if (parsed.clipLike) NameKinds.CLIP else parsed.kind.name,
                enrichState = EnrichState.PENDING,
            )
            db.withTransaction {
                if (twin != null) {
                    // A second copy of known content joins the title of the first.
                    dao.insertFile(
                        row.copy(
                            titleId = twin.titleId,
                            episodeId = twin.episodeId,
                            playableKey = twin.playableKey,
                            parsedSeason = twin.parsedSeason,
                            parsedEpisode = twin.parsedEpisode,
                            parsedEpisodeEnd = twin.parsedEpisodeEnd,
                            parsedAbsolute = twin.parsedAbsolute,
                            nameKind = twin.nameKind,
                            framePath = twin.framePath,
                        ),
                    )
                } else {
                    val title = resolver.resolve(parsed, ParsePaths.baseName(rel), id.fileId)
                    insertResolved(row, title)
                }
            }
            added++
            progress.found()
            onNewRows()
        }

        private suspend fun insertResolved(row: MediaFile, title: Title) {
            val episodeId = resolver.episodeId(title.id, title.kind, row.nameKind, row.parsedSeason, row.parsedEpisode, row.parsedAbsolute)
            val key = PlayableKeys.of(title.id, title.kind, row.nameKind, row.parsedSeason, row.parsedEpisode, row.parsedAbsolute, row.fileId)
            dao.insertFile(row.copy(titleId = title.id, episodeId = episodeId, playableKey = key))
            resolver.queueEpisodes(title)
        }

        private suspend fun updateChanged(cached: CachedFile, file: File, rel: String, size: Long, mtime: Long, hasSidecar: Boolean) {
            val id = identity(file) ?: return
            val now = clock.now()
            changed++
            if (id.fileId == cached.fileId) {
                dao.updatePath(cached.rowId, volume.uuid, rel, size, mtime, now, hasSidecar)
                return
            }
            val oldKey = cached.playableKey
            val newKey = if (oldKey == PlayableKeys.file(cached.fileId)) PlayableKeys.file(id.fileId) else oldKey
            db.withTransaction {
                dao.updateContent(cached.rowId, id.fileId, id.osHash, size, mtime, now, hasSidecar, newKey)
                val watch = db.watch()
                if (newKey != oldKey) {
                    watch.get(oldKey)?.let {
                        watch.delete(oldKey)
                        watch.upsert(it.copy(playableKey = newKey))
                    }
                }
                watch.filePrefs(cached.fileId)?.let {
                    watch.deleteFilePrefs(cached.fileId)
                    watch.upsertFilePrefs(it.copy(fileId = id.fileId))
                }
            }
            onNewRows()
        }

        private fun parse(relPath: String): ParsedName {
            val path = ParsePaths.forParser(root.relPath, relPath)
            return runCatching { NameParser.parse(path) }.getOrElse {
                ParsedName(
                    title = ParsePaths.baseName(relPath), year = null, kind = NameKind.MOVIE, season = null, episode = null,
                    episodeEnd = null, absolute = null, revision = null, animeHint = false, subtitled = false, dubbed = false,
                    releaseTags = emptyList(),
                )
            }
        }

        private fun ParsedName.episodeTag(): EpisodeTag? = (episode ?: absolute)?.let { EpisodeTag(season, it) }

        private fun protectedFromMissing(rel: String): Boolean =
            nestedRoots.any { rel.startsWith(it) } || unreadable.any { rel.startsWith("$it/") }

        private fun verifiedByEarlierRun(cached: CachedFile): Boolean {
            if (start == null) return false
            val inside = if (root.relPath.isEmpty()) cached.relPath else cached.relPath.removePrefix(root.relPath + "/")
            return ScanOrder.isDone(ParsePaths.folderOf(inside), start) && File(volume.path, cached.relPath).exists()
        }

        /** MediaStore can lag behind or fail, and the walk does not go there, so only the disk can call these gone. */
        private fun heldOutsideTheWalk(cached: CachedFile): Boolean =
            !opts.scanHiddenFolders && ScanRules.isAppMedia(cached.relPath) && File(volume.path, cached.relPath).exists()

        suspend fun finish(stopReason: String?, judgeMissing: Boolean) {
            flushBatches()
            if (judgeMissing) {
                val gone = cache.values.filter { cached ->
                    cached.available && cached.relPath !in seen && !protectedFromMissing(cached.relPath) &&
                        !verifiedByEarlierRun(cached) && !heldOutsideTheWalk(cached)
                }
                gone.map { it.rowId }.chunked(BATCH).forEach { dao.markUnavailable(it) }
            }
            dao.finishLog(log.id, clock.now(), stopReason ?: log.stopReason, counts())
        }
    }

    private companion object {
        const val MAX_DEPTH = 24
        const val BATCH = 500
        const val CHECKPOINT_EVERY_MS = 500L
        val RESUME_WINDOW_MS = TimeUnit.HOURS.toMillis(1)
        val LOG_KEEP_MS = TimeUnit.DAYS.toMillis(30)
    }
}
