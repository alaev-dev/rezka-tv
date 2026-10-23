package dev.alaev.rezkatv.rezka.player

import dev.alaev.rezkatv.rezka.Tile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekMathTest {

    @Test
    fun positionsEveryStep() {
        assertArrayEquals(longArrayOf(0, 10_000, 20_000), SeekMath.positions(25_000, 10_000))
        assertArrayEquals(longArrayOf(0, 10_000), SeekMath.positions(20_000, 10_000))
        assertEquals(0, SeekMath.positions(0, 10_000).size)
    }

    @Test
    fun tileForPosition() {
        val tiles = (0 until 4).map { Tile(it * 30_000L, (it + 1) * 30_000L, "u", 0, 0, 160, 90) }
        assertEquals(0, SeekMath.tileIndex(tiles, 0))
        assertEquals(0, SeekMath.tileIndex(tiles, 29_999))
        assertEquals(1, SeekMath.tileIndex(tiles, 30_000))
        assertEquals(3, SeekMath.tileIndex(tiles, 119_000))
        assertEquals(3, SeekMath.tileIndex(tiles, 125_000)) // хвост после последнего кадра
        assertEquals(-1, SeekMath.tileIndex(emptyList(), 5_000))
    }

    @Test
    fun accelerationGrowsWithHold() {
        assertEquals(0, SeekMath.extraSteps(0))
        assertEquals(0, SeekMath.extraSteps(7))
        assertTrue(SeekMath.extraSteps(30) > SeekMath.extraSteps(10))
        assertTrue(SeekMath.extraSteps(100) > SeekMath.extraSteps(30))
    }
}
