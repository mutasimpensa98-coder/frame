package com.frameglass.data.scan

import android.util.Log
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.RootFolder
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.StoredParse
import com.frameglass.data.meta.TitleOps
import com.frameglass.data.repo.SettingsStore
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import kotlinx.coroutines.CancellationException

/**
 * Each step runs once. Rows written before clip names existed still carry the old reading, such as IMG_0123 as
 * episode 123 of a series called IMG, and a rescan never parses an unchanged file again. Rows written before the
 * anime hint needed a release sign still say anime for a name like Beach Day - 3.mp4. Anything the owner matched
 * or gave a cover by hand keeps its title.
 */
internal class ClipPass(
    private val db: FrameDb,
    private val ops: TitleOps,
    private val settings: SettingsStore,
) {
    suspend fun run() {
        if (!settings.clipPassDone() && attempt(::moveClips)) settings.setClipPassDone()
        if (!settings.animePassDone() && attempt(::settleAnime)) settings.setAnimePassDone()
    }

    private suspend fun attempt(step: suspend () -> Unit): Boolean = try {
        step()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // This must not stop the search itself. The next open tries again.
        Log.w("FrameData", "clip pass failed", e)
        false
    }

    private fun parse(file: MediaFile, roots: List<RootFolder>): ParsedName? {
        val root = roots.filter { it.volumeUuid == file.volumeUuid && file.relPath.startsWith(it.relPath + "/") }
            .maxByOrNull { it.relPath.length }
        return runCatching { NameParser.parse(ParsePaths.forParser(root?.relPath.orEmpty(), file.relPath)) }.getOrNull()
    }

    private suspend fun moveClips() {
        val scan = db.scan()
        val roots = scan.roots()
        val titles = db.titles().allTitles().associateBy { it.id }
        for (file in scan.allFiles()) {
            if (file.nameKind == NameKinds.CLIP) continue
            val title = titles[file.titleId] ?: continue
            if (title.tmdbId != null || title.imdbId != null || title.manualMatch || title.manualCover) continue
            val parsed = parse(file, roots)
            if (parsed?.clipLike == true) ops.makeClip(file, parsed, ParsePaths.baseName(file.relPath))
        }
    }

    private suspend fun settleAnime() {
        val scan = db.scan()
        val roots = scan.roots()
        for (title in db.titles().allTitles()) {
            if (title.kind != TitleKind.ANIME || title.tmdbId != null || title.imdbId != null || title.manualMatch) continue
            val files = scan.filesOfTitle(title.id)
            val parsed = files.map { parse(it, roots) }
            if (files.isEmpty() || parsed.any { it?.animeHint == true }) continue
            // The old tags would keep these files from ever counting as plain numbers, and so from staying local.
            ops.settleAsSeries(title.id, files.zip(parsed).mapNotNull { (file, p) -> p?.let { StoredParse.reread(file, it) } })
        }
    }
}
