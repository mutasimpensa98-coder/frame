package com.frameglass.data.scan

import android.util.Log
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.NameKinds
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.meta.TitleOps
import com.frameglass.data.repo.SettingsStore
import com.frameglass.parser.NameParser
import kotlinx.coroutines.CancellationException

/**
 * Runs once. Rows written before clip names existed still carry the old reading, such as IMG_0123 as episode
 * 123 of a series called IMG, and a rescan never parses an unchanged file again. Anything the owner matched or
 * gave a cover by hand keeps its title.
 */
internal class ClipPass(
    private val db: FrameDb,
    private val ops: TitleOps,
    private val settings: SettingsStore,
) {
    suspend fun run() {
        if (settings.clipPassDone()) return
        try {
            moveClips()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // This must not stop the search itself. The next open tries again.
            Log.w("FrameData", "clip pass failed", e)
            return
        }
        settings.setClipPassDone()
    }

    private suspend fun moveClips() {
        val scan = db.scan()
        val roots = scan.roots()
        val titles = db.titles().allTitles().associateBy { it.id }
        for (file in scan.allFiles()) {
            if (file.nameKind == NameKinds.CLIP) continue
            val title = titles[file.titleId] ?: continue
            if (title.tmdbId != null || title.manualMatch || title.manualCover) continue
            val root = roots.filter { it.volumeUuid == file.volumeUuid && file.relPath.startsWith(it.relPath + "/") }
                .maxByOrNull { it.relPath.length }
            val parsed = runCatching { NameParser.parse(ParsePaths.forParser(root?.relPath.orEmpty(), file.relPath)) }.getOrNull()
            if (parsed?.clipLike == true) ops.makeClip(file, parsed, ParsePaths.baseName(file.relPath))
        }
    }
}
