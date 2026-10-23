package dev.alaev.rezkatv.rezka.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsTest {

    private val topJson = """
        {"source":"IMDb","generated":"2026-10-20","buckets":{
         "films":[
          {"rank":1,"imdb":"tt0111161","rating":9.3,"votes":3100000,"year":1994,"genres":["Драма"],"orig":"The Shawshank Redemption","id":"10","url":"/films/drama/10-pobeg.html","title":"Побег из Шоушенка","poster":"https://s/1.jpg","sub":"1994, США, Драмы"},
          {"rank":2,"imdb":"tt0068646","rating":9.2,"votes":2100000,"year":1972,"genres":["Криминал","Драма"],"orig":"The Godfather","id":"1167","url":"/films/drama/1167-krestnyy-otec-1972-latest.html","title":"Крестный отец","poster":"https://s/2.jpg","sub":"1972, США, Драмы"},
          {"rank":3,"imdb":"tt0468569","rating":9.0,"votes":3000000,"year":2008,"genres":["Боевик","Криминал","Драма"],"orig":"The Dark Knight","id":"30","url":"/films/action/30-x.html","title":"Тёмный рыцарь","poster":"","sub":""}
         ],
         "anime":[
          {"rank":1,"imdb":"tt0245429","rating":8.6,"votes":900000,"year":2001,"genres":["Приключения"],"orig":"Spirited Away","id":"829","url":"/animation/adventures/829-unesennye-prizrakami-2001.html","title":"Унесённые призраками","poster":"https://s/3.jpg","sub":"2001, Япония"}
         ]}}
    """.trimIndent()

    private val oscarsJson = """
        {"source":"Wikidata (CC0)","generated":"2026-10-20","ceremonies":[
         {"n":97,"year":2025,"films":"2024","nominees":{
          "picture":[
           {"imdb":"tt28607951","title":"Анора","orig":"Anora","year":2024,"won":true,"id":"1","url":"/films/drama/1-anora-2024.html","poster":"https://s/a.jpg","sub":"2024, США"},
           {"imdb":"tt20221436","title":"Эмилия Перес","orig":"Emilia Pérez","year":2024,"won":false,"id":"2","url":"/films/2.html","poster":"https://s/e.jpg","sub":""},
           {"imdb":"tt9999999","title":"Нигде не найденный","orig":"Nowhere","year":2024,"won":false}
          ],
          "international":[
           {"imdb":"tt20221436","title":"Эмилия Перес","orig":"Emilia Pérez","year":2024,"won":false,"id":"2","url":"/films/2.html","poster":"https://s/e.jpg","sub":""}
          ]}},
         {"n":3,"year":1930,"films":"1929–1930","nominees":{
          "picture":[{"imdb":"tt0020629","title":"На западном фронте без перемен","orig":"All Quiet on the Western Front","year":1930,"won":true,"id":"5","url":"/films/5.html","poster":"","sub":""}]}}
        ]}
    """.trimIndent()

    @Test
    fun parsesTopLists() {
        val top = ListsJson.top(topJson)
        assertEquals("2026-10-20", top.generated)
        val films = top.buckets.getValue(TopBucket.FILMS)
        assertEquals(3, films.size)
        val godfather = films[1]
        assertEquals(2, godfather.rank)
        assertEquals(9.2, godfather.rating, 0.001)
        assertEquals(2_100_000, godfather.votes)
        assertEquals(listOf("Криминал", "Драма"), godfather.genres)
        assertEquals("/films/drama/1167-krestnyy-otec-1972-latest.html", godfather.path)
        assertEquals(1970, godfather.decade)
        assertEquals(1, top.buckets.getValue(TopBucket.ANIME).size)
        // Раздела нет в файле — пустой список, а не ошибка
        assertTrue(top.buckets.getValue(TopBucket.SERIES).isEmpty())
    }

    @Test
    fun parsesOscars() {
        val oscars = ListsJson.oscars(oscarsJson)
        assertEquals(2, oscars.ceremonies.size)
        val c = oscars.ceremonies[0]
        assertEquals(97, c.number)
        assertEquals(2025, c.year)
        val picture = c.nominees.getValue(OscarCategory.PICTURE)
        assertTrue(picture[0].won)
        assertEquals("1", picture[0].id)
        val missing = picture[2]
        assertFalse(missing.onRezka)
        assertNull(missing.path)
        assertEquals(listOf(OscarCategory.PICTURE, OscarCategory.INTERNATIONAL), oscars.categories)
        assertFalse(OscarCategory.ANIMATED in c.nominees)
    }

    @Test
    fun filtersByDecadeGenreAndWatched() {
        val films = ListsJson.top(topJson).buckets.getValue(TopBucket.FILMS)
        assertEquals(listOf(2000, 1990, 1970), TopFilter.decades(films))
        assertEquals(listOf("Драма", "Криминал", "Боевик"), TopFilter.genres(films))
        assertEquals(listOf("1167"), TopFilter(decade = 1970).apply(films) { false }.map { it.id })
        assertEquals(listOf("1167", "30"), TopFilter(genre = "Криминал").apply(films) { false }.map { it.id })
        val watched = setOf("10", "30")
        assertEquals(listOf("1167"), TopFilter(hideWatched = true).apply(films) { it in watched }.map { it.id })
        assertTrue(TopFilter(decade = 2000, genre = "Драма", hideWatched = true).apply(films) { it in watched }.isEmpty())
        assertEquals(3, TopFilter().apply(films) { true }.size)
    }

    @Test
    fun oscarProgressCountsOnlyRezkaFilmsOnce() {
        val oscars = ListsJson.oscars(oscarsJson)
        val c = oscars.ceremonies[0]
        assertEquals(OscarProgress(watched = 1, total = 2, missing = 1), OscarProgress.ceremony(c, OscarCategory.PICTURE) { it == "2" })
        assertEquals(OscarProgress(0, 0, 0), OscarProgress.ceremony(c, OscarCategory.ANIMATED) { true })
        // По всем церемониям: 3 фильма на rezka (Анора, Эмилия Перес, 1930), один — нет
        assertEquals(OscarProgress(2, 3, 1), OscarProgress.overall(oscars, OscarCategory.PICTURE) { it == "1" || it == "5" })
        // Фильм дважды в одном списке считается один раз
        val twice = c.nominees.getValue(OscarCategory.PICTURE) + c.nominees.getValue(OscarCategory.INTERNATIONAL)
        assertEquals(OscarProgress(1, 2, 1), OscarProgress.of(twice) { it == "2" })
    }

    @Test
    fun texts() {
        assertEquals("2,1 млн", ListText.votes(2_134_567))
        assertEquals("2,2 млн", ListText.votes(2_160_000))
        assertEquals("IMDb 9.0 · 3,3 млн", ListText.imdb(9.0, 3_254_911))
        assertEquals("85 тыс.", ListText.votes(85_400))
        assertEquals("950", ListText.votes(950))
        assertEquals("IMDb 8.7 · 1,0 млн", ListText.imdb(8.66, 1_000_000))
        assertEquals("1990-е", ListText.decade(1990))
        assertEquals("500 тайтлов", ListText.titles(500))
        assertEquals("21 тайтл", ListText.titles(21))
        assertEquals("3 тайтла", ListText.titles(3))
        assertEquals("12 тайтлов", ListText.titles(12))
        assertEquals("111 тайтлов", ListText.titles(111))
        val oscars = ListsJson.oscars(oscarsJson)
        assertEquals("97-я церемония · Оскар 2025 · фильмы 2024 года", ListText.ceremony(oscars.ceremonies[0]))
        assertEquals("3-я церемония · Оскар 1930 · фильмы 1929–1930 годов", ListText.ceremony(oscars.ceremonies[1]))
    }
}
