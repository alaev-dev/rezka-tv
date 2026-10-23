package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    @Test
    fun saveFormMatchesSitePlayer() {
        assertEquals(
            "post_id=300&translator_id=7&season=2&episode=8&current_time=754&duration=3270",
            Progress.saveForm("300", "7", 2, 8, 754_900, 3_270_400),
        )
        assertEquals(
            "post_id=400&translator_id=56&season=0&episode=0&current_time=0&duration=5400",
            Progress.saveForm("400", "56", 0, 0, 0, 5_400_000),
        )
    }

    @Test
    fun finishedNearCredits() {
        val episode = 45 * 60_000L
        assertFalse(Progress.isFinished(40 * 60_000L, episode))
        assertTrue(Progress.isFinished(episode - 120_000, episode)) // 5% от 45 мин — 2 мин 15 с
        val short = 20 * 60_000L
        assertFalse(Progress.isFinished(short - 100_000, short)) // минимум полторы минуты
        assertTrue(Progress.isFinished(short - 80_000, short))
        assertFalse(Progress.isFinished(1000, 0))
    }

    @Test
    fun startPosition() {
        val duration = 60 * 60_000L
        assertEquals(0, Progress.startPosition(10_000, duration)) // чуть-чуть — с начала
        assertEquals(600_000, Progress.startPosition(600_000, duration))
        assertEquals(0, Progress.startPosition(duration - 30_000, duration)) // досмотрели — с начала
        assertEquals(600_000, Progress.startPosition(600_000, 0)) // длительность неизвестна
    }
}
