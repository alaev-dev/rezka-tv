package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheJsonTest {

    @Test
    fun pageRoundTrip() {
        val page = Page(
            listOf(
                Card("1", "https://r.test/1.html", "Фильм «1»", "https://s.test/1.jpg", "2020, США", null),
                Card("2", "https://r.test/2.html", "Сериал", "", "", "2 сезон 7 серия (Студия)"),
            ),
            "https://r.test/page/2/",
        )
        assertEquals(page, CacheJson.page(CacheJson.page(page)))
        assertEquals(Page(emptyList(), null), CacheJson.page(CacheJson.page(Page(emptyList(), null))))
    }

    @Test
    fun detailsRoundTrip() {
        val details = Details(
            id = "300", url = "https://r.test/300.html", title = "Шоу", originalTitle = null,
            poster = "https://s.test/300.jpg", description = "Описание\nв две строки", meta = "2019 · США",
            ratings = "IMDb 8.5",
            translators = listOf(Translator("355", "Студия"), Translator("77", "Режиссёрская", camrip = true, director = true)),
            episodes = listOf(Episode(1, 1, "Серия 1"), Episode(2, 0, "Серия 0")),
            position = Position("355", 2, 0),
            favs = "token",
        )
        assertEquals(details, CacheJson.details(CacheJson.details(details)))
        val film = details.copy(episodes = emptyList(), position = null, ratings = null, favs = null)
        assertEquals(film, CacheJson.details(CacheJson.details(film)))
    }
}
