package com.frameglass.data.logic

import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.MediaFile
import com.frameglass.data.db.NameKinds
import com.frameglass.parser.NameKind
import com.frameglass.parser.ParsedName

internal fun interface Clock {
    fun now(): Long
}

internal object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/** Builds and rewrites playable keys per section 4.5. Numbers are the parser's own, never remapped. */
internal object PlayableKeys {
    fun movie(titleId: Long): String = "t:$titleId"
    fun episode(titleId: Long, season: Int, number: Int): String = "t:$titleId:s$season:e$number"
    fun file(fileId: String): String = "f:$fileId"
    fun uri(hash: String): String = "u:$hash"

    fun seasonOf(nameKind: String, season: Int?): Int = season ?: if (nameKind == NameKinds.SPECIAL) 0 else 1

    fun of(
        titleId: Long,
        kind: TitleKind,
        nameKind: String,
        season: Int?,
        episode: Int?,
        absolute: Int?,
        fileId: String,
    ): String {
        // An extra such as NCOP1 carries a number that must never collide with episode 1.
        if (nameKind == NameKinds.EXTRA) return file(fileId)
        if (kind == TitleKind.MOVIE) return movie(titleId)
        val number = episode ?: absolute ?: return file(fileId)
        return episode(titleId, seasonOf(nameKind, season), number)
    }

    /** Moves a key from one title to another. File and uri keys carry no title and stay as they are. */
    fun rehome(key: String, from: Long, to: Long): String {
        val prefix = "t:$from"
        return when {
            key == prefix -> "t:$to"
            key.startsWith("$prefix:") -> "t:$to" + key.removePrefix(prefix)
            else -> key
        }
    }

    fun titleIdOf(key: String): Long? =
        if (key.startsWith("t:")) key.substring(2).substringBefore(':').toLongOrNull() else null

    /** Rewrites a key through a whole id map at once, so a swap of two ids cannot chain. */
    fun remap(key: String, ids: Map<Long, Long>): String {
        val id = titleIdOf(key) ?: return key
        val to = ids[id] ?: return key
        return rehome(key, id, to)
    }

    /** All keys of a multi-episode file such as S02E07E08, so both episodes get marked. */
    fun covered(key: String, titleId: Long, nameKind: String, season: Int?, first: Int?, last: Int?): List<String> {
        if (first == null || last == null || last <= first || !key.startsWith("t:$titleId:s")) return listOf(key)
        val s = seasonOf(nameKind, season)
        return (first..last).map { episode(titleId, s, it) }
    }
}

internal data class GroupCandidate(val id: Long, val kind: TitleKind, val year: Int?)

internal object GroupKeys {
    fun key(kind: TitleKind, normalizedName: String, year: Int?): String =
        "${kind.name}|$normalizedName|${year ?: "-"}"

    /** Clips never group by name: 1.mp4 in two folders is two videos. The content is the identity. */
    fun clip(fileId: String): String = "CLIP|$fileId"

    private fun isTv(kind: TitleKind) = kind != TitleKind.MOVIE

    /**
     * Finds the existing group for a file whose exact key was not found. Series and anime share a group,
     * because one file can look like either. A missing year joins a single candidate, and two different
     * years stay apart, so remakes such as Dune 1984 and Dune 2021 do not merge.
     */
    fun pick(kind: TitleKind, year: Int?, candidates: List<GroupCandidate>): GroupCandidate? {
        val sameClass = candidates.filter { isTv(it.kind) == isTv(kind) }
        val exact = sameClass.filter { it.year == year }
        if (exact.isNotEmpty()) return exact.firstOrNull { it.kind == kind } ?: exact.first()
        return sameClass.filter { it.year == null || year == null }.singleOrNull()
    }
}

internal fun ParsedName.titleKind(): TitleKind = when {
    kind == NameKind.MOVIE -> TitleKind.MOVIE
    animeHint -> TitleKind.ANIME
    else -> TitleKind.SERIES
}

internal object PathRange {
    private const val TOP = "􏿿"

    /** Bounds of every relPath under [prefix]. '0' is the character right after '/' in byte order. */
    fun low(prefix: String): String = if (prefix.isEmpty()) "" else "$prefix/"
    fun high(prefix: String): String = if (prefix.isEmpty()) TOP else "${prefix}0"
}

internal object ParsePaths {
    private const val VOLUME_PATH_SEGMENTS = 4

    /**
     * The path handed to the parser: the path inside the added folder, plus that folder's name and its
     * parent's. The parent matters when the added folder is itself "Season 02", and anything higher only
     * adds noise such as a folder that happens to be called Extras. A whole volume has no folder to anchor
     * on, so only the three folders closest to the file count, which still reaches Show/Season 2/Extras.
     */
    fun forParser(rootRelPath: String, fileRelPath: String): String {
        if (rootRelPath.isEmpty()) return fileRelPath.split('/').takeLast(VOLUME_PATH_SEGMENTS).joinToString("/")
        val inside = fileRelPath.removePrefix("$rootRelPath/")
        return rootRelPath.split('/').takeLast(2).joinToString("/") + "/" + inside
    }

    fun baseName(relPath: String): String = relPath.substringAfterLast('/').substringBeforeLast('.')

    fun folderOf(relPath: String): String = relPath.substringBeforeLast('/', "")

    fun join(a: String, b: String): String = if (a.isEmpty()) b else "$a/$b"

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
}

/** How a parse is kept on a MediaFile row. */
internal object StoredParse {
    fun tags(parsed: ParsedName): String? = parsed.releaseTags.joinToString(",").ifEmpty { null }

    /**
     * [file] with its tags and anime number taken from [parsed]. The parser once stored a name in round brackets at
     * the start, as in (Name) Event 02.mp4, as a release tag with an anime number, so the row never counted as a
     * plain number.
     */
    fun reread(file: MediaFile, parsed: ParsedName): MediaFile = file.copy(releaseTags = tags(parsed), parsedAbsolute = parsed.absolute)
}

/** A cleaned file name, used for an episode row only when no real episode title exists. */
internal object CleanName {
    private val BRACKETS = Regex("""\[[^\]]*]|\([^)]*\)|\{[^}]*\}""")
    private val SEPARATORS = Regex("""[._]+""")
    private val SPACES = Regex("""\s+""")

    fun of(relPath: String, releaseTags: String?): String {
        val base = ParsePaths.baseName(relPath)
        var text = base.replace(BRACKETS, " ").replace(SEPARATORS, " ")
        releaseTags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { tag ->
            text = text.replace(tag.replace(SEPARATORS, " "), " ", ignoreCase = true)
        }
        val cleaned = text.replace(SPACES, " ").trim(' ', '-')
        return cleaned.ifEmpty { base }
    }
}
