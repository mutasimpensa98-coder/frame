package com.frameglass.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringTest {
    private fun score(query: String, name: String, queryYear: Int? = null, year: Int? = null) =
        Scoring.score(listOf(query), listOf(name), queryYear, year)

    private fun pick(vararg scores: Double) = Scoring.pick(scores.toList()) { it }

    @Test
    fun exactTitleScoresNinety() = assertEquals(0.90, score("Quiet Coast", "Quiet Coast"), 1e-9)

    @Test
    fun originalTitleCountsAsExact() {
        val score = Scoring.score(listOf("الاختيار"), listOf("The Choice", "الاختيار"), null, null)
        assertEquals(0.90, score, 1e-9)
    }

    @Test
    fun anAlternativeTitleCountsAsExact() {
        val names = listOf("The Choice", "Original", "Alternative Name")
        assertEquals(0.90, Scoring.score(listOf("alternative name"), names, null, null), 1e-9)
    }

    @Test
    fun caseAndPunctuationDoNotBreakExact() {
        assertEquals(0.90, score("spider man no way home", "Spider-Man: No Way Home"), 1e-9)
    }

    @Test
    fun accentsAndApostrophesDoNotBreakExact() {
        assertEquals(0.90, score("Pokemon", "Pokémon"), 1e-9)
        assertEquals(0.90, score("Frieren Beyond Journeys End", "Frieren: Beyond Journey’s End"), 1e-9)
    }

    @Test
    fun otherwiseTokenJaccardTimes078() {
        assertEquals(2.0 / 3.0 * 0.78, score("Quiet Coast", "Quiet Coast Returns"), 1e-9)
        assertEquals(0.0, score("Quiet Coast", "Loud Mountain"), 1e-9)
    }

    @Test
    fun jaccardIgnoresWordOrderButExactDoesNot() {
        assertEquals(0.78, score("Coast Quiet", "Quiet Coast"), 1e-9)
    }

    @Test
    fun yearMatchAddsTen() = assertEquals(1.00, score("Quiet Coast", "Quiet Coast", 2026, 2026), 1e-9)

    @Test
    fun yearMismatchTakesTwentyFive() = assertEquals(0.65, score("Quiet Coast", "Quiet Coast", 2026, 2019), 1e-9)

    @Test
    fun anUnknownYearChangesNothing() {
        assertEquals(0.90, score("Quiet Coast", "Quiet Coast", null, 2019), 1e-9)
        assertEquals(0.90, score("Quiet Coast", "Quiet Coast", 2026, null), 1e-9)
    }

    @Test
    fun arabicIsComparedThroughTheNormalizer() {
        assertEquals(0.90, score("الْعُنْوَان", "العنوان"), 1e-9)
        assertEquals(0.90, score("مدرسة", "مدرسه"), 1e-9)
    }

    @Test
    fun autoNeedsSeventyFive() {
        assertEquals(0.75, pick(0.75)!!, 1e-9)
        assertNull(pick(0.7499))
    }

    @Test
    fun aMarginOfExactlyTwelvePasses() {
        // 0.90 and 0.78 are the two rule outputs this boundary really happens between.
        assertNotNull(pick(Scoring.EXACT, Scoring.JACCARD_WEIGHT))
    }

    @Test
    fun aMarginUnderTwelveFails() = assertNull(pick(0.90, 0.79))

    @Test
    fun anExactTitleInTheWrongYearIsNotAuto() {
        val wrongYear = score("Quiet Coast", "Quiet Coast", 2026, 2019)
        assertNull(pick(wrongYear))
    }

    @Test
    fun twoIdenticalTitlesAreLeftToTheOwner() = assertNull(pick(0.90, 0.90))

    @Test
    fun noCandidatesPicksNothing() = assertNull(Scoring.pick(emptyList<Double>()) { it })
}
