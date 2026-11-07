package com.frameglass.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream

/*
 * F-M9 step 2. aircompressor would be a new dependency, so this takes the brief's last option:
 * mappings.min.json on Wi-Fi. The file is 9.3 MB, so it is streamed and only rows linking
 * anilist or mal to tmdb_show are kept, in a small private file.
 *
 * MetadataClient has no way to hand rows to :data, so the rows live here instead of the
 * AnimeMap table, with the same fields.
 */

internal const val MAPPINGS_URL =
    "https://github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.min.json"

private const val STORE_HEADER = "#frameglass-anime-map-1"
private const val STORE_NAME = "anime-map.tsv"
private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private const val RETRY_MS = 6L * 60 * 60 * 1000

internal enum class AnimeSource(val prefix: String, val code: String) {
    ANILIST("anilist:", "a"),
    MAL("mal:", "m"),
}

internal class Span(val first: Int, val last: Int?) {
    fun contains(episode: Int): Boolean = episode >= first && (last == null || episode <= last)

    companion object {
        fun parse(text: String): Span? {
            val dash = text.indexOf('-')
            if (dash < 0) return text.toIntOrNull()?.let { Span(it, it) }
            val first = text.substring(0, dash).toIntOrNull() ?: return null
            val tail = text.substring(dash + 1)
            if (tail.isEmpty()) return Span(first, null)
            return tail.toIntOrNull()?.let { Span(first, it) }
        }
    }
}

/**
 * One `"source": "target"` pair. The README says a positive ratio means each source episode spans
 * n targets, but the data says the reverse. Measured 2026-11-07 on schema 3.0.3: all 335 closed
 * positive rows have source count = target count * n ("1-50" to "1-25|2"), and all 338 closed
 * negative rows have source count * n = target count ("1-51" to "1-102|-2"). `|0` occurs on 13
 * rows and means 1:1. This follows the data.
 */
internal class EpisodeRange(val source: Span, val target: List<Span>, val ratio: Int) {
    fun forward(episode: Int): Int? {
        if (!source.contains(episode)) return null
        val offset = episode - source.first
        return targetAt(
            when {
                ratio > 0 -> offset / ratio
                ratio < 0 -> offset * -ratio
                else -> offset
            },
        )
    }

    fun backward(episode: Int): Int? {
        val index = indexInTarget(episode) ?: return null
        val offset = when {
            ratio > 0 -> index * ratio
            ratio < 0 -> index / -ratio
            else -> index
        }
        return (source.first + offset).takeIf(source::contains)
    }

    private fun targetAt(index: Int): Int? {
        var remaining = index
        for (span in target) {
            val last = span.last ?: return span.first + remaining
            val size = last - span.first + 1
            if (remaining < size) return span.first + remaining
            remaining -= size
        }
        return null
    }

    private fun indexInTarget(episode: Int): Int? {
        var skipped = 0
        for (span in target) {
            if (span.contains(episode)) return skipped + episode - span.first
            val last = span.last ?: return null
            skipped += last - span.first + 1
        }
        return null
    }

    companion object {
        fun parse(source: String, target: String): EpisodeRange? {
            val from = Span.parse(source) ?: return null
            val spans = target.substringBefore('|').split(',').map { Span.parse(it) ?: return null }
            return EpisodeRange(from, spans, target.substringAfter('|', "0").toIntOrNull() ?: return null)
        }
    }
}

/** With [reverse] the range was read from a tmdb_show key, so its source side is TMDB numbering. */
internal class AnimeRow(
    val source: AnimeSource,
    val id: Long,
    val show: Long,
    val season: Int,
    val reverse: Boolean,
    private val range: EpisodeRange,
) {
    fun tmdbEpisode(animeEpisode: Int): Int? =
        if (reverse) range.backward(animeEpisode) else range.forward(animeEpisode)

    fun animeEpisode(tmdbEpisode: Int): Int? =
        if (reverse) range.forward(tmdbEpisode) else range.backward(tmdbEpisode)

    fun toLine(sourceText: String, targetText: String): String =
        listOf(source.code, id, show, season, if (reverse) "r" else "f", sourceText, targetText).joinToString("\t")

    companion object {
        fun parse(line: String): AnimeRow? {
            val fields = line.split('\t')
            if (fields.size != 7) return null
            return AnimeRow(
                source = AnimeSource.entries.firstOrNull { it.code == fields[0] } ?: return null,
                id = fields[1].toLongOrNull() ?: return null,
                show = fields[2].toLongOrNull() ?: return null,
                season = fields[3].toIntOrNull() ?: return null,
                reverse = fields[4] == "r",
                range = EpisodeRange.parse(fields[5], fields[6]) ?: return null,
            )
        }
    }
}

