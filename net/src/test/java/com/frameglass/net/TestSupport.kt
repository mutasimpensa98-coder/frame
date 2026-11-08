package com.frameglass.net

import com.frameglass.parser.NameKind
import com.frameglass.parser.ParsedName
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/* Shape-only dummies, built at runtime so no key-like literal sits in a fixture (R4). */
internal val V3_KEY = "ab".repeat(16)
internal val V4_TOKEN = listOf("eyJhbGciOiJub25lIn0", "c".repeat(60), "d".repeat(40)).joinToString(".")
internal val OMDB_KEY = "ef".repeat(4)
internal val OS_KEY = "os".repeat(8)
internal val SUBDL_KEY = "sd".repeat(8)
internal val SUBSOURCE_KEY = "ss".repeat(8)

internal const val TMDB = "api.themoviedb.org/3"
internal const val ANILIST = "graphql.anilist.co/"

internal class FakeConfig(
    var tmdb: String? = V3_KEY,
    var omdb: String? = null,
    override var tmdbBaseUrl: String = DEFAULT_TMDB_BASE,
    override var onlineEnabled: Boolean = true,
    var openSubtitles: String? = null,
    var subDl: String? = null,
    var subSource: String? = null,
    override var tmdbEnabled: Boolean = true,
    override var imdbEnabled: Boolean = true,
) : NetConfig, ServiceSwitches {
    override fun key(kind: ApiKey): String? = when (kind) {
        ApiKey.TMDB -> tmdb
        ApiKey.OMDB -> omdb
        ApiKey.OPENSUBTITLES -> openSubtitles
        ApiKey.SUBDL -> subDl
        ApiKey.SUBSOURCE -> subSource
    }
}

/** [bytes] replaces [body] for a binary reply such as a zip. */
internal class Reply(
    val code: Int = 200,
    val body: String = "{}",
    val headers: Map<String, String> = emptyMap(),
    val bytes: ByteArray? = null,
)

/** Sits at the tail of the real client, after the guard, so no socket is ever opened. */
internal class FakeServer : Interceptor {
    val requests = CopyOnWriteArrayList<Request>()
    private val routes = ConcurrentHashMap<String, MutableList<Reply>>()

    /** Runs on OkHttp's own threads, so a test can hold a call open while others start. */
    @Volatile
    var onRequest: (Request) -> Unit = {}

    /** Answers by query, which the routes cannot see. Null falls through to the routes. */
    @Volatile
    var answer: (Request) -> Reply? = { null }

    /** Replies are served in order and the last one repeats. An unknown route is a 404. */
    fun on(hostAndPath: String, vararg replies: Reply) {
        routes[hostAndPath] = replies.toMutableList()
    }

    fun json(hostAndPath: String, body: String) = on(hostAndPath, Reply(body = body))

    fun hits(hostAndPath: String): List<Request> = requests.filter { keyOf(it) == hostAndPath }

    fun bodyOf(request: Request): String = Buffer().also { request.body?.writeTo(it) }.readUtf8()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        onRequest(request)
        val queue = routes[keyOf(request)]
        val reply = answer(request) ?: when {
            queue == null && "/search/" in request.url.encodedPath -> Reply(body = page())
            queue == null -> Reply(404, """{"status_message":"not found"}""")
            else -> next(queue)
        }
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(reply.code)
            .message("fake")
            .body(reply.bytes?.toResponseBody("application/zip".toMediaType()) ?: reply.body.toResponseBody("application/json".toMediaType()))
        reply.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    /** Subtitle searches run in parallel, so two calls can reach one queue at the same moment. */
    private fun next(queue: MutableList<Reply>): Reply = synchronized(queue) {
        if (queue.size > 1) queue.removeAt(0) else queue[0]
    }

    private fun keyOf(request: Request): String = request.url.host + request.url.encodedPath
}

/**
 * [keyless] adds the sites that need no key. Off by default, so a test of one keyed provider sees only that
 * provider's requests.
 */
internal class Rig(
    val config: FakeConfig = FakeConfig(),
    val dir: File = File(System.getProperty("java.io.tmpdir"), "fg-net-${System.nanoTime()}"),
    keyless: Boolean = false,
) {
    val server = FakeServer()
    val pauses = CopyOnWriteArrayList<Long>()
    var now = 1_800_000_000_000L
    var wifi = true
    private val gate = RateGate { now }
    val okHttp = newFrameClient(NetPolicy { config }, server)
    val http = Http(okHttp, gate) { waited ->
        pauses += waited
        now += waited
    }
    val mappings = AnimeMappings(dir, http, config, { wifi }, { now })
    val client = FrameMetadataClient(config, http, mappings)
    val providers = subtitleProviders(http, config).filter { keyless || it.keyKind != null }
    val subtitles = FrameSubtitleClient(config, http, File(dir, "scratch"), providers = providers)

    fun clean() {
        dir.deleteRecursively()
    }
}

internal fun <T> blocking(block: suspend () -> T): T = runBlocking { block() }

internal fun <T> NetResult<T>.value(): T = when (this) {
    is NetResult.Ok -> value
    is NetResult.Failure -> throw AssertionError("expected Ok, got $reason ($message)")
}

internal fun parsed(
    title: String,
    year: Int? = null,
    kind: NameKind = NameKind.EPISODE,
    season: Int? = null,
    episode: Int? = null,
    animeHint: Boolean = false,
    tags: List<String> = emptyList(),
) = ParsedName(
    title = title,
    year = year,
    kind = kind,
    season = season,
    episode = episode,
    episodeEnd = null,
    absolute = null,
    revision = null,
    animeHint = animeHint,
    subtitled = false,
    dubbed = false,
    releaseTags = tags,
)

internal fun tvRow(
    id: Long,
    name: String,
    year: Int = 2024,
    original: String = name,
    genres: String = "18",
    country: String = "US",
    language: String = "en",
) = """{"id":$id,"name":"$name","original_name":"$original","first_air_date":"$year-01-05",""" +
    """"poster_path":"/p$id.jpg","overview":"plot $id","genre_ids":[$genres],""" +
    """"origin_country":["$country"],"original_language":"$language"}"""

internal fun movieRow(id: Long, title: String, year: Int = 2024, original: String = title, genres: String = "18") =
    """{"id":$id,"title":"$title","original_title":"$original","release_date":"$year-03-01",""" +
        """"poster_path":"/m$id.jpg","overview":"plot $id","genre_ids":[$genres],"original_language":"en"}"""

internal fun multiRow(row: String, mediaType: String) = row.replaceFirst("{", """{"media_type":"$mediaType",""")

internal fun page(vararg rows: String) = """{"results":[${rows.joinToString(",")}]}"""
