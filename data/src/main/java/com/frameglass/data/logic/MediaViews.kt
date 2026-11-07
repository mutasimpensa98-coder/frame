package com.frameglass.data.logic

import com.frameglass.data.api.DeletePlan
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.MediaGroup
import com.frameglass.data.api.StorageSummary
import com.frameglass.data.db.MediaFile

/**
 * The Media Manager, built from the same snapshot as the library. Excluded folders have no file rows and private
 * files live in their own table, so neither can reach a total. Only files on storage now count. Hidden titles
 * still hold space, so they count.
 */
internal object MediaViews {
    const val LARGEST = 200
    private const val FOLDER_PREFIX = "folder|"

    private val BIGGEST = compareByDescending<MediaEntry> { it.sizeBytes }
        .thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) }

    fun summary(s: Snapshot, deviceTotal: Long, deviceFree: Long): StorageSummary = StorageSummary(
        videoBytes = s.liveFiles.sumOf { it.size },
        videoCount = s.liveFiles.size,
        folderCount = s.liveFiles.mapTo(HashSet()) { it.volumeUuid to ParsePaths.folderOf(it.relPath) }.size,
        deviceTotalBytes = deviceTotal,
        deviceFreeBytes = deviceFree,
    )

    fun entries(s: Snapshot, group: MediaGroup, clipsName: String): List<MediaEntry> = when (group) {
        MediaGroup.FOLDERS -> folders(s)
        MediaGroup.LARGEST_VIDEOS -> s.liveFiles.map { fileEntry(s, it) }.sortedWith(BIGGEST).take(LARGEST)
        MediaGroup.TITLES -> titles(s)
        MediaGroup.CLIPS -> listOfNotNull(clipGroup(s, clipsName))
    }

    /** Contents of one folder, title or clip group, biggest first. */
    fun children(s: Snapshot, entry: MediaEntry): List<MediaEntry> =
        if (entry.kind == MediaEntryKind.FILE) emptyList() else filesOf(s, entry).map { fileEntry(s, it) }.sortedWith(BIGGEST)

    /** Each file once, however many of the selected entries hold it. */
    fun expand(s: Snapshot, entries: List<MediaEntry>): List<MediaFile> =
        entries.flatMap { filesOf(s, it) }.distinctBy { it.rowId }

    private fun folderPath(s: Snapshot, f: MediaFile): String =
        folderPath(s, f.volumeUuid, ParsePaths.folderOf(f.relPath))

    private fun folderPath(s: Snapshot, volume: String, rel: String): String {
        val base = s.volumesById[volume]?.lastPath ?: return rel
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    private fun cover(s: Snapshot, f: MediaFile): String? =
        f.framePath ?: f.titleId?.let { (s.infos[it] ?: s.hiddenInfos[it])?.cover }

    private fun fileEntry(s: Snapshot, f: MediaFile): MediaEntry {
        val folder = folderPath(s, f)
        return MediaEntry(
            id = "file|${f.rowId}",
            kind = MediaEntryKind.FILE,
            name = f.relPath.substringAfterLast('/'),
            detail = folder,
            sizeBytes = f.size,
            fileCount = 1,
            durationMs = f.durationMs.takeIf { it > 0 },
            width = f.width.takeIf { it > 0 },
            height = f.height.takeIf { it > 0 },
            coverPath = cover(s, f),
            titleId = f.titleId,
            folderPath = folder,
            fileRowId = f.rowId,
        )
    }

    private fun totalDuration(files: List<MediaFile>): Long? = files.sumOf { it.durationMs }.takeIf { it > 0 }

    private fun folders(s: Snapshot): List<MediaEntry> =
        s.liveFiles.groupBy { it.volumeUuid to ParsePaths.folderOf(it.relPath) }.map { (key, files) ->
            val (volume, rel) = key
            val path = folderPath(s, volume, rel)
            MediaEntry(
                id = "$FOLDER_PREFIX$volume|$rel",
                kind = MediaEntryKind.FOLDER,
                name = rel.substringAfterLast('/').ifEmpty { s.volumesById[volume]?.label ?: path },
                detail = path,
                sizeBytes = files.sumOf { it.size },
                fileCount = files.size,
                durationMs = totalDuration(files),
                width = null,
                height = null,
                coverPath = files.maxBy { it.size }.let { cover(s, it) },
                titleId = null,
                folderPath = path,
                fileRowId = null,
            )
        }.sortedWith(BIGGEST)

    private fun allInfos(s: Snapshot): List<TitleInfo> = s.infos.values + s.hiddenInfos.values

    private fun titles(s: Snapshot): List<MediaEntry> = allInfos(s).filter { !it.isClip }.mapNotNull { info ->
        val files = s.filesByTitle[info.id].orEmpty().filter { it.available }
        if (files.isEmpty()) return@mapNotNull null
        val folder = files.map { folderPath(s, it) }.distinct().singleOrNull()
        MediaEntry(
            id = "title|${info.id}",
            kind = MediaEntryKind.TITLE,
            name = info.title.name,
            detail = folder,
            sizeBytes = files.sumOf { it.size },
            fileCount = files.size,
            durationMs = totalDuration(files),
            width = null,
            height = null,
            coverPath = info.cover,
            titleId = info.id,
            folderPath = folder,
            fileRowId = null,
        )
    }.sortedWith(BIGGEST)

    private fun clipFiles(s: Snapshot): List<MediaFile> =
        allInfos(s).filter { it.isClip }.flatMap { s.filesByTitle[it.id].orEmpty() }.filter { it.available }

    private fun clipGroup(s: Snapshot, name: String): MediaEntry? {
        val files = clipFiles(s)
        if (files.isEmpty()) return null
        return MediaEntry(
            id = "clips",
            kind = MediaEntryKind.CLIP_GROUP,
            name = name,
            detail = null,
            sizeBytes = files.sumOf { it.size },
            fileCount = files.size,
            durationMs = totalDuration(files),
            width = null,
            height = null,
            coverPath = files.maxBy { it.size }.let { cover(s, it) },
            titleId = null,
            folderPath = null,
            fileRowId = null,
        )
    }

    private fun filesOf(s: Snapshot, entry: MediaEntry): List<MediaFile> = when (entry.kind) {
        MediaEntryKind.FILE -> if (DeletePlanner.isSidecar(entry)) emptyList() else listOfNotNull(s.liveByRow[entry.fileRowId])
        MediaEntryKind.FOLDER -> {
            val volume = entry.id.removePrefix(FOLDER_PREFIX).substringBefore('|')
            val rel = entry.id.removePrefix(FOLDER_PREFIX).substringAfter('|', "")
            s.liveFiles.filter { it.volumeUuid == volume && ParsePaths.folderOf(it.relPath) == rel }
        }
        MediaEntryKind.TITLE -> s.filesByTitle[entry.titleId].orEmpty().filter { it.available }
        MediaEntryKind.CLIP_GROUP -> clipFiles(s)
    }
}

