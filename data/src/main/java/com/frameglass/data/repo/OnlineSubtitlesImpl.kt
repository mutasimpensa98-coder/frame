package com.frameglass.data.repo

import com.frameglass.data.api.ApiKeyKind
import com.frameglass.data.api.OnlineSubtitles
import com.frameglass.data.api.PlaybackItem
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.api.SubtitleFailure
import com.frameglass.data.api.SubtitleResult
import com.frameglass.data.db.FrameDb
import com.frameglass.data.logic.FileIdentity
import com.frameglass.data.logic.ParsePaths
import com.frameglass.data.logic.SubtitleFacts
import com.frameglass.data.logic.SubtitleQueries
import com.frameglass.net.NetResult
import com.frameglass.net.SubtitleClient
import com.frameglass.net.SubtitleHit
import com.frameglass.net.SubtitleQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

/** F-S9 on a tap in the subtitle menu. Keys go through the same store as every other key, see [SettingsStore.setKey]. */
internal class OnlineSubtitlesImpl(
    private val db: FrameDb,
    private val settings: SettingsStore,
    private val config: NetConfigImpl,
    private val client: Lazy<SubtitleClient>,
    private val labels: SidecarLabels,
) : OnlineSubtitles {
    override val available: Flow<Boolean> = combine(
        settings.settings,
        settings.hasKey(ApiKeyKind.OPENSUBTITLES),
        settings.hasKey(ApiKeyKind.SUBDL),
        settings.hasKey(ApiKeyKind.SUBSOURCE),
    ) { s, openSubtitles, subDl, subSource -> s.onlineFeatures && (openSubtitles || subDl || subSource) }
        .distinctUntilChanged()

    override suspend fun search(item: PlaybackItem): Result<List<SubtitleResult>> = withContext(Dispatchers.IO) {
        if (item.isPrivate) return@withContext Result.failure(PrivateItemException())
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

    /** The parser's title goes out, as for every lookup: the display name may be Arabic and the provider's English. */
    private suspend fun queryFor(item: PlaybackItem): SubtitleQuery {
        val opts = settings.current()
        val row = item.fileRowId?.let { db.scan().file(it) }
        val title = (item.titleId ?: row?.titleId)?.let { db.titles().title(it) }
        val local = if (row == null) File(item.uri).takeIf { it.isAbsolute && it.isFile } else null
        val hash = row?.osHash ?: local?.takeIf { opts.sendFileHash }?.let { runCatching { FileIdentity.compute(it).osHash }.getOrNull() }
        val facts = SubtitleFacts(
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
        return SubtitleQueries.build(facts, opts)
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
        val key = item.fileRowId?.let { db.scan().file(it)?.fileId } ?: item.playableKey
        val folder = labels.folderOf(key).apply { mkdirs() }
        when (val saved = client.value.download(hit, item.season, item.episode, folder)) {
            is NetResult.Failure -> Result.failure(SubtitleFailure(saved.reason))
            is NetResult.Ok -> Result.success(labels.online(saved.value, result.language, result.provider))
        }
    }
}

/** Private items never reach a subtitle provider. */
internal class PrivateItemException : IllegalStateException()
