package com.frameglass.net

import java.io.File
import java.io.StringWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Rows copied from mappings.min.json, schema 3.0.3, read on 2026-11-07 (MIT). */
internal val MAPPINGS_EXCERPT = """
{"${'$'}meta":{"schema_version":"3.0.3","generated_on":"2026-11-07T08:06:52Z"},
 "anidb:916:R":{"anilist:95":{"1-50":"1-50"},"tmdb_show:37546:s1":{"1-25":"1-25"}},
 "anilist:21":{"anidb:69:R":{"1-1180":"1-1180"},"mal:21":{"1-1180":"1-1180"},
   "tmdb_show:37854:s21":{"892-1088":"892-1088"},"tmdb_show:37854:s22":{"1089-":"1089-"},
   "tvdb_show:81797:s0":{"542":"39"}},
 "anilist:95":{"mal:95":{"1-50":"1-50"},"tmdb_show:37546:s1":{"1-50":"1-25|2"},"tvdb_show:71585:s1":{"1-50":"1-50"}},
 "anilist:103":{"tmdb_show:34179:s1":{"1-30":"1-30"},"tmdb_show:34179:s2":{"31-56":"1-26"},"tmdb_show:34179:s3":{"57-74":"1-18"}},
 "anilist:280":{"mal:280":{"1-51":"1-51"},"tmdb_show:34772:s1":{"1-51":"1-102|-2"}},
 "anilist:400":{"tmdb_show:35106:s1":{"1-24":"1-15,17-22,24-26"}},
 "anilist:559":{"tmdb_show:13375:s0":{"1":"1-4|-4"}},
 "anilist:849":{"anidb:3651:R":{"1-14":"1-14"},"mal:849":{"1-14":"1-14"}},
 "anilist:900":{"tmdb_show:900:s0":{"1":"7"},"tmdb_show:900:s2":{"1-3":"1-3"}},
 "anilist:901":{"tmdb_movie:128":{"1":"1"}},
 "anilist:902":{"tmdb_show:902:s1":{"1-2":null}},
 "mal:21":{"anilist:21":{"1-1180":"1-1180"},"tmdb_show:37854:s21":{"892-1088":"892-1088"}},
 "mal:95":{"tmdb_show:37546:s1":{"1-50":"1-25|2"}},
 "mal:3000":{"tmdb_show:34179:s2":{"1-26":"1-26"}},
 "tmdb_movie:128":{"anilist:901":{"1":"1"}},
 "tmdb_show:42511:s1":{"anidb:3651:R":{"1-14":"1-14"},"anilist:849":{"1-14":"1-14|0"},"anilist:4382":{"1-28":"1-28|0"}},
 "tmdb_show:999:s4":{"mal:555":{"1-25":"1-50|-2"}},
 "tvdb_show:2:s0":{"anilist:1001":{}}
}
""".trimIndent()

class EpisodeRangeTest {
    private fun range(source: String, target: String) = EpisodeRange.parse(source, target)!!

    @Test
    fun aPlainRangeShiftsOneToOne() {
        val range = range("31-56", "1-26")
        assertEquals(1, range.forward(31))
        assertEquals(26, range.forward(56))
        assertNull(range.forward(30))
        assertNull(range.forward(57))
    }

    @Test
    fun aSingleEpisodeIsARangeOfOne() {
        assertEquals(39, range("542", "39").forward(542))
        assertNull(range("542", "39").forward(543))
    }

    @Test
    fun anOpenRangeKeepsGoing() {
        val range = range("14-", "13-")
        assertEquals(13, range.forward(14))
        assertEquals(1187, range.forward(1188))
        assertEquals(1188, range.backward(1187))
    }

    @Test
    fun aGappedTargetSkipsTheGap() {
        val range = range("1-24", "1-15,17-22,24-26")
        assertEquals(15, range.forward(15))
        assertEquals(17, range.forward(16))
        assertEquals(22, range.forward(21))
        assertEquals(24, range.forward(22))
        assertEquals(26, range.forward(24))
        assertEquals(16, range.backward(17))
        assertNull(range.backward(16))
    }

    @Test
    fun aPositiveRatioPacksNSourceEpisodesIntoOneTarget() {
        // Real row: 50 AniList episodes live in 25 TMDB episodes. The README's prose says the opposite.
        val range = range("1-50", "1-25|2")
        assertEquals(1, range.forward(1))
        assertEquals(1, range.forward(2))
        assertEquals(2, range.forward(3))
        assertEquals(25, range.forward(50))
        assertEquals(3, range.backward(2))
    }

    @Test
    fun aNegativeRatioSpreadsOneSourceEpisodeOverNTargets() {
        // Real row: 51 AniList episodes span 102 TMDB episodes.
        val range = range("1-51", "1-102|-2")
        assertEquals(1, range.forward(1))
        assertEquals(3, range.forward(2))
        assertEquals(101, range.forward(51))
        assertEquals(1, range.backward(2))
        assertEquals(2, range.backward(3))
        assertEquals(51, range.backward(102))
    }

