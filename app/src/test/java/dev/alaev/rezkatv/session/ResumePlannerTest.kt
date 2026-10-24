package dev.alaev.rezkatv.session

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class ResumePlannerTest {

    private fun local(
        season: Int,
        episode: Int,
        translator: String = "7",
        reported: SitePosition? = SitePosition(translator, season, episode),
    ) = WatchSession(
        app = WatchSession.APP_REZKA, url = "u", title = "t", translatorId = translator, season = season, episode = episode,
        positionMs = 1_394_000, durationMs = 3_282_000, updatedAt = 0, bootCount = 1, interrupted = true, reported = reported,
    )

    @Test
    fun keepsLocalPositionWhenSiteIsUnchanged() {
        val expected = ResumePlan("7", 2, 8, 1_394_000, false)
        val plan = ResumePlanner.plan(local(2, 8), SitePosition("7", 2, 8))
        assertThat("сайт показывает то же — своё место", plan, equalTo(expected))
    }

    @Test
    fun followsSiteToLaterEpisodeWatchedElsewhere() {
        val expected = ResumePlan("7", 2, 9, ResumePlanner.FROM_STORE, true)
        val plan = ResumePlanner.plan(local(2, 8), SitePosition("7", 2, 9))
        assertThat("на ноутбуке досмотрели до 2×9 — серия оттуда, время своё", plan, equalTo(expected))
    }

    @Test
    fun followsSiteAcrossSeasonBoundary() {
        val expected = ResumePlan("7", 3, 1, ResumePlanner.FROM_STORE, true)
        val plan = ResumePlanner.plan(local(2, 8), SitePosition("7", 3, 1))
        assertThat("переход на другом устройстве и через границу сезона", plan, equalTo(expected))
    }

    @Test
    fun keepsTimeWhenOnlyTranslatorChangedElsewhere() {
        val expected = ResumePlan("232", 2, 8, 1_394_000, true)
        val plan = ResumePlanner.plan(local(2, 8), SitePosition("232", 2, 8))
        assertThat("та же серия, другая озвучка: время своё", plan, equalTo(expected))
    }

    @Test
    fun trustsLocalWhenOurReportNeverReachedSite() {
        val expected = ResumePlan("7", 2, 8, 1_394_000, false)
        val plan = ResumePlanner.plan(local(2, 8, reported = null), SitePosition("7", 2, 7))
        assertThat("отправка не дошла (не было сети) — верим себе", plan, equalTo(expected))
    }

    @Test
    fun trustsLocalWhenSiteShowsOurOlderReport() {
        val expected = ResumePlan("7", 2, 8, 1_394_000, false)
        val plan = ResumePlanner.plan(local(2, 8, reported = SitePosition("7", 2, 7)), SitePosition("7", 2, 7))
        assertThat("сайт показывает нашу старую отправку — позже просто не отправили", plan, equalTo(expected))
    }

    @Test
    fun followsFilmTranslatorChangedElsewhere() {
        val expected = ResumePlan("56", 0, 0, 1_394_000, true)
        val plan = ResumePlanner.plan(local(0, 0, "238"), SitePosition("56", 0, 0))
        assertThat("у фильма сменили озвучку на другом устройстве", plan, equalTo(expected))
    }

    @Test
    fun startsFromSiteWhenNothingWatchedHere() {
        val expected = ResumePlan("7", 1, 3, ResumePlanner.FROM_STORE, false)
        val plan = ResumePlanner.plan(null, SitePosition("7", 1, 3))
        assertThat("здесь не смотрели — с позиции сайта", plan, equalTo(expected))
    }

    @Test
    fun keepsLocalPositionWhenSiteKnowsNothing() {
        val expected = ResumePlan("7", 2, 8, 1_394_000, false)
        val plan = ResumePlanner.plan(local(2, 8), null)
        assertThat("сайт ничего не знает — своё место", plan, equalTo(expected))
    }

    @Test
    fun plansNothingWithoutAnyPosition() {
        val plan = ResumePlanner.plan(null, null)
        assertThat("ни здесь, ни на сайте не смотрели — продолжать нечего", plan, nullValue())
    }
}
