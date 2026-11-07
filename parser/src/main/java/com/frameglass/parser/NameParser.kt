package com.frameglass.parser

object NameParser {
    private enum class Role { GENERIC, SEASON, SPECIALS, EXTRAS, TITLE }

    private class Folder(val role: Role, val scan: Scan?, val season: Int? = scan?.season)

    private val SHORT_NUMBER = Regex("[0-9]{1,2}")

    /** Parses a path relative to a library root. [relativePath] may hold parent folders. */
    fun parse(relativePath: String): ParsedName {
        val segments = relativePath.split('/', '\\').map(NameText::clean).filter { it.isNotEmpty() }
        val fileName = segments.lastOrNull() ?: return nameless()
        val stem = NameText.stripExtension(fileName)
        val folders = segments.dropLast(1).asReversed().mapIndexed { index, name ->
            readFolder(name, hasOuterFolder = index < segments.size - 2)
        }

        val titleFolder = folders.firstOrNull { it.role == Role.TITLE }?.scan
        val inSpecials = folders.any { it.role == Role.SPECIALS }
        val inExtras = folders.any { it.role == Role.EXTRAS }
        val inSeason = folders.any { it.role == Role.SEASON } || inSpecials || inExtras
        val folderSeason = folders.firstNotNullOfOrNull { it.season }
        val file = NameScanner.scan(
            stem,
            NameContext(
                inSeasonFolder = inSeason || titleFolder?.season != null,
                parentKey = titleFolder?.title?.let(ArabicNormalizer::key),
                phoneFile = ClipNames.isPhoneFile(fileName),
            ),
        )

        val own = file.title.takeUnless { ArabicNormalizer.key(it) in Vocabulary.GENERIC_STEMS }.orEmpty()
        val useFolderTitle = titleFolder != null && (own.isEmpty() || (inSeason && !file.hasMarker))
        val title = when {
            useFolderTitle -> titleFolder.title
            own.isNotEmpty() -> own
            file.fallbackTitle.isNotEmpty() -> file.fallbackTitle
            file.title.isNotEmpty() -> file.title
            else -> NameText.normalizeSeparators(stem).ifEmpty { fileName }
        }
        val sameTitle = titleFolder != null &&
            ArabicNormalizer.key(titleFolder.title) == ArabicNormalizer.key(title)

        val season = file.season ?: folderSeason
        val extra = file.extra || inExtras
        val seriesFolder = titleFolder?.seriesHint == true
        val parsed = ParsedName(
            title = title,
            year = file.year ?: titleFolder?.year?.takeIf { sameTitle },
            kind = when {
                extra -> NameKind.EXTRA
                file.special || inSpecials || season == 0 || titleFolder?.special == true -> NameKind.SPECIAL
                file.episode != null || season != null || file.seriesHint || seriesFolder -> NameKind.EPISODE
                else -> NameKind.MOVIE
            },
            season = season,
            episode = file.episode.takeUnless { extra },
            episodeEnd = file.episodeEnd.takeUnless { extra },
            // A low number under a season folder counts from that season, not from the show's start.
            absolute = file.absolute.takeUnless { extra || (folderSeason != null && (it ?: 0) < 100) },
            revision = file.revision,
            animeHint = file.animeHint || titleFolder?.animeHint == true,
            subtitled = file.subtitled || folders.any { it.scan?.subtitled == true },
            dubbed = file.dubbed || folders.any { it.scan?.dubbed == true },
            releaseTags = (file.tags + titleFolder?.tags.orEmpty()).toList(),
        )
        val clip = ClipNames.matches(fileName, stem, file, parsed, titleFolder != null, segments.dropLast(1))
        return if (clip) asClip(parsed, stem) else parsed
    }

    /** The numbers of a clip are counters and dates, and its title is its own name. */
    private fun asClip(parsed: ParsedName, stem: String) = parsed.copy(
        title = NameText.normalizeSeparators(stem).ifEmpty { parsed.title },
        year = null,
        kind = NameKind.MOVIE,
        season = null,
        episode = null,
        episodeEnd = null,
        absolute = null,
        revision = null,
        animeHint = false,
        clipLike = true,
    )

    private fun readFolder(name: String, hasOuterFolder: Boolean): Folder {
        val key = ArabicNormalizer.key(NameText.normalizeSeparators(name))
        when {
            key in Vocabulary.GENERIC_FOLDERS || ReleaseTags.isDisc(key.replace(" ", "")) ->
                return Folder(Role.GENERIC, null)
            key in Vocabulary.SPECIAL_FOLDERS -> return Folder(Role.SPECIALS, null, season = 0)
            key in Vocabulary.EXTRA_FOLDERS -> return Folder(Role.EXTRAS, null)
            hasOuterFolder && SHORT_NUMBER.matches(key) ->
                return Folder(Role.SEASON, null, season = key.toInt())
        }
        val scan = NameScanner.scan(name, NameContext(isFolder = true))
        val role = when {
            scan.title.isNotEmpty() -> Role.TITLE
            scan.season == 0 -> Role.SPECIALS
            scan.season != null -> Role.SEASON
            else -> Role.GENERIC
        }
        return Folder(role, scan)
    }

    private fun nameless() = ParsedName(
        title = "",
        year = null,
        kind = NameKind.MOVIE,
        season = null,
        episode = null,
        episodeEnd = null,
        absolute = null,
        revision = null,
        animeHint = false,
        subtitled = false,
        dubbed = false,
        releaseTags = emptyList(),
    )
}
