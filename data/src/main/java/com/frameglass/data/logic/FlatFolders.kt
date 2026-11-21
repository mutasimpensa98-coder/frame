package com.frameglass.data.logic

import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.FolderSort
import com.frameglass.data.api.FolderView
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.Volume

/**
 * Folders as one flat list (owner request of 2026-11-22): every folder that holds videos directly, and no folder
 * inside a folder. It reads the same files as the tree of [FolderBuilder], so the rules of the library hold with
 * no rule of its own: an excluded folder has no file rows, a private video lives in another table, and the files
 * of a hidden title are not among [Snapshot.visibleFiles]. A row stands for the videos directly in its folder, so
 * its count and size are theirs alone.
 */
internal object FlatFolders {
    /** The most folders, and the most videos, a search lists. */
    const val MAX_FOUND = 200

    /** How many folders of a parent path the second line of a row shows before it is cut at the start. */
    private const val HINT_SEGMENTS = 2
    private const val CUT = "…/"

    /** One folder that holds videos directly. */
    private class Group(val volume: Volume, val rel: String, val path: String, val files: List<MediaFile>) {
        val name: String = rel.substringAfterLast('/').ifEmpty { volumeName(volume, path) }
        val size: Long = files.sumOf { it.size }

        /** When its newest video was written, which is what "newest" means for a folder of recordings. */
        val newest: Long = files.maxOf { it.mtime }

        /** The folders above this one inside its volume, outermost first. */
        val above: List<String> = if (rel.isEmpty()) emptyList() else rel.split('/').dropLast(1)
    }

    private fun volumeName(volume: Volume, path: String): String = volume.label ?: path.substringAfterLast('/')

    private val BY_NAME: Comparator<Group> =
        Comparator<Group> { a, b -> NaturalOrder.compare(a.name, b.name) }
            .thenComparator { a, b -> NaturalOrder.compare(a.path, b.path) }

    private val FILES_BY_NAME: Comparator<MediaFile> =
        Comparator<MediaFile> { a, b -> NaturalOrder.compare(fileName(a), fileName(b)) }
            .thenComparator { a, b -> NaturalOrder.compare(a.relPath, b.relPath) }
            .thenBy { it.volumeUuid }

    private fun fileName(f: MediaFile): String = f.relPath.substringAfterLast('/')

    private fun order(sort: FolderSort): Comparator<Group> = when (sort) {
        FolderSort.NAME -> BY_NAME
        FolderSort.SIZE -> compareByDescending<Group> { it.size }.then(BY_NAME)
        FolderSort.COUNT -> compareByDescending<Group> { it.files.size }.then(BY_NAME)
        FolderSort.NEWEST -> compareByDescending<Group> { it.newest }.then(BY_NAME)
    }

    /** A volume the database knows but has no path for cannot be opened, so its folders are left out, as in the tree. */
    private fun groups(s: Snapshot, files: List<MediaFile>): List<Group> =
        files.groupBy { it.volumeUuid to ParsePaths.folderOf(it.relPath) }.mapNotNull { (key, inside) ->
            val (uuid, rel) = key
            val volume = s.volumesById[uuid] ?: return@mapNotNull null
            val base = volume.lastPath ?: return@mapNotNull null
            Group(volume, rel, if (rel.isEmpty()) base else "$base/$rel", inside)
        }

    /** The parent path as the second line shows it: the end of it, or the name of the volume for a top folder. */
    private fun shortParent(g: Group): String = when {
        g.rel.isEmpty() -> g.path
        g.above.isEmpty() -> volumeName(g.volume, g.volume.lastPath.orEmpty())
        g.above.size <= HINT_SEGMENTS -> g.above.joinToString("/")
        else -> CUT + g.above.takeLast(HINT_SEGMENTS).joinToString("/")
    }

    private fun fullParent(g: Group): String =
        if (g.rel.isEmpty()) g.path else (listOf(volumeName(g.volume, g.volume.lastPath.orEmpty())) + g.above).joinToString("/")

    /**
     * The second line of every folder whose name another folder shares, whatever the case. The short parent path is
     * enough when it differs within the name; two that still read the same, such as one path on two drives, get the
     * whole path with the name of the volume in front.
     */
    private fun hints(groups: List<Group>): Map<Group, String> {
        val out = HashMap<Group, String>()
        groups.groupBy { it.name.lowercase() }.values.filter { it.size > 1 }.forEach { same ->
            val short = same.associateWith(::shortParent)
            val uses = short.values.groupingBy { it }.eachCount()
            same.forEach { g -> out[g] = short.getValue(g).takeIf { uses[it] == 1 } ?: fullParent(g) }
        }
        return out
    }

    private fun entries(s: Snapshot, groups: List<Group>, hints: Map<Group, String>): List<FolderEntry> =
        groups.map { g -> FolderBuilder.folderEntry(g.path, g.name, g.files, s).copy(direct = true, hint = hints[g]) }

    /** Every folder that holds videos directly, in the order of [sort]. */
    fun build(s: Snapshot, sort: FolderSort = FolderSort.NAME): FolderView {
        val all = groups(s, s.visibleFiles)
        return FolderView(null, null, entries(s, all.sortedWith(order(sort)), hints(all)))
    }

    private fun directFiles(s: Snapshot, volume: Volume, rel: String): List<MediaFile> =
        s.visibleFiles.filter { it.volumeUuid == volume.uuid && ParsePaths.folderOf(it.relPath) == rel }

    private fun fileEntries(s: Snapshot, files: List<MediaFile>): List<FolderEntry> =
        files.sortedWith(FILES_BY_NAME).mapNotNull { f -> s.absolutePath(f)?.let { FolderBuilder.fileEntry(it, f, s) } }

    /** The videos directly in [path], by name. The folders inside it are rows of the flat list, not of this page. */
    fun videos(path: String, s: Snapshot): FolderView {
        val (volume, rel) = FolderBuilder.locate(path, s) ?: return FolderView(path, path.substringAfterLast('/'), emptyList())
        val name = rel.substringAfterLast('/').ifEmpty { volumeName(volume, path) }
        return FolderView(path, name, fileEntries(s, directFiles(s, volume, rel)))
    }

    /**
     * Folders, then videos, whose name holds every one of [words]. [key] turns a name into the form the words are
     * in. [under] keeps the search inside that folder: everything below it with [deep], or only the videos directly
     * in it without. The folder searched is the page itself, so it is never one of its own results.
     */
    fun search(s: Snapshot, words: List<String>, under: String?, deep: Boolean, key: (String) -> String): FolderView {
        val nothing = FolderView(under, null, emptyList())
        if (words.isEmpty()) return nothing
        val scope = if (under == null) {
            s.visibleFiles
        } else {
            val (volume, rel) = FolderBuilder.locate(under, s) ?: return nothing
            when {
                !deep -> directFiles(s, volume, rel)
                rel.isEmpty() -> s.visibleFiles.filter { it.volumeUuid == volume.uuid }
                else -> s.visibleFiles.filter { it.volumeUuid == volume.uuid && it.relPath.startsWith("$rel/") }
            }
        }

        fun found(name: String): Boolean = key(name).let { text -> words.all { it in text } }

        val all = if (under != null && !deep) emptyList() else groups(s, scope)
        val folders = all.filter { it.path != under && found(it.name) }.sortedWith(BY_NAME).take(MAX_FOUND)
        val files = scope.filter { found(fileName(it)) }.sortedWith(FILES_BY_NAME).take(MAX_FOUND)
        return FolderView(under, null, entries(s, folders, hints(all)) + fileEntries(s, files))
    }
}
