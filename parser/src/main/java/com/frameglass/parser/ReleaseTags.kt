package com.frameglass.parser

/** Release tokens. Strong ones end a title wherever they stand; weak ones can be title words. */
internal object ReleaseTags {
    private val STRONG: Map<String, String> = canonical(
        "2160p", "1440p", "1080p", "1080i", "720p", "576p", "480p", "360p", "4K", "8K",
        "WEB-DL", "WEBRip", "BluRay", "BDRip", "BRRip", "BDRemux", "DVDRip", "DVDScr", "HDRip",
        "HDTV", "HDTVRip", "HDCAM", "HDTS", "PDTV", "SATRip", "TVRip", "VHSRip", "Remux",
        "x264", "x265", "H264", "H265", "HEVC", "AV1", "VP9", "XviD", "DivX", "10bit", "8bit",
        "Hi10", "Hi10P", "HDR10", "HDR10+", "DoVi", "AC3", "EAC3", "E-AC3", "TrueHD", "Atmos",
        "DTS-HD", "DTS-X", "FLAC", "AAC",
    ) + mapOf(
        "webdl" to "WEB-DL", "web-rip" to "WEBRip", "blu-ray" to "BluRay",
        "bd-rip" to "BDRip", "br-rip" to "BRRip", "dvd-rip" to "DVDRip",
    )
    private val WEAK: Map<String, String> = canonical(
        "WEB", "HD", "SD", "UHD", "FHD", "QHD", "DVD", "BD", "HDR", "SDR", "DV", "AVC", "DTS",
        "MP3", "Opus", "DD", "DDP", "PROPER", "REPACK", "RERIP", "REAL", "INTERNAL", "LIMITED",
        "EXTENDED", "UNRATED", "UNCUT", "UNCENSORED", "CENSORED", "REMASTERED", "THEATRICAL",
        "IMAX", "3D", "DUAL", "Dual-Audio", "MULTI", "NF", "AMZN", "HMAX", "DSNP", "ATVP", "HULU",
        "CR", "CAM", "TS", "TC", "HC", "5.1", "7.1", "2.0", "2.1",
    )
    private val DROPPABLE_AT_END = setOf(
        "hd", "sd", "uhd", "fhd", "qhd", "dvd", "proper", "repack", "remastered", "uncut",
        "unrated", "uncensored", "dual-audio",
    )

    private val RESOLUTION = Regex("[0-9]{3,4}[pPiI]")
    private val DIMENSIONS = Regex("[0-9]{3,4}[xX][0-9]{3,4}")
    private val AUDIO =
        Regex("(?:AAC|AC3|EAC3|DDP?|DD\\+|DTS|TrueHD|FLAC|Opus)[0-9][.][0-9]", RegexOption.IGNORE_CASE)
    private val DISC = Regex("(?:CD|Disc|Disk|DVD)[0-9]", RegexOption.IGNORE_CASE)

    private fun canonical(vararg tags: String): Map<String, String> =
        tags.associateBy { it.lowercase() }

    private fun patterned(token: String): Boolean =
        RESOLUTION.matches(token) || DIMENSIONS.matches(token) || AUDIO.matches(token)

    /** A strong tag at the start of [token], which may carry a `-GROUP` suffix. */
    fun strong(token: String): String? {
        STRONG[token.lowercase()]?.let { return it }
        if (patterned(token)) return token
        val dash = token.lastIndexOf('-')
        return if (dash > 0) strong(token.substring(0, dash)) else null
    }

    fun weak(token: String): String? =
        WEAK[token.lowercase()] ?: token.takeIf { DISC.matches(it) }

    /** The canonical form of [token] taken whole, with no group suffix. */
    fun exact(token: String): String? =
        STRONG[token.lowercase()] ?: WEAK[token.lowercase()]
            ?: token.takeIf { patterned(it) || DISC.matches(it) }

    fun isDisc(token: String): Boolean = DISC.matches(token)

    fun droppableAtEnd(token: String): Boolean =
        token.lowercase() in DROPPABLE_AT_END || isDisc(token)
}
