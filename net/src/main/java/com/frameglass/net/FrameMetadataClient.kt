package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import com.frameglass.parser.NameKind
import com.frameglass.parser.ParsedName
import java.io.File

private const val MAX_CHOICES = 3

/** F-M1: alternative titles are spent on this many results per search. */
private const val ALT_TITLE_RESULTS = 3

/** A title credits sheet does not need the hundreds of names TMDB can return. */
private const val MAX_CAST = 20
private val CREW_JOBS = setOf("Director", "Writer", "Screenplay", "Story", "Creator")
private const val CREATOR_JOB = "Creator"

/** TMDB fills untranslated episodes with these, which are worth less than a real English title. */
private val GENERIC_EPISODE_NAME = Regex("""^(episode|ep\.?|الحلقة|حلقة)\s*\d+$""", RegexOption.IGNORE_CASE)

internal class FrameMetadataClient(
    private val config: NetConfig,
    private val http: Http,
    private val mappings: AnimeMappings,
) : MetadataClient {
    private val tmdb = TmdbApi(http, config)
    private val aniList = AniListApi(http)
    private val skip = SkipApi(http)
    private val omdb = OmdbApi(http, config)

    override suspend fun match(name: ParsedName): NetResult<MatchOutcome> = soft {
        when {
            name.title.isBlank() -> Ok(MatchOutcome(auto = null, choices = emptyList()))
            !tmdb.hasKey() -> Failure(Reason.NO_KEY)
            name.animeHint -> matchAnimeFirst(name)
            else -> runPlan(SearchPlan.of(name), name, known = null)
        }
    }

    /** F-M9: AniList names the work, anibridge gives the TMDB show, TMDB supplies display data. */
    private suspend fun matchAnimeFirst(name: ParsedName): NetResult<MatchOutcome> {
        val hit = aniList.identify(listOf(name.title), name.year).valueOrNull()
            ?: return runPlan(SearchPlan.of(name), name, known = null)
        val media = hit.media
        val ids = AnimeIds(media.id, media.idMal)
        if (!media.isMovie) {
            mappings.refreshIfDue()
            val show = mappings.index().showOf(media.id, media.idMal)
            if (show != null) {
                when (val found = tmdb.details(MetaKind.TV, show, ENGLISH, full = false)) {
                    is Ok -> {
                        val candidate = found.value.candidate(MetaKind.TV, hit.score, isAnime = true)
                        return Ok(MatchOutcome(candidate, emptyList(), ids.anilist, ids.mal))
                    }
                    is Failure -> if (found.reason != Reason.NOT_FOUND) return found
                }
            }
        }
        return runPlan(SearchPlan.ofAnime(name, media), name, ids)
    }

    private suspend fun runPlan(plan: SearchPlan, name: ParsedName, known: AnimeIds?): NetResult<MatchOutcome> {
        var pool = emptyList<MatchCandidate>()
        for (pass in plan.passes) {
            when (val found = tmdb.search(pass.kind, pass.text, pass.year)) {
                is Failure -> return found
                is Ok -> pool = (pool + rank(found.value, pass.kind, plan))
                    .sortedByDescending(MatchCandidate::score)
                    .distinctBy { it.kind to it.tmdbId }
            }
            Scoring.pick(pool, MatchCandidate::score)?.let { best ->
                val ids = known ?: if (best.isAnime) animeIdsOf(best, name) else null
                return Ok(MatchOutcome(best, emptyList(), ids?.anilist, ids?.mal))
            }
        }
        return Ok(MatchOutcome(null, pool.take(MAX_CHOICES), known?.anilist, known?.mal))
    }

    /** Rescores the best few with their alternative titles. A failed lookup only loses that bonus. */
    private suspend fun rank(items: List<TmdbItem>, kind: MetaKind, plan: SearchPlan): List<MatchCandidate> {
        val ranked = items.sortedByDescending { plan.score(it.names, it.year) }
        return ranked.mapIndexed { index, item ->
            val alternatives = if (index < ALT_TITLE_RESULTS) {
                tmdb.alternativeTitles(kind, item.id).valueOrNull().orEmpty()
            } else {
                emptyList()
            }
            item.candidate(kind, plan.score(item.names + alternatives, item.year))
        }
    }

    /** F-M9, TMDB first: a genre 16, origin JP result runs AniList. Losing that only loses AniSkip. */
    private suspend fun animeIdsOf(best: MatchCandidate, name: ParsedName): AnimeIds? {
        val names = listOfNotNull(name.title, best.title, best.originalTitle)
        aniList.identify(names, best.year).valueOrNull()?.let { return AnimeIds(it.media.id, it.media.idMal) }
        if (best.kind != MetaKind.TV) return null
        return mappings.index().idsOf(best.tmdbId, name.season ?: 1)
    }

    override suspend fun searchManual(query: String, language: String): NetResult<List<MatchCandidate>> = soft {
        tmdb.searchMulti(query, language).map { items ->
            items.mapNotNull { item ->
                val kind = when (item.mediaType) {
                    "movie" -> MetaKind.MOVIE
                    "tv" -> MetaKind.TV
                    else -> return@mapNotNull null
                }
                item.candidate(kind, Scoring.score(listOf(query), item.names, null, item.year))
            }
        }
    }

    override suspend fun details(kind: MetaKind, tmdbId: Long, language: String): NetResult<TitleMeta> = soft {
        when (val primary = tmdb.details(kind, tmdbId, language, full = true)) {
            is Failure -> primary
            is Ok -> {
                val english = if (isEnglish(language)) {
                    null
                } else {
                    tmdb.details(kind, tmdbId, ENGLISH, full = false).valueOrNull()
                }
                Ok(titleMeta(kind, tmdbId, primary.value, english, language))
            }
        }
    }

    private fun titleMeta(
        kind: MetaKind,
        tmdbId: Long,
        primary: TmdbDetails,
        english: TmdbDetails?,
        language: String,
    ) = TitleMeta(
        kind = kind,
        tmdbId = tmdbId,
        imdbId = (primary.imdbId ?: primary.externalIds?.imdbId)?.takeIf(String::isNotBlank),
        title = displayTitle(primary, english, language),
        originalTitle = primary.original,
        year = primary.year ?: english?.year,
        overview = primary.overview.orBlankTo(english?.overview),
        runtimeMinutes = primary.runtimeMinutes ?: english?.runtimeMinutes,
        genres = primary.genres.mapNotNull(TmdbNamed::name).ifEmpty {
            english?.genres?.mapNotNull(TmdbNamed::name).orEmpty()
        },
        tmdbRating = primary.voteAverage?.takeIf { it > 0.0 },
        posterUrl = TmdbImages.poster(primary.posterPath ?: english?.posterPath),
        backdropUrl = TmdbImages.backdrop(primary.backdropPath ?: english?.backdropPath),
        logoUrl = TmdbImages.logo(pickLogo(primary.images?.logos.orEmpty(), language)),
        cast = primary.credits?.cast.orEmpty().sortedBy(TmdbCast::order).take(MAX_CAST).map { person ->
            PersonMeta(
                tmdbId = person.id,
                name = person.name.orEmpty(),
                character = person.character?.takeIf(String::isNotBlank),
                photoUrl = TmdbImages.profile(person.profilePath),
                order = person.order,
            )
        },
        crew = crewOf(primary),
        collectionId = primary.collection?.id,
        collectionName = primary.collection?.name.orBlankTo(english?.collection?.name),
        seasons = primary.seasons.map(TmdbSeasonRef::number).sorted(),
    )

    /** With no translation TMDB returns the original title, so English goes first for those. */
    private fun displayTitle(primary: TmdbDetails, english: TmdbDetails?, language: String): String {
        val localized = primary.displayTitle
        val englishTitle = english?.displayTitle
        val untranslated = localized == primary.original && primary.originalLanguage != languageCode(language)
        return when {
            localized == null -> englishTitle ?: primary.original.orEmpty()
            untranslated && englishTitle != null -> englishTitle
            else -> localized
        }
    }

    private fun crewOf(details: TmdbDetails): List<CrewMeta> {
        val creators = details.createdBy.mapNotNull { it.name }.map { CrewMeta(CREATOR_JOB, it) }
        val credited = details.credits?.crew.orEmpty()
            .filter { it.job in CREW_JOBS && !it.name.isNullOrBlank() }
            .map { CrewMeta(it.job.orEmpty(), it.name.orEmpty()) }
        return (creators + credited).distinct()
    }

    override suspend fun season(tmdbId: Long, season: Int, language: String): NetResult<List<EpisodeMeta>> = soft {
        when (val primary = tmdb.season(tmdbId, season, language)) {
            is Failure -> primary
            is Ok -> {
                val rows = primary.value
                val needsEnglish = !isEnglish(language) && rows.any { it.overview.isNullOrBlank() || !it.hasRealName }
                val english = if (needsEnglish) {
                    tmdb.season(tmdbId, season, ENGLISH).valueOrNull().orEmpty().associateBy(TmdbEpisode::number)
                } else {
                    emptyMap()
                }
                Ok(rows.map { episodeMeta(season, it, english[it.number]) })
            }
        }
    }

    private fun episodeMeta(season: Int, episode: TmdbEpisode, english: TmdbEpisode?) = EpisodeMeta(
        season = season,
        number = episode.number,
        title = episode.name.takeIf { episode.hasRealName } ?: english?.name.orBlankTo(episode.name),
        overview = episode.overview.orBlankTo(english?.overview),
        stillUrl = TmdbImages.still(episode.stillPath ?: english?.stillPath),
        runtimeMinutes = (episode.runtime ?: english?.runtime)?.takeIf { it > 0 },
        airDate = episode.airDate.orBlankTo(english?.airDate),
    )

    private val TmdbEpisode.hasRealName: Boolean
        get() = !name.isNullOrBlank() && !GENERIC_EPISODE_NAME.matches(name.trim())

    override suspend fun imdbRating(imdbId: String): NetResult<Double> = soft { omdb.rating(imdbId) }

    override suspend fun download(url: String, target: File): NetResult<Unit> = soft {
        http.download(url, target, "download")
    }

    override suspend fun animeEpisode(anilistId: Long, malId: Long?, absoluteOrEpisode: Int): NetResult<AnimeEpisodeMap> =
        soft {
            mappings.refreshIfDue()
            val index = mappings.index()
            index.toTmdb(anilistId, malId, absoluteOrEpisode)?.let { return@soft Ok(it) }
            val show = index.showOf(anilistId, malId) ?: return@soft Failure(Reason.NOT_FOUND, "no mapping")
            tmdb.absoluteEpisode(show, absoluteOrEpisode)
        }

    /**
     * F-S4 order: AniSkip, then TheIntroDB. A transient failure is reported only when nothing was
     * found, so :data can tell "try later" from an empty answer.
     */
    override suspend fun skipSegments(
        malId: Long?,
        tmdbId: Long?,
        season: Int?,
        episode: Int?,
        episodeLengthMs: Long,
    ): NetResult<List<NetSegment>> = soft {
        val exact = if (tmdbId != null && season != null && episode != null) {
            mappings.index().toMal(tmdbId, season, episode)
        } else {
            null
        }
        val mal = exact?.malId ?: malId
        val malEpisode = exact?.episode ?: episode
        var failure: Failure? = null
        if (mal != null && malEpisode != null) {
            when (val found = skip.aniSkip(mal, malEpisode, episodeLengthMs)) {
                is Ok -> if (found.value.isNotEmpty()) return@soft found
                is Failure -> failure = found
            }
        }
        if (tmdbId != null && (season == null) == (episode == null)) {
            when (val found = skip.theIntroDb(tmdbId, season, episode, episodeLengthMs)) {
                is Ok -> if (found.value.isNotEmpty()) return@soft found
                is Failure -> failure = failure ?: found
            }
        }
        failure ?: Ok(emptyList())
    }

    private suspend fun <T> soft(block: suspend () -> NetResult<T>): NetResult<T> =
        if (config.onlineEnabled) softly(block) else Failure(Reason.OFFLINE_SETTING)

    private fun String?.orBlankTo(other: String?): String? = this?.takeIf(String::isNotBlank) ?: other?.takeIf(String::isNotBlank)
}

