package dev.alaev.rezkatv.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePlannerTest {

    private fun local(season: Int, episode: Int, translator: String = "7", reported: SitePosition? = SitePosition(translator, season, episode)) =
        WatchSession(
            app = WatchSession.APP_REZKA, url = "u", title = "t", translatorId = translator, season = season, episode = episode,
            positionMs = 1_394_000, durationMs = 3_282_000, updatedAt = 0, bootCount = 1, interrupted = true, reported = reported,
        )

    @Test
    fun siteUnchangedKeepsLocalPosition() {
        assertEquals(ResumePlan("7", 2, 8, 1_394_000, false), ResumePlanner.plan(local(2, 8), SitePosition("7", 2, 8)))
    }

    @Test
    fun siteAheadOnAnotherDeviceWins() {
        // На ноутбуке досмотрели до 2×9: время сайт не отдаёт — серия с сохранённого места или с начала
        assertEquals(ResumePlan("7", 2, 9, ResumePlanner.FROM_STORE, true), ResumePlanner.plan(local(2, 8), SitePosition("7", 2, 9)))
        // и через границу сезона
        assertEquals(ResumePlan("7", 3, 1, ResumePlanner.FROM_STORE, true), ResumePlanner.plan(local(2, 8), SitePosition("7", 3, 1)))
    }

    @Test
    fun otherTranslatorSameEpisodeKeepsTime() {
        assertEquals(ResumePlan("232", 2, 8, 1_394_000, true), ResumePlanner.plan(local(2, 8), SitePosition("232", 2, 8)))
    }

    @Test
    fun ourReportNeverReachedTheSite() {
        // Сайт отстаёт, потому что наша отправка не дошла (не было сети): верим себе
        assertEquals(ResumePlan("7", 2, 8, 1_394_000, false), ResumePlanner.plan(local(2, 8, reported = null), SitePosition("7", 2, 7)))
        // А если дошла 2×7, а сайт показывает 2×7, хотя смотрим 2×8 — тоже своё (позже не отправили)
        assertEquals(ResumePlan("7", 2, 8, 1_394_000, false), ResumePlanner.plan(local(2, 8, reported = SitePosition("7", 2, 7)), SitePosition("7", 2, 7)))
    }

    @Test
    fun filmTranslatorChangedElsewhere() {
        assertEquals(ResumePlan("56", 0, 0, 1_394_000, true), ResumePlanner.plan(local(0, 0, "238"), SitePosition("56", 0, 0)))
    }

    @Test
    fun onlyOneSide() {
        assertEquals(ResumePlan("7", 1, 3, ResumePlanner.FROM_STORE, false), ResumePlanner.plan(null, SitePosition("7", 1, 3)))
        assertEquals(ResumePlan("7", 2, 8, 1_394_000, false), ResumePlanner.plan(local(2, 8), null))
        assertNull(ResumePlanner.plan(null, null))
    }
}
