package com.frameglass.data.backup

import com.frameglass.data.api.AccentColor
import com.frameglass.data.api.AppSettings
import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.ThemeMode
import com.frameglass.data.api.TitleKind
import com.frameglass.data.logic.PlayableKeys
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

internal class BackupException(message: String) : Exception(message)

/** Holds preferences only. API keys live in their own encrypted store and have no field here. */
@Serializable
internal data class BackupSettings(
    val onlineFeatures: Boolean,
    val doubleTapSeconds: Int,
    val backgroundAudio: Boolean,
    val autoPlayNext: Boolean,
    val nightAudio: Boolean,
    val subtitleScale: Float,
    val subtitleColor: Int,
    val subtitleBackground: Boolean,
    val subtitleFontPath: String?,
    val subtitleBottomOffset: Float,
    val scanHiddenFolders: Boolean,
    val scanNomediaFolders: Boolean,
    val showUnavailable: Boolean,
    val tmdbBaseUrl: String,
    val neverSleepingHintDone: Boolean,
    val onboardingDone: Boolean,
    /** Has a default so a version 2 backup written before this setting still restores. */
    val scanWholePhone: Boolean = true,
    val themeMode: String = ThemeMode.DARK.name,
    val accent: String = AccentColor.WHITE.name,
    val coverTint: Boolean = false,
    val sendReleaseName: Boolean = true,
    val sendFileHash: Boolean = true,
    val vaultLockSeconds: Int = 0,
    val cinemaMode: Boolean = false,
    /**
     * Null in a backup written before the switches existed. Restoring one keeps the switch as it is on the phone, so
     * a service the owner turned off does not start calling again.
     */
    val tmdbEnabled: Boolean? = null,
    val imdbEnabled: Boolean? = null,
    /** Null in a backup written before the setting existed, which keeps what the phone holds. */
    val autoSubtitles: String? = null,
    /** Null in a backup written before the setting existed, which keeps what the phone holds. */
    val hideUnseenStills: Boolean? = null,
) {
    /** [current] is what the phone holds now, for the fields an older backup does not have. */
    fun toAppSettings(fontExists: Boolean, current: AppSettings) = AppSettings(
        onlineFeatures = onlineFeatures,
        doubleTapSeconds = doubleTapSeconds,
        backgroundAudio = backgroundAudio,
        autoPlayNext = autoPlayNext,
        nightAudio = nightAudio,
        subtitleScale = subtitleScale,
        subtitleColor = subtitleColor,
        subtitleBackground = subtitleBackground,
        subtitleFontPath = subtitleFontPath.takeIf { fontExists },
        subtitleBottomOffset = subtitleBottomOffset,
        scanWholePhone = scanWholePhone,
        scanHiddenFolders = scanHiddenFolders,
        scanNomediaFolders = scanNomediaFolders,
        showUnavailable = showUnavailable,
        tmdbBaseUrl = tmdbBaseUrl,
        themeMode = ThemeMode.valueOf(themeMode),
        accent = AccentColor.valueOf(accent),
        coverTint = coverTint,
        cinemaMode = cinemaMode,
        sendReleaseName = sendReleaseName,
        sendFileHash = sendFileHash,
        vaultLockSeconds = vaultLockSeconds,
        neverSleepingHintDone = neverSleepingHintDone,
        onboardingDone = onboardingDone,
        tmdbEnabled = tmdbEnabled ?: current.tmdbEnabled,
        imdbEnabled = imdbEnabled ?: current.imdbEnabled,
        autoSubtitles = autoSubtitles?.let(AutoSubtitleMode::parse) ?: current.autoSubtitles,
        hideUnseenStills = hideUnseenStills ?: current.hideUnseenStills,
    )

    companion object {
        fun from(s: AppSettings) = BackupSettings(
            s.onlineFeatures, s.doubleTapSeconds, s.backgroundAudio, s.autoPlayNext, s.nightAudio, s.subtitleScale,
            s.subtitleColor, s.subtitleBackground, s.subtitleFontPath, s.subtitleBottomOffset, s.scanHiddenFolders,
            s.scanNomediaFolders, s.showUnavailable, s.tmdbBaseUrl, s.neverSleepingHintDone, s.onboardingDone,
            s.scanWholePhone, s.themeMode.name, s.accent.name, s.coverTint, s.sendReleaseName, s.sendFileHash,
            s.vaultLockSeconds, s.cinemaMode, s.tmdbEnabled, s.imdbEnabled, s.autoSubtitles.name,
            s.hideUnseenStills,
        )
    }
}

@Serializable
internal data class BackupVolume(val uuid: String, val label: String?, val isRemovable: Boolean, val lastPath: String?)

@Serializable
internal data class BackupRoot(val volumeUuid: String, val relPath: String, val addedAt: Long)

