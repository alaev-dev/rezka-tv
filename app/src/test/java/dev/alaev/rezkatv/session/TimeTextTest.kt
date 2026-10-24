package dev.alaev.rezkatv.session

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class TimeTextTest {

    @Test
    fun formatsShortTimeAsMinutesAndSeconds() {
        val ms = 1_394_000L
        val expected = "23:14"
        assertThat("время меньше часа — минуты и секунды", TimeText.format(ms), equalTo(expected))
    }

    @Test
    fun formatsLongTimeWithHours() {
        val ms = 3_909_000L
        val expected = "1:05:09"
        assertThat("время от часа — с часами и ведущими нулями", TimeText.format(ms), equalTo(expected))
    }

    @Test
    fun clampsNegativeTimeToZero() {
        val ms = -5L
        val expected = "0:00"
        assertThat("отрицательное время показывается как ноль", TimeText.format(ms), equalTo(expected))
    }
}
