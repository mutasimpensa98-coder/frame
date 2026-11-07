package com.frameglass.player.home

import com.frameglass.data.api.HomeFeed

/** What Home shows for a feed. */
internal enum class HomeView {
    /** The first feed has not arrived. */
    Loading,

    /** No video was found, or none is available now. */
    NoVideos,

    /** Cinema mode is on, and none of the videos is a film, a series or an anime. */
    NoCinemaTitles,

    /** Videos exist, but none has hero art yet, none is started and no row fills. They are all in Library. */
    LibraryOnly,

    /** Heroes, Continue watching or rows. */
    Feed,
}

/** A Cinema mode feed with nothing in it is not an empty library, so it is told apart before the list. */
internal fun homeView(feed: HomeFeed?): HomeView = when {
    feed == null -> HomeView.Loading
    feed.empty -> HomeView.NoVideos
    feed.cinemaEmpty -> HomeView.NoCinemaTitles
    feed.heroes.isEmpty() && feed.continueWatching.isEmpty() && feed.rows.isEmpty() -> HomeView.LibraryOnly
    else -> HomeView.Feed
}
