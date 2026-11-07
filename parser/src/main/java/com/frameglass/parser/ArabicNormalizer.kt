package com.frameglass.parser

import java.text.Normalizer

/** Builds keys for search and matching. A key is never shown to the user. */
object ArabicNormalizer {
    fun key(text: String): String {
        // NFKC turns Arabic presentation forms and full-width Latin into their base letters.
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val out = StringBuilder(folded.length)
        var pendingSpace = false
        for (ch in folded) {
            when {
                isInvisibleMark(ch) || isArabicMark(ch) -> Unit
                ch.isWhitespace() -> pendingSpace = out.isNotEmpty()
                else -> {
                    if (pendingSpace) out.append(' ')
                    pendingSpace = false
                    out.append(foldLetter(ch))
                }
            }
        }
        return out.toString()
    }

    private fun foldLetter(ch: Char): Char = when (ch) {
        'آ', 'أ', 'إ' -> 'ا'
        'ة' -> 'ه'
        'ى', 'ئ' -> 'ي'
        'ؤ' -> 'و'
        else -> asciiDigit(ch)
    }

    private fun isArabicMark(ch: Char): Boolean =
        ch in 'ً'..'ٟ' || ch == 'ٰ' || ch in 'ۖ'..'ۭ' || ch == 'ـ'
}

/** Bidi controls plus the other invisible characters that break token comparison. */
internal fun isInvisibleMark(ch: Char): Boolean = when (ch) {
    '\u00AD', '\u061C', '\u200B', '\u200C', '\u200D', '\u200E', '\u200F', '\u2060', '\uFEFF' -> true
    in '\u202A'..'\u202E', in '\u2066'..'\u2069' -> true
    else -> false
}

internal fun asciiDigit(ch: Char): Char = when (ch) {
    in '٠'..'٩' -> '0' + (ch - '٠')
    in '۰'..'۹' -> '0' + (ch - '۰')
    else -> ch
}

internal fun isArabicLetter(ch: Char?): Boolean =
    ch != null && (ch in 'ؠ'..'ۯ' || ch in 'ﭐ'..'﷿' || ch in 'ﹰ'..'ﻼ')
