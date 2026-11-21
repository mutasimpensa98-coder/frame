package com.frameglass.data.logic

import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.api.VaultDeletePlan
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.Title
import com.frameglass.data.db.VaultItem
import com.frameglass.data.db.VaultSidecar
import com.frameglass.data.db.WatchState

/** Playable keys of private items. They name no title and no file, and only the vault can open them. */
internal object VaultRefs {
    private const val GROUP = "v:"
    private const val ITEM = "vi:"

    fun group(id: Long): String = "$GROUP$id"
    fun item(id: Long): String = "$ITEM$id"
    fun isVault(key: String): Boolean = key.startsWith(GROUP) || key.startsWith(ITEM)
    fun groupOf(key: String): Long? = if (key.startsWith(GROUP)) key.removePrefix(GROUP).toLongOrNull() else null
    fun itemOf(key: String): Long? = if (key.startsWith(ITEM)) key.removePrefix(ITEM).toLongOrNull() else null
}

internal object VaultCards {
    fun watchOf(item: VaultItem): WatchState? {
        if (item.lastPlayedAt == 0L && item.watchPositionMs == 0L && !item.watched) return null
        return WatchState(item.file.playableKey, item.watchPositionMs, item.watchDurationMs, item.watched, item.lastPlayedAt)
    }

    /**
     * The library's own playables, so episode order, versions, resume and next behave the same in here. A file's
     * row id reads as the vault item id. Everything in the private folder plays, extras too.
     */
    fun playables(group: Long, rows: List<VaultItem>, episodes: List<EpisodeLite>): List<Playable> {
        val files = rows.map { row ->
            val kind = if (row.file.nameKind == NameKinds.EXTRA) NameKinds.MOVIE else row.file.nameKind
            row.file.copy(rowId = row.id, available = true, nameKind = kind)
        }
        val states = rows.mapNotNull(::watchOf).groupBy { it.playableKey }
        return Assembler.playables(group, files, episodes) { key -> states[key]?.maxByOrNull { it.lastPlayedAt } }
    }

    fun card(group: Long, title: Title?, rows: List<VaultItem>, onDisk: (VaultItem) -> Boolean): TitleCard {
        val items = playables(group, rows, emptyList())
        val watched = items.isNotEmpty() && items.all { it.watched }
        val started = items.any { it.started }
        val first = rows.first().file
        return TitleCard(
            id = group,
            kind = title?.kind ?: TitleKind.MOVIE,
            name = title?.name ?: CleanName.of(first.relPath, first.releaseTags),
            year = title?.year,
            coverPath = title?.coverPath ?: title?.framePath ?: rows.firstNotNullOfOrNull { it.file.framePath },
            rating = null,
            progress = if (!started || watched) {
                null
            } else {
                (items.sumOf { p -> if (p.watched) 1.0 else (p.partial ?: 0f).toDouble() } / items.size).toFloat().coerceIn(0f, 1f)
            },
            unavailable = rows.none(onDisk),
            watched = watched,
        )
    }
}

internal object VaultNames {
    /** "Name.mkv" becomes "Name (2).mkv". */
    fun numbered(name: String, n: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($n)" else "${name.substring(0, dot)} ($n)${name.substring(dot)}"
    }

    /** Keeps a subtitle matching its video when the video had to take a new name. */
    fun sidecarFor(sidecarName: String, oldVideo: String, newVideo: String): String {
        val oldBase = oldVideo.substringBeforeLast('.')
        val newBase = newVideo.substringBeforeLast('.')
        return if (oldBase != newBase && sidecarName.startsWith(oldBase)) newBase + sidecarName.removePrefix(oldBase) else sidecarName
    }
}

internal enum class MoveInRecovery { REMOVE_ORIGINAL, KEEP_BOTH, UNDO }

internal object VaultRecovery {
    /**
     * A restart found a move in that committed but never finished. The row alone removes nothing: [copyWhole] and
     * [originalWhole] come from hashing both sides again. Something else at the original's path is not the original.
     */
    fun moveIn(copyWhole: Boolean, originalWhole: Boolean): MoveInRecovery = when {
        copyWhole && originalWhole -> MoveInRecovery.REMOVE_ORIGINAL
        originalWhole -> MoveInRecovery.UNDO
        else -> MoveInRecovery.KEEP_BOTH
    }
}

/**
 * The delete inside the private folder, with no disk and no database: what a plan covers, which rows a confirmed
 * plan may still remove, which files go with a row, and what else the app must forget so nothing of a deleted
 * private video stays behind, in the library or in a backup.
 */
