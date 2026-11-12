package com.frameglass.player.tour

import com.frameglass.player.nav.Route

/** Where the feature tour sits in the app's flow. */
internal object TourFlow {
    /**
     * The first page on launch. A fresh install opens on the tour, and its setup steps follow it. An install that
     * finished onboarding, such as one updated in place, never shows the tour by itself.
     */
    fun firstRoute(onboardingDone: Boolean): Route = if (onboardingDone) Route.Tabs else Route.Tour(setup = true)

    /**
     * What replaces [tour] when it ends or is skipped: the setup steps on the first run, or null to go back to the
     * page that opened it, which is Settings.
     */
    fun after(tour: Route.Tour): Route? = if (tour.setup) Route.Onboarding else null
}
