package com.frameglass.net

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 4.9 and R4, enforced by reading the source of every module. */
class SingleClientTest {
    private val projectRoot: File = generateSequence(File("").absoluteFile, File::getParentFile)
        .first { File(it, "settings.gradle.kts").isFile }

    private val modules: List<File> = projectRoot.listFiles().orEmpty()
        .filter { File(it, "build.gradle.kts").isFile }

    /** Every source set except tests. Vendored libraries and build output are not ours. */
    private fun sourcesOf(module: File): List<File> =
        File(module, "src").walkTopDown()
            .onEnter { it.name != "test" && it.name != "androidTest" }
            .filter { it.extension == "kt" || it.extension == "java" }
            .toList()

    private fun relative(file: File) = file.relativeTo(projectRoot).invariantSeparatorsPath

    private val theClientFile = "net/src/main/java/com/frameglass/net/HttpCore.kt"

    @Test
    fun theProjectBuildsExactlyOneOkHttpClient() {
        val sources = modules.flatMap(::sourcesOf)
        assertTrue("the scan found no source", sources.any { relative(it) == theClientFile })
        val named = sources.filter { "OkHttpClient" in it.readText() }.map(::relative)
        assertEquals(listOf(theClientFile), named)

        val builders = Regex("""OkHttpClient\s*\(|OkHttpClient\.Builder\s*\(|\.newBuilder\s*\(\s*\)\s*\.\s*(addInterceptor|build)""")
        val client = sources.first { relative(it) == theClientFile }.readText()
        assertEquals(1, builders.findAll(client).count())
    }

    @Test
    fun nothingElseOpensANetworkConnection() {
        val other = Regex("""HttpURLConnection|java\.net\.http|\.openConnection\s*\(|URL\s*\([^)]*\)\s*\.(openStream|readText)""")
        val found = modules.flatMap(::sourcesOf).filter { other.containsMatchIn(it.readText()) }.map(::relative)
        assertEquals(emptyList<String>(), found)
    }

    /**
     * coil-network-okhttp registers itself through a ServiceLoader and builds its own client on the
     * first https image, and MatchChoice.posterUrl is one. That client would skip the allow-list
     * and the Online features switch.
     */
    @Test
    fun coilIsHandedTheGuardedClient() {
        val dependsOnCoilNetwork = Regex("""libs\.coil\.okhttp|coil-network-""")
        val bound = Regex("""OkHttpNetworkFetcherFactory\s*\([^)]*Net\.callFactory""")
        val unbound = modules
            .filter { dependsOnCoilNetwork.containsMatchIn(File(it, "build.gradle.kts").readText()) }
            .filter { module -> sourcesOf(module).none { bound.containsMatchIn(it.readText()) } }
            .map(File::getName)
        assertEquals(
            "A second HTTP client: these modules must add OkHttpNetworkFetcherFactory(callFactory = { Net.callFactory() }) " +
                "to their ImageLoader, or drop coil-network-okhttp",
            emptyList<String>(),
            unbound,
        )
    }

    @Test
    fun aCoilFactoryIsNeverBuiltWithItsDefaultClient() {
        val factory = Regex("""OkHttpNetworkFetcherFactory\s*\(""")
        val bound = Regex("""OkHttpNetworkFetcherFactory\s*\([^)]*Net\.callFactory""")
        val found = modules.flatMap(::sourcesOf)
            .filter { factory.findAll(it.readText()).count() != bound.findAll(it.readText()).count() }
            .map(::relative)
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun theNetModuleLogsNothingSoNoKeyCanBeLogged() {
        val logging = Regex("""\b(Log\.[vdiwe]|println|printStackTrace|HttpLoggingInterceptor|Timber)\b""")
        val found = sourcesOf(File(projectRoot, "net"))
            .filter { logging.containsMatchIn(it.readText()) }
            .map(::relative)
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun noProviderKeySitsInTheModule() {
        val keyShaped = Regex("""\b[0-9a-f]{32}\b|eyJ[A-Za-z0-9_-]{20,}\.""")
        val found = sourcesOf(File(projectRoot, "net"))
            .filter { keyShaped.containsMatchIn(it.readText()) }
            .map(::relative)
        assertEquals(emptyList<String>(), found)
    }
}