@Serializable
internal data class BackupExcluded(val volumeUuid: String, val relPath: String, val addedAt: Long)

/** Identity and match only. Cached TMDB text and images come back through a refresh. */
@Serializable
internal data class BackupTitle(
    val id: Long,
    val kind: String,
    val groupKey: String,
    val groupName: String,
    val localName: String,
    val localYear: Int?,
    val tmdbId: Long?,
    val tmdbIsMovie: Boolean?,
    val anilistId: Long?,
    val malId: Long?,
    val imdbId: String?,
    val matchState: String,
    val matchConfidence: Double?,
    val manualMatch: Boolean,
    val coverFile: String?,
    val removed: Boolean,
    val createdAt: Long,
    val favorite: Boolean = false,
    /** When the title joined the watchlist. Null when it is not there, and in a backup written before the watchlist. */
    val watchlistAt: Long? = null,
)

@Serializable
internal data class BackupAlias(val groupKey: String, val titleId: Long)

@Serializable
internal data class BackupFile(
    val fileId: String,
    val volumeUuid: String,
    val relPath: String,
    val size: Long,
    val mtime: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val hdrType: String?,
    val audioSummary: String?,
    val osHash: String,
    val firstSeenAt: Long,
    val titleId: Long?,
    val releaseTags: String?,
    val hasSidecarSub: Boolean,
    val hasEmbeddedSub: Boolean,
    val playableKey: String,
    val parsedSeason: Int?,
    val parsedEpisode: Int?,
    val parsedEpisodeEnd: Int?,
    val parsedAbsolute: Int?,
    val nameKind: String,
)

@Serializable
internal data class BackupWatch(
    val playableKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val lastPlayedAt: Long,
    val titleId: Long?,
)

@Serializable
internal data class BackupSeriesPrefs(
    val titleId: Long,
    val audioLang: String?,
    val subLang: String?,
    val subsOff: Boolean,
    val speed: Float,
    val aspectMode: String?,
)

@Serializable
internal data class BackupFilePrefs(
    val fileId: String,
    val audioTrack: String?,
    val subtitleTrack: String?,
    val subtitleUri: String?,
    val subtitleEncoding: String?,
    val subtitleDelayMs: Long,
    val subsOff: Boolean,
    val speed: Float,
    val aspectMode: String?,
)

@Serializable
internal data class BackupDelay(val deviceKey: String, val delayMs: Long)

/** Manual intro times, the one IntroSegment source only the owner can make. */
@Serializable
internal data class BackupIntro(val titleId: Long, val season: Int?, val kind: String, val startMs: Long, val endMs: Long)

@Serializable
internal data class BackupDoc(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.VERSION,
    val exportedAt: Long,
    val settings: BackupSettings,
    val volumes: List<BackupVolume>,
    val roots: List<BackupRoot>,
    val titles: List<BackupTitle>,
    val aliases: List<BackupAlias>,
    val files: List<BackupFile>,
    val watch: List<BackupWatch>,
    val seriesPrefs: List<BackupSeriesPrefs>,
    val filePrefs: List<BackupFilePrefs>,
    val audioDelays: List<BackupDelay>,
    val intros: List<BackupIntro>,
    val excluded: List<BackupExcluded> = emptyList(),
)

/** Takes everything that belongs to the private folder out of a backup, safety copies included. */
internal object BackupPrivacy {
    /** [titles] are titles with no public file left, and [files] are file ids held only in the private folder. */
    fun strip(doc: BackupDoc, titles: Set<Long>, files: Set<String>): BackupDoc = doc.copy(
        titles = doc.titles.filter { it.id !in titles },
        aliases = doc.aliases.filter { it.titleId !in titles },
        watch = doc.watch.filter { it.titleId !in titles && PlayableKeys.titleIdOf(it.playableKey) !in titles },
        seriesPrefs = doc.seriesPrefs.filter { it.titleId !in titles },
        filePrefs = doc.filePrefs.filter { it.fileId !in files },
        intros = doc.intros.filter { it.titleId !in titles },
    )
}