    @Test
    fun aRatioOfZeroIsOneToOne() {
        assertEquals(5, range("1-14", "1-14|0").forward(5))
        assertEquals(5, range("1-14", "1-14|0").backward(5))
    }

    @Test
    fun aTargetShorterThanItsSourceHasNoAnswerPastItsEnd() {
        assertNull(range("1-2", "1").forward(2))
    }

    @Test
    fun brokenRangesAreRejected() {
        assertNull(EpisodeRange.parse("a", "1"))
        assertNull(EpisodeRange.parse("1", "x-2"))
        assertNull(EpisodeRange.parse("1", "1|z"))
    }
}

class AnimeIndexTest {
    private val index: AnimeIndex = run {
        val out = StringWriter()
        MappingParser.convert(MAPPINGS_EXCERPT.byteInputStream(), out)
        AnimeIndex.read(out.toString().lineSequence())
    }

    @Test
    fun onlyAnimeToShowRowsAreKept() {
        val out = StringWriter()
        val rows = MappingParser.convert(MAPPINGS_EXCERPT.byteInputStream(), out)
        val body = out.toString().lines().drop(1).filter(String::isNotEmpty)
        assertEquals(rows, body.size)
        val shows = body.map { it.split('\t')[2].toLong() }.toSet()
        // Nothing from anidb, tvdb or tmdb_movie, and no mal-to-anilist link.
        assertEquals(setOf(37854L, 37546L, 34179L, 34772L, 35106L, 13375L, 900L, 42511L, 999L), shows)
    }

    @Test
    fun anAbsoluteNumberLandsInItsTmdbSeason() {
        assertEquals(AnimeEpisodeMap(37854, 21, 1071), index.toTmdb(21, null, 1071))
        assertEquals(AnimeEpisodeMap(37854, 22, 1100), index.toTmdb(21, null, 1100))
    }

    @Test
    fun aContinuousNumberMapsIntoALaterSeason() {
        assertEquals(AnimeEpisodeMap(34179, 2, 5), index.toTmdb(103, null, 35))
        assertEquals(AnimeEpisodeMap(34179, 3, 18), index.toTmdb(103, null, 74))
    }

    @Test
    fun ratiosApplyInTheForwardDirection() {
        assertEquals(AnimeEpisodeMap(37546, 1, 2), index.toTmdb(95, null, 3))
        assertEquals(AnimeEpisodeMap(34772, 1, 3), index.toTmdb(280, null, 2))
        assertEquals(AnimeEpisodeMap(13375, 0, 1), index.toTmdb(559, null, 1))
    }

    @Test
    fun aRowOnlyReadableFromTheTmdbSideIsStillUsed() {
        // anilist:849 has no tmdb_show target. Its link exists only under tmdb_show:42511:s1.
        assertEquals(AnimeEpisodeMap(42511, 1, 5), index.toTmdb(849, null, 5))
        assertEquals(42511L, index.showOf(4382, null))
    }

    @Test
    fun aReverseRowInvertsItsRatio() {
        // Same shape as the real row tmdb_show:37546:s1 -> anilist:95, read from the anime side.
        assertEquals(AnimeEpisodeMap(999, 4, 1), index.toTmdb(1, 555, 2))
        assertEquals(AnimeEpisodeMap(999, 4, 2), index.toTmdb(1, 555, 3))
        assertEquals(MalEpisode(555, 3), index.toMal(999, 4, 2))
    }

    @Test
    fun theMalIdIsTriedWhenAniListHasNoRow() {
        assertEquals(AnimeEpisodeMap(37546, 1, 2), index.toTmdb(424242, 95, 3))
    }

    @Test
    fun aRegularSeasonBeatsASpecial() {
        assertEquals(AnimeEpisodeMap(900, 2, 1), index.toTmdb(900, null, 1))
        assertEquals(900L, index.showOf(900, null))
    }

    @Test
    fun anExplicitlyUnmappedRangeIsDropped() = assertNull(index.toTmdb(902, null, 1))

    @Test
    fun anUnknownEntryHasNoAnswer() {
        assertNull(index.toTmdb(777, null, 1))
        assertNull(index.showOf(777, null))
    }

    @Test
    fun aTmdbSeasonResolvesToItsOwnMalEntry() {
        // The title may be anchored on season 1, but AniSkip needs the entry for season 2.
        assertEquals(MalEpisode(3000, 7), index.toMal(34179, 2, 7))
        assertNull(index.toMal(34179, 1, 7))
    }

    @Test
    fun aRatioIsInvertedWhenGoingToMal() {
        assertEquals(MalEpisode(95, 3), index.toMal(37546, 1, 2))
    }

    @Test
    fun theIdsOfASeasonComeFromItsRows() {
        assertEquals(AnimeIds(anilist = 103, mal = 3000), index.idsOf(34179, 2))
        assertNull(index.idsOf(34179, 9))
    }