internal data class AnimeIds(val anilist: Long?, val mal: Long?)

internal data class MalEpisode(val malId: Long, val episode: Int)

internal class AnimeIndex(rows: List<AnimeRow>) {
    private val rowOrder = compareBy<AnimeRow>({ it.reverse }, { it.season == 0 }, { it.season })
    private val byAnilist = rows.filter { it.source == AnimeSource.ANILIST }.groupBy(AnimeRow::id).sorted()
    private val byMal = rows.filter { it.source == AnimeSource.MAL }.groupBy(AnimeRow::id).sorted()
    private val byShow = rows.groupBy(AnimeRow::show).sorted()

    private fun Map<Long, List<AnimeRow>>.sorted() = mapValues { (_, list) -> list.sortedWith(rowOrder) }

    /** A season-relative or absolute number resolved through the ranges of that AniList or MAL entry. */
    fun toTmdb(anilistId: Long, malId: Long?, episode: Int): AnimeEpisodeMap? {
        for (rows in rowsOf(anilistId, malId)) {
            for (row in rows) {
                val mapped = row.tmdbEpisode(episode) ?: continue
                return AnimeEpisodeMap(row.show, row.season, mapped)
            }
        }
        return null
    }

    fun showOf(anilistId: Long, malId: Long?): Long? = rowsOf(anilistId, malId).firstOrNull()?.firstOrNull()?.show

    /** AniSkip keys on the MAL entry of that exact season, which is often not the title's first entry. */
    fun toMal(show: Long, season: Int, episode: Int): MalEpisode? {
        for (row in byShow[show].orEmpty()) {
            if (row.source != AnimeSource.MAL || row.season != season) continue
            val mapped = row.animeEpisode(episode) ?: continue
            return MalEpisode(row.id, mapped)
        }
        return null
    }

    fun idsOf(show: Long, season: Int): AnimeIds? {
        val rows = byShow[show].orEmpty().filter { it.season == season }
        if (rows.isEmpty()) return null
        return AnimeIds(
            anilist = rows.firstOrNull { it.source == AnimeSource.ANILIST }?.id,
            mal = rows.firstOrNull { it.source == AnimeSource.MAL }?.id,
        )
    }

    private fun rowsOf(anilistId: Long, malId: Long?): List<List<AnimeRow>> =
        listOfNotNull(byAnilist[anilistId], malId?.let(byMal::get))

    companion object {
        val EMPTY = AnimeIndex(emptyList())

        fun read(lines: Sequence<String>): AnimeIndex {
            val all = lines.iterator()
            if (!all.hasNext() || all.next() != STORE_HEADER) return EMPTY
            return AnimeIndex(all.asSequence().mapNotNull(AnimeRow::parse).toList())
        }
    }
}

/** Reads a mapping file key by key, so the 9.3 MB document is never held in memory as a tree. */
internal object MappingParser {
    /** Writes the store format and returns the number of rows kept. */
    @OptIn(ExperimentalSerializationApi::class)
    fun convert(input: InputStream, output: Writer): Int {
        output.write(STORE_HEADER)
        output.write("\n")
        val writer = RowWriter(output)
        NetJson.decodeFromStream(writer, input)
        return writer.rows
    }

    private class RowWriter(private val output: Writer) : DeserializationStrategy<Unit> {
        var rows = 0
            private set

        override val descriptor: SerialDescriptor =
            MapSerializer(String.serializer(), JsonElement.serializer()).descriptor

