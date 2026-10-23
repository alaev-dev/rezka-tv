package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RezkaParserTest {

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/rezka/$name")) { "missing fixture $name" }.readText()

    @Test
    fun listingCardsAndNextPage() {
        val page = RezkaParser.cards(fixture("listing.html"), "https://rezka.test/new/")

        assertEquals(2, page.cards.size)
        val film = page.cards[0]
        assertEquals("100", film.id)
        assertEquals("https://rezka.test/films/action/100-test-film-2023.html", film.url)
        assertEquals("Тестовый фильм", film.title)
        assertEquals("https://static.test/i/100.jpg", film.poster)
        assertEquals("2023, Малайзия, Боевики", film.subtitle)
        assertNull(film.badge)

        val series = page.cards[1]
        assertEquals("https://rezka.test/i/200.jpg", series.poster) // относительный src → абсолютный
        assertEquals("1 сезон, 4 серия", series.badge)

        assertEquals("https://rezka.test/new/page/2/?filter=last", page.nextUrl)
    }

    @Test
    fun lastPageHasNoNext() {
        val html = fixture("listing.html").replace("b-navigation__next", "b-navigation__nope")
        assertNull(RezkaParser.cards(html, "https://rezka.test/new/").nextUrl)
    }

    @Test
    fun continueWatchingSkipsHeaderRow() {
        val page = RezkaParser.continueWatching(fixture("continue.html"), "https://rezka.test/continue/")

        assertEquals(2, page.cards.size)
        val show = page.cards[0]
        assertEquals("300", show.id)
        assertEquals("https://rezka.test/series/action/300-test-show-2019-latest.html", show.url)
        assertEquals("Тестовое шоу", show.title)
        assertEquals("https://static.test/i/300.jpg", show.poster)
        assertEquals("2019-2026, проект завершен", show.subtitle)
        assertEquals("2 сезон 7 серия (Студия (18+))", show.badge)

        val film = page.cards[1]
        assertEquals("400", film.id)
        assertEquals("2008", film.subtitle)
        assertEquals("Дубляж", film.badge)
    }

    @Test
    fun seriesDetails() {
        val url = "https://rezka.test/series/action/300-test-show-2019-latest.html#continue"
        val details = RezkaParser.details(fixture("series.html"), url)

        assertEquals("300", details.id)
        assertEquals("https://rezka.test/series/action/300-test-show-2019-latest.html", details.url)
        assertEquals("Тестовое шоу", details.title)
        assertEquals("The Test Show", details.originalTitle)
        assertEquals("https://static.test/i/300-cover.jpg", details.poster)
        assertEquals("Короткое описание для теста.", details.description)
        assertEquals("2019 · США · Боевики, Драмы · 60 мин.", details.meta)
        assertEquals("IMDb 8.5 · КП 8.21", details.ratings)
        assertEquals(listOf(Translator("355", "Студия (18+)"), Translator("232", "TVShows")), details.translators)
        assertEquals(listOf(1, 2), details.seasons)
        assertEquals(Episode(2, 7, "Серия 7"), details.episodes.last())
        assertEquals(Position("355", 2, 7), details.position)
        assertTrue(details.isSeries)
    }

    @Test
    fun filmDetailsWithSingleTranslator() {
        val details = RezkaParser.details(fixture("film.html"), "https://rezka.test/films/fiction/400-test-trip-2008.html")

        assertFalse(details.isSeries)
        assertEquals(listOf(Translator("56", "Дубляж")), details.translators)
        assertEquals(Position("56", 0, 0), details.position)
        assertEquals("2008 · США", details.meta)
        assertNull(details.ratings)
        assertNull(details.originalTitle)
        assertEquals("https://rezka.test/i/400-cover.jpg", details.poster)
    }

    @Test
    fun filmTranslatorsFlagsAndFavsToken() {
        val details = RezkaParser.details(fixture("film_translators.html"), "https://rezka.test/films/x/500-test.html")

        assertEquals("Тестовый фильм с озвучками", details.title)
        assertEquals(
            listOf(
                Translator("56", "Дубляж"),
                Translator("77", "Режиссёрская версия", camrip = true, ads = true, director = true),
            ),
            details.translators,
        )
        assertEquals("11111111-2222-3333-4444-555555555555", details.favs)
        assertEquals("https://rezka.test/i/500-cover.jpg", details.poster)
        assertFalse(details.isSeries) // похожие тайтлы из .b-sidelist не превращаются в серии
    }

    @Test
    fun currentEpisodeFromPlayerInit() {
        val details = RezkaParser.details(fixture("series.html"), "https://rezka.test/series/action/300-x.html")
        assertEquals(Episode(2, 7, "Серия 7"), details.currentEpisode)
        assertEquals("S2:E7", details.currentEpisode?.code)
    }

    @Test
    fun episodesFromTranslatorResponseFragment() {
        val fragment = """<ul id="simple-episodes-list-1" class="b-simple_episodes__list clearfix">""" +
            """<li class="b-simple_episode__item active" data-id="300" data-season_id="1" data-episode_id="1">Серия 1</li>""" +
            """<li class="b-simple_episode__item" data-id="300" data-season_id="1" data-episode_id="2">Серия 2</li></ul>""" +
            """<ul id="simple-episodes-list-2"><li class="b-simple_episode__item" data-season_id="2" data-episode_id="1">Серия 1</li>""" +
            """<li class="b-simple_episode__item" data-season_id="x" data-episode_id="2">битая</li></ul>"""

        assertEquals(
            listOf(Episode(1, 1, "Серия 1"), Episode(1, 2, "Серия 2"), Episode(2, 1, "Серия 1")),
            RezkaParser.episodes(fragment),
        )
    }

    @Test
    fun continueWatchingKeepsOnlyFirstRows() {
        val page = RezkaParser.continueWatching(fixture("continue.html"), "https://rezka.test/continue/", limit = 1)
        assertEquals(listOf("300"), page.cards.map { it.id })
    }

    @Test
    fun seasonAndEpisodeFromBadge() {
        assertEquals(2 to 7, RezkaParser.seasonEpisode("2 сезон 7 серия (Студия (18+))"))
        assertEquals(1 to 4, RezkaParser.seasonEpisode("1 сезон, 4 серия"))
        assertNull(RezkaParser.seasonEpisode("Дубляж"))
        assertNull(RezkaParser.seasonEpisode(null))
    }

    @Test
    fun loginPageDetection() {
        assertTrue(RezkaParser.isLoginPage("<form id=\"loginForm\" action=\"/ajax/login/\">"))
        assertFalse(RezkaParser.isLoginPage(fixture("listing.html")))
    }

    @Test
    fun playLinks() {
        val url = "https://rezka.test/series/a/300-x.html#continue"
        assertEquals("https://rezka.test/series/a/300-x.html#t:355-s:2-e:7", PlayLink.build(url, "355", 2, 7))
        assertEquals("https://rezka.test/series/a/300-x.html#t:56", PlayLink.build(url, "56"))
    }
}
