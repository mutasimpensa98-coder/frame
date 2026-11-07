package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Longer than one slow call, shorter than a provider's rate-limit wait. search answers once, so a
 * provider that stalls must not hold back the others' results.
 */
private const val PROVIDER_DEADLINE_MS = 30_000L

private const val ARABIC = "ar"
private const val ENGLISH_TAG = "en"
private const val MAX_ID_IN_NAME = 80

private val NOT_A_NAME_CHAR = Regex("[^A-Za-z0-9]+")
private val RELEASE_WORD = Regex("[a-z0-9]+")

internal class FrameSubtitleClient(
    private val config: NetConfig,
    http: Http,
    private val scratchDir: File,
    private val deadlineMs: Long = PROVIDER_DEADLINE_MS,
) : SubtitleClient {
    private val providers: List<SubtitleProvider> = listOf(
        OpenSubtitlesApi(http, config),
        SubDlApi(http, config),
        SubSourceApi(http, config),
    )

    override val configuredProviders: List<ApiKey>
        get() = providers.map(SubtitleProvider::keyKind).filter { config.keyOf(it) != null }

    override suspend fun search(query: SubtitleQuery): NetResult<List<SubtitleHit>> = online {
        val active = providers.filter { config.keyOf(it.keyKind) != null }
        if (active.isEmpty()) return@online Failure(Reason.NO_KEY)
        val languages = SubtitleRanking.languages(query.languages)
        val results = coroutineScope {
            active.map { provider -> async { withDeadline { provider.search(query, languages) } } }.awaitAll()
        }
        val found = results.mapNotNull { it.valueOrNull() }
        if (found.isEmpty()) {
            results.first()
        } else {
            Ok(SubtitleRanking.rank(found.flatten(), query, languages, providers.map(SubtitleProvider::name)))
        }
    }

    private suspend fun withDeadline(
        block: suspend () -> NetResult<List<SubtitleHit>>,
    ): NetResult<List<SubtitleHit>> = softly {
        withTimeoutOrNull(deadlineMs) { block() } ?: Failure(Reason.NETWORK, "deadline")
    }

    override suspend fun download(hit: SubtitleHit, season: Int?, episode: Int?, targetDir: File): NetResult<File> =
        online {
            val provider = providers.firstOrNull { it.name == hit.provider }
                ?: return@online Failure(Reason.BAD_RESPONSE, "unknown provider")
            if (config.keyOf(provider.keyKind) == null) return@online Failure(Reason.NO_KEY)
            val stem = stemOf(hit, season, episode)
            withContext(Dispatchers.IO) { SubtitleArchive.saved(targetDir, stem) }?.let { return@online Ok(it) }

            val payload = File(scratchDir, "$stem.${System.nanoTime()}.download")
            try {
                when (val fetched = provider.fetch(hit, payload)) {
                    is Failure -> fetched
                    is Ok -> withContext(Dispatchers.IO) {
                        val ask = SubtitleArchive.Ask(season, episode, hit.language, hit.seasonPack)
                        SubtitleArchive.place(payload, ask, targetDir, stem)
                    }
                }
            } finally {
                payload.delete()
            }
        }

    private suspend fun <T> online(block: suspend () -> NetResult<T>): NetResult<T> =
        if (config.onlineEnabled) softly(block) else Failure(Reason.OFFLINE_SETTING)
}

/** The name is built only from safe characters, so a provider's ID cannot reach outside the folder. */
internal fun stemOf(hit: SubtitleHit, season: Int?, episode: Int?): String {
    val id = hit.id.replace(NOT_A_NAME_CHAR, "-").trim('-').takeLast(MAX_ID_IN_NAME)
    val slot = if (episode == null) "" else ".s${season ?: 0}e$episode"
    val language = hit.language.replace(NOT_A_NAME_CHAR, "-")
    return "${hit.provider.lowercase()}-$id$slot.$language"
}

internal object SubtitleRanking {
    /** The tags to search, Arabic first and English second whatever order they came in. */
    fun languages(asked: List<String>): List<String> {
        val tags = asked.mapNotNull(::tagOf).distinct().ifEmpty { listOf(ARABIC, ENGLISH_TAG) }
        return tags.sortedBy { if (it == ARABIC) 0 else if (it == ENGLISH_TAG) 1 else 2 }
    }

    private fun tagOf(raw: String): String? {
        val text = raw.trim().lowercase().replace('_', '-')
        return when {
            text.isEmpty() -> null
            text == "ara" || text == "arabic" || text.startsWith("ar-") -> ARABIC
            text == "eng" || text == "english" || text.startsWith("en-") -> ENGLISH_TAG
            else -> text
        }
    }

    /**
     * F-S9: a hash match first, then language. Below that a release close to the file keeps its timing,
     * a single file beats a pack, and downloads then the default provider order settle the rest.
     */
    fun rank(
        hits: List<SubtitleHit>,
        query: SubtitleQuery,
        languages: List<String>,
        providerOrder: List<String>,
    ): List<SubtitleHit> {
        val fileWords = query.releaseName?.let(::wordsOf).orEmpty()
        val closeness = hits.associateWith { hit -> wordsOf(hit.release).count { it in fileWords } }
        return hits.distinctBy { it.provider to it.id }.sortedWith(
            compareByDescending<SubtitleHit> { it.hashMatch }
                .thenBy { languages.indexOf(it.language) }
                .thenByDescending { closeness[it] }
                .thenBy { it.seasonPack }
                .thenByDescending { it.downloads ?: -1 }
                .thenBy { providerOrder.indexOf(it.provider) },
        )
    }

    private fun wordsOf(release: String): Set<String> =
        RELEASE_WORD.findAll(release.lowercase()).map { it.value }.toSet()
}