/** One title's search: what TMDB is asked, in order, and what results are compared against. */
private class SearchPlan(
    private val queries: List<String>,
    private val year: Int?,
    val passes: List<Pass>,
) {
    class Pass(val kind: MetaKind, val text: String, val year: Int?)

    fun score(names: List<String>, candidateYear: Int?): Double = Scoring.score(queries, names, year, candidateYear)

    companion object {
        fun of(name: ParsedName): SearchPlan {
            val kind = if (name.kind == NameKind.MOVIE) MetaKind.MOVIE else MetaKind.TV
            return SearchPlan(listOf(name.title), name.year, passes(kind, listOf(name.title), name.year))
        }

        /** AniList's own year settles two same-named works, such as an anime and its live remake. */
        fun ofAnime(name: ParsedName, media: AniMedia): SearchPlan {
            val kind = if (media.isMovie) MetaKind.MOVIE else MetaKind.TV
            val year = name.year ?: media.startYear
            val texts = (media.searchNames + name.title).filter(String::isNotBlank).distinct()
            return SearchPlan(listOf(name.title) + media.names, year, passes(kind, texts, year))
        }

        /**
         * A movie year filter is exact on generic titles but can hide a mis-dated file, so the plain
         * search follows it. The other kind runs last, because the parser can guess the kind wrong.
         */
        private fun passes(kind: MetaKind, texts: List<String>, year: Int?): List<Pass> = buildList {
            if (kind == MetaKind.MOVIE && year != null) add(Pass(kind, texts.first(), year))
            texts.forEach { add(Pass(kind, it, null)) }
            add(Pass(if (kind == MetaKind.MOVIE) MetaKind.TV else MetaKind.MOVIE, texts.first(), null))
        }
    }
}