        private val targets = MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), String.serializer().nullable),
        )

        override fun deserialize(decoder: Decoder) {
            decoder.decodeStructure(descriptor) {
                while (true) {
                    val keyIndex = decodeElementIndex(descriptor)
                    if (keyIndex == CompositeDecoder.DECODE_DONE) break
                    val key = decodeStringElement(descriptor, keyIndex)
                    val valueIndex = decodeElementIndex(descriptor)
                    if (isWanted(key)) {
                        write(key, decodeSerializableElement(descriptor, valueIndex, targets))
                    } else {
                        decodeSerializableElement(descriptor, valueIndex, JsonElement.serializer())
                    }
                }
            }
        }

        private fun isWanted(key: String): Boolean =
            key.startsWith(SHOW_PREFIX) || AnimeSource.entries.any { key.startsWith(it.prefix) }

        private fun write(key: String, entries: Map<String, Map<String, String?>>) {
            for ((target, ranges) in entries) {
                val forward = key.startsWith(SHOW_PREFIX).not()
                val (animeKey, showKey) = if (forward) key to target else target to key
                val source = AnimeSource.entries.firstOrNull { animeKey.startsWith(it.prefix) } ?: continue
                val id = animeKey.removePrefix(source.prefix).toLongOrNull() ?: continue
                val (show, season) = parseShow(showKey) ?: continue
                for ((from, to) in ranges) {
                    // A null target means "explicitly unmapped" in the schema.
                    val range = EpisodeRange.parse(from, to ?: continue) ?: continue
                    output.write(AnimeRow(source, id, show, season, !forward, range).toLine(from, to))
                    output.write("\n")
                    rows++
                }
            }
        }

        private fun parseShow(key: String): Pair<Long, Int>? {
            val parts = key.split(':')
            if (parts.size != 3 || parts[0] != "tmdb_show" || !parts[2].startsWith("s")) return null
            val show = parts[1].toLongOrNull() ?: return null
            return show to (parts[2].drop(1).toIntOrNull() ?: return null)
        }
    }

    private const val SHOW_PREFIX = "tmdb_show:"
}

/** Weekly refresh on Wi-Fi only. A failed refresh keeps the rows already on the phone. */
internal class AnimeMappings(
    private val dir: File,
    private val http: Http,
    private val config: NetConfig,
    private val onWifi: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val store = File(dir, STORE_NAME)
    private val refreshLock = Mutex()

    /** Separate from [refreshLock], so a segment lookup at playback never waits for a download. */
    private val loadLock = Mutex()
    private var lastAttempt: Long? = null

    @Volatile
    private var loaded: AnimeIndex? = null

    suspend fun refreshIfDue() {
        if (!config.onlineEnabled) return
        refreshLock.withLock {
            if (store.isFile && now() - store.lastModified() < WEEK_MS) return
            if (!onWifi()) return
            val last = lastAttempt
            if (last != null && now() - last < RETRY_MS) return
            lastAttempt = now()
            if (replaceStore()) loadLock.withLock { loaded = null }
        }
    }

    suspend fun index(): AnimeIndex {
        loaded?.let { return it }
        return loadLock.withLock {
            loaded ?: withContext(Dispatchers.IO) { readStore() }.also { loaded = it }
        }
    }

    private suspend fun replaceStore(): Boolean {
        withContext(Dispatchers.IO) { dir.mkdirs() }
        val json = File(dir, "mappings.min.json")
        if (http.download(MAPPINGS_URL, json, "anibridge") !is NetResult.Ok) return false
        val fresh = File(dir, "$STORE_NAME.tmp")
        return withContext(Dispatchers.IO) {
            try {
                val rows = json.inputStream().buffered().use { input ->
                    fresh.bufferedWriter().use { MappingParser.convert(input, it) }
                }
                rows > 0 && install(fresh)
            } catch (_: IOException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            } finally {
                json.delete()
                fresh.delete()
            }
        }
    }

    private fun install(fresh: File): Boolean {
        if (!fresh.renameTo(store)) {
            store.delete()
            if (!fresh.renameTo(store)) return false
        }
        store.setLastModified(now())
        return true
    }

    private fun readStore(): AnimeIndex = try {
        if (store.isFile) store.useLines(block = AnimeIndex::read) else AnimeIndex.EMPTY
    } catch (_: IOException) {
        AnimeIndex.EMPTY
    }
}
