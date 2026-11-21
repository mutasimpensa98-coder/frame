package com.frameglass.data.api

/*
 * View sizes (owner request of 2026-11-21): how big the cards of a list are, from a compact list to a large grid.
 * The choice is kept per place in AppSettings. A place with nothing stored shows its default, so an install from
 * before this setting looks as it did.
 */

/** Smallest to largest, in the order the chooser lists them. */
enum class ViewSize {
    /** One short line per item: a small tile, the name, and one line of facts. */
    COMPACT_LIST,

    /** The list row with a thumbnail, the title and its facts. */
    LIST,

    /** Four posters to a line, or three 16:9 frames. The picture alone. */
    SMALL_GRID,

    /** Three posters to a line, or two frames. The grid the app has always had. */
    MEDIUM_GRID,

    /** Two posters to a line, or one frame, with a larger title and one more line of facts. */
    LARGE_GRID,
    ;

    /** A list has one item to a line, whatever the picture's shape. */
    val isList: Boolean get() = this == COMPACT_LIST || this == LIST

    companion object {
        /** A stored or restored name. Anything unknown, including null, is "never chosen". */
        fun parse(name: String?): ViewSize? = entries.firstOrNull { it.name == name }
    }
}

/** A group of screens that share one remembered size. [default] is what it shows until the owner picks. */
enum class ViewPlace(val default: ViewSize) {
    /** Library with any filter but Folders. The grid it has always opened with. */
    LIBRARY(ViewSize.MEDIUM_GRID),

    /** The Folders filter of Library and every folder page. It was a list already, so the compact one leads. */
    FOLDERS(ViewSize.COMPACT_LIST),

    /** Inside the private folder. */
    PRIVATE(ViewSize.MEDIUM_GRID),

    /** The row, genre and person pages, and the results of Search. */
    LISTS(ViewSize.MEDIUM_GRID),
}

/** What the owner chose for [place], or null when nothing was chosen there. */
fun AppSettings.storedViewSize(place: ViewPlace): ViewSize? = when (place) {
    ViewPlace.LIBRARY -> viewLibrary
    ViewPlace.FOLDERS -> viewFolders
    ViewPlace.PRIVATE -> viewPrivate
    ViewPlace.LISTS -> viewLists
}

/** The size [place] shows now. */
fun AppSettings.viewSize(place: ViewPlace): ViewSize = storedViewSize(place) ?: place.default

/** Remembers [size] for [place] and leaves every other place as it was. */
fun AppSettings.withViewSize(place: ViewPlace, size: ViewSize?): AppSettings = when (place) {
    ViewPlace.LIBRARY -> copy(viewLibrary = size)
    ViewPlace.FOLDERS -> copy(viewFolders = size)
    ViewPlace.PRIVATE -> copy(viewPrivate = size)
    ViewPlace.LISTS -> copy(viewLists = size)
}
