package com.frameglass.net

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Replies below are hand-written in the shapes the providers' docs and working clients show.
 * Keys are the runtime dummies from TestSupport, so no key-like literal sits here (R4).
 */

internal const val OS_SEARCH = "api.opensubtitles.com/api/v1/subtitles"
internal const val OS_DOWNLOAD = "api.opensubtitles.com/api/v1/download"
internal const val OS_FILE_HOST = "www.opensubtitles.com"
internal const val SUBDL_SEARCH = "api.subdl.com/api/v1/subtitles"
internal const val SUBSOURCE_MOVIES = "api.subsource.net/api/v1/movies/search"
internal const val SUBSOURCE_LIST = "api.subsource.net/api/v1/subtitles"

internal const val SRT_TEXT = "1\n00:00:01,000 --> 00:00:02,000\nHello\n\n2\n00:00:03,000 --> 00:00:04,000\nBye\n"
internal const val VTT_TEXT = "WEBVTT\n\n00:01.000 --> 00:02.000\nHello\n"
internal const val ASS_TEXT = "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\nFormat: Name\n\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\n"
internal const val SSA_TEXT = "[Script Info]\nScriptType: v4.00\n\n[V4 Styles]\nFormat: Name\n\n[Events]\nDialogue: Marked=0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\n"

internal fun subtitleQuery(
    title: String = "Quiet Coast",
    year: Int? = 2024,
    season: Int? = null,
    episode: Int? = null,
    tmdbId: Long? = null,
    imdbId: String? = null,
    languages: List<String> = listOf("ar", "en"),
    osHash: String? = null,
    fileSize: Long? = null,
    releaseName: String? = null,
) = SubtitleQuery(title, year, season, episode, tmdbId, imdbId, languages, osHash, fileSize, releaseName)

internal fun subtitleHit(
    provider: String = PROVIDER_OPENSUBTITLES,
    id: String = "1",
    language: String = "ar",
    release: String = "Release",
    hashMatch: Boolean = false,
    downloads: Int? = null,
    format: String? = null,
    seasonPack: Boolean = false,
) = SubtitleHit(provider, id, language, release, hashMatch, downloads, format, seasonPack)

internal fun osItem(
    fileId: Long,
    release: String = "Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP",
    downloads: Int = 10,
    hashMatch: Boolean = false,
    language: String = "ar",
) = """{"id":"$fileId","type":"subtitle","attributes":{"subtitle_id":"$fileId","language":"$language",""" +
    """"download_count":$downloads,"new_download_count":1,"hearing_impaired":false,"hd":true,"fps":23.976,""" +
    """"votes":2,"ratings":8.5,"from_trusted":true,"foreign_parts_only":false,""" +
    """"upload_date":"2024-03-01T10:00:00Z","ai_translated":false,"machine_translated":false,""" +
    """"moviehash_match":$hashMatch,"release":"$release","format":"srt",""" +
    """"feature_details":{"feature_id":1,"feature_type":"Episode","year":2024,"title":"Quiet Coast",""" +
    """"season_number":1,"episode_number":2,"parent_tmdb_id":4},""" +
    """"url":"https://www.opensubtitles.com/ar/subtitles/legacy/$fileId",""" +
    """"files":[{"file_id":$fileId,"cd_number":1,"file_name":"$release.srt"}]}}"""

internal fun osSearch(vararg items: String) =
    """{"total_pages":1,"total_count":${items.size},"per_page":60,"page":1,"data":[${items.joinToString(",")}]}"""

internal fun osTicket(link: String, remaining: Int = 4) =
    """{"link":"$link","file_name":"sample.srt","requests":1,"remaining":$remaining,""" +
        """"message":"Your quota will be renewed in 07 hours","reset_time":"07 hours",""" +
        """"reset_time_utc":"2026-11-08T00:00:00.000Z"}"""

internal fun subDlItem(
    url: String = "/subtitle/3197651-3213944.zip",
    release: String = "Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP",
    season: String = "1",
    episode: String = "2",
    from: String = "null",
    end: String = "null",
    fullSeason: Boolean = false,
    releases: List<String> = listOf(release),
) = """{"release_name":"$release","name":"SUBDL::sample.zip","lang":"arabic","author":"someone","url":"$url",""" +
    """"subtitlePage":"/s/info/x","season":$season,"episode":$episode,"language":"AR","hi":false,""" +
    """"episode_from":$from,"episode_end":$end,"full_season":$fullSeason,""" +
    """"releases":[${releases.joinToString(",") { "\"$it\"" }}]}"""

