package com.frameglass.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android compiles patterns with ICU, which throws on a bare "{" or "}" that the JVM accepts. JVM tests can never
 * see that crash (1.0.105 closed the player when subtitles loaded), so this reads every pattern literal in the
 * project's main sources and fails on a brace that is not escaped, a quantifier, a class member or `\p{..}`.
 */
class RegexBracesTest {
    private val literal = Regex(
        "(?:Regex|toRegex|Pattern\\.compile)\\(\\s*(\"\"\"([\\s\\S]*?)\"\"\"|\"((?:\\\\.|[^\"\\\\])*)\")",
    )

    @Test
    fun everyPatternEscapesItsBraces() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: error("project root not found")
        val bad = mutableListOf<String>()
        root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "test" }
            .filter { it.isFile && it.extension == "kt" && it.path.replace('\\', '/').contains("/src/main/") }
            .forEach { file ->
                val text = file.readText()
                for (m in literal.findAll(text)) {
                    val raw = m.groups[2]?.value
                    val body = raw ?: m.groupValues[3].replace("\\\\", "\\")
                    if (hasBareBrace(body)) {
                        val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                        bad += "${file.relativeTo(root)}:$line  $body"
                    }
                }
            }
        assertTrue("Unescaped brace in a pattern, Android will throw:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun theCheckCatchesTheShipped1_0_105Pattern() {
        assertTrue(hasBareBrace("\\{[^}]*}"))
        assertTrue(!hasBareBrace("\\{[^}]*\\}"))
        assertTrue(!hasBareBrace("\\p{M}+ \\d{1,2} [^\\p{L}\\p{N}]"))
    }

    private fun hasBareBrace(pattern: String): Boolean {
        val t = pattern
            .replace(Regex("\\$\\{[^}]*\\}"), "")
            .replace(Regex("\\\\p\\{[^}]*\\}"), "")
            .replace(Regex("\\\\."), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("\\{\\d*,?\\d*\\}"), "")
        return '{' in t || '}' in t
    }
}
