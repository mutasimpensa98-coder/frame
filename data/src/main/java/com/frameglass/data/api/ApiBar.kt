package com.frameglass.data.api

/*
 * Bar sections (owner request of 2026-11-22): which of the main pages have a place in the tab bar. The choice is
 * one string in AppSettings. With nothing stored the bar shows the default, so an install from before this setting
 * opens on the new default bar.
 */

/** The pages that can sit in the tab bar, in the order the bar shows them. [id] is what is stored and never changes. */
enum class BarSection(val id: String) {
    HOME("home"),
    FOLDERS("folders"),
    LIBRARY("library"),
    SEARCH("search"),
    SETTINGS("settings"),
    ;

    /** A page of videos. Settings is not one: the bar may lose it, and a gear button then stands in for it. */
    val isContent: Boolean get() = this != SETTINGS

    companion object {
        fun of(id: String?): BarSection? = entries.firstOrNull { it.id == id }
    }
}

/** The rules of the bar's sections. Pure, so they are tested without a phone. */
object BarSections {
    /** Search is a section the owner can turn on. It is off by default, because Library has a search of its own. */
    val Default: List<BarSection> = listOf(BarSection.HOME, BarSection.FOLDERS, BarSection.LIBRARY, BarSection.SETTINGS)

    private const val SEPARATOR = ","

    /** The known sections a stored value names, in bar order, or null when it names no content section at all. */
    private fun known(stored: String?): List<BarSection>? {
        if (stored == null) return null
        val named = stored.split(SEPARATOR).mapNotNullTo(HashSet()) { BarSection.of(it.trim()) }
        return BarSection.entries.filter { it in named }.takeIf { sections -> sections.any { it.isContent } }
    }

    /**
     * The sections a stored value turns on, in bar order. Nothing stored is the default, and so is a value that
     * would leave no content section, such as an empty or a damaged one. An id this build does not know is skipped.
     */
    fun parse(stored: String?): List<BarSection> = known(stored) ?: Default

    /** What is written for [sections]. */
    fun encode(sections: Collection<BarSection>): String =
        BarSection.entries.filter { it in sections }.joinToString(SEPARATOR) { it.id }

    /** A stored or restored value as this build writes it, or null when it holds no usable choice. */
    fun normalise(stored: String?): String? = known(stored)?.let(::encode)

    /** False for the last content section still on: the bar never loses every page of videos. */
    fun canTurnOff(section: BarSection, enabled: Collection<BarSection>): Boolean =
        section in enabled && (!section.isContent || enabled.any { it.isContent && it != section })

    /** [enabled] after one switch moved, in bar order. A switch that may not move leaves it as it was. */
    fun toggle(enabled: Collection<BarSection>, section: BarSection, on: Boolean): List<BarSection> {
        val next = when {
            on -> enabled + section
            canTurnOff(section, enabled) -> enabled - section
            else -> enabled
        }
        return BarSection.entries.filter { it in next }
    }
}

/** The sections in the bar now, in order. */
fun AppSettings.visibleSections(): List<BarSection> = BarSections.parse(barSections)

/** Remembers the owner's choice of sections. */
fun AppSettings.withSections(sections: Collection<BarSection>): AppSettings =
    copy(barSections = BarSections.normalise(BarSections.encode(sections)))
