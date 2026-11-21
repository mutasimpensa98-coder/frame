package com.frameglass.data.api

/*
 * Library layout (owner requests of 2026-11-22): how Folders are listed, and the order of the filter chips at the
 * top of Library. Both are kept in AppSettings as nullable fields, where null is "never chosen" and shows the
 * default, so an install and a backup from before them need no migration.
 */

/** How Folders are listed. */
enum class FoldersMode {
    /** One list of every folder that holds videos directly, with no folder inside a folder. */
    FLAT,

    /** The tree the app had before: a folder opens on its folders and its videos. */
    TREE,
    ;

    companion object {
        /** What an install shows until the owner picks, an older install too. */
        val DEFAULT = FLAT

        /** A stored or restored name. Anything unknown, including null, is "never chosen". */
        fun parse(name: String?): FoldersMode? = entries.firstOrNull { it.name == name }
    }
}

/** The order of the flat list of folders. It lives for the session, as the sort of Library does. */
enum class FolderSort {
    /** By name, in natural order. */
    NAME,

    /** The most space first. */
    SIZE,

    /** The most videos first. */
    COUNT,

    /** The folder whose newest video is the most recent first. */
    NEWEST,
}

/** How Folders are listed now: what the owner chose, or the default. */
val AppSettings.foldersView: FoldersMode get() = foldersMode ?: FoldersMode.DEFAULT

/**
 * The order of the filter chips of Library. The saved order may be shorter than the list of chips, because a build
 * can add a chip, and it may name chips that are not on screen, because Anime and Watchlist show only while they
 * hold something. So an order is always completed first, and what is on screen is cut out of the whole.
 */
object ChipOrder {
    /** The order the app has always had. */
    val DEFAULT: List<LibraryFilter> = listOf(
        LibraryFilter.ALL,
        LibraryFilter.MOVIES,
        LibraryFilter.SERIES,
        LibraryFilter.ANIME,
        LibraryFilter.VIDEOS,
        LibraryFilter.FAVORITES,
        LibraryFilter.WATCHLIST,
        LibraryFilter.FOLDERS,
    )

    private const val SEPARATOR = ","

    /** The stored text of an order. Names this build does not know are dropped, and nothing left is "never chosen". */
    fun parse(text: String?): List<LibraryFilter>? = fromNames(text?.split(SEPARATOR))

    fun fromNames(names: List<String>?): List<LibraryFilter>? =
        names?.mapNotNull { name -> LibraryFilter.entries.firstOrNull { it.name == name.trim() } }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }

    fun names(order: List<LibraryFilter>): List<String> = order.map { it.name }

    fun encode(order: List<LibraryFilter>): String = names(order).joinToString(SEPARATOR)

    /** Every chip, in the saved order, with the chips it does not name after them in the default order. */
    fun full(saved: List<LibraryFilter>?): List<LibraryFilter> {
        val known = saved.orEmpty().distinct()
        return known + DEFAULT.filter { it !in known }
    }

    /** True when [filter] has a chip now. Anime and Watchlist need something to show; null is "not known yet". */
    private fun present(filter: LibraryFilter, hasAnime: Boolean?, hasWatchlist: Boolean?): Boolean? = when (filter) {
        LibraryFilter.ANIME -> hasAnime
        LibraryFilter.WATCHLIST -> hasWatchlist
        else -> true
    }

    /**
     * The chips on screen, in order. A conditional chip takes its saved place when it appears. [current] always has
     * its chip, so the filter in view is never without one.
     */
    fun shown(
        saved: List<LibraryFilter>?,
        hasAnime: Boolean,
        hasWatchlist: Boolean,
        current: LibraryFilter? = null,
    ): List<LibraryFilter> = full(saved).filter { it == current || present(it, hasAnime, hasWatchlist) == true }

    /**
     * The filter Library opens on: the first chip of the order that is on screen. Null while that cannot be told
     * yet, because the first candidate is a conditional chip whose list has not loaded; the caller waits, so the
     * screen never opens on one filter and jumps to another.
     */
    fun opening(saved: List<LibraryFilter>?, hasAnime: Boolean?, hasWatchlist: Boolean?): LibraryFilter? {
        for (filter in full(saved)) {
            when (present(filter, hasAnime, hasWatchlist)) {
                true -> return filter
                null -> return null
                false -> continue
            }
        }
        return LibraryFilter.ALL
    }

    /**
     * The whole order after the chip at [from] of [shown] was dropped at [to] of [shown]. Chips that are not on
     * screen keep their places: the moved chip lands right before the chip that now follows it when it moved toward
     * the start, and right after the chip that now precedes it when it moved toward the end.
     */
    fun move(saved: List<LibraryFilter>?, shown: List<LibraryFilter>, from: Int, to: Int): List<LibraryFilter> {
        val whole = full(saved)
        if (from !in shown.indices || to !in shown.indices || from == to) return whole
        val moved = shown[from]
        val after = shown.toMutableList().apply { add(to, removeAt(from)) }
        val rest = whole.filter { it != moved }.toMutableList()
        val at = if (to < from) {
            rest.indexOf(after[to + 1])
        } else {
            rest.indexOf(after[to - 1]) + 1
        }
        rest.add(at.coerceIn(0, rest.size), moved)
        return rest
    }

    /** True when [saved] is no different from never having moved a chip, so there is nothing to reset. */
    fun isDefault(saved: List<LibraryFilter>?): Boolean = saved == null || full(saved) == DEFAULT
}