internal fun subDlReply(vararg items: String) =
    """{"status":true,"results":[{"sd_id":1,"type":"tv","name":"Quiet Coast","tmdb_id":77,"year":2024}],""" +
        """"subtitles":[${items.joinToString(",")}]}"""

internal fun ssMovie(
    id: Long,
    title: String = "Quiet Coast",
    year: Int = 2024,
    season: String = "1",
    imdb: String = "tt0133093",
    tmdb: String = "77",
) = """{"movieId":$id,"title":"$title","alternateTitle":null,"type":"tvseries","releaseYear":$year,""" +
    """"imdbId":"$imdb","tmdbId":$tmdb,"season":$season,"subtitleCount":12,"posters":{}}"""

internal fun ssMovies(vararg rows: String) = """{"success":true,"data":[${rows.joinToString(",")}]}"""

internal fun ssSubtitle(
    id: Long,
    releases: List<String> = listOf("Quiet.Coast.S01E02.1080p.WEB-DL.x265-GROUP"),
    files: Int = 1,
    downloads: Long = 40,
) = """{"subtitleId":$id,"movieId":5,"language":"arabic","releaseInfo":[${releases.joinToString(",") { "\"$it\"" }}],""" +
    """"commentary":"","files":$files,"size":2048,"hearingImpaired":false,"foreignParts":false,"framerate":null,""" +
    """"productionType":"translated","releaseType":"web","downloads":$downloads,"comments":0,""" +
    """"rating":{"good":1,"bad":0,"total":1},"uploaderId":9,"createdAt":"2024-03-01T10:00:00.000Z",""" +
    """"contributors":[],"link":"/subtitle/quiet-coast/arabic/$id"}"""

internal fun ssList(vararg rows: String) =
    """{"success":true,"data":[${rows.joinToString(",")}],"pagination":{"page":1,"limit":100,"total":${rows.size},"pages":1}}"""

internal const val GD_HOST = "api.gestdown.info"
internal const val GD_SHOW_ID = "4QuietCoastShow00000a"
internal const val GD_SHOWS = "$GD_HOST/shows/search/Quiet%20Coast"

internal fun gdEpisodePath(season: Int, episode: Int, language: String, show: String = GD_SHOW_ID) =
    "$GD_HOST/subtitles/get/$show/$season/$episode/$language"

internal fun gdShow(
    id: String = GD_SHOW_ID,
    name: String = "Quiet Coast",
    tmdb: String = "77",
    seasons: List<Int> = listOf(1, 2),
) = """{"id":"$id","name":"$name","nbSeasons":${seasons.size},"seasons":$seasons,"tvDbId":null,""" +
    """"tmdbId":$tmdb,"slug":"quiet-coast"}"""

internal fun gdShows(vararg rows: String) = """{"shows":[${rows.joinToString(",")}]}"""

internal fun gdSubtitle(
    id: String = "7SubOne0000000000000a",
    version: String = "WEB-DL GROUP",
    completed: Boolean = true,
    downloads: Int = 40,
) = """{"subtitleId":"$id","version":"$version","completed":$completed,"hearingImpaired":false,"corrected":true,""" +
    """"hd":true,"downloadUri":"/subtitles/download/$id","language":"Arabic","discovered":"2024-03-01T10:00:00Z",""" +
    """"downloadCount":$downloads,"source":"Addic7ed","qualities":["1080p"],"release":null}"""

internal fun gdEpisode(vararg subtitles: String) =
    """{"matchingSubtitles":[${subtitles.joinToString(",")}],""" +
        """"episode":{"season":1,"number":2,"title":"Harbour","show":"Quiet Coast","discovered":"2024-03-01T10:00:00Z"}}"""

internal fun zipOf(vararg files: Pair<String, String>, charset: Charset = Charsets.UTF_8): ByteArray =
    zipBytesOf(files.map { (name, text) -> name to text.toByteArray() }, charset)

internal fun zipBytesOf(files: List<Pair<String, ByteArray>>, charset: Charset = Charsets.UTF_8): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes, charset).use { zip ->
        for ((name, content) in files) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}
