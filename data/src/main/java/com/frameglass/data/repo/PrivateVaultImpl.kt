package com.frameglass.data.repo

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.PrivateVault
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.TrackPrefs
import com.frameglass.data.api.VaultClosedException
import com.frameglass.data.api.VaultProgress
import com.frameglass.data.api.VaultState
import com.frameglass.data.db.EnrichState
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.LookupJob
import com.frameglass.data.db.LookupQueue
import com.frameglass.data.db.LookupState
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.Title
import com.frameglass.data.db.VaultItem
import com.frameglass.data.db.VaultRowState
import com.frameglass.data.db.VaultSidecar
import com.frameglass.data.db.WatchState
import com.frameglass.data.logic.CleanName
import com.frameglass.data.logic.Clock
import com.frameglass.data.logic.Labels
import com.frameglass.data.logic.MoveInRecovery
import com.frameglass.data.logic.NaturalOrder
import com.frameglass.data.logic.NextUp
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.Playable
import com.frameglass.data.logic.PlayableKeys
import com.frameglass.data.logic.ResumeLogic
import com.frameglass.data.logic.SegmentRules
import com.frameglass.data.logic.SidecarClaim
import com.frameglass.data.logic.SidecarMatcher
import com.frameglass.data.logic.VaultCards
import com.frameglass.data.logic.VaultNames
import com.frameglass.data.logic.VaultRecovery
import com.frameglass.data.logic.VaultRefs
import com.frameglass.data.logic.WatchRules
import com.frameglass.data.meta.TitleResolver
import com.frameglass.data.scan.DeviceStorage
import com.frameglass.data.scan.MountedVolume
import com.frameglass.data.scan.Scanner
import com.frameglass.data.scan.SidecarDisk
import com.frameglass.data.scan.Volumes
import com.frameglass.data.secure.PlacedCopies
import com.frameglass.data.secure.SafeFiles
import com.frameglass.data.secure.VaultAuth
import com.frameglass.data.secure.VaultFiles
import com.frameglass.parser.NameKind
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What playback needs from the private folder. Every other call here refuses a locked folder or keeps to its own rows. */
internal interface VaultPlayback {
    fun owns(path: String): Boolean
    suspend fun open(ref: PlayRef, prefs: suspend (String) -> TrackPrefs): PlaybackItem?
    suspend fun savePosition(item: PlaybackItem, positionMs: Long, durationMs: Long)
    suspend fun markFinished(item: PlaybackItem)
    suspend fun setWatched(playableKey: String, watched: Boolean)
    suspend fun fileKey(item: PlaybackItem): String?
}

