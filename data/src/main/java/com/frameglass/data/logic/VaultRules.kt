package com.frameglass.data.logic

import com.frameglass.data.api.TitleCard
import com.frameglass.data.api.TitleKind
import com.frameglass.data.db.EpisodeLite
import com.frameglass.data.db.NameKinds
import com.frameglass.data.db.Title
import com.frameglass.data.db.VaultItem
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