internal object BackupCodec {
    const val FORMAT = "frame-glass-backup"
    const val VERSION = 2
    const val JSON_ENTRY = "backup.json"
    const val COVER_DIR = "covers/"

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }
    private val COVER_NAME = Regex("""covers/[A-Za-z0-9][A-Za-z0-9._-]*""")

    fun isCoverName(name: String): Boolean = COVER_NAME.matches(name)

    fun encode(doc: BackupDoc): String = json.encodeToString(BackupDoc.serializer(), doc)

    fun decode(text: String): BackupDoc {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw BackupException("not a backup file")
        val format = root["format"]?.jsonPrimitive?.content
        val version = root["version"]?.jsonPrimitive?.intOrNull
        if (format != FORMAT) throw BackupException("wrong format")
        if (version != VERSION) throw BackupException("unsupported version")
        return try {
            json.decodeFromString(BackupDoc.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("damaged backup: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw BackupException("damaged backup: ${e.message}")
        }
    }

    private fun require(condition: Boolean, problem: String) {
        if (!condition) throw BackupException(problem)
    }

    private inline fun <reified T : Enum<T>> isEnum(name: String) = enumValues<T>().any { it.name == name }

    private fun safeRelative(path: String) =
        !path.startsWith("/") && path.split('/').none { it == ".." } && !path.contains('\u0000')

    /** Everything a restore relies on. Runs before any write, so a bad file changes nothing. */
    fun validate(doc: BackupDoc, coverEntries: Set<String>) {
        require(isEnum<ThemeMode>(doc.settings.themeMode) && isEnum<AccentColor>(doc.settings.accent), "bad appearance")
        require(doc.settings.vaultLockSeconds >= 0, "bad lock time")
        doc.excluded.forEach { require(safeRelative(it.relPath), "bad folder path") }
        val titleIds = doc.titles.mapTo(HashSet()) { it.id }
        require(titleIds.size == doc.titles.size, "duplicate title id")
        require(doc.titles.mapTo(HashSet()) { it.groupKey }.size == doc.titles.size, "duplicate group key")
        for (title in doc.titles) {
            require(isEnum<TitleKind>(title.kind), "bad title kind")
            require(isEnum<MatchState>(title.matchState), "bad match state")
            require(title.groupKey.isNotEmpty() && title.localName.isNotEmpty(), "empty title")
            title.coverFile?.let { require(it in coverEntries, "missing cover file") }
        }
        doc.aliases.forEach { require(it.titleId in titleIds, "alias of an unknown title") }
        doc.roots.forEach { require(safeRelative(it.relPath), "bad folder path") }
        val paths = HashSet<String>()
        for (file in doc.files) {
            require(file.fileId.isNotEmpty() && safeRelative(file.relPath), "bad file path")
            require(paths.add(file.volumeUuid + "/" + file.relPath), "duplicate file path")
            require(file.size >= 0, "bad file size")
            file.titleId?.let { require(it in titleIds, "file of an unknown title") }
            require(file.playableKey.isNotEmpty(), "file with no key")
        }
        for (state in doc.watch) {
            require(state.playableKey.isNotEmpty(), "progress with no key")
            require(state.positionMs >= 0 && state.durationMs >= 0, "bad position")
            state.titleId?.let { require(it in titleIds, "progress of an unknown title") }
        }
        doc.seriesPrefs.forEach { require(it.titleId in titleIds, "preferences of an unknown title") }
        for (intro in doc.intros) {
            require(intro.titleId in titleIds, "intro of an unknown title")
            require(isEnum<SegmentKind>(intro.kind) && intro.endMs >= intro.startMs, "bad intro")
        }
    }
}

internal class BackupArchive(val doc: BackupDoc, val covers: Map<String, File>) {
    companion object {
        private const val MAX_ENTRIES = 20_000
        private const val MAX_JSON_BYTES = 64L * 1024 * 1024
        private const val MAX_COVER_BYTES = 32L * 1024 * 1024

        private fun copy(input: InputStream, output: OutputStream, limit: Long) {
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return
                total += read
                if (total > limit) throw BackupException("entry too large")
                output.write(buffer, 0, read)
            }
        }

        /** Reads and validates the whole archive into [folder]. Throws [BackupException] on anything wrong. */
        fun read(input: InputStream, folder: File): BackupArchive {
            var text: String? = null
            val covers = HashMap<String, File>()
            var count = 0
            val zip = ZipInputStream(input.buffered())
            try {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES) throw BackupException("too many entries")
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == BackupCodec.JSON_ENTRY -> {
                            val bytes = java.io.ByteArrayOutputStream()
                            copy(zip, bytes, MAX_JSON_BYTES)
                            text = bytes.toString(Charsets.UTF_8.name())
                        }
                        BackupCodec.isCoverName(name) -> {
                            val file = File(folder, name.removePrefix(BackupCodec.COVER_DIR))
                            file.outputStream().use { copy(zip, it, MAX_COVER_BYTES) }
                            covers[name] = file
                        }
                        else -> throw BackupException("unknown entry")
                    }
                }
            } catch (e: java.util.zip.ZipException) {
                throw BackupException("damaged zip: ${e.message}")
            }
            val doc = BackupCodec.decode(text ?: throw BackupException("not a backup file"))
            BackupCodec.validate(doc, covers.keys)
            return BackupArchive(doc, covers)
        }
    }
}