internal object TrashIndex {
    /**
     * MediaStore rows by lower-case path, because its path column ignores case. Two files on a case-sensitive drive
     * can share a key, and sending the wrong one to the trash would touch a file outside the plan. Neither gets a
     * row, so both read as having no trash.
     */
    fun <T> byPath(rows: List<Pair<String, T>>): Map<String, T> =
        rows.groupBy({ it.first.lowercase() }, { it.second }).filterValues { it.size == 1 }.mapValues { it.value.single() }
}

/** One thing a delete touches. A video has [rowId], and a sidecar lists the videos that read it in [owners]. */
internal data class DeleteTarget(
    val path: String,
    val name: String,
    val folder: String,
    val sizeBytes: Long,
    val rowId: Long?,
    val owners: List<Long>,
    val recoverable: Boolean,
)

/**
 * A plan is frozen when the owner sees it. Its entries are the exact files, one each, so a video that lands in a
 * folder while the dialog is open cannot be deleted, and whether a file was promised to the trash travels with it.
 */
internal object DeletePlanner {
    const val SAMPLE_NAMES = 5
    private const val PREFIX = "delete"
    private const val VIDEO = "video"
    private const val SIDECAR = "sidecar"
    private const val TRASH = "trash"
    private const val PERMANENT = "gone"

    fun plan(videos: List<DeleteTarget>, sidecars: List<DeleteTarget>, includeSidecars: Boolean): DeletePlan {
        val chosen = videos + if (includeSidecars) sidecars else emptyList()
        return DeletePlan(
            entries = chosen.map(::entry),
            fileCount = videos.size,
            totalBytes = videos.sumOf { it.sizeBytes },
            sidecarCount = sidecars.size,
            sidecarBytes = sidecars.sumOf { it.sizeBytes },
            includeSidecars = includeSidecars,
            recoverableCount = chosen.count { it.recoverable },
            permanentCount = chosen.count { !it.recoverable },
            sampleNames = videos.take(SAMPLE_NAMES).map { it.name },
        )
    }

    private fun entry(t: DeleteTarget): MediaEntry = MediaEntry(
        id = listOf(
            PREFIX,
            if (t.rowId != null) VIDEO else SIDECAR,
            if (t.recoverable) TRASH else PERMANENT,
            t.owners.joinToString(","),
        ).joinToString("|"),
        kind = MediaEntryKind.FILE,
        name = t.name,
        detail = t.folder,
        sizeBytes = t.sizeBytes,
        fileCount = 1,
        durationMs = null,
        width = null,
        height = null,
        coverPath = null,
        titleId = null,
        folderPath = t.folder,
        fileRowId = t.rowId,
    )

    fun isSidecar(entry: MediaEntry): Boolean = entry.id.startsWith("$PREFIX|$SIDECAR|")

    /** Null for anything planDelete did not write, which delete() then leaves alone. */
    fun decode(entry: MediaEntry): DeleteTarget? {
        val parts = entry.id.split('|')
        if (parts.size != 4 || parts[0] != PREFIX || entry.kind != MediaEntryKind.FILE) return null
        val folder = entry.folderPath ?: return null
        val video = parts[1] == VIDEO
        if (video && entry.fileRowId == null) return null
        val owners = parts[3].split(',').mapNotNull { it.toLongOrNull() }
        return DeleteTarget(
            path = "$folder/${entry.name}",
            name = entry.name,
            folder = folder,
            sizeBytes = entry.sizeBytes,
            rowId = if (video) entry.fileRowId else null,
            owners = owners,
            recoverable = parts[2] == TRASH,
        )
    }
}
