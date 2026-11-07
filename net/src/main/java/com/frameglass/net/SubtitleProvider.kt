package com.frameglass.net

import com.frameglass.net.NetResult.Ok
import com.frameglass.parser.NameParser
import com.frameglass.parser.ParsedName
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** OpenSubtitles refuses a call that does not name its client. The others get the same name. */
internal const val SUBTITLE_USER_AGENT = "FrameGlass v1.0"

/** Stops a hostile reply early. A whole season of subtitles fits many times over. */
internal const val MAX_PAYLOAD_BYTES = 50L * 1024 * 1024

internal interface SubtitleProvider {
    val name: String
    val keyKind: ApiKey

    /** [languages] is never empty. */
    suspend fun search(query: SubtitleQuery, languages: List<String>): NetResult<List<SubtitleHit>>

    /** Saves what the provider sends, a zip or a bare subtitle, to [target]. */
    suspend fun fetch(hit: SubtitleHit, target: File): NetResult<Unit>
}

internal fun NetConfig.keyOf(kind: ApiKey): String? = key(kind)?.trim()?.takeIf(String::isNotEmpty)

internal val SubtitleQuery.isEpisodic: Boolean get() = season != null || episode != null

/**
 * One request per language, because each reply is a capped page and English would crowd Arabic off
 * it. A language that fails keeps the others, and the provider fails only when all of them did.
 */
internal suspend fun eachLanguage(
    languages: List<String>,
    search: suspend (String) -> NetResult<List<SubtitleHit>>,
): NetResult<List<SubtitleHit>> = coroutineScope {
    val results = languages.map { language -> async { search(language) } }.awaitAll()
    val found = results.mapNotNull { it.valueOrNull() }
    if (found.isEmpty()) results.first() else Ok(found.flatten())
}

internal enum class EpisodeFit { EXACT, PACK, OTHER }

/** What release names say about the asked episode. A name with no episode is a whole season. */
internal fun fitOf(names: List<String>, season: Int?, episode: Int?): EpisodeFit {
    if (episode == null) return EpisodeFit.EXACT
    if (names.isEmpty()) return EpisodeFit.PACK
    return names.minOf { NameParser.parse("$it.mkv").fitTo(season, episode) }
}

private fun ParsedName.fitTo(season: Int?, episode: Int): EpisodeFit = when {
    covers(season, episode) -> EpisodeFit.EXACT
    !hasEpisode && (season == null || this.season == null || this.season == season) -> EpisodeFit.PACK
    else -> EpisodeFit.OTHER
}

internal val ParsedName.hasEpisode: Boolean get() = episode != null || absolute != null

internal fun ParsedName.covers(season: Int?, episode: Int): Boolean {
    if (season != null && this.season != null && this.season != season) return false
    val first = this.episode ?: absolute ?: return false
    return episode in first..(episodeEnd ?: first)
}
