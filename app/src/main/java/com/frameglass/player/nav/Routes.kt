package com.frameglass.player.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind

/**
 * The cover a Details page opened from. [key] pairs the two ends of the shared transition and names the
 * row too, because one title can sit in two rows at once. [path] paints the first frame, before the
 * title's data has loaded.
 */
internal data class ArtRef(val key: String? = null, val path: String? = null)

/** The pages opened from the main Settings list that hold only settings. */
internal enum class SettingsTopic { APPEARANCE, LANGUAGE, LIBRARY, PLAYBACK, SUBTITLES, ONLINE, BACKUP }

internal sealed interface Route {
    data object Onboarding : Route

    /**
     * The feature tour. With [setup] it is the first run, and the setup steps of [Onboarding] follow it. Without, it
     * was opened from Settings and returns there.
     */
    data class Tour(val setup: Boolean) : Route
    data object Tabs : Route
    data object About : Route
    data object MediaManager : Route
    data object PrivateFolder : Route
    data object Stats : Route
    data class Settings(val topic: SettingsTopic) : Route

    /** One folder, title or clip group in Media Manager: the fields MediaManager.children reads, as plain values. */
    data class MediaItems(
        val kind: MediaEntryKind,
        val id: String,
        val name: String,
        val titleId: Long?,
        val folderPath: String?,
    ) : Route {
        fun entry() = MediaEntry(
            id = id,
            kind = kind,
            name = name,
            detail = folderPath,
            sizeBytes = 0,
            fileCount = 0,
            durationMs = null,
            width = null,
            height = null,
            coverPath = null,
            titleId = titleId,
            folderPath = folderPath,
            fileRowId = null,
        )
    }

    data class Details(val titleId: Long, val art: ArtRef = ArtRef()) : Route
    data class Person(val personId: Long) : Route
    data class Genre(val name: String) : Route
    data class Row(val kind: HomeRowKind, val label: String?) : Route

    /**
     * One folder. With [direct] it is a row of the flat list of folders, and the page shows the videos directly in
     * it and nothing of the folders inside it. Without, it is a folder of the tree.
     */
    data class Folder(val path: String, val direct: Boolean = false) : Route
}

private const val Separator = "\u001F"

private fun fields(vararg parts: Any?): String = parts.joinToString(Separator) { it?.toString().orEmpty() }

private fun Route.encode(): String = when (this) {
    Route.Onboarding -> "onboarding"
    is Route.Tour -> fields("tour", setup)
    Route.Tabs -> "tabs"
    Route.About -> "about"
    Route.MediaManager -> "media"
    Route.PrivateFolder -> "private"
    Route.Stats -> "stats"
    is Route.Settings -> fields("settings", topic.name)
    is Route.MediaItems -> fields("mediaItems", kind.name, id, name, titleId, folderPath)
    is Route.Details -> fields("details", titleId, art.key, art.path)
    is Route.Person -> fields("person", personId)
    is Route.Genre -> fields("genre", name)
    is Route.Row -> fields("row", kind.name, label)
    is Route.Folder -> fields("folder", path, direct)
}

private fun decode(saved: String): Route? {
    val parts = saved.split(Separator)
    fun text(index: Int): String? = parts.getOrNull(index)?.ifEmpty { null }
    return when (parts[0]) {
        "onboarding" -> Route.Onboarding
        "tour" -> Route.Tour(setup = text(1).toBoolean())
        "tabs" -> Route.Tabs
        "about" -> Route.About
        "media" -> Route.MediaManager
        "private" -> Route.PrivateFolder
        "stats" -> Route.Stats
        "settings" -> text(1)?.let { Route.Settings(SettingsTopic.valueOf(it)) }
        "mediaItems" -> {
            val kind = text(1)
            val id = text(2)
            val name = text(3)
            if (kind == null || id == null || name == null) {
                null
            } else {
                Route.MediaItems(MediaEntryKind.valueOf(kind), id, name, text(4)?.toLongOrNull(), text(5))
            }
        }
        "details" -> text(1)?.toLongOrNull()?.let { Route.Details(it, ArtRef(text(2), text(3))) }
        "person" -> text(1)?.toLongOrNull()?.let(Route::Person)
        "genre" -> text(1)?.let(Route::Genre)
        "row" -> text(1)?.let { Route.Row(HomeRowKind.valueOf(it), text(2)) }
        // A stack saved before the flat list has no third field, which reads as a folder of the tree.
        "folder" -> text(1)?.let { Route.Folder(it, direct = text(2).toBoolean()) }
        else -> null
    }
}

/** The back stack survives process death as plain strings, which needs no keep rules under R8. */
internal val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved ->
        mutableStateListOf<Route>().apply {
            addAll(saved.mapNotNull(::decode))
            if (isEmpty()) add(Route.Tabs)
        }
    },
)
