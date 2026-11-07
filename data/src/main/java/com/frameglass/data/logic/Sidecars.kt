package com.frameglass.data.logic

internal data class SidecarFile(val folder: String, val name: String)

internal data class EpisodeTag(val season: Int?, val episode: Int) {
    fun sameAs(other: EpisodeTag): Boolean = episode == other.episode && (season ?: 1) == (other.season ?: 1)
}

internal data class SidecarHit(
    val file: SidecarFile,
    val language: String?,
    val forced: Boolean,
    val signs: Boolean,
) {
    private val partial get() = forced || signs

    /** Arabic first, and a full Arabic track outranks forced or Signs tracks, per F-S7. */
    val rank: Int
        get() = when {
            language == "ar" && !partial -> 0
            language == "ar" -> 1
            language == null && !partial -> 2
            language == "en" && !partial -> 3
            !partial -> 4
            else -> 5
        }
}

/** F-S7. Takes names only, so it works on listings and needs no file access. */
internal object SidecarMatcher {
    val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
    val SUB_FOLDERS = setOf("subs", "subtitles")

    private val LANGUAGES = mapOf(
        "ar" to "ar", "ara" to "ar", "arabic" to "ar", "عربي" to "ar", "العربية" to "ar", "عربية" to "ar",
        "en" to "en", "eng" to "en", "english" to "en",
        "fr" to "fr", "fre" to "fr", "fra" to "fr", "french" to "fr",
        "de" to "de", "ger" to "de", "deu" to "de", "german" to "de",
        "es" to "es", "spa" to "es", "spanish" to "es",
        "it" to "it", "ita" to "it", "italian" to "it",
        "pt" to "pt", "por" to "pt", "portuguese" to "pt",
        "ru" to "ru", "rus" to "ru", "russian" to "ru",
        "tr" to "tr", "tur" to "tr", "turkish" to "tr",
        "fa" to "fa", "per" to "fa", "fas" to "fa", "persian" to "fa",
        "ur" to "ur", "urd" to "ur", "urdu" to "ur",
        "ja" to "ja", "jpn" to "ja", "japanese" to "ja",
        "ko" to "ko", "kor" to "ko", "korean" to "ko",
        "zh" to "zh", "chi" to "zh", "zho" to "zh", "chinese" to "zh",
    )
    private val FORCED = setOf("forced", "foreign")
    private val SIGNS = setOf("signs", "sign", "songs")
    private val SEPARATORS = charArrayOf('.', '-', '_', ' ')

    fun isSubtitle(name: String): Boolean = ParsePaths.extension(name) in EXTENSIONS

    fun mimeType(name: String): String = when (ParsePaths.extension(name)) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        else -> "text/x-ssa"
    }

    private fun tagsAfterBase(base: String, subtitleName: String): List<String>? {
        val stem = subtitleName.substringBeforeLast('.')
        if (stem.equals(base, ignoreCase = true)) return emptyList()
        if (stem.length <= base.length || !stem.regionMatches(0, base, 0, base.length, ignoreCase = true)) return null
        if (stem[base.length] !in SEPARATORS) return null
        return stem.substring(base.length + 1).split(*SEPARATORS).filter { it.isNotEmpty() }
    }

    private fun hit(file: SidecarFile, tags: List<String>): SidecarHit {
        val lower = tags.map { it.lowercase() }
        return SidecarHit(
            file = file,
            language = lower.firstNotNullOfOrNull { LANGUAGES[it] },
            forced = lower.any { it in FORCED },
            signs = lower.any { it in SIGNS },
        )
    }

    /** [parse] reads the season and episode of a file name, for the fallback when no base name matches. */
    fun match(
        videoName: String,
        sameFolder: List<SidecarFile>,
        subFolders: List<SidecarFile>,
        parse: (String) -> EpisodeTag?,
    ): List<SidecarHit> {
        val base = videoName.substringBeforeLast('.')
        val byBase = (sameFolder + subFolders).mapNotNull { file ->
            tagsAfterBase(base, file.name)?.let { hit(file, it) }
        }
        if (byBase.isNotEmpty()) return sorted(byBase)

        val tag = parse(videoName) ?: return emptyList()
        val only = sameFolder.filter { parse(it.name)?.sameAs(tag) == true }.singleOrNull() ?: return emptyList()
        val tokens = only.name.substringBeforeLast('.').split(*SEPARATORS).filter { it.isNotEmpty() }.takeLast(2)
        return listOf(hit(only, tokens))
    }

    private fun sorted(hits: List<SidecarHit>): List<SidecarHit> =
        hits.sortedWith(compareBy<SidecarHit> { it.rank }.thenComparator { a, b -> NaturalOrder.compare(a.file.name, b.file.name) })
}

/** A sidecar that a moving or deleted video reads. [shared] means a video staying behind reads it too. */
internal data class SidecarClaim(val hit: SidecarHit, val owners: List<String>, val shared: Boolean)

internal object SidecarClaims {
    /**
     * The fallback in [SidecarMatcher] lets two versions of an episode read one subtitle, so a file can only go
     * with its video when no video staying in the folder reads it.
     */
    fun of(moving: Collection<String>, staying: Collection<String>, match: (String) -> List<SidecarHit>): List<SidecarClaim> {
        val owners = LinkedHashMap<SidecarFile, MutableList<String>>()
        val hits = HashMap<SidecarFile, SidecarHit>()
        for (video in moving) {
            for (hit in match(video)) {
                owners.getOrPut(hit.file) { ArrayList() } += video
                hits[hit.file] = hit
            }
        }
        val kept = staying.flatMapTo(HashSet()) { video -> match(video).map { it.file } }
        return owners.map { (file, names) -> SidecarClaim(hits.getValue(file), names, file in kept) }
    }
}
