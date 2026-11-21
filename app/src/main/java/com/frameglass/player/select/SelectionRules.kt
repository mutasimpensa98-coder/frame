package com.frameglass.player.select

import com.frameglass.data.api.EpisodeRow
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.FolderEntry
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.WatchTarget
import com.frameglass.player.ui.CardMenuRules

/**
 * One thing a list lets the owner pick. [key] stays the same across list updates, so a scan cannot drop a pick.
 * [onStorage] is false when no file of it is on storage now: such an item can be picked, and nothing moves or
 * deletes it.
 */
internal sealed interface SelectItem {
    val key: String
    val name: String
    val onStorage: Boolean

    /** A film, a series, an anime or a clip: every card of Library, the title lists and Search. */
    data class Title(
        val id: Long,
        override val name: String,
        override val onStorage: Boolean,
        val watched: Boolean,
    ) : SelectItem {
        override val key: String get() = "title|$id"
    }

    /**
     * A folder row in Folders. With [direct] it is a row of the flat list, which stands for the videos directly in
     * the folder and never for the folders inside it.
     */
    data class Folder(
        val path: String,
        override val name: String,
        override val onStorage: Boolean,
        val direct: Boolean = false,
    ) : SelectItem {
        override val key: String get() = "folder|$path"
    }

    /** A video row in Folders. */
    data class File(val fileRowId: Long, override val name: String, override val onStorage: Boolean) : SelectItem {
        override val key: String get() = "file|$fileRowId"
    }

    /** One episode on Details. [label] is "S1 E2", or the episode's title when it has no number. */
    data class Episode(
        val playableKey: String,
        val title: String,
        val label: String?,
        override val onStorage: Boolean,
        val watched: Boolean,
    ) : SelectItem {
        override val key: String get() = "episode|$playableKey"
        override val name: String get() = listOfNotNull(title, label?.takeIf { it.isNotBlank() }).joinToString(" ")
    }

    /** A card inside the private folder. */
    data class Private(val id: Long, override val name: String) : SelectItem {
        override val key: String get() = "private|$id"
        override val onStorage: Boolean get() = true
    }
}

internal fun TitleCard.selectItem() = SelectItem.Title(id, name, onStorage = !unavailable, watched = watched)

internal fun TitleCard.privateSelectItem() = SelectItem.Private(id, name)

/** A video row whose file the library no longer knows has nothing to act on, so it cannot be picked. */
internal fun FolderEntry.selectItem(): SelectItem? = when {
    isDirectory -> SelectItem.Folder(path, name, onStorage = !unavailable, direct = direct)
    else -> play?.fileRowId?.let { SelectItem.File(it, name, onStorage = !unavailable) }
}

/** An episode with no file, such as one known only from the episode list of the series, cannot be picked. */
internal fun EpisodeRow.selectItem(seriesName: String): SelectItem? = play?.playableKey?.let { key ->
    SelectItem.Episode(key, seriesName, label.ifBlank { title }, onStorage = available && !missing, watched = watched)
}

/** Which list the picks sit in. It decides which actions the bar offers. */
internal enum class SelectionPlace {
    /** Library and every list of titles: the row, genre and person pages, and Search. */
    TITLES,

    /** The Favourites filter, where the favourite action takes titles out. */
    FAVORITES,

    /** The Watchlist filter, where the watchlist action takes titles out. */
    WATCHLIST,

    /** Folders: folders and video rows. */
    FOLDERS,

    /** The episode list of a series. */
    EPISODES,

    /** Inside the private folder: move out, or delete for good. */
    PRIVATE,
}

internal enum class SelectionAction {
    WATCHLIST_ADD,
    WATCHLIST_REMOVE,
    FAVORITE_ADD,
    FAVORITE_REMOVE,
    MARK_WATCHED,
    MARK_UNWATCHED,
    HIDE,
    MOVE_TO_PRIVATE,
    MOVE_OUT,
    DELETE,

    /** The delete inside the private folder. It is for good, and has a confirmation sheet of its own. */
    DELETE_PRIVATE,
}

/** One button of the action bar. A button that does not apply to the picks stays in place, dimmed. */
internal data class BarAction(val action: SelectionAction, val enabled: Boolean)

/**
 * The action bar as it is laid out: [shown] in a row, [overflow] behind "More", and [pinned] at the end, where
 * Delete always sits.
 */
internal data class BarLayout(val shown: List<BarAction>, val overflow: List<BarAction>, val pinned: List<BarAction>)

