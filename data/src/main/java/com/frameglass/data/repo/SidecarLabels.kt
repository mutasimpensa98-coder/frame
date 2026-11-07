package com.frameglass.data.repo

import android.content.Context
import com.frameglass.data.R
import com.frameglass.data.api.SidecarSubtitle
import com.frameglass.data.logic.SidecarHit
import com.frameglass.data.logic.SidecarMatcher
import java.io.File
import java.util.Locale

/** Names for subtitles in the device language, and subtitles saved in app storage for one file. */
internal class SidecarLabels(private val context: Context) {
    private fun languageName(tag: String): String =
        Locale.forLanguageTag(tag).getDisplayLanguage(context.resources.configuration.locales[0])

    fun label(language: String?, forced: Boolean, signs: Boolean, fallback: String): String {
        val name = language?.let(::languageName) ?: return fallback
        return when {
            forced -> context.getString(R.string.data_subtitle_forced, name)
            signs -> context.getString(R.string.data_subtitle_signs, name)
            else -> name
        }
    }

    fun of(hit: SidecarHit): SidecarSubtitle = SidecarSubtitle(
        path = File(hit.file.folder, hit.file.name).path,
        label = label(hit.language, hit.forced, hit.signs, hit.file.name),
        language = hit.language,
        mimeType = SidecarMatcher.mimeType(hit.file.name),
    )

    fun folderOf(fileKey: String): File = File(context.filesDir, "$FOLDER/$fileKey")

    fun online(file: File, language: String?, provider: String): SidecarSubtitle = SidecarSubtitle(
        path = file.path,
        // In isolates, so a Latin provider name cannot pull Arabic punctuation around.
        label = language?.let { context.getString(R.string.data_subtitle_online, languageName(it), "\u2068$provider\u2069") } ?: file.name,
        language = language,
        mimeType = SidecarMatcher.mimeType(file.name),
    )

    /** The client names these provider-id.s1e2.ar.srt, so the language and provider are in the name. */
    fun saved(fileKey: String): List<SidecarSubtitle> = folderOf(fileKey).listFiles().orEmpty()
        .filter { SidecarMatcher.isSubtitle(it.name) }
        .map { file ->
            val language = file.name.substringBeforeLast('.').substringAfterLast('.', "").ifEmpty { null }
            online(file, language, file.name.substringBefore('-'))
        }

    private companion object {
        const val FOLDER = "subs"
    }
}
