package com.frameglass.playerui

import com.frameglass.data.api.SegmentKind
import com.frameglass.data.api.SkipSegment

/** What the corner shows during playback, before the episode ends. */
internal enum class NextOffer {
    NONE,

    /** The credits have begun: the next-episode card, counting down when auto-play is on. */
    CREDITS_CARD,

    /** No credits data: a light «الحلقة الجاية» button in the last minute, with no countdown. */
    LIGHT_BUTTON,
}

/** What happens when the item ends. */
internal enum class EndAction {
    /** The sleep timer was set to the end of the episode and the player is on screen: stay, paused at the end. */
    STAY_PAUSED,

    /** Nothing follows, or the sleep timer ended the episode behind the screen: close, and stop background audio. */
    CLOSE,

    /** F-S2: the next-episode card at the end. */
    SHOW_NEXT,
}

/**
 * F-S2 with the credits (round of 2026-11-12), F-S3 and F-T6 end of episode, as pure rules.
 * Credits come from F-S4 data: AniSkip `ed`, TheIntroDB credits or the owner's manual mark, all [SegmentKind.ED].
 */
internal object EpisodeEnd {
    /** The light button shows this long before the end. */
    const val LIGHT_BUTTON_MS = 60_000L
    const val LIGHT_BUTTON_MIN_LENGTH_MS = 4 * LIGHT_BUTTON_MS

    /** A credits segment in the first half is no ending, such as a song in the middle, so it starts no card. */
    private const val CREDITS_FROM_FRACTION = 0.5

    /** The ending credits, or null when the data has none. */
    fun credits(segments: List<SkipSegment>, durationMs: Long): SkipSegment? =
        segments
            .filter { it.kind == SegmentKind.ED && it.endMs > it.startMs }
            .filter { durationMs <= 0 || it.startMs >= durationMs * CREDITS_FROM_FRACTION }
            .minByOrNull { it.startMs }

    fun offer(
        positionMs: Long,
        durationMs: Long,
        segments: List<SkipSegment>,
        hasNext: Boolean,
        sleepsAtEnd: Boolean,
        looping: Boolean,
        creditsDismissed: Boolean,
    ): NextOffer {
        if (!hasNext || sleepsAtEnd || looping) return NextOffer.NONE
        val credits = credits(segments, durationMs)
        if (credits != null) {
            val inCredits = positionMs >= credits.startMs && positionMs < credits.endMs
            return if (inCredits && !creditsDismissed) NextOffer.CREDITS_CARD else NextOffer.NONE
        }
        // In a short video the last minute would be most of it.
        if (durationMs < LIGHT_BUTTON_MIN_LENGTH_MS || positionMs <= 0) return NextOffer.NONE
        return if (durationMs - positionMs <= LIGHT_BUTTON_MS) NextOffer.LIGHT_BUTTON else NextOffer.NONE
    }

    /**
     * F-S3 with the credits: from where they begin the item is finished. The 90 % rule stays where it was, in the
     * position save of :data, so whichever comes first marks it.
     */
    fun creditsReached(positionMs: Long, durationMs: Long, segments: List<SkipSegment>): Boolean {
        val credits = credits(segments, durationMs) ?: return false
        return positionMs >= credits.startMs
    }

    fun atEnd(sleepsAtEnd: Boolean, hasNext: Boolean, onScreen: Boolean): EndAction = when {
        sleepsAtEnd -> if (onScreen) EndAction.STAY_PAUSED else EndAction.CLOSE
        !hasNext -> EndAction.CLOSE
        else -> EndAction.SHOW_NEXT
    }

    /** The owner picked another item by hand: "end of this episode" was about the old one, so it ends. Minutes stay. */
    fun sleepAfterManualPick(sleep: SleepTimer?): SleepTimer? = sleep?.takeIf { it.endsAtElapsed != null }

    /**
     * The owner's credits mark is stored as time before the end, because episodes differ in length. It counts only
     * where no online or stored credits exist, though the brief puts manual first for intros: those sources are
     * timed for this very episode, and the mark is a guess carried over from another.
     */
    fun withManualCredits(segments: List<SkipSegment>, fromEndMs: Long?, durationMs: Long): List<SkipSegment> {
        if (fromEndMs == null || durationMs <= 0 || fromEndMs <= 0 || fromEndMs >= durationMs) return segments
        if (segments.any { it.kind == SegmentKind.ED }) return segments
        return segments + SkipSegment(SegmentKind.ED, durationMs - fromEndMs, durationMs, MANUAL_CREDITS)
    }

    const val MANUAL_CREDITS = "manual-credits"
}