/** What a move into the private folder takes: whole titles, and single files. */
internal data class MoveIn(val titleIds: List<Long>, val fileRowIds: List<Long>) {
    val isEmpty: Boolean get() = titleIds.isEmpty() && fileRowIds.isEmpty()
}

/**
 * The rules of multi-select, free of any layout: picking, which actions fit the picks, and what each action is
 * handed. Every screen and every card shape reads the same answers from here.
 */
internal object SelectionRules {
    /** Buttons the action bar holds before the rest go behind "More". */
    const val BAR_SLOTS = 5

    fun <T> toggle(picked: Map<String, T>, key: String, item: T): Map<String, T> =
        if (key in picked) picked - key else picked + (key to item)

    /** Adds everything listed, in list order, after what is picked already. */
    fun <T> selectAll(picked: Map<String, T>, all: List<T>, keyOf: (T) -> String): Map<String, T> =
        picked + all.associateBy(keyOf)

    /** Drops picks that left the list, and takes the listed value of those that stayed, in list order. */
    fun <T> retain(picked: Map<String, T>, listed: List<T>, keyOf: (T) -> String): Map<String, T> =
        listed.filter { keyOf(it) in picked }.associateBy(keyOf)

    /** True when the list holds something and all of it is picked, so "Select all" turns into "Clear all". */
    fun allPicked(picked: Set<String>, listed: List<String>): Boolean =
        listed.isNotEmpty() && listed.all { it in picked }

    private fun offered(place: SelectionPlace): List<SelectionAction> = when (place) {
        SelectionPlace.TITLES -> listOf(
            SelectionAction.WATCHLIST_ADD,
            SelectionAction.FAVORITE_ADD,
            SelectionAction.MARK_WATCHED,
            SelectionAction.HIDE,
            SelectionAction.MOVE_TO_PRIVATE,
            SelectionAction.DELETE,
        )
        SelectionPlace.FAVORITES -> listOf(
            SelectionAction.FAVORITE_REMOVE,
            SelectionAction.WATCHLIST_ADD,
            SelectionAction.MARK_WATCHED,
            SelectionAction.HIDE,
            SelectionAction.MOVE_TO_PRIVATE,
            SelectionAction.DELETE,
        )
        SelectionPlace.WATCHLIST -> listOf(
            SelectionAction.WATCHLIST_REMOVE,
            SelectionAction.FAVORITE_ADD,
            SelectionAction.MARK_WATCHED,
            SelectionAction.HIDE,
            SelectionAction.MOVE_TO_PRIVATE,
            SelectionAction.DELETE,
        )
        SelectionPlace.FOLDERS -> listOf(SelectionAction.MOVE_TO_PRIVATE, SelectionAction.DELETE)
        SelectionPlace.EPISODES -> listOf(
            SelectionAction.MARK_WATCHED,
            SelectionAction.MOVE_TO_PRIVATE,
            SelectionAction.DELETE,
        )
        SelectionPlace.PRIVATE -> listOf(SelectionAction.MOVE_OUT, SelectionAction.DELETE_PRIVATE)
    }

    private fun watched(item: SelectItem): Boolean? = when (item) {
        is SelectItem.Title -> item.watched
        is SelectItem.Episode -> item.watched
        else -> null
    }

    private fun applies(action: SelectionAction, items: Collection<SelectItem>): Boolean = when (action) {
        SelectionAction.WATCHLIST_ADD,
        SelectionAction.WATCHLIST_REMOVE,
        SelectionAction.FAVORITE_ADD,
        SelectionAction.FAVORITE_REMOVE,
        SelectionAction.HIDE,
        -> items.any { it is SelectItem.Title }
        SelectionAction.MARK_WATCHED,
        SelectionAction.MARK_UNWATCHED,
        -> items.any { watched(it) != null }
        SelectionAction.MOVE_TO_PRIVATE,
        SelectionAction.DELETE,
        -> items.any { it !is SelectItem.Private && it.onStorage }
        SelectionAction.MOVE_OUT,
        SelectionAction.DELETE_PRIVATE,
        -> items.any { it is SelectItem.Private }
    }

    /**
     * The actions of [place] in bar order, each marked with whether it applies to [items]. The watched button reads
     * "Mark unwatched" only when everything picked that can be watched already is, as the Details menu does for one
     * title; with a mix it marks the rest watched.
     */
    fun actions(place: SelectionPlace, items: Collection<SelectItem>): List<BarAction> {
        val marks = items.mapNotNull(::watched)
        val allWatched = marks.isNotEmpty() && marks.all { it }
        return offered(place).map { offer ->
            val turned = offer == SelectionAction.MARK_WATCHED && allWatched
            val action = if (turned) SelectionAction.MARK_UNWATCHED else offer
            BarAction(action, applies(action, items))
        }
    }

