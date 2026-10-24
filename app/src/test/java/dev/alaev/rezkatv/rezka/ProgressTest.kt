package dev.alaev.rezkatv.rezka

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class ProgressTest {

    @Test
    fun savesEpisodeProgressInSitePlayerForm() {
        val expected = "post_id=300&translator_id=7&season=2&episode=8&current_time=754&duration=3270"
        val form = Progress.saveForm("300", "7", 2, 8, 754_900, 3_270_400)
        assertThat("серия: те же поля, что у плеера сайта, время в секундах", form, equalTo(expected))
    }

    @Test
    fun savesMovieProgressWithZeroSeasonAndEpisode() {
        val expected = "post_id=400&translator_id=56&season=0&episode=0&current_time=0&duration=5400"
        val form = Progress.saveForm("400", "56", 0, 0, 0, 5_400_000)
        assertThat("фильм: сезон и серия нулевые", form, equalTo(expected))
    }

    @Test
    fun keepsEpisodeUnfinishedBeforeCredits() {
        val episode = 45 * 60_000L
        val finished = Progress.isFinished(40 * 60_000L, episode)
        assertThat("за пять минут до конца серия не досмотрена", !finished)
    }

    @Test
    fun finishesEpisodeInLastFivePercent() {
        val episode = 45 * 60_000L
        val finished = Progress.isFinished(episode - 120_000, episode)
        assertThat("5 % от 45 минут — 2 мин 15 с: за 2 минуты до конца уже титры", finished)
    }

    @Test
    fun keepsShortEpisodeUnfinishedBeforeLastMinuteAndHalf() {
        val short = 20 * 60_000L
        val finished = Progress.isFinished(short - 100_000, short)
        assertThat("у короткой серии хвост не меньше полутора минут", !finished)
    }

    @Test
    fun finishesShortEpisodeInLastMinuteAndHalf() {
        val short = 20 * 60_000L
        val finished = Progress.isFinished(short - 80_000, short)
        assertThat("за 80 секунд до конца короткая серия досмотрена", finished)
    }

    @Test
    fun treatsVideoOfUnknownDurationAsUnfinished() {
        val finished = Progress.isFinished(1000, 0)
        assertThat("без длительности досмотренным не считается", !finished)
    }

    @Test
    fun startsFromBeginningAfterBriefWatch() {
        val expected = 0L
        val start = Progress.startPosition(10_000, 60 * 60_000L)
        assertThat("смотрели совсем чуть-чуть — с начала", start, equalTo(expected))
    }

    @Test
    fun resumesFromSavedPosition() {
        val saved = 600_000L
        val start = Progress.startPosition(saved, 60 * 60_000L)
        assertThat("с места остановки", start, equalTo(saved))
    }

    @Test
    fun startsFromBeginningWhenWatchedToCredits() {
        val duration = 60 * 60_000L
        val expected = 0L
        val start = Progress.startPosition(duration - 30_000, duration)
        assertThat("досмотрели до титров — с начала", start, equalTo(expected))
    }

    @Test
    fun resumesWhenDurationIsUnknown() {
        val saved = 600_000L
        val start = Progress.startPosition(saved, 0)
        assertThat("длительность неизвестна — с места остановки", start, equalTo(saved))
    }
}
