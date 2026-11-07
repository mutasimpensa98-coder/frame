package com.frameglass.net

import com.frameglass.net.NetResult.Failure
import com.frameglass.net.NetResult.Ok
import com.frameglass.net.NetResult.Reason
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resumeWithException

internal const val CONNECT_TIMEOUT_SECONDS = 8L
internal const val READ_TIMEOUT_SECONDS = 15L

/** Longest a call sleeps for a rate limit before it gives up with [Reason.RATE_LIMIT]. */
internal const val MAX_RATE_WAIT_MS = 65_000L

private const val MAX_REDIRECTS = 5
private const val MAX_ATTEMPTS = 2
private const val DEFAULT_BACKOFF_MS = 30_000L
private const val FALLBACK_WINDOW_MS = 60_000L

/** Above this a reset header is epoch seconds (AniList), below it a delta (AniSkip, TheIntroDB). */
private const val EPOCH_SECONDS_FLOOR = 1_000_000_000L

internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

internal val NetJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Brief section 4.9, as exact names. Hosts were read from live responses on 2026-11-07:
 * anibridge releases go github.com -> release-assets.githubusercontent.com.
 * OpenSubtitles' download links are www.opensubtitles.com/download/.../subfile/..., and
 * dl.opensubtitles.com answers 301 to the same path. dl.subdl.com serves its zips with no redirect.
 * SubSource serves downloads from its API host, per subsource.net/api-docs.
 * The custom TMDB base URL is added at check time because it can change in Settings.
 * OMDb's poster links point at [OMDB_POSTER_HOST]. That host was not read from a live reply [UNVERIFIED].
 */
internal val ALLOWED_HOSTS: Set<String> = setOf(
    "api.themoviedb.org",
    "image.tmdb.org",
    "graphql.anilist.co",
    "github.com",
    "release-assets.githubusercontent.com",
    "objects.githubusercontent.com",
    "api.aniskip.com",
    "api.theintrodb.org",
    "api.opensubtitles.com",
    "www.opensubtitles.com",
    "dl.opensubtitles.com",
    "api.subdl.com",
    "dl.subdl.com",
    "api.subsource.net",
    "www.omdbapi.com",
    OMDB_POSTER_HOST,
)

/** Hosts that serve only to reach a TMDB match. The TMDB switch closes all of them, and the custom base URL. */
private val TMDB_HOSTS: Set<String> = setOf(
    "api.themoviedb.org",
    "image.tmdb.org",
    "graphql.anilist.co",
    "github.com",
    "release-assets.githubusercontent.com",
    "objects.githubusercontent.com",
)

private val OMDB_HOSTS: Set<String> = setOf("www.omdbapi.com", OMDB_POSTER_HOST)

/** Dropped on a redirect to another host, including hosts on the allow-list. */
private val CREDENTIAL_HEADERS = listOf("Authorization", "Api-Key", "X-API-Key")

/** Must stay an [IOException]: anything else thrown from an interceptor kills the OkHttp thread. */
internal class BlockedRequestException(detail: String) : IOException(detail)

internal class OnlineDisabledException : IOException("online features are off")

/** The message is a short label for [Failure.message]: "tmdb" or "omdb", then "switched off". */
internal class ServiceOffException(service: String) : IOException("$service switched off")

private val NetConfig.tmdbOn: Boolean get() = (this as? ServiceSwitches)?.tmdbEnabled != false
private val NetConfig.imdbOn: Boolean get() = (this as? ServiceSwitches)?.imdbEnabled != false

internal class NetPolicy(private val config: () -> NetConfig?) {
    fun check(url: HttpUrl) {
        val current = config() ?: throw BlockedRequestException("not configured")
        if (!current.onlineEnabled) throw OnlineDisabledException()
        if (!url.isHttps) throw BlockedRequestException("cleartext")
        val host = url.host
        val customTmdb = host == tmdbApiRoot(current.tmdbBaseUrl)?.host
        if (host !in ALLOWED_HOSTS && !customTmdb) throw BlockedRequestException(host)
        if ((customTmdb || host in TMDB_HOSTS) && !current.tmdbOn) throw ServiceOffException("tmdb")
        if (host in OMDB_HOSTS && !current.imdbOn) throw ServiceOffException("omdb")
    }
}

/**
 * Follows redirects itself so every hop is checked before a socket opens. An OkHttp network
 * interceptor would only see a redirect target after connecting to it.
 */
