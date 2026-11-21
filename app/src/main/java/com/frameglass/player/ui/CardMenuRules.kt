package com.frameglass.player.ui

import com.frameglass.data.api.ContinueItem
import com.frameglass.data.api.FileVersion
import com.frameglass.data.api.HeroItem
import com.frameglass.data.api.MediaEntry
import com.frameglass.data.api.MediaEntryKind
import com.frameglass.data.api.PlayRef
import com.frameglass.data.api.TitleKind

/** The one episode a card stands for. [label] is "S1 E2", or the episode's title when it has no number. */
internal data class CardEpisode(val playableKey: String, val label: String?)

/** How the delete row of a title reads. A series says "the whole series", so it is never taken for one episode. */
internal enum class DeleteWording { PLAIN, WHOLE_SERIES }

/**
 * The rows of the long-press menu that depend on what the card is. Watchlist, favourite, hide and exclude always
 * show; these do not.
 */
internal data class TitleMenuRows(
    /** What "Play in pop-up" plays, or null for no such row. */
    val popUp: PlayRef?,
    /** The episode "Delete this episode" removes, or null for no such row. */
    val episode: CardEpisode?,
    /** How the delete of the whole title reads. Null when no file is on storage: nothing to move or delete. */
    val delete: DeleteWording?,
)

internal object CardMenuRules {
    /**
     * [titlePlay] is what Details would play: the resume item, or the first thing to play. [cardPlay] is what a tap
     * on the card itself plays, for a card that plays, and wins. [cardEpisode] comes from a Continue watching card.
     */
    fun titleRows(
        kind: TitleKind,
        availableFiles: Int,
        titlePlay: PlayRef?,
        cardPlay: PlayRef? = null,
        cardEpisode: CardEpisode? = null,
    ): TitleMenuRows {
        if (availableFiles <= 0) return TitleMenuRows(popUp = null, episode = null, delete = null)
        // A lone video filed as a series, such as an unmatched clip, has nothing else to tell apart: one plain delete.
        val series = kind != TitleKind.MOVIE && availableFiles > 1
        return TitleMenuRows(
            popUp = cardPlay ?: titlePlay,
            episode = cardEpisode.takeIf { series },
            delete = if (series) DeleteWording.WHOLE_SERIES else DeleteWording.PLAIN,
        )
    }

    /** True when [path] is [root] itself or anything inside it. A sibling whose name only starts the same is not. */
    fun isUnder(root: String, path: String?): Boolean {
        if (path == null) return false
        val top = root.trimEnd('/')
        return top.isNotEmpty() && (path.trimEnd('/') == top || path.startsWith("$top/"))
    }

    /**
     * What a folder holds for a delete or a move: every Media Manager folder entry at [path] or under it.
     * Media Manager lists only folders that hold videos directly, so a folder of folders has no entry of its own,
     * and asking for its own entry alone would find nothing to delete in a folder full of videos.
     */
    fun foldersUnder(path: String, folders: List<MediaEntry>): List<MediaEntry> =
        folders.filter { it.kind == MediaEntryKind.FOLDER && isUnder(path, it.folderPath) }

    /** Every file of a title that is on storage now, for the confirmation sheet. */
    fun titleEntry(titleId: Long, name: String) = MediaEntry(
        id = "title|$titleId",
        kind = MediaEntryKind.TITLE,
        name = name,
        detail = null,
        sizeBytes = 0,
        fileCount = 0,
        durationMs = null,
        width = null,
        height = null,
        coverPath = null,
        titleId = titleId,
        folderPath = null,
        fileRowId = null,
    )

    fun fileEntry(fileRowId: Long, name: String) = MediaEntry(
        id = "file|$fileRowId",
        kind = MediaEntryKind.FILE,
        name = name,
        detail = null,
        sizeBytes = 0,
        fileCount = 1,
        durationMs = null,
        width = null,
        height = null,
        coverPath = null,
        titleId = null,
        folderPath = null,
        fileRowId = fileRowId,
    )

    /**
     * One episode's files for the confirmation sheet: every version of it that is on storage, and nothing else of
     * the series. Each is named "Title S1 E2", which is what a failed delete reports.
     */
    fun episodeEntries(versions: List<FileVersion>, title: String, label: String?): List<MediaEntry> {
        val name = listOfNotNull(title, label?.takeIf { it.isNotBlank() }).joinToString(" ")
        return versions.filter { it.available }.map { fileEntry(it.fileRowId, name) }
    }
}

/** A Continue watching card plays its episode on a tap, so its menu can play and delete that one episode. */
internal fun ContinueItem.cardTarget() = CardTarget.Title(
    titleId = titleId,
    name = name,
    play = play,
    episode = play.playableKey?.let { CardEpisode(it, episodeLabel ?: episodeTitle) },
)

/** The hero stands in for the Continue card of the title played last, and then offers what that card would. */
internal fun HeroItem.cardTarget() = continueItem?.cardTarget() ?: CardTarget.Title(titleId, name, play)
