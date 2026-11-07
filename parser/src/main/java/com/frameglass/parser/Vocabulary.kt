package com.frameglass.parser

/**
 * Word lists. Arabic entries are compared on [ArabicNormalizer.key], so spelling variants of one
 * word need one entry. Arabic is never matched by regex: ICU on ART and java.util.regex disagree.
 */
internal object Vocabulary {
    private fun keys(vararg words: String): Set<String> =
        words.mapTo(HashSet()) { ArabicNormalizer.key(it) }

    private fun lower(vararg words: String): Set<String> =
        words.mapTo(HashSet()) { it.lowercase() }

    val NUMBER_WORD = ArabicNormalizer.key("رقم")
    val SITE_WORD = ArabicNormalizer.key("موقع")
    val OF_WORD = ArabicNormalizer.key("من")

    val EPISODE_WORDS = keys("الحلقة", "حلقة", "ح", "الحلقات", "حلقات", "ايبيسود", "الايبيسود")
    val SEASON_WORDS = keys("الموسم", "موسم", "م", "السيزون", "سيزون")
    val PART_WORDS = keys("الجزء", "جزء", "ج")
    val OVA_WORDS = keys("أوفا", "الأوفا", "أونا", "الأونا")
    val SPECIAL_WORDS = keys("خاصة", "الخاصة")

    private val ORDINALS: Map<String, Int> = buildMap {
        fun add(value: Int, vararg words: String) =
            words.forEach { put(ArabicNormalizer.key(it), value) }
        add(1, "الأول", "الأولى", "الحادي", "الحادية")
        add(2, "الثاني", "الثانية", "التاني", "التانية")
        add(3, "الثالث", "الثالثة", "التالت", "التالتة")
        add(4, "الرابع", "الرابعة")
        add(5, "الخامس", "الخامسة")
        add(6, "السادس", "السادسة")
        add(7, "السابع", "السابعة")
        add(8, "الثامن", "الثامنة", "التامن", "التامنة")
        add(9, "التاسع", "التاسعة")
        add(10, "العاشر", "العاشرة")
    }
    private val TENS: Map<String, Int> = buildMap {
        fun add(value: Int, vararg words: String) =
            words.forEach { put(ArabicNormalizer.key(it), value) }
        add(20, "العشرون", "العشرين")
        add(30, "الثلاثون", "الثلاثين")
        add(40, "الأربعون", "الأربعين")
        add(50, "الخمسون", "الخمسين")
    }
    private val TEEN_WORDS = keys("عشر", "عشرة")
    private val AND = ArabicNormalizer.key("و")

    /** Reads a spelled ordinal at [from], returning its value and how many words it used. */
    fun ordinal(wordKeys: List<String>, from: Int): Pair<Int, Int>? {
        val first = wordKeys.getOrNull(from) ?: return null
        TENS[first]?.let { return it to 1 }
        val unit = ORDINALS[first] ?: return null
        val next = wordKeys.getOrNull(from + 1) ?: return unit to 1
        if (next in TEEN_WORDS) return unit + 10 to 2
        if (next.startsWith(AND)) TENS[next.drop(1)]?.let { return unit + it to 2 }
        if (next == AND) wordKeys.getOrNull(from + 2)?.let { k -> TENS[k]?.let { return unit + it to 3 } }
        return unit to 1
    }

    val SERIES_PREFIX = keys("مسلسل", "برنامج", "كرتون", "سلسلة")
    val MOVIE_PREFIX = keys("فيلم", "فلم", "مسرحية")
    val ANIME_PREFIX = keys("انمي", "انيمي")
    val NOISE_PREFIX = keys("مشاهدة", "تحميل", "تنزيل", "حصريا", "حصري")

