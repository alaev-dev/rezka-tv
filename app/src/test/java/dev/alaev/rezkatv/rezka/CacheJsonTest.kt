package dev.alaev.rezkatv.rezka

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class CacheJsonTest {

    private fun page() = Page(
        listOf(
            Card("1", "https://r.test/1.html", "Фильм «1»", "https://s.test/1.jpg", "2020, США", null),
            Card("2", "https://r.test/2.html", "Сериал", "", "", "2 сезон 7 серия (Студия)"),
        ),
        "https://r.test/page/2/",
    )

    private fun series() = Details(
        id = "300", url = "https://r.test/300.html", title = "Шоу", originalTitle = null,
        poster = "https://s.test/300.jpg", description = "Описание\nв две строки", meta = "2019 · США",
        ratings = "IMDb 8.5",
        translators = listOf(Translator("355", "Студия"), Translator("77", "Режиссёрская", camrip = true, director = true)),
        episodes = listOf(Episode(1, 1, "Серия 1"), Episode(2, 0, "Серия 0")),
        position = Position("355", 2, 0),
        favs = "token",
    )

    @Test
    fun restoresPageWithCardsAndNextLink() {
        val page = page()
        val restored = CacheJson.page(CacheJson.page(page))
        assertThat("карточки и ссылка на следующую страницу переживают кэш", restored, equalTo(page))
    }

    @Test
    fun restoresEmptyPageWithoutNextLink() {
        val page = Page(emptyList(), null)
        val restored = CacheJson.page(CacheJson.page(page))
        assertThat("пустая последняя страница переживает кэш", restored, equalTo(page))
    }

    @Test
    fun restoresSeriesDetailsWithTranslatorsAndEpisodes() {
        val details = series()
        val restored = CacheJson.details(CacheJson.details(details))
        assertThat("озвучки, серии, позиция и флаги переживают кэш", restored, equalTo(details))
    }

    @Test
    fun restoresFilmDetailsWithoutOptionalFields() {
        val film = series().copy(episodes = emptyList(), position = null, ratings = null, favs = null)
        val restored = CacheJson.details(CacheJson.details(film))
        assertThat("фильм без необязательных полей переживает кэш", restored, equalTo(film))
    }
}
