package com.frameglass.glass

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The geometry of [LiquidTabBar] as plain numbers, so it is tested without a screen.
 *
 * Two scales are used. A *position* counts tabs from the start edge: 0 is the first tab and 1.5 is halfway between
 * the second and the third. A *unit* counts slot widths from the start edge of the first slot, so the first tab's
 * centre is 0.5. The two differ only while a tab is arriving or leaving, when its slot is narrower than the others:
 * units stay continuous through that, which is why the pill is kept in them. Nothing here knows the layout
 * direction except [fromStart], which is the one place a raw x is mirrored.
 */
internal object TabBarMath {
    /** From this many tabs up the bar spans the width it is given. */
    const val FullWidthTabs = 4f

    /** Width of the pill in slots. */
    const val PillSlots = 1.16f

    /** The share of a slot its icon and label cover, for [coverage]. */
    const val ContentSlots = 0.7f

    /** Slots per second at which the pill reaches its full stretch. A one-tab move peaks near 10. */
    const val FullStretchSpeed = 8f

    /** How far past the midpoint between two tabs the pill must be before the tick of [tick] moves on. */
    const val TickHysteresis = 0.08f

    /** A label drawn smaller than this share of its size is not drawn at all. */
    const val MinLabelScale = 0.8f

    /** The band of [labelScale] over which a label fades out before [MinLabelScale]. */
    const val LabelFade = 0.06f

    /**
     * Width of the bar for [count] tabs, which is fractional while one arrives or leaves. Four tabs and more span
     * [full]. Fewer make a shorter capsule: [padding] at both ends and one slot per tab, each as wide as a slot of
     * the four-tab bar and never wider than [slotCap], so two tabs on a wide screen stay a capsule.
     */
    fun barWidth(count: Float, full: Float, padding: Float, slotCap: Float): Float {
        if (count >= FullWidthTabs) return full
        val slot = min((full - 2 * padding) / FullWidthTabs, slotCap)
        val short = FullWidthTabs - 1
        val width = if (count <= short) {
            2 * padding + max(count, 0f) * slot
        } else {
            // The last step up to the full width, which is more than one slot where the cap holds.
            val three = 2 * padding + short * slot
            three + (full - three) * (count - short)
        }
        return min(width, full)
    }

    /** Width of one whole slot in a bar [barWidth] wide that holds [count] tabs. */
    fun slotWidth(count: Float, barWidth: Float, padding: Float): Float =
        (barWidth - 2 * padding) / max(count, 1f)

    /** Centres of the slots in units. A slot is as wide as its weight: 1 for a tab at rest, less on its way in or out. */
    fun centers(weights: FloatArray): FloatArray {
        var edge = 0f
        return FloatArray(weights.size) { index ->
            val weight = weights[index]
            val center = edge + weight / 2f
            edge += weight
            center
        }
    }

    /** Pixels from the start edge of the bar for [unit]. */
    fun pixel(unit: Float, slot: Float, padding: Float): Float = padding + unit * slot

    /** The unit under a point [fromStart] pixels from the start edge of the bar. */
    fun unit(fromStart: Float, slot: Float, padding: Float): Float = if (slot > 0f) (fromStart - padding) / slot else 0f

    /** A raw x in a bar [width] wide as a distance from the start edge, and back: the two are the same fold. */
    fun fromStart(x: Float, width: Float, rtl: Boolean): Float = if (rtl) width - x else x

    /** The unit of a pill at [position] over tabs centred at [centers], held between the first and the last. */
    fun unitAt(position: Float, centers: FloatArray): Float {
        if (centers.isEmpty()) return 0f
        val clamped = position.coerceIn(0f, centers.lastIndex.toFloat())
        val before = clamped.toInt().coerceAtMost(centers.lastIndex)
        val after = min(before + 1, centers.lastIndex)
        return centers[before] + (centers[after] - centers[before]) * (clamped - before)
    }

    /**
     * The position of a pill at [unit] over tabs centred at [centers]: [unitAt] the other way round, so pages
     * brought to it stand exactly where the pill is.
     */
    fun positionAt(unit: Float, centers: FloatArray): Float {
        if (centers.size < 2) return 0f
        val held = clamp(unit, centers)
        var before = 0
        while (before < centers.lastIndex - 1 && centers[before + 1] <= held) before++
        val span = centers[before + 1] - centers[before]
        return if (span > 0f) before + (held - centers[before]) / span else before.toFloat()
    }

    /** A finger's unit held to where the pill can go: from the first tab's centre to the last one's. */
    fun clamp(unit: Float, centers: FloatArray): Float =
        if (centers.isEmpty()) 0f else unit.coerceIn(centers.first(), centers.last())

    /** The tab whose centre is nearest to [unit]. Of two equally near, the earlier one. */
    fun nearest(unit: Float, centers: FloatArray): Int {
        var best = 0
        for (index in centers.indices) {
            if (abs(centers[index] - unit) < abs(centers[best] - unit)) best = index
        }
        return best
    }

    /**
     * The tab the last haptic tick was for, after the pill moved to [unit]: the nearest one, once the pill is
     * [TickHysteresis] past the midpoint. A finger resting on a midpoint therefore ticks once and not on every tremor.
     */
    fun tick(last: Int, unit: Float, centers: FloatArray): Int {
        if (centers.isEmpty()) return last
        val near = nearest(unit, centers)
        if (last !in centers.indices) return near
        return if (abs(centers[last] - unit) > abs(centers[near] - unit) + 2 * TickHysteresis) near else last
    }

    /**
     * How much of a tab's icon and label the pill covers, from 0 to 1, with the pill at [unit] and the tab centred
     * at [center]. The content of a tab takes the colour of text on the pill by this share, so colours travel with it.
     */
    fun coverage(unit: Float, center: Float): Float {
        val distance = abs(unit - center)
        val overlap = (PillSlots + ContentSlots) / 2f - distance
        return (overlap / ContentSlots).coerceIn(0f, 1f)
    }

    /** How much wider than itself the pill is drawn while it moves at [speed] slots per second. */
    fun stretch(speed: Float): Float = FrameMotion.PillStretch * min(abs(speed) / FullStretchSpeed, 1f)

    /**
     * The scale every label is drawn at so the widest one, [natural] pixels, fits a slot with [gutter] at both
     * sides. All labels share it, so the bar never mixes sizes.
     */
    fun labelScale(slot: Float, natural: Float, gutter: Float): Float =
        if (natural <= 0f) 1f else ((slot - 2 * gutter) / natural).coerceIn(0f, 1f)

    /** Labels fade out when they would have to shrink below [MinLabelScale], and the icons then stand alone. */
    fun labelAlpha(scale: Float): Float = ((scale - MinLabelScale) / LabelFade).coerceIn(0f, 1f)
}
