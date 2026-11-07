package com.frameglass.data.logic

import com.frameglass.data.db.ExcludedFolder
import com.frameglass.data.db.RootFolder

internal object PathScope {
    /** Shared storage ignores case, and MediaStore and the disk can spell one folder two ways. */
    fun within(path: String, folder: String): Boolean =
        folder.isEmpty() || path.equals(folder, ignoreCase = true) ||
            (path.length > folder.length && path[folder.length] == '/' && path.startsWith(folder, ignoreCase = true))
}

/**
 * Folders the owner excluded. Excluding a folder turns off the added folders inside it, so an added folder that
 * still sits inside an exclusion was added after it, and that later wish wins for that folder only.
 */
internal class Exclusions(rows: List<ExcludedFolder>) {
    private val byVolume: Map<String, List<String>> = rows.groupBy({ it.volumeUuid }, { it.relPath })

    val isEmpty: Boolean get() = byVolume.isEmpty()

    fun wholeVolume(volume: String): Boolean = byVolume[volume].orEmpty().any { it.isEmpty() }

    /** True when a run started at [rootRel] must stay out of [relDir]. Both are relative to the volume. */
    fun blocks(volume: String, rootRel: String, relDir: String): Boolean =
        byVolume[volume].orEmpty().any { PathScope.within(relDir, it) && !PathScope.within(rootRel, it) }

    /** True when no run would reach this file any more, so its row must go. */
    fun drops(volume: String, relPath: String, roots: List<RootFolder>): Boolean {
        val folder = ParsePaths.folderOf(relPath)
        if (byVolume[volume].orEmpty().none { PathScope.within(folder, it) }) return false
        return roots.none { root ->
            root.volumeUuid == volume && PathScope.within(folder, root.relPath) && !blocks(volume, root.relPath, folder)
        }
    }
}
