package com.frameglass.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbDetailsTest {
    private val rig = Rig()

    @After
    fun clean() = rig.clean()

    private val fullTv = """
        {"id":10,"name":"ترجمة","original_name":"Sousou","first_air_date":"2023-09-29","overview":"",
         "episode_run_time":[],"last_episode_to_air":{"runtime":24},
         "genres":[{"id":16,"name":"رسوم متحركة"}],"vote_average":8.8,
         "poster_path":"/poster.jpg","backdrop_path":"/back.jpg","original_language":"ja",
         "origin_country":["JP"],"created_by":[{"id":1,"name":"Creator One"}],
         "seasons":[{"season_number":1},{"season_number":0},{"season_number":2}],
         "external_ids":{"imdb_id":"tt22248376"},
         "credits":{"cast":[
             {"id":2,"name":"Second","character":"B","profile_path":"/b.jpg","order":1},
             {"id":1,"name":"First","character":"A","profile_path":null,"order":0}],
           "crew":[{"job":"Director","name":"Dir One"},{"job":"Gaffer","name":"Light One"},{"job":"Director","name":"Dir One"}]},
         "images":{"logos":[
             {"iso_639_1":"ja","file_path":"/ja.png","vote_average":9.0,"vote_count":9},
             {"iso_639_1":"en","file_path":"/en-low.png","vote_average":1.0,"vote_count":1},
             {"iso_639_1":"en","file_path":"/en-high.svg","vote_average":5.0,"vote_count":3}]}}
    """.trimIndent()

    private val englishTv = """
        {"id":10,"name":"Frieren","original_name":"Sousou","overview":"An elf journeys on.",
         "poster_path":"/poster-en.jpg","backdrop_path":"/back-en.jpg","genres":[{"id":16,"name":"Animation"}]}
    """.trimIndent()

    @Test
    fun displayFieldsComeFromTheDeviceLanguageWithAnEnglishFallback() {
        rig.server.on("$TMDB/tv/10", Reply(body = fullTv), Reply(body = englishTv))
        val meta = blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value()

        val languages = rig.server.hits("$TMDB/tv/10").map { it.url.queryParameter("language") }
        assertEquals(listOf("ar", "en-US"), languages)
        assertEquals("ترجمة", meta.title)
        assertEquals("An elf journeys on.", meta.overview)
        assertEquals(listOf("رسوم متحركة"), meta.genres)
    }

    @Test
    fun anUntranslatedTitleFallsBackToEnglish() {
        val untranslated = fullTv.replace("\"name\":\"ترجمة\"", "\"name\":\"Sousou\"")
        rig.server.on("$TMDB/tv/10", Reply(body = untranslated), Reply(body = englishTv))
        assertEquals("Frieren", blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value().title)
    }

    @Test
    fun anOriginalArabicTitleStaysArabic() {
        val arabicWork = fullTv
            .replace("\"original_name\":\"Sousou\"", "\"original_name\":\"ترجمة\"")
            .replace("\"original_language\":\"ja\"", "\"original_language\":\"ar\"")
        rig.server.on("$TMDB/tv/10", Reply(body = arabicWork), Reply(body = englishTv))
        assertEquals("ترجمة", blocking { rig.client.details(MetaKind.TV, 10, "ar-LY") }.value().title)
    }

    @Test
    fun englishDevicesSpendOneRequest() {
        rig.server.json("$TMDB/tv/10", englishTv)
        blocking { rig.client.details(MetaKind.TV, 10, "en-GB") }.value()
        assertEquals(1, rig.server.hits("$TMDB/tv/10").size)
    }

    @Test
    fun aFailedEnglishFallbackKeepsThePrimaryFields() {
        rig.server.on("$TMDB/tv/10", Reply(body = fullTv), Reply(500))
        val meta = blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value()
        assertEquals("ترجمة", meta.title)
        assertNull(meta.overview)
    }

    @Test
    fun imagesUseTheBriefSizes() {
        rig.server.on("$TMDB/tv/10", Reply(body = fullTv), Reply(body = englishTv))
        val meta = blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value()
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", meta.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w1280/back.jpg", meta.backdropUrl)
        assertEquals("https://image.tmdb.org/t/p/w185/b.jpg", meta.cast[1].photoUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/en-high.png", meta.logoUrl)
    }

    @Test
    fun theLogoPrefersTheDeviceLanguageThenEnglishAndNothingElse() {
        val logos = listOf(
            TmdbImage("ja", "/ja.png", 9.0, 9),
            TmdbImage("en", "/en.png", 1.0, 1),
            TmdbImage("ar", "/ar.png", 0.5, 1),
        )
        assertEquals("/ar.png", pickLogo(logos, "ar-LY"))
        assertEquals("/en.png", pickLogo(logos, "fr"))
        assertNull(pickLogo(logos.filter { it.language == "ja" }, "ar"))
    }

    @Test
    fun logosAreRequestedForTheDeviceLanguageAndEnglish() {
        rig.server.on("$TMDB/tv/10", Reply(body = fullTv), Reply(body = englishTv))
        blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value()
        val request = rig.server.hits("$TMDB/tv/10").first()
        assertEquals("credits,images,external_ids", request.url.queryParameter("append_to_response"))
        assertEquals("ar,en", request.url.queryParameter("include_image_language"))
    }

    @Test
    fun castAndCrewAreOrderedAndFiltered() {
        rig.server.on("$TMDB/tv/10", Reply(body = fullTv), Reply(body = englishTv))
        val meta = blocking { rig.client.details(MetaKind.TV, 10, "ar") }.value()
        assertEquals(listOf("First", "Second"), meta.cast.map(PersonMeta::name))
        assertEquals(listOf(CrewMeta("Creator", "Creator One"), CrewMeta("Director", "Dir One")), meta.crew)
        assertEquals(listOf(0, 1, 2), meta.seasons)
        assertEquals("tt22248376", meta.imdbId)
        assertEquals(24, meta.runtimeMinutes)
        assertEquals(2023, meta.year)
        assertEquals(8.8, meta.tmdbRating!!, 1e-9)
    }

    @Test
    fun aMovieCarriesItsCollectionAndImdbId() {
        val movie = """
            {"id":5,"title":"Quiet Coast","original_title":"Quiet Coast","release_date":"2026-03-01","runtime":112,
             "imdb_id":"tt1234567","vote_average":0,"belongs_to_collection":{"id":77,"name":"Coast Collection"}}
        """.trimIndent()
        rig.server.json("$TMDB/movie/5", movie)
        val meta = blocking { rig.client.details(MetaKind.MOVIE, 5, "en-US") }.value()
        assertEquals(77L, meta.collectionId)
        assertEquals("Coast Collection", meta.collectionName)
        assertEquals("tt1234567", meta.imdbId)
        assertEquals(112, meta.runtimeMinutes)
        assertNull(meta.tmdbRating)
        assertTrue(meta.seasons.isEmpty())
        assertEquals(
            "credits,images",
            rig.server.hits("$TMDB/movie/5").single().url.queryParameter("append_to_response"),
        )
    }

    @Test
    fun aMissingTitleIsNotFound() {
        val result = blocking { rig.client.details(MetaKind.MOVIE, 404, "en-US") }
        assertEquals(NetResult.Reason.NOT_FOUND, (result as NetResult.Failure).reason)
    }

    @Test
    fun seasonsFillEmptyAndPlaceholderFieldsFromEnglish() {
        val arabic = """{"episodes":[
            {"episode_number":1,"name":"الحلقة 1","overview":"","still_path":"/s1.jpg","runtime":24,"air_date":"2023-09-29"},
            {"episode_number":2,"name":"الرحلة","overview":"قصة","still_path":null,"runtime":0}]}"""
        val english = """{"episodes":[
            {"episode_number":1,"name":"The End","overview":"Plot one","still_path":"/en1.jpg"},
            {"episode_number":2,"name":"Journey","overview":"Plot two","still_path":"/en2.jpg"}]}"""
        rig.server.on("$TMDB/tv/10/season/1", Reply(body = arabic), Reply(body = english))
        val rows = blocking { rig.client.season(10, 1, "ar") }.value()

        assertEquals("The End", rows[0].title)
        assertEquals("Plot one", rows[0].overview)
        assertEquals("https://image.tmdb.org/t/p/w300/s1.jpg", rows[0].stillUrl)
        assertEquals("الرحلة", rows[1].title)
        assertEquals("قصة", rows[1].overview)
        assertEquals("https://image.tmdb.org/t/p/w300/en2.jpg", rows[1].stillUrl)
        assertNull(rows[1].runtimeMinutes)
        assertEquals("2023-09-29", rows[0].airDate)
    }

    @Test
    fun aFullyTranslatedSeasonSpendsOneRequest() {
        val arabic = """{"episodes":[{"episode_number":1,"name":"الرحلة","overview":"قصة"}]}"""
        rig.server.json("$TMDB/tv/10/season/1", arabic)
        blocking { rig.client.season(10, 1, "ar") }.value()
        assertEquals(1, rig.server.hits("$TMDB/tv/10/season/1").size)
    }

    @Test
    fun omdbSendsOnlyTheIdAndKeyAndReadsTheRating() {
        rig.config.omdb = OMDB_KEY
        rig.server.json(
            "www.omdbapi.com/",
            """{"Title":"X","Poster":"https://omdb.example/p.jpg","imdbRating":"8.7","Response":"True"}""",
        )
        assertEquals(8.7, blocking { rig.client.imdbRating("tt1234567") }.value(), 1e-9)
        val url = rig.server.requests.single().url
        assertEquals(setOf("i", "apikey"), url.queryParameterNames)
        assertEquals("tt1234567", url.queryParameter("i"))
    }

    @Test
    fun omdbFailuresAreTyped() {
        fun reasonOf(reply: Reply): NetResult.Reason {
            val rig = Rig(FakeConfig(omdb = OMDB_KEY))
            rig.server.on("www.omdbapi.com/", reply)
            return (blocking { rig.client.imdbRating("tt1") } as NetResult.Failure).reason
        }
        assertEquals(NetResult.Reason.NOT_FOUND, reasonOf(Reply(body = """{"imdbRating":"N/A"}""")))
        assertEquals(NetResult.Reason.NOT_FOUND, reasonOf(Reply(body = """{"Response":"False","Error":"Movie not found!"}""")))
        assertEquals(NetResult.Reason.NO_KEY, reasonOf(Reply(401, """{"Response":"False","Error":"Invalid API key!"}""")))
        assertEquals(NetResult.Reason.RATE_LIMIT, reasonOf(Reply(401, """{"Response":"False","Error":"Request limit reached!"}""")))
    }

    @Test
    fun omdbWithoutAKeyStaysLocal() {
        val result = blocking { rig.client.imdbRating("tt1") }
        assertEquals(NetResult.Reason.NO_KEY, (result as NetResult.Failure).reason)
        assertTrue(rig.server.requests.isEmpty())
    }
}