/**
 * The private folder of the 2026-11-07 waiver. A move takes the file's whole row out of MediaFile, so the library,
 * search, Media Manager and metadata never see it, and its progress goes with it. The Title row stays, so moving
 * out brings the match and cover back. Titles left with no public file are filtered from backups by id.
 *
 * A move is safe to kill at any point. The stored copy is verified before the row moves, and the row records what is
 * unfinished, so [recover] completes it on the next scan. File names stay out of the log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PrivateVaultImpl(
    private val context: Context,
    private val db: FrameDb,
    private val auth: VaultAuth,
    private val volumes: Volumes,
    private val scanner: Scanner,
    private val rescan: () -> Unit,
    private val resolver: TitleResolver,
    private val labels: Labels,
    private val sidecarLabels: SidecarLabels,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
) : PrivateVault, VaultPlayback {
    private val dao get() = db.vault()
    private val scan get() = db.scan()
    private val titles get() = db.titles()
    private val folder: File get() = File(context.filesDir, FOLDER).apply { mkdirs() }

    private val unlocked = MutableStateFlow(false)
    private val moving = MutableStateFlow<VaultProgress?>(null)
    private val moves = Mutex()

    @Volatile
    private var recovered = false
    private var autoLock: Job? = null

    /** Counts stay hidden while locked, so a closed folder does not say what it holds. */
    override val state: Flow<VaultState> = combine(auth.configured, auth.biometric, unlocked, dao.totals()) { configured, biometric, open, totals ->
        VaultState(configured, open, biometric, if (open) totals.count else 0, if (open) totals.bytes else 0)
    }.distinctUntilChanged()

    override val progress: StateFlow<VaultProgress?> get() = moving

    override val items: Flow<List<TitleCard>> = unlocked
        .flatMapLatest { open -> if (open) dao.storedFlow().map(::cards) else flowOf(emptyList()) }
        .flowOn(Dispatchers.IO)

    private suspend fun cards(rows: List<VaultItem>): List<TitleCard> =
        rows.groupBy { it.groupId }.map { (group, members) ->
            val title = members.first().file.titleId?.let { titles.title(it) }
            VaultCards.card(group, title, members) { File(folder, it.storedName).isFile }
        }.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }

    override suspend fun setup(password: String): String {
        val code = auth.setup(password)
        unlocked.value = true
        return code
    }

    override suspend fun unlockWithPassword(password: String): Boolean {
        val matched = auth.checkPassword(password)
        if (matched) unlocked.value = true
        return matched
    }

    /** A late call after the owner turned fingerprint off must not open anything. */
    override fun unlockAfterBiometric() {
        scope.launch { if (auth.isConfigured() && auth.biometricEnabled()) unlocked.value = true }
    }

    override suspend fun resetWithRecoveryCode(code: String, newPassword: String): Boolean {
        if (newPassword.isBlank() || !auth.checkRecovery(code)) return false
        auth.setPassword(newPassword)
        unlocked.value = true
        return true
    }

    override suspend fun changePassword(old: String, new: String): Boolean {
        if (new.isBlank() || !auth.checkPassword(old)) return false
        auth.setPassword(new)
        return true
    }

    /** Turning fingerprint on takes an open folder, or anyone holding the phone could enrol their way in. */
    override suspend fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !unlocked.value) return
        auth.setBiometric(enabled)
    }

    override fun lock() {
        autoLock?.cancel()
        autoLock = null
        unlocked.value = false
    }

    /** The app left the screen. A private video still in picture-in-picture keeps an activity started. */
    fun onAppHidden() {
        if (!unlocked.value) return
        autoLock?.cancel()
        autoLock = scope.launch {
            delay(settings.current().vaultLockSeconds * 1_000L)
            lock()
        }
    }

    fun onAppVisible() {
        autoLock?.cancel()
        autoLock = null
    }

    /** Resolved first, so a path with .. in it cannot walk in. A path that will not resolve counts as ours and stays shut. */
    override fun owns(path: String): Boolean =
        runCatching { File(path).canonicalPath.startsWith(folder.canonicalPath + File.separator) }.getOrDefault(true)

    /** Filling the folder reveals nothing, so a locked folder still takes videos. It only has to exist. */
    override suspend fun moveIn(titleIds: List<Long>, fileRowIds: List<Long>): Result<Int> {
        if (!auth.isConfigured()) return Result.failure(VaultClosedException())
        return detached {
            val files = (titleIds.flatMap { scan.filesOfTitle(it) } + fileRowIds.mapNotNull { scan.file(it) })
                .distinctBy { it.rowId }.filter { it.available }
            runSteps(files.map { file -> Step(file.relPath.substringAfterLast('/'), file.size) { onBytes -> moveOneIn(file, onBytes) } })
        }
    }

    override suspend fun moveOut(titleId: Long): Result<Int> {
        if (!unlocked.value) return Result.failure(VaultClosedException())
        return detached {
            val rows = dao.stored().filter { it.groupId == titleId }
            val result = runSteps(rows.map { row -> Step(row.file.relPath.substringAfterLast('/'), row.file.size) { onBytes -> moveOneOut(row, onBytes) } })
            if (result.getOrDefault(0) > 0) rescan()
            result
        }
    }

    /** A move outlives the screen that started it, which only watches [progress]. */
    private suspend fun detached(block: suspend () -> Result<Int>): Result<Int> =
        scope.async(Dispatchers.IO) { moves.withLock { runCatching { block() }.getOrElse { Result.failure(it) } } }.await()

    private class Step(val name: String, val size: Long, val run: suspend ((Int) -> Unit) -> Unit)

    /** One bad file does not stop the rest. Success carries how many moved, and failure means none did. */
    private suspend fun runSteps(steps: List<Step>): Result<Int> {
        val total = steps.sumOf { it.size }
        var done = 0L
        var moved = 0
        var failure: Exception? = null
        var shownAt = 0L
        try {
            steps.forEachIndexed { index, step ->
                val left = steps.size - index
                var current = 0L
                moving.value = VaultProgress(step.name, done, total, left)
                try {
                    step.run { bytes ->
                        current += bytes
                        val now = System.nanoTime()
                        if (now - shownAt >= SHOW_EVERY_NS) {
                            shownAt = now
                            moving.value = VaultProgress(step.name, done + current, total, left)
                        }
                    }
                    moved++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "vault move failed: ${e.javaClass.simpleName}")
                    failure = e
                }
                done += step.size
            }
        } finally {
            moving.value = null
        }
        val error = failure
        return if (moved == 0 && error != null) Result.failure(error) else Result.success(moved)
    }

    private fun ensureRoom(target: File, bytes: Long) {
        if (DeviceStorage.freeBytes(target) < bytes + RESERVE_BYTES) throw IOException("not enough space")
    }

    private class Placed(val storedName: String, val sha256: String, val sizeBytes: Long, val sourceTime: Long)

    private suspend fun place(source: File, onBytes: (Int) -> Unit, copies: PlacedCopies): Placed {
        val name = VaultFiles.randomName(VaultFiles.extensionOf(source.name))
        val part = File(folder, name + PART)
        val sourceTime = source.lastModified()
        val hash = VaultFiles.copyVerified(source, part, onBytes = onBytes)
        val target = File(folder, name)
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("could not finish the copy")
        }
        copies.add(target)
        return Placed(name, hash, target.length(), sourceTime)
    }

    /** A file that changed after its copy was verified is not what the copy holds, so it stays where it is. */
    private fun unchanged(original: File, copy: Placed): Boolean =
        original.length() == copy.sizeBytes && original.lastModified() == copy.sourceTime

    private suspend fun moveOneIn(file: MediaFile, onBytes: (Int) -> Unit) {
        val base = volumes.pathMap()[file.volumeUuid] ?: throw IOException("volume is gone")
        val source = File(base, file.relPath)
        if (!SafeFiles.isPlain(source, File(base))) throw IOException("not a plain file")
        ensureRoom(folder, source.length())
        val videoFolder = source.parentFile ?: throw IOException("no folder")
        val claims = SidecarDisk.claims(videoFolder, listOf(source.name))
            .filter { SafeFiles.isPlain(File(it.hit.file.folder, it.hit.file.name), File(base)) }
        val copies = PlacedCopies()
        try {
            val video = place(source, onBytes, copies)
            val sidecars = claims.map { claim -> claim to place(File(claim.hit.file.folder, claim.hit.file.name), {}, copies) }
            scanner.exclusive { withContext(NonCancellable) { commitIn(file, source, videoFolder, video, sidecars, copies) } }
        } catch (e: Exception) {
            copies.discard()
            throw e
        }
    }

    private suspend fun commitIn(
        before: MediaFile,
        source: File,
        videoFolder: File,
        video: Placed,
        sidecars: List<Pair<SidecarClaim, Placed>>,
        copies: PlacedCopies,
    ) {
        VaultFiles.syncFolder(folder)
        val watch = db.watch()
        val saved = ArrayList<WatchState>()
        val itemId = db.withTransaction {
            val file = scan.file(before.rowId)
            if (file == null || file.relPath != before.relPath || file.volumeUuid != before.volumeUuid) throw IOException("the file changed")
            val state = watch.get(file.playableKey)
            val id = dao.insert(
                VaultItem(
                    storedName = video.storedName,
                    sha256 = video.sha256,
                    state = VaultRowState.MOVING_IN,
                    movedAt = clock.now(),
                    watchPositionMs = state?.positionMs ?: 0,
                    watchDurationMs = state?.durationMs ?: 0,
                    watched = state?.watched == true,
                    lastPlayedAt = state?.lastPlayedAt ?: 0,
                    file = file,
                ),
            )
            dao.insertSidecars(
                sidecars.map { (claim, stored) ->
                    VaultSidecar(
                        itemId = id,
                        storedName = stored.storedName,
                        originalRel = File(claim.hit.file.folder, claim.hit.file.name).relativeTo(videoFolder).path.replace(File.separatorChar, '/'),
                        sizeBytes = stored.sizeBytes,
                        sha256 = stored.sha256,
                        language = claim.hit.language,
                        forced = claim.hit.forced,
                        signs = claim.hit.signs,
                        keptOriginal = claim.shared,
                    )
                },
            )
            scan.deleteFiles(listOf(file.rowId))
            saved += hideWatch(file)
            val titleId = file.titleId
            if (titleId != null && scan.filesOfTitle(titleId).isEmpty()) db.meta().deleteQueue(titleId)
            id
        }
        copies.handToRow()
        if (source.exists() && !(unchanged(source, video) && SafeFiles.remove(source))) {
            rollback(itemId, before, saved)
            copies.takeBack()
            throw IOException("could not remove the original")
        }
        sidecars.filter { (claim, _) -> !claim.shared }.forEach { (claim, stored) ->
            val original = File(claim.hit.file.folder, claim.hit.file.name)
            if (unchanged(original, stored)) SafeFiles.remove(original)
        }
        dao.setState(itemId, VaultRowState.STORED)
    }

    /** Progress leaves with the last file of a playable. Another version staying behind keeps it. */
    private suspend fun hideWatch(file: MediaFile): List<WatchState> {
        val watch = db.watch()
        val session = watch.session()
        if (session != null && (session.watchKey == file.playableKey || session.fileRowId == file.rowId)) watch.clearSession()
        if (scan.byPlayable(file.playableKey).isNotEmpty()) return emptyList()
        val titleId = file.titleId
        val keys = if (titleId == null) {
            listOf(file.playableKey)
        } else {
            PlayableKeys.covered(file.playableKey, titleId, file.nameKind, file.parsedSeason, file.parsedEpisode, file.parsedEpisodeEnd)
        }
        return keys.mapNotNull { key -> watch.get(key)?.also { watch.delete(key) } }
    }

    private suspend fun rollback(itemId: Long, file: MediaFile, states: List<WatchState>) {
        db.withTransaction {
            dao.deleteSidecars(itemId)
            dao.delete(itemId)
            scan.insertFile(file)
            states.forEach { db.watch().upsert(it) }
        }
    }

    private class RestorePlace(val volume: MountedVolume, val folderRel: String) {
        val folder: File get() = if (folderRel.isEmpty()) File(volume.path) else File(volume.path, folderRel)
    }

    /** Back to the original folder, or Movies/Frame Glass if that folder or its drive is gone. */
    private fun restorePlace(item: VaultItem): RestorePlace {
        val mounted = volumes.mounted()
        val volume = mounted.firstOrNull { it.uuid == item.file.volumeUuid }
        val original = ParsePaths.folderOf(item.file.relPath)
        if (volume != null) {
            val place = RestorePlace(volume, original)
            if (place.folder.isDirectory) return place
        }
        val fallback = mounted.firstOrNull { it.uuid == Volumes.PRIMARY } ?: mounted.firstOrNull() ?: throw IOException("no storage")
        return RestorePlace(fallback, FALLBACK_FOLDER)
    }

    /** Never overwrites. The name must be free on disk and in the database, where a gone file can still hold it. */
    private suspend fun freeRel(volume: MountedVolume, folderRel: String, name: String): String {
        var candidate = name
        var n = 1
        while (true) {
            val rel = ParsePaths.join(folderRel, candidate)
            if (!File(volume.path, rel).exists() && scan.byPath(volume.uuid, rel) == null) return rel
            candidate = VaultNames.numbered(name, n++)
        }
    }

    private suspend fun moveOneOut(item: VaultItem, onBytes: (Int) -> Unit) {
        val stored = File(folder, item.storedName)
        if (!stored.isFile) throw IOException("stored file is missing")
        val place = restorePlace(item)
        if (!place.folder.isDirectory && !place.folder.mkdirs()) throw IOException("no folder")
        ensureRoom(place.folder, stored.length())
        val temp = File(place.folder, TEMP_PREFIX + VaultFiles.randomName(TEMP_EXTENSION))
        dao.setTemp(item.id, temp.path)
        try {
            VaultFiles.copyVerified(stored, temp, expected = item.sha256, onBytes = onBytes)
            scanner.exclusive { withContext(NonCancellable) { commitOut(item, place, temp) } }
        } catch (e: Exception) {
            temp.delete()
            withContext(NonCancellable) { dao.setTemp(item.id, null) }
            throw e
        }
    }

    /** The file appears under its real name only here, with no scan running, so a walk cannot add it as new. */
    private suspend fun commitOut(item: VaultItem, place: RestorePlace, temp: File) {
        val originalName = item.file.relPath.substringAfterLast('/')
        val rel = freeRel(place.volume, place.folderRel, originalName)
        val target = File(place.volume.path, rel)
        if (!VaultFiles.moveNoReplace(temp, target)) throw IOException("could not finish the copy")
        VaultFiles.syncFolder(place.folder)
        val sidecars = dao.sidecars(item.id)
        val written = ArrayList<File>()
        try {
            sidecars.forEach { sidecar -> restoreSidecar(sidecar, place.folder, originalName, target.name, written) }
            target.setLastModified(item.file.mtime)
            commitRow(item, place, rel, target.lastModified())
        } catch (e: Exception) {
            // The private copy is still whole, so nothing may stay behind in public.
            written.forEach { it.delete() }
            target.delete()
            throw e
        }
        File(folder, item.storedName).delete()
        sidecars.forEach { File(folder, it.storedName).delete() }
        db.withTransaction {
            dao.deleteSidecars(item.id)
            dao.delete(item.id)
        }
    }

    private suspend fun restoreSidecar(sidecar: VaultSidecar, videoFolder: File, oldVideo: String, newVideo: String, written: MutableList<File>) {
        val stored = File(folder, sidecar.storedName)
        if (!stored.isFile) return
        val oldName = sidecar.originalRel.substringAfterLast('/')
        val name = VaultNames.sidecarFor(oldName, oldVideo, newVideo)
        val inner = ParsePaths.folderOf(sidecar.originalRel)
        val parent = if (inner.isNotEmpty() && File(videoFolder, inner).isDirectory) File(videoFolder, inner) else videoFolder
        var target = File(parent, name)
        // A shared subtitle never left. A different one at this name is the owner's: it stays, and this one takes a number.
        if (target.exists() && (sidecar.keptOriginal || VaultFiles.matches(target, sidecar.sha256))) return
        var n = 1
        while (target.exists()) target = File(parent, VaultNames.numbered(name, n++))
        val part = File(parent, TEMP_PREFIX + VaultFiles.randomName(TEMP_EXTENSION))
        VaultFiles.copyVerified(stored, part, expected = sidecar.sha256)
        if (!VaultFiles.moveNoReplace(part, target)) {
            part.delete()
            throw IOException("could not finish the copy")
        }
        VaultFiles.syncFolder(parent)
        written += target
    }

    private fun parsedName(rel: String): ParsedName = runCatching { NameParser.parse(ParsePaths.forParser("", rel)) }.getOrElse {
        ParsedName(
            title = ParsePaths.baseName(rel), year = null, kind = NameKind.MOVIE, season = null, episode = null, episodeEnd = null,
            absolute = null, revision = null, animeHint = false, subtitled = false, dubbed = false, releaseTags = emptyList(),
        )
    }

    /** A fresh path cannot change what the file was: the numbers are the parser's own from when it was found. */
    private suspend fun commitRow(item: VaultItem, place: RestorePlace, rel: String, mtime: Long) {
        val f = item.file
        val now = clock.now()
        val watch = db.watch()
        db.withTransaction {
            val title = f.titleId?.let { titles.title(it) } ?: resolver.resolve(parsedName(rel), ParsePaths.baseName(rel), f.fileId)
            val key = PlayableKeys.of(title.id, title.kind, f.nameKind, f.parsedSeason, f.parsedEpisode, f.parsedAbsolute, f.fileId)
            val episodeId = resolver.episodeId(title.id, title.kind, f.nameKind, f.parsedSeason, f.parsedEpisode, f.parsedAbsolute)
            scan.insertFile(
                f.copy(
                    rowId = 0,
                    volumeUuid = place.volume.uuid,
                    relPath = rel,
                    mtime = mtime,
                    available = true,
                    lastSeenAt = now,
                    titleId = title.id,
                    episodeId = episodeId,
                    playableKey = key,
                    enrichState = if (f.enrichState == EnrichState.DONE) EnrichState.DONE else EnrichState.PENDING,
                    enrichTries = 0,
                ),
            )
            VaultCards.watchOf(item)?.let { state ->
                watch.upsert(WatchRules.merge(watch.get(key), state.copy(playableKey = key, titleId = title.id)))
            }
            backInLibrary(title, now)
            dao.setState(item.id, VaultRowState.RESTORED)
        }
    }

    private suspend fun backInLibrary(title: Title, now: Long) {
        if (title.removed) titles.setRemoved(title.id, false)
        if (title.tmdbId == null && title.matchState == MatchState.QUEUED) {
            db.meta().upsertQueue(LookupQueue(title.id, LookupState.QUEUED, 0, now, LookupJob.MATCH))
        }
        resolver.queueEpisodes(title)
    }

    /**
     * Finishes what a killed app left. It runs inside a scan, before the walk, so an original that should be gone
     * is not found again. If a move is running this process is alive and there is nothing to finish, so it waits
     * for the next scan rather than wait on a lock the move may need back.
     */
    suspend fun recover() {
        if (recovered || !moves.tryLock()) return
        try {
            val paths = volumes.pathMap()
            for (item in dao.unfinished()) finish(item, paths)
            folder.listFiles().orEmpty().filter { it.name.endsWith(PART) }.forEach { it.delete() }
            recovered = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // This must not stop the search itself. The next scan tries again.
            Log.w(TAG, "vault recovery failed: ${e.javaClass.simpleName}")
        } finally {
            moves.unlock()
        }
    }

    private suspend fun finish(item: VaultItem, paths: Map<String, String>) {
        item.tempPath?.let { path ->
            File(path).delete()
            dao.setTemp(item.id, null)
        }
        when (item.state) {
            VaultRowState.MOVING_IN -> finishMoveIn(item, paths)
            VaultRowState.RESTORED -> {
                val sidecars = dao.sidecars(item.id)
                File(folder, item.storedName).delete()
                sidecars.forEach { File(folder, it.storedName).delete() }
                db.withTransaction {
                    dao.deleteSidecars(item.id)
                    dao.delete(item.id)
                }
            }
        }
    }

    /** With the drive gone this waits for a later run. Each file goes only if both of its sides still hash right. */
    private suspend fun finishMoveIn(item: VaultItem, paths: Map<String, String>) {
        val base = paths[item.file.volumeUuid] ?: return
        val source = File(base, item.file.relPath)
        val copyWhole = VaultFiles.matches(File(folder, item.storedName), item.sha256)
        val originalWhole = SafeFiles.isPlain(source, File(base)) && VaultFiles.matches(source, item.sha256)
        when (VaultRecovery.moveIn(copyWhole, originalWhole)) {
            MoveInRecovery.UNDO -> return undoMoveIn(item)
            MoveInRecovery.REMOVE_ORIGINAL -> if (!SafeFiles.remove(source)) return
            MoveInRecovery.KEEP_BOTH -> Unit
        }
        val videoFolder = source.parentFile
        if (videoFolder != null) {
            dao.sidecars(item.id).filter { !it.keptOriginal }.forEach { sidecar ->
                val file = File(videoFolder, sidecar.originalRel)
                val copied = VaultFiles.matches(File(folder, sidecar.storedName), sidecar.sha256) && VaultFiles.matches(file, sidecar.sha256)
                if (copied && SidecarMatcher.isSubtitle(file.name) && SafeFiles.isPlain(file, File(base))) SafeFiles.remove(file)
            }
        }
        dao.setState(item.id, VaultRowState.STORED)
    }

    /** The private copy is damaged and the original is whole, so the file goes back. Sidecars only go after the original, so theirs are there too. */
    private suspend fun undoMoveIn(item: VaultItem) {
        val sidecars = dao.sidecars(item.id)
        val watch = db.watch()
        db.withTransaction {
            dao.deleteSidecars(item.id)
            dao.delete(item.id)
            if (scan.byPath(item.file.volumeUuid, item.file.relPath) == null) scan.insertFile(item.file)
            VaultCards.watchOf(item)?.let { state ->
                watch.upsert(WatchRules.merge(watch.get(state.playableKey), state.copy(titleId = item.file.titleId)))
            }
        }
        File(folder, item.storedName).delete()
        sidecars.forEach { File(folder, it.storedName).delete() }
    }

    override suspend fun open(ref: PlayRef, prefs: suspend (String) -> TrackPrefs): PlaybackItem? {
        if (!unlocked.value) return null
        val key = ref.playableKey ?: return null
        val rows = dao.stored()
        val wanted = VaultRefs.itemOf(key)?.let { id -> rows.firstOrNull { it.id == id } }
        val group = wanted?.groupId ?: VaultRefs.groupOf(key) ?: return null
        val members = rows.filter { it.groupId == group }
        if (members.isEmpty()) return null
        val titleId = members.first().file.titleId
        val playables = VaultCards.playables(group, members, titleId?.let { titles.episodeRows(it) }.orEmpty())
        val current = wanted?.let { w -> playables.firstOrNull { p -> p.files.any { it.rowId == w.id } } }
            ?: ResumeLogic.pick(playables) ?: playables.first()
        val row = wanted ?: members.first { it.id == current.best.rowId }
        val stored = File(folder, row.storedName)
        if (!stored.isFile) return null
        val title = titleId?.let { titles.title(it) }
        val next = NextUp.after(playables, current.key)
        val season = current.season.takeIf { current.numbered && title?.kind != TitleKind.MOVIE }
        val episode = current.number.takeIf { season != null }
        return PlaybackItem(
            ref = ref,
            playableKey = VaultRefs.item(row.id),
            fileRowId = null,
            titleId = null,
            uri = stored.path,
            title = title?.name ?: CleanName.of(row.file.relPath, row.file.releaseTags),
            subtitle = if (season != null && episode != null) labels.withTitle(labels.episode(season, episode), current.episode?.name) else null,
            season = season,
            episode = episode,
            posterPath = title?.coverPath ?: title?.framePath ?: row.file.framePath,
            resumeMs = WatchRules.resumeMs(VaultCards.watchOf(row), false),
            durationMs = row.file.durationMs.takeIf { it > 0 } ?: row.watchDurationMs,
            sidecars = sidecars(row),
            segments = titleId?.let { SegmentRules.pick(db.meta().segments(it), season, row.file.episodeId) }.orEmpty(),
            prefs = prefs(row.file.fileId),
            next = next?.let { PlayRef(playableKey = VaultRefs.item(it.best.rowId)) },
            nextLabel = next?.let(::nextLabel),
            isPrivate = true,
        )
    }

    private fun nextLabel(p: Playable): String =
        if (p.numbered) labels.withTitle(labels.episode(p.season, p.number), p.episode?.name) else CleanName.of(p.best.relPath, p.best.releaseTags)

    private suspend fun sidecars(row: VaultItem): List<SidecarSubtitle> {
        val own = dao.sidecars(row.id).filter { File(folder, it.storedName).isFile }.map { sidecar ->
            SidecarSubtitle(
                path = File(folder, sidecar.storedName).path,
                label = sidecarLabels.label(sidecar.language, sidecar.forced, sidecar.signs, sidecar.originalRel.substringAfterLast('/')),
                language = sidecar.language,
                mimeType = SidecarMatcher.mimeType(sidecar.storedName),
            )
        }
        return own + sidecarLabels.saved(row.file.fileId)
    }

    /** Playback that started while unlocked may still report after an auto-lock. */
    private suspend fun write(key: String, change: (VaultItem) -> WatchState) {
        val item = VaultRefs.itemOf(key)?.let { dao.item(it) } ?: return
        val next = change(item)
        dao.saveWatch(item.id, next.positionMs, next.durationMs, next.watched, next.lastPlayedAt)
    }

    override suspend fun savePosition(item: PlaybackItem, positionMs: Long, durationMs: Long) = write(item.playableKey) { row ->
        WatchRules.onPosition(VaultCards.watchOf(row), row.file.playableKey, null, positionMs, durationMs, clock.now())
    }

    override suspend fun markFinished(item: PlaybackItem) = write(item.playableKey) { row ->
        WatchRules.onFinished(VaultCards.watchOf(row), row.file.playableKey, null, item.durationMs, clock.now())
    }

    override suspend fun setWatched(playableKey: String, watched: Boolean) = write(playableKey) { row ->
        WatchRules.onManual(VaultCards.watchOf(row), row.file.playableKey, null, watched)
    }

    override suspend fun fileKey(item: PlaybackItem): String? =
        VaultRefs.itemOf(item.playableKey)?.let { dao.item(it)?.file?.fileId }

    private companion object {
        const val TAG = "FrameData"
        const val FOLDER = "vault"
        const val PART = ".part"
        const val TEMP_PREFIX = ".frameglass-"
        const val TEMP_EXTENSION = "tmp"
        const val FALLBACK_FOLDER = "Movies/Frame Glass"
        const val RESERVE_BYTES = 200L * 1024 * 1024
        const val SHOW_EVERY_NS = 100_000_000L
    }
}
