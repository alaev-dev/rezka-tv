package dev.alaev.rezkatv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchSessionTest {

    private val now = 1_800_000_000_000L
    private val episode = WatchSession(
        app = WatchSession.APP_REZKA, url = "https://rezka.test/series/x/300-show.html", title = "Шоу «Тест»",
        poster = "https://static.test/300.jpg", translatorId = "7", translatorName = "Студия", season = 2, episode = 8,
        positionMs = 1_394_000, durationMs = 3_282_000, updatedAt = now, bootCount = 118, interrupted = true,
        stoppedByScreenOff = true, promptedAt = 0, reported = SitePosition("7", 2, 8),
    )

    @Test
    fun jsonRoundTrip() {
        assertEquals(episode, WatchSession.fromJson(episode.toJson()))
        val film = episode.copy(season = 0, episode = 0, reported = null, poster = "", stoppedByScreenOff = false)
        assertEquals(film, WatchSession.fromJson(film.toJson()))
    }

    @Test
    fun brokenFileIsNoSession() {
        assertNull(WatchSession.fromJson(""))
        assertNull(WatchSession.fromJson("{\"app\":\"rezka\"}"))
        assertNull(WatchSession.fromJson("{not json"))
    }

    @Test
    fun episodeCode() {
        assertEquals("S2:E8", episode.episodeCode)
        assertEquals("", episode.copy(season = 0, episode = 0).episodeCode)
    }

}