    val SUB_ARABIC = keys("مترجم", "مترجمة", "مترجمين", "ترجمة")
    val DUB_ARABIC = keys("مدبلج", "مدبلجة", "مدبلجين", "دبلجة")
    val SUB_LATIN = lower(
        "sub", "subs", "subbed", "subtitle", "subtitles", "subtitled", "esub", "esubs",
        "multisub", "multisubs", "multi-sub", "multi-subs", "arsub", "ar-sub",
    )
    val DUB_LATIN = lower("dub", "dubs", "dubbed", "ardub", "ar-dub", "dublado")

    /** Dropped only at the end of a title, because each can also be a real title word. */
    val TRAILING_NOISE = keys(
        "كامل", "كاملة", "حصريا", "حصري", "اون", "لاين", "اونلاين", "بجودة", "جودة", "عالية",
        "بالعربية", "بالعربي", "للعربية", "نسخة", "أصلية",
    )

    val SITE_NAMES = lower(
        "egybest", "egy-best", "mycima", "wecima", "cima4u", "cimaclub", "cima-club", "cimanow",
        "cimalek", "faselhd", "fasel-hd", "arabseed", "akwam", "shahid4u", "shahed4u", "tuktukcima",
        "movs4u", "fushaar", "hdcima", "egydead", "anime4up", "witanime", "animerco", "animelek",
        "animeiat", "xsanime", "zimabadk", "yts", "yify", "rarbg", "1337x", "tamilrockers",
        "اكوام",
    )

    val YEAR_LEAD_WORDS = lower("of", "in", "from", "to", "the", "since", "before", "after", "at")

    /** A file in such a folder is named on its own: the folder says nothing about it. */
    val GENERIC_FOLDERS = keys(
        "movies", "movie", "films", "film", "tv", "tv shows", "tvshows", "shows", "series", "anime",
        "animes", "cartoons", "cartoon", "kids", "videos", "video", "media", "downloads", "download",
        "telegram", "telegram video", "torrent", "torrents", "complete", "completed", "new folder",
        "whatsapp", "whatsapp video", "dcim", "camera", "android",
        "أفلام", "مسلسلات", "انمي", "انميات", "كرتون", "فيديو", "فيديوهات", "تنزيلات", "تحميلات",
        "التنزيلات", "التحميلات",
    )
    val SPECIAL_FOLDERS = keys(
        "specials", "special", "sp", "sps", "ova", "ovas", "oad", "oads", "حلقات خاصة",
        "الحلقات الخاصة",
    )
    val EXTRA_FOLDERS = keys(
        "extras", "extra", "bonus", "featurettes", "behind the scenes", "deleted scenes",
        "interviews", "trailers", "shorts", "scenes", "samples", "sample", "nc", "ncop", "nced",
        "creditless", "menus", "previews", "pv", "cm", "openings", "endings", "إضافات", "ملحقات",
    )
    val GENERIC_STEMS = keys("movie", "film", "video", "main", "فيلم", "فلم", "فيديو")

    /** Folders that cameras, screen recorders and social apps save into. Telegram is left out on purpose: real downloads land there. */
    val CLIP_FOLDERS = keys(
        "dcim", "camera", "screenshots", "screenshot", "screen recordings", "screen recording", "screenrecorder",
        "screen recorder", "screenrecord", "screencasts", "videocaptures", "instagram", "messenger", "tiktok",
        "snapchat", "facebook", "signal", "capcut", "inshot",
    )
    val WHATSAPP_WORD = ArabicNormalizer.key("whatsapp")

    /** A whole name that says nothing, unless a folder gives the title. */
    val CLIP_STEMS = keys("video", "clip", "فيديو", "مقطع")

    /** How the same stamps read on a phone set to Arabic. Each needs a date after it to count. */
    val CLIP_STEM_STARTS = keys("فيديو واتساب", "فيديو واتس اب", "مقطع فيديو من واتساب", "تسجيل الشاشة", "تسجيل شاشة")

    /** Counted only as the last word after the title, so episode titles can hold these words. */
    val TAIL_EXTRAS = lower("sample", "trailer", "teaser")
    val TITLE_END_EXTRAS = lower("trailer", "teaser")
}