internal class GuardInterceptor(private val policy: NetPolicy) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var hops = 0
        while (true) {
            policy.check(request.url)
            val response = chain.proceed(request)
            val next = redirectOf(request, response) ?: return response
            response.close()
            if (++hops > MAX_REDIRECTS) throw ProtocolException("too many redirects")
            request = next
        }
    }

    private fun redirectOf(request: Request, response: Response): Request? {
        if (!response.isRedirect || request.method != "GET") return null
        val target = response.header("Location")?.let(request.url::resolve) ?: return null
        val builder = request.newBuilder().url(target)
        if (target.host != request.url.host) CREDENTIAL_HEADERS.forEach(builder::removeHeader)
        return builder.build()
    }
}

/** The only place in the app that builds an OkHttpClient. SingleClientTest enforces that. */
internal fun newFrameClient(policy: NetPolicy, vararg testTail: Interceptor): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(GuardInterceptor(policy))
        .apply { testTail.forEach(::addInterceptor) }
        .build()

/** Holds the one client for the process. A config bound later replaces the earlier one. */
internal object SharedHttp {
    @Volatile
    private var config: NetConfig? = null

    private val client: OkHttpClient by lazy { newFrameClient(NetPolicy { config }) }
    private val http: Http by lazy { Http(client, RateGate(System::currentTimeMillis)) }

    val callFactory: Call.Factory get() = client

    fun bind(config: NetConfig): Http {
        this.config = config
        return http
    }
}

/** Remembers, per host, when the next call may go out, from the headers each provider sends. */
internal class RateGate(private val clock: () -> Long) {
    private val notBefore = ConcurrentHashMap<String, Long>()

    fun waitMs(host: String): Long = ((notBefore[host] ?: return 0L) - clock()).coerceAtLeast(0L)

    fun observe(host: String, code: Int, headers: Headers) {
        val now = clock()
        var until = 0L
        if (code == 429 || code == 503) {
            retryAfterMs(headers, now)?.let { until = now + it }
        }
        // TheIntroDB reports its daily quota in the same shape under X-UsageLimit.
        for (family in listOf("X-RateLimit", "X-UsageLimit")) {
            if (headers["$family-Remaining"]?.toLongOrNull() != 0L) continue
            until = maxOf(until, resetAt(headers["$family-Reset"]?.toLongOrNull(), now))
        }
        if (code == 429 && until <= now) until = now + DEFAULT_BACKOFF_MS
        if (until > now) notBefore.merge(host, until, ::maxOf)
    }

    private fun retryAfterMs(headers: Headers, now: Long): Long? {
        val raw = headers["Retry-After"] ?: return null
        raw.trim().toLongOrNull()?.let { return it.coerceAtLeast(0L) * 1000L }
        return headers.getInstant("Retry-After")?.toEpochMilli()?.minus(now)?.coerceAtLeast(0L)
    }

    private fun resetAt(reset: Long?, now: Long): Long = when {
        reset == null || reset <= 0L -> now + FALLBACK_WINDOW_MS
        reset >= EPOCH_SECONDS_FLOOR -> reset * 1000L
        else -> now + reset * 1000L
    }
}

internal class HttpReply(val code: Int, val body: String) {
    val isOk: Boolean get() = code in 200..299
}

/** [Failure.message] is a short label. It never carries a URL, because URLs can carry a key. */
internal fun failureFor(code: Int, label: String, keyed: Boolean): Failure = when {
    (code == 401 || code == 403) && keyed -> Failure(Reason.NO_KEY, "$label rejected the key")
    code == 404 -> Failure(Reason.NOT_FOUND, label)
    code == 429 -> Failure(Reason.RATE_LIMIT, label)
    code >= 500 -> Failure(Reason.NETWORK, "$label HTTP $code")
    else -> Failure(Reason.BAD_RESPONSE, "$label HTTP $code")
}

internal inline fun <A, B> NetResult<A>.map(transform: (A) -> B): NetResult<B> = when (this) {
    is Failure -> this
    is Ok -> Ok(transform(value))
}

internal fun <T> NetResult<T>.valueOrNull(): T? = (this as? Ok)?.value

/** The last line of the "nothing throws" contract in NetApi.kt. */
internal suspend fun <T> softly(block: suspend () -> NetResult<T>): NetResult<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: IOException) {
    Failure(Reason.NETWORK)
} catch (_: RuntimeException) {
    Failure(Reason.BAD_RESPONSE)
}

