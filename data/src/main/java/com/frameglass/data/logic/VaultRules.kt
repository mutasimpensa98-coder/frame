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
     * Titles of the [removed] rows that are left with nothing: no other private row ([privateLeft]) and no file in
     * the library ([publicFiles]). Such a title is forgotten whole. One that still has a file anywhere keeps its
     * match, cover and progress, as after any delete.
     */
    fun orphanTitles(removed: List<VaultItem>, privateLeft: Map<Long, Int>, publicFiles: Map<Long, Int>): Set<Long> =
        removed.mapNotNullTo(LinkedHashSet()) { it.file.titleId }
            .filterTo(LinkedHashSet()) { (privateLeft[it] ?: 0) == 0 && (publicFiles[it] ?: 0) == 0 }

    /**
     * File ids of the [removed] rows whose own leftovers can go: the frame, the saved subtitles and the track
     * choices. A copy of the same file that [staying] in the folder or the library still holds keeps them.
     */
    fun freeFileIds(removed: List<VaultItem>, staying: List<VaultItem>, publicFileIds: Set<String>): Set<String> {
        val held = staying.mapTo(HashSet()) { it.file.fileId } + publicFileIds
        return removed.mapTo(LinkedHashSet()) { it.file.fileId }.filterTo(LinkedHashSet()) { it !in held }
    }
}
