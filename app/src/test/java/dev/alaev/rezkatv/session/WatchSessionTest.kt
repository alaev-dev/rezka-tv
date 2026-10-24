package dev.alaev.rezkatv.session

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class WatchSessionTest {

    private fun episode() = WatchSession(
        app = WatchSession.APP_REZKA, url = "https://rezka.test/series/x/300-show.html", title = "Шоу «Тест»",
        poster = "https://static.test/300.jpg", translatorId = "7", translatorName = "Студия", season = 2, episode = 8,
        positionMs = 1_394_000, durationMs = 3_282_000, updatedAt = 1_800_000_000_000L, bootCount = 118,
        interrupted = true, stoppedByScreenOff = true, promptedAt = 0, reported = SitePosition("7", 2, 8),
    )

    @Test
    fun restoresEpisodeSessionFromFile() {
        val session = episode()
        val restored = WatchSession.fromJson(session.toJson())
        assertThat("все поля серии переживают выключение", restored, equalTo(session))
    }

    @Test
    fun restoresFilmSessionWithoutOptionalFields() {
        val film = episode().copy(season = 0, episode = 0, reported = null, poster = "", stoppedByScreenOff = false)
        val restored = WatchSession.fromJson(film.toJson())
        assertThat("фильм без постера и отправки на сайт переживает выключение", restored, equalTo(film))
    }

    @Test
    fun readsEmptyFileAsNoSession() {
        val restored = WatchSession.fromJson("")
        assertThat("пустой файл — нечего продолжать", restored, nullValue())
    }

    @Test
    fun readsIncompleteJsonAsNoSession() {
        val restored = WatchSession.fromJson("{\"app\":\"rezka\"}")
        assertThat("без обязательных полей — нечего продолжать", restored, nullValue())
    }

    @Test
    fun readsBrokenJsonAsNoSession() {
        val restored = WatchSession.fromJson("{not json")
        assertThat("испорченный файл — нечего продолжать", restored, nullValue())
    }

    @Test
    fun showsEpisodeCode() {
        val expected = "S2:E8"
        val code = episode().episodeCode
        assertThat("код серии — сезон и серия", code, equalTo(expected))
    }

    @Test
    fun showsNoEpisodeCodeForFilm() {
        val code = episode().copy(season = 0, episode = 0).episodeCode
        assertThat("у фильма кода серии нет", code, emptyString())
    }
}