internal object VaultDeleteRules {
    /**
     * [wanted] are cards of the folder, [stored] every stored row, [sidecars] the subtitles of each row by its id and
     * [names] the name of each card. A card that is not in the folder adds nothing, and no card counts twice.
     */
    fun plan(
        wanted: List<Long>,
        stored: List<VaultItem>,
        sidecars: Map<Long, List<VaultSidecar>>,
        names: Map<Long, String>,
    ): VaultDeletePlan {
        val byCard = stored.groupBy { it.groupId }
        val cards = wanted.distinct().filter { it in byCard }
        val rows = cards.flatMap { byCard.getValue(it) }
        val subtitles = rows.flatMap { sidecars[it.id].orEmpty() }
        return VaultDeletePlan(
            titleIds = cards,
            itemIds = rows.map { it.id },
            fileCount = rows.size,
            subtitleCount = subtitles.size,
            totalBytes = rows.sumOf { it.file.size } + subtitles.sumOf { it.sizeBytes },
            names = cards.map { names[it].orEmpty() },
        )
    }

    /**
     * The rows a confirmed plan removes now: the ones it named that are still stored. A video moved in after the
     * owner read the sheet is not in the plan, so it stays, even on a card the plan covers.
     */
    fun targets(plan: VaultDeletePlan, stored: List<VaultItem>): List<VaultItem> {
        val named = plan.itemIds.toSet()
        return stored.filter { it.id in named }
    }

    /** The files of one row inside the folder: the video first, then the subtitles that moved in with it. */
    fun storedFiles(row: VaultItem, sidecars: List<VaultSidecar>): List<String> =
        listOf(row.storedName) + sidecars.map { it.storedName }

    /**
     * True only for a title that was never anything but private, and is now empty. "No file row" is not enough:
     * removing or excluding a library folder deletes its file rows on purpose and keeps the title, its match and
     * its progress, so the folder can come back to all of it. So anything the owner did with the title outside the
     * private folder keeps it: progress on another episode, a favourite or watchlist mark, a match or a cover
     * chosen by hand.
     */
    fun forgettable(traces: TitleTraces): Boolean =
        traces.privateLeft == 0 && traces.libraryFiles == 0 && traces.otherProgress == 0 &&
            !traces.favorite && !traces.onWatchlist && !traces.manualMatch && !traces.manualCover

    /**
     * The titles among [titleIds] that are forgotten whole after their private videos were deleted. One the app
     * knows nothing about, because it is already gone, is left alone. Every other title stays as it is, and only
     * what belonged to the deleted file goes: its progress went with its row, and [freeFileIds] names the rest.
     */
    fun orphanTitles(titleIds: List<Long>, traces: Map<Long, TitleTraces>): Set<Long> =
        titleIds.filterTo(LinkedHashSet()) { id -> traces[id]?.let(::forgettable) == true }

    /**
     * File ids among [removed] whose own leftovers can go: the frame, the saved subtitles and the track choices.
     * One that a row still [held] in the folder or in the library has keeps them.
     */
    fun freeFileIds(removed: List<String>, held: Set<String>): Set<String> =
        removed.filterTo(LinkedHashSet()) { it !in held }
}

/** What the app still holds for a title once a private video of it is deleted. */
internal data class TitleTraces(
    /** Other videos of it still in the private folder, in any state. */
    val privateLeft: Int,
    /** File rows of it in the library, on storage or not. */
    val libraryFiles: Int,
    /** Saved progress of its playables. The deleted video's own progress lived in its private row and is gone. */
    val otherProgress: Int,
    val favorite: Boolean,
    val onWatchlist: Boolean,
    val manualMatch: Boolean,
    val manualCover: Boolean,
)

/** One deleted private video whose leftovers are still to be cleaned: its file id, and its title when it had one. */
internal data class VaultLeftover(val fileId: String, val titleId: Long?)

/**
 * The list of deleted private videos whose cleanup has not finished, kept as plain lines in app storage: the title
 * id, a tab, the file id. It names no file and no title. A delete adds a line before the row goes, and the cleanup
 * takes the line away when it is done, so an app killed in between finishes the cleanup on its next start.
 */
internal object VaultCleanupJournal {
    private const val SEPARATOR = '\t'

    fun line(entry: VaultLeftover): String = "${entry.titleId ?: ""}$SEPARATOR${entry.fileId}"

    /** Every whole line, once. A line cut short by a kill, or anything else unreadable, is skipped. */
    fun parse(text: String): List<VaultLeftover> = text.lineSequence().mapNotNull { line ->
        val at = line.indexOf(SEPARATOR)
        if (at < 0) return@mapNotNull null
        val fileId = line.substring(at + 1)
        val title = line.substring(0, at)
        val titleId = if (title.isEmpty()) null else title.toLongOrNull() ?: return@mapNotNull null
        if (fileId.isEmpty()) null else VaultLeftover(fileId, titleId)
    }.distinct().toList()

    /** The journal with [done] taken out. Lines added while the cleanup ran stay. */
    fun without(text: String, done: Collection<VaultLeftover>): String {
        val finished = done.toSet()
        return parse(text).filter { it !in finished }.joinToString("") { line(it) + "\n" }
    }
}
