package dev.alaev.rezkatv.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {

    @Test
    fun format() {
        assertEquals("23:14", TimeText.format(1_394_000))
        assertEquals("1:05:09", TimeText.format(3_909_000))
        assertEquals("0:00", TimeText.format(-5))
    }
}
