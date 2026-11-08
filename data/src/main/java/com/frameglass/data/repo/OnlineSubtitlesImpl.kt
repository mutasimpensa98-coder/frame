package com.frameglass.data.repo

import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.AutoSubtitleMode
import com.frameglass.data.api.AutoSubtitleOutcome
import com.frameglass.data.api.AutoSubtitleSource
import com.frameglass.data.api.MatchState
import com.frameglass.data.api.OnlineSubtitles
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.api.SubtitleFailure
import com.frameglass.data.api.SubtitleResult
import com.frameglass.data.api.SubtitleSiteSource
import com.frameglass.data.api.SubtitleSites
import com.frameglass.data.db.FrameDb
import com.frameglass.data.db.NameKinds
import com.frameglass.data.logic.AutoSubtitlePick
import com.frameglass.data.logic.ClipRules
import com.frameglass.data.logic.FileIdentity
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.SubtitleFacts
import com.frameglass.data.logic.SubtitleQueries
import com.frameglass.data.logic.SubtitleSiteList
import com.frameglass.net.NetResult
import com.frameglass.net.SubtitleClient
import com.frameglass.net.SubtitleHit
import com.frameglass.net.SubtitleQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads tried for one automatic search before it gives up on this file. */
private const val MAX_AUTO_DOWNLOADS = 3

/**
 * F-S9 on a tap in the subtitle menu, plus the automatic download of the waiver of 2026-11-08. Keys go through the
 * same store as every other key, see [SettingsStore.setKey].
 */