    @Test
    fun aStoreFromAnotherFormatIsIgnored() {
        assertNull(AnimeIndex.read(sequenceOf("#old-format", "a\t1\t2\t1\tf\t1\t1")).showOf(1, null))
    }

    /** Local check against a real copy. Set FG_MAPPINGS_JSON to run it. Nothing goes online. */
    @Test
    fun theRealFileParses() {
        val path = System.getenv("FG_MAPPINGS_JSON")
        assumeTrue(path != null)
        val store = File.createTempFile("anime-map", ".tsv")
        try {
            val started = System.nanoTime()
            val rows = File(path!!).inputStream().buffered().use { input ->
                store.bufferedWriter().use { MappingParser.convert(input, it) }
            }
            val real = store.useLines(block = AnimeIndex::read)
            val ms = (System.nanoTime() - started) / 1_000_000
            println("real mappings: $rows rows, ${store.length()} bytes on disk, $ms ms")
            assertTrue(rows > 25_000)
            assertEquals(AnimeEpisodeMap(37854, 21, 1071), real.toTmdb(21, null, 1071))
            assertEquals(AnimeEpisodeMap(37546, 1, 2), real.toTmdb(95, null, 3))
            assertEquals(AnimeEpisodeMap(42511, 1, 5), real.toTmdb(849, null, 5))
            assertEquals(MalEpisode(52991, 5), real.toMal(209867, 1, 5))
        } finally {
            store.delete()
        }
    }
}

class AnimeMappingsRefreshTest {
    private val rig = Rig()
    private val day = 24L * 60 * 60 * 1000
    private val release = "github.com/anibridge/anibridge-mappings/releases/download/v3/mappings.min.json"
    private val asset = "https://release-assets.githubusercontent.com/github-production-release-asset/1/2"

    private fun serveMappings(body: String = MAPPINGS_EXCERPT) {
        rig.server.on(release, Reply(302, headers = mapOf("Location" to asset)))
        rig.server.on("release-assets.githubusercontent.com/github-production-release-asset/1/2", Reply(body = body))
    }

    @After
    fun clean() = rig.clean()

    private fun refresh() = blocking { rig.mappings.refreshIfDue() }

    @Test
    fun theFirstRefreshDownloadsThroughTheGithubRedirectAndKeepsOnlyTheRows() {
        serveMappings()
        refresh()
        assertEquals(AnimeEpisodeMap(37854, 21, 1071), blocking { rig.mappings.index() }.toTmdb(21, null, 1071))
        assertEquals(listOf("anime-map.tsv"), rig.dir.list()!!.toList())
    }

    @Test
    fun phoneDataNeverDownloads() {
        serveMappings()
        rig.wifi = false
        refresh()
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun theOnlineSwitchNeverDownloads() {
        serveMappings()
        rig.config.onlineEnabled = false
        refresh()
        assertTrue(rig.server.requests.isEmpty())
    }

    @Test
    fun theFileIsLeftAloneForAWeekThenReplaced() {
        serveMappings()
        refresh()
        val first = rig.server.requests.size

        rig.now += 6 * day
        refresh()
        assertEquals(first, rig.server.requests.size)

        rig.now += 2 * day
        serveMappings("""{"anilist:1":{"tmdb_show:2:s3":{"1-4":"5-8"}}}""")
        refresh()
        assertEquals(first * 2, rig.server.requests.size)
        val index = blocking { rig.mappings.index() }
        assertEquals(AnimeEpisodeMap(2, 3, 5), index.toTmdb(1, null, 1))
        assertNull(index.toTmdb(21, null, 1071))
    }

    @Test
    fun aFailedRefreshKeepsTheRowsAndDoesNotRetryAtOnce() {
        serveMappings()
        refresh()
        rig.now += 8 * day
        rig.server.on(release, Reply(500))
        refresh()
        val afterFailure = rig.server.requests.size
        refresh()
        assertEquals(afterFailure, rig.server.requests.size)
        assertNotNull(blocking { rig.mappings.index() }.toTmdb(21, null, 1071))
    }

    @Test
    fun aDamagedDownloadKeepsTheRows() {
        serveMappings()
        refresh()
        rig.now += 8 * day
        serveMappings("""{"anilist:1":{"tmdb_show:2:s3":""")
        refresh()
        assertNotNull(blocking { rig.mappings.index() }.toTmdb(21, null, 1071))
        assertEquals(listOf("anime-map.tsv"), rig.dir.list()!!.toList())
    }

    @Test
    fun aDownloadWithNoUsableRowsKeepsTheRows() {
        serveMappings()
        refresh()
        rig.now += 8 * day
        serveMappings("""{"${'$'}meta":{}}""")
        refresh()
        assertNotNull(blocking { rig.mappings.index() }.toTmdb(21, null, 1071))
    }

    @Test
    fun noFileAndNoWifiIsAnEmptyIndex() {
        rig.wifi = false
        assertNull(blocking { rig.mappings.index() }.showOf(21, null))
    }
}
