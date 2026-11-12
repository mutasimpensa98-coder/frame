package com.frameglass.engine

/**
 * Which cues show at a given time, for a subtitle that plays beside Media3 and not through it. Overlapping
 * entries show together, in file order. [at] hands back one list object per stretch of time, so a caller
 * can tell by identity that nothing changed.
 */
class CueTimeline<T>(val entries: List<Entry<T>>) {
    data class Entry<T>(val startUs: Long, val endUs: Long, val value: T)

    private val edges: LongArray
    private val shown: List<List<T>>

    init {
        val timed = entries.filter { it.endUs > it.startUs }.sortedBy { it.startUs }
        edges = timed.flatMap { listOf(it.startUs, it.endUs) }.distinct().sorted().toLongArray()
        val running = ArrayList<Entry<T>>()
        var next = 0
        shown = edges.map { edge ->
            while (next < timed.size && timed[next].startUs <= edge) running += timed[next++]
            running.removeAll { it.endUs <= edge }
            running.map { it.value }
        }
    }

    val isEmpty: Boolean get() = edges.isEmpty()

    /** The cues on screen at [positionUs]. */
    fun at(positionUs: Long): List<T> {
        val index = indexAt(positionUs)
        return if (index < 0) emptyList() else shown[index]
    }

    /** When the cues next change after [positionUs], or null when nothing is left to change. */
    fun nextChange(positionUs: Long): Long? = edges.getOrNull(indexAt(positionUs) + 1)

    private fun indexAt(positionUs: Long): Int {
        val found = edges.binarySearch(positionUs)
        return if (found >= 0) found else -found - 2
    }
}