/** Stops with false as soon as more than [limit] bytes have come through. */
internal fun InputStream.copyCapped(output: OutputStream, limit: Long): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return true
        total += count
        if (total > limit) return false
        output.write(buffer, 0, count)
    }
}

internal inline fun <reified T> NetResult<String>.decoded(label: String): NetResult<T> = when (this) {
    is Failure -> this
    is Ok -> try {
        Ok(NetJson.decodeFromString<T>(value))
    } catch (_: IllegalArgumentException) {
        Failure(Reason.BAD_RESPONSE, label)
    }
}

internal class Http(
    private val client: OkHttpClient,
    private val gate: RateGate,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun get(url: HttpUrl, label: String, keyed: Boolean = false, bearer: String? = null): NetResult<String> {
        val request = Request.Builder().url(url)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .build()
        return checked(fetch(request, label), label, keyed)
    }

    suspend fun postJson(url: HttpUrl, json: String, label: String): NetResult<String> {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return checked(fetch(request, label), label, keyed = false)
    }

    /** Like [get], for a request that carries its own headers. */
    suspend fun text(request: Request, label: String, keyed: Boolean): NetResult<String> =
        checked(fetch(request, label), label, keyed)

    /** Any HTTP status comes back as [Ok] for providers that put the reason in the body. */
    suspend fun fetch(request: Request, label: String): NetResult<HttpReply> =
        exchange(request, label) { Ok(HttpReply(it.code, it.body.string())) }

    suspend fun download(url: String, target: File, label: String): NetResult<Unit> {
        val parsed = url.toHttpUrlOrNull() ?: return Failure(Reason.BAD_RESPONSE, "$label bad url")
        return download(Request.Builder().url(parsed).build(), target, label)
    }

    /** A body over [maxBytes] fails and leaves nothing behind. */
    suspend fun download(
        request: Request,
        target: File,
        label: String,
        keyed: Boolean = false,
        maxBytes: Long = Long.MAX_VALUE,
    ): NetResult<Unit> = exchange(request, label) { response ->
        if (response.isSuccessful) save(response, target, label, maxBytes) else failureFor(response.code, label, keyed)
    }

    private fun checked(reply: NetResult<HttpReply>, label: String, keyed: Boolean): NetResult<String> = when (reply) {
        is Failure -> reply
        is Ok -> if (reply.value.isOk) Ok(reply.value.body) else failureFor(reply.value.code, label, keyed)
    }

    private fun save(response: Response, target: File, label: String, maxBytes: Long): NetResult<Unit> {
        target.absoluteFile.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        try {
            val fits = response.body.byteStream().use { input ->
                part.outputStream().use { output -> input.copyCapped(output, maxBytes) }
            }
            if (!fits) return Failure(Reason.BAD_RESPONSE, "$label too large")
            if (part.length() == 0L) return Failure(Reason.BAD_RESPONSE, "$label empty")
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) return Failure(Reason.BAD_RESPONSE, "$label rename")
            }
            return Ok(Unit)
        } finally {
            part.delete()
        }
    }

    private suspend fun <T> exchange(
        request: Request,
        label: String,
        read: (Response) -> NetResult<T>,
    ): NetResult<T> {
        val host = request.url.host
        var attempts = 0
        while (true) {
            attempts++
            val wait = gate.waitMs(host)
            if (wait > MAX_RATE_WAIT_MS) return Failure(Reason.RATE_LIMIT, label)
            if (wait > 0L) pause(wait)
            val result: NetResult<T>? = try {
                call(request) { response ->
                    gate.observe(host, response.code, response.headers)
                    if (response.code == 429) null else read(response)
                }
            } catch (_: OnlineDisabledException) {
                return Failure(Reason.OFFLINE_SETTING)
            } catch (off: ServiceOffException) {
                return Failure(Reason.OFFLINE_SETTING, off.message)
            } catch (blocked: BlockedRequestException) {
                return Failure(Reason.BAD_RESPONSE, "blocked ${blocked.message}")
            } catch (_: IOException) {
                return Failure(Reason.NETWORK, label)
            }
            if (result != null) return result
            if (attempts >= MAX_ATTEMPTS) return Failure(Reason.RATE_LIMIT, label)
        }
    }

    private suspend fun <T> call(request: Request, read: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use(read) })
                }
            })
        }
}