internal class OnlineSubtitlesImpl(
    private val db: FrameDb,
    private val settings: SettingsStore,
    private val config: NetConfigImpl,
    private val client: Lazy<SubtitleClient>,
    private val labels: SidecarLabels,
    private val tried: AutoTried,
) : OnlineSubtitles, SubtitleSiteSource, AutoSubtitleSource {
    override val available: Flow<Boolean> = settings.settings.map { it.onlineFeatures }.distinctUntilChanged()

    override val sites: Flow<SubtitleSites> = combine(
        settings.hasKey(ApiKeyKind.OPENSUBTITLES),
        settings.hasKey(ApiKeyKind.SUBDL),
        settings.hasKey(ApiKeyKind.SUBSOURCE),
    ) { openSubtitles, subDl, subSource ->
        val saved = buildSet {
            if (openSubtitles) add(ApiKeyKind.OPENSUBTITLES)
            if (subDl) add(ApiKeyKind.SUBDL)
            if (subSource) add(ApiKeyKind.SUBSOURCE)
        }
        SubtitleSiteList.of(client.value.sites, saved)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun search(item: PlaybackItem): Result<List<SubtitleResult>> = withContext(Dispatchers.IO) {
        if (item.isPrivate) return@withContext Result.failure(PrivateItemException())
        // A site that needs no key would otherwise get a home video's name on every tap.
        if (isClip(item)) return@withContext Result.success(emptyList())
        config.awaitLoaded()
        when (val found = client.value.search(queryFor(item))) {
            is NetResult.Failure -> Result.failure(SubtitleFailure(found.reason))
            is NetResult.Ok -> Result.success(found.value.map(::resultOf))
        }
    }

    private fun resultOf(hit: SubtitleHit) = SubtitleResult(
        token = hit.id,
        provider = hit.provider,
        language = hit.language,
        release = hit.release,
        hashMatch = hit.hashMatch,
        downloads = hit.downloads,
        format = hit.format,
        seasonPack = hit.seasonPack,
    )

    /** The same test the library uses, so a clip that Fix match turned into a title searches like any other. */
    private suspend fun isClip(item: PlaybackItem): Boolean {
        val titleId = item.titleId ?: item.fileRowId?.let { db.scan().file(it)?.titleId } ?: return false
        val title = db.titles().title(titleId) ?: return false
        val files = db.scan().filesOfTitle(titleId)
        val matched = title.matchState == MatchState.AUTO || title.matchState == MatchState.MANUAL
        return ClipRules.isClip(
            kind = title.kind,
            matched = matched,
            clipName = files.any { it.nameKind == NameKinds.CLIP },
            durationMs = files.maxOfOrNull { it.durationMs } ?: 0,
            plainNumber = files.all(ClipRules::plainNumber),
        )
    }

    /** The parser's title goes out, as for every lookup: the display name may be Arabic and the provider's English. */
    private suspend fun queryFor(item: PlaybackItem): SubtitleQuery {
        val opts = settings.current()
        return SubtitleQueries.build(factsFor(item, withHash = opts.sendFileHash), opts)
    }

    /** The hash is read from the file only when it may be sent, because reading it costs two 64 KiB reads. */
    private suspend fun factsFor(item: PlaybackItem, withHash: Boolean): SubtitleFacts {
        val row = item.fileRowId?.let { db.scan().file(it) }
        val title = (item.titleId ?: row?.titleId)?.let { db.titles().title(it) }
        val local = if (row == null) File(item.uri).takeIf { it.isAbsolute && it.isFile } else null
        val hash = if (withHash) row?.osHash ?: local?.let { runCatching { FileIdentity.compute(it).osHash }.getOrNull() } else null
        return SubtitleFacts(
            title = title?.localName ?: item.title,
            year = title?.year ?: title?.localYear,
            season = item.season,
            episode = item.episode,
            tmdbId = title?.tmdbId,
            imdbId = title?.imdbId,
            osHash = hash,
            fileSize = row?.size ?: local?.length(),
            releaseName = (row?.relPath ?: local?.name)?.let(ParsePaths::baseName),
        )
    }

    private suspend fun fileKeyOf(item: PlaybackItem): String =
        item.fileRowId?.let { db.scan().file(it)?.fileId } ?: item.playableKey

    override suspend fun autoFetch(item: PlaybackItem, languages: List<String>): AutoSubtitleOutcome =
        withContext(Dispatchers.IO) {
            try {
                fetchOnce(item, languages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                AutoSubtitleOutcome.Skipped
            }
        }

    /**
     * Once per file. A network failure, a used-up limit or a missing key leaves the file untried, so a later play
     * tries again; an answer, even an empty one, is remembered.
     */
    private suspend fun fetchOnce(item: PlaybackItem, languages: List<String>): AutoSubtitleOutcome {
        if (item.isPrivate || languages.isEmpty()) return AutoSubtitleOutcome.Skipped
        config.awaitLoaded()
        val opts = settings.current()
        if (!opts.onlineFeatures || opts.autoSubtitles == AutoSubtitleMode.OFF) return AutoSubtitleOutcome.Skipped
        val key = fileKeyOf(item)
        if (tried.wasTried(key) || isClip(item)) return AutoSubtitleOutcome.Skipped
        val facts = factsFor(item, withHash = false)
        val hits = when (val found = client.value.search(SubtitleQueries.automatic(facts, languages))) {
            is NetResult.Failure -> {
                if (found.reason != NetResult.Reason.NOT_FOUND) return AutoSubtitleOutcome.Skipped
                emptyList()
            }
            is NetResult.Ok -> found.value
        }
        val tapOnly = client.value.sites.filter { it.tapOnly }.map { it.name }.toSet()
        val ranked = AutoSubtitlePick.ranked(hits.map(::resultOf), facts.releaseName, languages, tapOnly)
        for (candidate in ranked.take(MAX_AUTO_DOWNLOADS)) {
            val saved = download(item, candidate)
            saved.getOrNull()?.let { sidecar ->
                tried.markTried(key)
                return AutoSubtitleOutcome.Loaded(sidecar, candidate)
            }
            val reason = (saved.exceptionOrNull() as? SubtitleFailure)?.reason
            if (reason != NetResult.Reason.NOT_FOUND && reason != NetResult.Reason.BAD_RESPONSE) return AutoSubtitleOutcome.Skipped
        }
        tried.markTried(key)
        return AutoSubtitleOutcome.NothingFound
    }

    override suspend fun download(item: PlaybackItem, result: SubtitleResult): Result<SidecarSubtitle> = withContext(Dispatchers.IO) {
        if (item.isPrivate) return@withContext Result.failure(PrivateItemException())
        config.awaitLoaded()
        val hit = SubtitleHit(
            provider = result.provider,
            id = result.token,
            language = result.language,
            release = result.release,
            hashMatch = result.hashMatch,
            downloads = result.downloads,
            format = result.format,
            seasonPack = result.seasonPack,
        )
        val folder = labels.folderOf(fileKeyOf(item)).apply { mkdirs() }
        when (val saved = client.value.download(hit, item.season, item.episode, folder)) {
            is NetResult.Failure -> Result.failure(SubtitleFailure(saved.reason))
            is NetResult.Ok -> Result.success(labels.online(saved.value, result.language, result.provider))
        }
    }
}

/** Private items never reach a subtitle provider. */
internal class PrivateItemException : IllegalStateException()
