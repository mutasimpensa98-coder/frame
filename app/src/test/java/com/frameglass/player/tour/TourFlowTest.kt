package com.frameglass.player.tour

import com.frameglass.player.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TourFlowTest {
    @Test
    fun aFreshInstallOpensOnTheTourOfTheFirstRun() {
        assertEquals(Route.Tour(setup = true), TourFlow.firstRoute(onboardingDone = false))
    }

    @Test
    fun anInstallThatFinishedOnboardingNeverShowsTheTourByItself() {
        assertEquals(Route.Tabs, TourFlow.firstRoute(onboardingDone = true))
    }

    @Test
    fun theFirstRunTourLeadsToTheSetupSteps() {
        assertEquals(Route.Onboarding, TourFlow.after(Route.Tour(setup = true)))
    }

    @Test
    fun theTourFromSettingsGoesBackToSettings() {
        assertNull(TourFlow.after(Route.Tour(setup = false)))
    }
}
