package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import com.frameglass.parser.NameParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Far above any real subtitle. The header's own size is never trusted, so this is enforced on the bytes. */
internal const val MAX_SUBTITLE_BYTES = 32L * 1024 * 1024

/** A season pack holds tens of files. Parsing a name per entry should stay bounded. */
private const val MAX_ENTRIES = 2_000

/** Every marker a real subtitle shows sits in its first cues. */
private const val SNIFF_BYTES = 256 * 1024

private val ACCEPTED_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
private const val JUNK_FOLDER = "__MACOSX"

/** A first entry, or the end record of an empty archive. */
private val ZIP_SIGNATURES = setOf(0x504B0304, 0x504B0506)

/** Windows zip tools write names in the system code page, with no flag saying so. */
private val LEGACY_NAMES: Charset = Charset.forName("windows-1256")

private val DRIVE_PATH = Regex("^[A-Za-z]:/")
private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

/** ASCII classes on purpose: \d is Unicode-aware under ICU on ART and not on the JVM. */
private val SRT_CUE = Regex("[0-9]{1,3}:[0-9]{2}:[0-9]{2}[,.][0-9]{1,3}[ \t]*-->")
private val ASS_MARKERS = listOf("[Script Info]", "[Events]", "Dialogue:")

private val LANGUAGE_WORDS = mapOf(
    "ar" to setOf("ar", "ara", "arabic", "عربي", "العربية"),
    "en" to setOf("en", "eng", "english"),
)

/**
 * Turns a provider's payload into one subtitle file. Entry names are only read here, never used as
 * paths, so a hostile name cannot write anywhere. An archive with such a name is still refused whole.
 * Android does the same when it opens one, and the JVM does not.
 */
internal object SubtitleArchive {
    class Ask(val season: Int?, val episode: Int?, val language: String, val seasonPack: Boolean)

    fun saved(dir: File, stem: String): File? =
        ACCEPTED_EXTENSIONS.map { File(dir, "$stem.$it") }.firstOrNull { it.length() > 0L }

    /** A [ZipException] covers both a broken zip and an encrypted entry. */
    fun place(payload: File, ask: Ask, dir: File, stem: String): NetResult<File> = try {
        dir.mkdirs()
        if (isZip(payload)) fromZip(payload, ask, dir, stem) else store(dir, stem) { payload.inputStream() }
    } catch (_: IOException) {
        Failure(Reason.BAD_RESPONSE, "archive")
    } catch (_: IllegalArgumentException) {
        Failure(Reason.BAD_RESPONSE, "archive")
    }

    private fun isZip(file: File): Boolean {
        val head = file.inputStream().use { it.readNBytes(Int.SIZE_BYTES) }
        return head.size == Int.SIZE_BYTES && ByteBuffer.wrap(head).int in ZIP_SIGNATURES
    }

    /** UTF-8 names fail in the opposite place on each platform: at open here, on read on ART. */
    private fun fromZip(payload: File, ask: Ask, dir: File, stem: String): NetResult<File> = try {
        ZipFile(payload, Charsets.UTF_8).use { extract(it, ask, dir, stem) }
    } catch (_: ZipException) {
        ZipFile(payload, LEGACY_NAMES).use { extract(it, ask, dir, stem) }
    } catch (_: IllegalArgumentException) {
        ZipFile(payload, LEGACY_NAMES).use { extract(it, ask, dir, stem) }
    }

    private fun extract(zip: ZipFile, ask: Ask, dir: File, stem: String): NetResult<File> {
        if (zip.size() > MAX_ENTRIES) return Failure(Reason.BAD_RESPONSE, "archive entries")
        val entries = zip.entries().toList()
        if (entries.any { escapes(it.name) }) return Failure(Reason.BAD_RESPONSE, "archive path")
        val chosen = pick(entries.filter(::isSubtitle), ask) ?: return Failure(Reason.NOT_FOUND, "archive")
        return store(dir, stem) { zip.getInputStream(chosen) }
    }

    private fun escapes(name: String): Boolean {
        val path = name.replace('\\', '/')
        return path.startsWith("/") || DRIVE_PATH.containsMatchIn(path) || '\u0000' in path ||
            path.split('/').any { it == ".." }
    }

    private fun isSubtitle(entry: ZipEntry): Boolean {
        val path = entry.name.replace('\\', '/')
        val fileName = path.substringAfterLast('/')
        return !entry.isDirectory && JUNK_FOLDER !in path.split('/') && !fileName.startsWith(".") &&
            fileName.substringAfterLast('.', "").lowercase() in ACCEPTED_EXTENSIONS
    }

    /**
     * Inside a pack a file must name the episode. A file with no episode in its name passes only when
     * nothing else could be meant, because the wrong episode is worse than none.
     */
    private fun pick(subtitles: List<ZipEntry>, ask: Ask): ZipEntry? {
        val wanted = ask.episode ?: return subtitles.bestFor(ask.language)
        val named = subtitles.map { it to NameParser.parse(it.name.substringBeforeLast('.') + ".mkv") }
        val fitting = named.filter { (_, name) -> name.covers(ask.season, wanted) }
        if (fitting.isNotEmpty()) {
            val sameSeason = fitting.filter { (_, name) -> name.season == ask.season }
            return sameSeason.ifEmpty { fitting }.map { it.first }.bestFor(ask.language)
        }
        val marked = named.any { (_, name) -> name.hasEpisode }
        val single = subtitles.size == 1
        val usable = if (ask.seasonPack) single && !marked else single || !marked
        return if (usable) subtitles.bestFor(ask.language) else null
    }

    private fun List<ZipEntry>.bestFor(language: String): ZipEntry? =
        maxWithOrNull(compareBy<ZipEntry> { namesLanguage(it.name, language) }.thenBy { it.size })

    private fun namesLanguage(name: String, language: String): Boolean {
        val marks = LANGUAGE_WORDS[language] ?: setOf(language)
        return name.lowercase().split(WORD_BREAK).any { it in marks }
    }

    /** The extension comes from the bytes, so a page saved as .srt never reaches the player. */
    private fun store(dir: File, stem: String, open: () -> InputStream): NetResult<File> {
        val part = File(dir, "$stem.part")
        try {
            val fits = open().use { input -> part.outputStream().use { input.copyCapped(it, MAX_SUBTITLE_BYTES) } }
            if (!fits) return Failure(Reason.BAD_RESPONSE, "subtitle too large")
            val extension = extensionOf(part) ?: return Failure(Reason.BAD_RESPONSE, "not a subtitle")
            val target = File(dir, "$stem.$extension")
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) return Failure(Reason.BAD_RESPONSE, "rename")
            }
            return Ok(target)
        } finally {
            part.delete()
        }
    }

    /** Stripping NUL lets one check read UTF-16 too. The markers are all ASCII. */
    private fun extensionOf(file: File): String? {
        val bytes = file.inputStream().use { it.readNBytes(SNIFF_BYTES) }
        val head = String(bytes, Charsets.ISO_8859_1).replace("\u0000", "")
        return when {
            "WEBVTT" in head.take(16) -> "vtt"
            ASS_MARKERS.any { it in head } -> if ("[V4 Styles]" in head) "ssa" else "ass"
            SRT_CUE.containsMatchIn(head) -> "srt"
            else -> null
        }
    }
}
