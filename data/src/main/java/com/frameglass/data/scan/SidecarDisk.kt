package com.frameglass.data.scan

import com.frameglass.data.logic.EpisodeTag
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.ScanRules
import com.frameglass.data.logic.SidecarClaim
import com.frameglass.data.logic.SidecarClaims
import com.frameglass.data.logic.SidecarFile
import com.frameglass.data.logic.SidecarHit
import com.frameglass.data.logic.SidecarMatcher
import com.frameglass.parser.NameParser
import java.io.File

/** F-S7 on real folders. [SidecarHit.file] carries absolute folders here. */
internal object SidecarDisk {
    /** The folders go along because a bare "07.mkv" only reads as an episode next to its season folder. */
    fun tagOf(folder: File, name: String): EpisodeTag? = runCatching {
        val path = listOfNotNull(folder.parentFile?.name, folder.name, name).joinToString("/")
        val parsed = NameParser.parse(path)
        (parsed.episode ?: parsed.absolute)?.let { EpisodeTag(parsed.season, it) }
    }.getOrNull()

    private fun listed(folder: File, names: List<String>): Pair<List<SidecarFile>, List<SidecarFile>> {
        val same = names.filter(SidecarMatcher::isSubtitle).map { SidecarFile(folder.path, it) }
        val sub = names.filter { it.lowercase() in SidecarMatcher.SUB_FOLDERS }.flatMap { child ->
            val dir = File(folder, child)
            dir.list().orEmpty().filter(SidecarMatcher::isSubtitle).map { SidecarFile(dir.path, it) }
        }
        return same to sub
    }

    fun matching(video: File): List<SidecarHit> {
        val folder = video.parentFile ?: return emptyList()
        val (same, sub) = listed(folder, folder.list().orEmpty().toList())
        return SidecarMatcher.match(video.name, same, sub) { tagOf(folder, it) }
    }

    /** What the videos named [moving] read in [folder], and which of it another video there reads as well. */
    fun claims(folder: File, moving: Collection<String>): List<SidecarClaim> {
        val names = folder.list().orEmpty().toList()
        val (same, sub) = listed(folder, names)
        if (same.isEmpty() && sub.isEmpty()) return emptyList()
        val staying = names.filter { name ->
            ParsePaths.extension(name) in Extensions.VIDEO && !ScanRules.isStaged(name) && name !in moving
        }
        return SidecarClaims.of(moving, staying) { video -> SidecarMatcher.match(video, same, sub) { tagOf(folder, it) } }
    }
}