    /** True for the two deletes, which sit at the end of the bar in the error colour. */
    fun destructive(action: SelectionAction): Boolean =
        action == SelectionAction.DELETE || action == SelectionAction.DELETE_PRIVATE

    /** Delete keeps its place at the end. The rest fill the row, and what does not fit goes behind "More". */
    fun bar(actions: List<BarAction>, slots: Int = BAR_SLOTS): BarLayout {
        val (pinned, rest) = actions.partition { destructive(it.action) }
        val room = (slots - pinned.size).coerceAtLeast(1)
        if (rest.size <= room) return BarLayout(rest, emptyList(), pinned)
        // "More" takes one of the places.
        return BarLayout(rest.take(room - 1), rest.drop(room - 1), pinned)
    }

    private fun inFolder(item: SelectItem.Folder, path: String): Boolean =
        if (item.direct) path.trimEnd('/') == item.path.trimEnd('/') else CardMenuRules.isUnder(item.path, path)

    /** Titles for the watchlist, favourites and hide, which only a title can take. */
    fun titleIds(items: Collection<SelectItem>): List<Long> =
        items.filterIsInstance<SelectItem.Title>().map { it.id }.distinct()

    fun privateIds(items: Collection<SelectItem>): List<Long> =
        items.filterIsInstance<SelectItem.Private>().map { it.id }.distinct()

    /** What "Mark watched" and "Mark unwatched" write to: a whole title, or one episode (F-S3). */
    fun watchTargets(items: Collection<SelectItem>): List<WatchTarget> = items.mapNotNull { item ->
        when (item) {
            is SelectItem.Title -> WatchTarget.Title(item.id)
            is SelectItem.Episode -> WatchTarget.Playable(item.playableKey)
            else -> null
        }
    }.distinct()

    /** Folders whose Media Manager entries must be looked up before a delete or a move. */
    fun foldersOnStorage(items: Collection<SelectItem>): List<SelectItem.Folder> =
        items.filterIsInstance<SelectItem.Folder>().filter { it.onStorage }.distinctBy { it.path }

    /** Episodes whose files must be looked up before a delete or a move. */
    fun episodesOnStorage(items: Collection<SelectItem>): List<String> =
        items.filterIsInstance<SelectItem.Episode>().filter { it.onStorage }.map { it.playableKey }.distinct()

    /**
     * Everything the confirmation sheet is asked about, in pick order. [folders] are Media Manager's folder entries
     * and [versions] the files of each picked episode by playable key. A picked folder of the tree stands for
     * every entry at its path or under it, so a folder of folders counts all the videos inside. A picked row of the
     * flat list stands for the entry at its own path alone. An item with nothing on storage, a folder with no video
     * anywhere under it and anything inside the private folder add no entry.
     */
    fun deleteEntries(
        items: Collection<SelectItem>,
        folders: List<MediaEntry>,
        versions: Map<String, List<FileVersion>>,
    ): List<MediaEntry> = items.filter { it.onStorage }.flatMap { item ->
        when (item) {
            is SelectItem.Title -> listOf(CardMenuRules.titleEntry(item.id, item.name))
            is SelectItem.File -> listOf(CardMenuRules.fileEntry(item.fileRowId, item.name))
            is SelectItem.Folder -> CardMenuRules.folderContent(item.path, item.direct, folders)
            is SelectItem.Episode ->
                CardMenuRules.episodeEntries(versions[item.playableKey].orEmpty(), item.title, item.label)
            is SelectItem.Private -> emptyList()
        }
    }.distinctBy { it.id }

    /**
     * What a move into the private folder takes. [folderFiles] are the file rows of each Media Manager folder by
     * its path. A picked folder of the tree takes those of every folder at its path or under it, and a picked row
     * of the flat list those of its own path alone. [versions] as for [deleteEntries]. Only files on storage go.
     */
    fun moveIn(
        items: Collection<SelectItem>,
        folderFiles: Map<String, List<Long>>,
        versions: Map<String, List<FileVersion>>,
    ): MoveIn {
        val movable = items.filter { it.onStorage }
        val rows = movable.flatMap { item ->
            when (item) {
                is SelectItem.File -> listOf(item.fileRowId)
                is SelectItem.Folder -> folderFiles.filterKeys { inFolder(item, it) }.values.flatten()
                is SelectItem.Episode ->
                    versions[item.playableKey].orEmpty().filter { it.available }.map { it.fileRowId }
                else -> emptyList()
            }
        }
        return MoveIn(titleIds(movable), rows.distinct())
    }
}
