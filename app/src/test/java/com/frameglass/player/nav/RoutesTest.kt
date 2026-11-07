package com.frameglass.player.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.SaverScope
import com.frameglass.data.api.HomeRowKind
import com.frameglass.data.api.MediaEntryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    private val anything = SaverScope { true }

    private fun roundTrip(routes: List<Route>): List<Route> {
        val stack = mutableStateListOf<Route>().apply { addAll(routes) }
        val saved = with(RouteStackSaver) { anything.save(stack) }
        return RouteStackSaver.restore(requireNotNull(saved)).orEmpty().toList()
    }

    @Test
    fun everyRouteSurvivesProcessDeath() {
        val routes = listOf(
            Route.Onboarding,
            Route.Tabs,
            Route.About,
            Route.MediaManager,
            Route.PrivateFolder,
            Route.Details(7, ArtRef("library:7", "/data/cover.jpg")),
            Route.Details(8),
            Route.Person(3),
            Route.Genre("Drama"),
            Route.Row(HomeRowKind.GENRE, "Drama"),
            Route.Row(HomeRowKind.TOP_RATED, null),
            Route.Folder("/storage/emulated/0/Movies"),
            Route.MediaItems(MediaEntryKind.FOLDER, "folder|primary|Movies/Old", "Old", null, "/storage/emulated/0/Movies/Old"),
            Route.MediaItems(MediaEntryKind.TITLE, "title|9", "Quiet Coast", 9, null),
            Route.MediaItems(MediaEntryKind.CLIP_GROUP, "clips", "Clips", null, null),
        ) + SettingsTopic.entries.map(Route::Settings)
        assertEquals(routes, roundTrip(routes))
    }

    @Test
    fun aMediaRouteRebuildsTheFieldsChildrenReads() {
        val entry = Route.MediaItems(MediaEntryKind.TITLE, "title|9", "Quiet Coast", 9, "/storage/Movies").entry()
        assertEquals(MediaEntryKind.TITLE, entry.kind)
        assertEquals("title|9", entry.id)
        assertEquals(9L, entry.titleId)
        assertEquals("/storage/Movies", entry.folderPath)
    }
}
