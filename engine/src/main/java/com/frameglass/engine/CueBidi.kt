package com.frameglass.engine

import android.text.SpannableStringBuilder
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi

/**
 * The cue twin of AssBidi. SubtitleView takes each paragraph's direction from its first strong letter, so a
 * line such as "S01 هو الأول." comes out left to right and its full stop lands on the wrong side.
 */
object CueBidi {
    private const val RIGHT_TO_LEFT_MARK = '\u200F'
    private const val EXPLICIT_MARKS = "\u200E\u200F\u202A\u202B\u202D\u202E\u2066\u2067\u2068"

    private fun isArabic(c: Char) = c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' ||
        c in 'ﭐ'..'﷿' || c in 'ﹰ'..'﻾'

    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /** Where a mark must go: the start of each mostly Arabic paragraph that opens with a Latin letter. */
    fun markOffsets(text: CharSequence): List<Int> {
        val offsets = ArrayList<Int>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (needsMark(text, start, end)) offsets += start
            start = end + 1
        }
        return offsets
    }

    private fun needsMark(text: CharSequence, start: Int, end: Int): Boolean {
        var arabic = 0
        var latin = 0
        var firstStrongIsLatin = false
        var seenStrong = false
        for (index in start until end) {
            val c = text[index]
            if (c in EXPLICIT_MARKS) return false
            val isArabicLetter = isArabic(c)
            val isLatinLetter = isLatin(c)
            if (isArabicLetter) arabic++
            if (isLatinLetter) latin++
            if (!seenStrong && (isArabicLetter || isLatinLetter)) {
                seenStrong = true
                firstStrongIsLatin = isLatinLetter
            }
        }
        return firstStrongIsLatin && arabic > 0 && arabic >= latin
    }

    /** Spans survive, because the text is copied before the marks go in. Bitmap cues pass through. */
    @OptIn(UnstableApi::class)
    fun fix(cue: Cue): Cue {
        val text = cue.text ?: return cue
        val offsets = markOffsets(text)
        if (offsets.isEmpty()) return cue
        val marked = SpannableStringBuilder(text)
        for (offset in offsets.asReversed()) marked.insert(offset, RIGHT_TO_LEFT_MARK.toString())
        return cue.buildUpon().setText(marked).build()
    }
}
