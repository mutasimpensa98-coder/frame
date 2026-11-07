package com.frameglass.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class ArabicNormalizerTest {
    private fun key(text: String) = ArabicNormalizer.key(text)

    @Test
    fun lowercases() = assertEquals("frieren", key("FrIeReN"))

    @Test
    fun stripsTashkeel() = assertEquals("العنوان", key("الْعُنْوَانُ"))

    @Test
    fun stripsEveryMarkInTheRange() {
        for (mark in 'ً'..'ٟ') assertEquals("با", key("ب${mark}ا"), mark.code.toString(16))
        for (mark in 'ۖ'..'ۭ') assertEquals("با", key("ب${mark}ا"), mark.code.toString(16))
        assertEquals("با", key("بٰا"))
    }

    @Test
    fun stripsTatweel() = assertEquals("مسلسل", key("مـسـلـسـل"))

    @Test
    fun stripsBidiMarks() {
        val marks = "‎‏؜‪‫‬‭‮⁦⁧⁨⁩"
        for (mark in marks) assertEquals("ab", key("a${mark}b"), mark.code.toString(16))
    }

    @Test
    fun stripsOtherInvisibleCharacters() {
        for (mark in "​‌‍⁠﻿­") {
            assertEquals("ab", key("a${mark}b"), mark.code.toString(16))
        }
    }

    @Test
    fun foldsAlefForms() {
        assertEquals("احمد", key("أحمد"))
        assertEquals("اسلام", key("إسلام"))
        assertEquals("امن", key("آمن"))
    }

    @Test
    fun foldsTehMarbuta() = assertEquals("مدرسه", key("مدرسة"))

    @Test
    fun foldsAlefMaksura() = assertEquals("موسي", key("موسى"))

    @Test
    fun foldsHamzaCarriers() {
        assertEquals("مومن", key("مؤمن"))
        assertEquals("قايد", key("قائد"))
    }

    @Test
    fun readsArabicIndicDigits() {
        assertEquals("0123456789", key("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("0123456789", key("۰۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun decomposedAndComposedHamzaMatch() {
        assertEquals(key("أحمد"), key("أحمد"))
        assertEquals(key("ؤ"), key("ؤ"))
        assertEquals(key("ئ"), key("ئ"))
        assertEquals(key("آ"), key("آ"))
    }

    @Test
    fun presentationFormsMatchBaseLetters() {
        assertEquals(key("لا"), key("ﻻ"))
        assertEquals(key("عنوان"), key("ﻋﻨﻮﺎﻥ"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() = assertEquals("a b c", key("  a  b\tc  "))

    @Test
    fun spellingVariantsShareAKey() {
        assertEquals(key("الحلقة الأولى"), key("الحلقه الاولي"))
        assertEquals(key("إنمي"), key("أنمي"))
        assertEquals(key("آنمي"), key("انمي"))
    }

    @Test
    fun keepsLatinPunctuationAndOtherScripts() {
        assertEquals("re:zero", key("Re:Zero"))
        assertEquals("ہ", key("ہ"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", key(""))
        assertEquals("", key(" ‏ "))
    }

    @Test
    fun isIdempotent() {
        for (text in listOf("الْعُنْوَان", "أحمد ١٢", "Frieren  S2", "‏موسى")) {
            assertEquals(key(text), key(key(text)))
        }
    }
}
