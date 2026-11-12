package com.frameglass.player.nav

import android.app.ActivityOptions
import android.content.Context
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.frameglass.data.api.PlayRef
import com.frameglass.glass.EffectsLevel
import com.frameglass.glass.FrameColors
import com.frameglass.glass.FrameMotion
import com.frameglass.glass.FrameSystemBars
import com.frameglass.glass.FrameTheme
import com.frameglass.glass.rememberEffectsLevel
import com.frameglass.player.details.DetailsScreen
import com.frameglass.player.library.FolderScreen
import com.frameglass.player.library.GenreScreen
import com.frameglass.player.library.RowScreen
import com.frameglass.player.media.DeleteSheet
import com.frameglass.player.media.MediaItemsScreen
import com.frameglass.player.media.MediaManagerScreen
import com.frameglass.player.onboarding.OnboardingScreen
import com.frameglass.player.person.PersonScreen
import com.frameglass.player.settings.AboutScreen
import com.frameglass.player.settings.AppearanceScreen
import com.frameglass.player.settings.BackupSettingsScreen
import com.frameglass.player.settings.LanguageScreen
import com.frameglass.player.settings.LibrarySettingsScreen
import com.frameglass.player.settings.OnlineSettingsScreen
import com.frameglass.player.settings.PlaybackSettingsScreen
import com.frameglass.player.settings.SubtitleSettingsScreen
import com.frameglass.player.ui.ArtUnderBars
import com.frameglass.player.ui.CardMenuHost
import com.frameglass.player.ui.LocalArtUnderBars
import com.frameglass.player.ui.rememberUiModel
import com.frameglass.player.vault.MoveSheet
import com.frameglass.player.stats.StatsScreen
import com.frameglass.player.vault.PrivateFolderScreen
import com.frameglass.playerui.PlayerRoute

/** Moves between screens. One instance per activity. */
@Stable
internal class Navigator(private val stack: SnapshotStateList<Route>, private val context: Context) {
    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun details(titleId: Long, art: ArtRef = ArtRef()) = push(Route.Details(titleId, art))

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Leaves onboarding for the tabs. */
    fun finishOnboarding() {
        stack[stack.lastIndex] = Route.Tabs
    }

    /** The video surface never morphs (brief 7.6), so the player simply fades in. */
    fun play(ref: PlayRef) {
        val fade = ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, android.R.anim.fade_out)
        context.startActivity(PlayerRoute.intent(context, ref), fade.toBundle())
    }
}

internal val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator is provided by FrameRoot") }

private const val PageShift = 12

private fun pushTransform(rtl: Boolean, reduced: Boolean): ContentTransform {
    val enter = fadeIn(FrameMotion.effects())
    return ContentTransform(
        targetContentEnter = if (reduced) {
            enter
        } else {
            // Raw pixels, so the side is flipped by hand for right-to-left.
            enter + slideInHorizontally(FrameMotion.defaultSpatial<IntOffset>()) { width ->
                (if (rtl) -width else width) / PageShift
            }
        },
        initialContentExit = fadeOut(FrameMotion.effects()),
        sizeTransform = null,
    )
}

private fun popTransform(rtl: Boolean, reduced: Boolean): ContentTransform {
    val exit = fadeOut(FrameMotion.effects())
    return ContentTransform(
        targetContentEnter = fadeIn(FrameMotion.effects()),
        initialContentExit = if (reduced) {
            exit
        } else {
            exit + slideOutHorizontally(FrameMotion.defaultSpatial<IntOffset>()) { width ->
                (if (rtl) -width else width) / PageShift
            }
        },
        targetContentZIndex = -1f,
        sizeTransform = null,
    )
}

/**
 * The whole app below the theme: every route inside one shared transition layout. [link] is a page an intent asked
 * for; it opens on top of the restored stack, never over onboarding, and [onLinkOpened] then clears it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FrameRoot(link: Route? = null, onLinkOpened: () -> Unit = {}) {
    val model = rememberUiModel()
    val settings by model.settings.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(FrameColors.Canvas)) {
        // DataStore answers in a few milliseconds; the canvas is what shows until then.
        val loaded = settings ?: return@Box
        val stack = rememberSaveable(saver = RouteStackSaver) {
            mutableStateListOf(if (loaded.onboardingDone) Route.Tabs else Route.Onboarding)
        }
        val context = LocalContext.current
        val navigator = remember(stack, context) { Navigator(stack, context) }
        LaunchedEffect(link) {
            val route = link ?: return@LaunchedEffect
            if (AppLinks.shouldOpen(stack, route)) navigator.push(route)
            onLinkOpened()
        }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val reduced = rememberEffectsLevel() == EffectsLevel.Degraded
        val art = remember { ArtUnderBars() }
        FrameSystemBars(darkIcons = FrameTheme.colors.isLight && !art.present)

        SharedTransitionLayout(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalSharedScope provides this,
                LocalNavigator provides navigator,
                LocalArtUnderBars provides art,
            ) {
                CardMenuHost {
                    NavDisplay(
                        backStack = stack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = navigator::back,
                        sharedTransitionScope = this,
                        transitionSpec = { pushTransform(rtl, reduced) },
                        popTransitionSpec = { popTransform(rtl, reduced) },
                        predictivePopTransitionSpec = { popTransform(rtl, reduced) },
                        entryProvider = entryProvider {
                            entry<Route.Onboarding> { OnboardingScreen() }
                            entry<Route.Tabs> { TabsScreen(tab, onSelect = { tab = it }) }
                            entry<Route.About> { AboutScreen() }
                            entry<Route.Details> { DetailsScreen(it) }
                            entry<Route.Person> { PersonScreen(it.personId) }
                            entry<Route.Genre> { GenreScreen(it.name) }
                            entry<Route.Row> { RowScreen(it.kind, it.label) }
                            entry<Route.Folder> { FolderScreen(it.path) }
                            entry<Route.MediaManager> { MediaManagerScreen() }
                            entry<Route.MediaItems> { MediaItemsScreen(it) }
                            entry<Route.PrivateFolder> { PrivateFolderScreen() }
                            entry<Route.Stats> { StatsScreen() }
                            entry<Route.Settings> { route ->
                                when (route.topic) {
                                    SettingsTopic.APPEARANCE -> AppearanceScreen()
                                    SettingsTopic.LANGUAGE -> LanguageScreen()
                                    SettingsTopic.LIBRARY -> LibrarySettingsScreen()
                                    SettingsTopic.PLAYBACK -> PlaybackSettingsScreen()
                                    SettingsTopic.SUBTITLES -> SubtitleSettingsScreen()
                                    SettingsTopic.ONLINE -> OnlineSettingsScreen()
                                    SettingsTopic.BACKUP -> BackupSettingsScreen()
                                }
                            }
                        },
                    )
                }
                DeleteSheet()
                MoveSheet()
            }
        }
    }
}
