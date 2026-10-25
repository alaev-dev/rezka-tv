package dev.alaev.rezkatv.rezka.player

import dev.alaev.rezkatv.rezka.Tile
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test

class SeekMathTest {

    private fun tiles() = (0 until 4).map { Tile(it * 30_000L, (it + 1) * 30_000L, "u", 0, 0, 160, 90) }

    @Test
    fun placesSeekPointEveryStepFromZero() {
        val expected = listOf(0L, 10_000L, 20_000L)
        val positions = SeekMath.positions(25_000, 10_000).toList()
        assertThat("точки через шаг от нуля", positions, equalTo(expected))
    }

    @Test
    fun leavesEndOfVideoToLeanback() {
        val expected = listOf(0L, 10_000L)
        val positions = SeekMath.positions(20_000, 10_000).toList()
        assertThat("конец не включается — до него доводит leanback", positions, equalTo(expected))
    }

    @Test
    fun placesNoSeekPointsWithoutDuration() {
        val expected = 0
        val positions = SeekMath.positions(0, 10_000)
        assertThat("без длительности перематывать некуда", positions.size, equalTo(expected))
    }

    @Test
    fun picksFirstTileAtStart() {
        val expected = 0
        val start = 0L
        val index = SeekMath.tileIndex(tiles(), start)
        assertThat("начало — первый кадр", index, equalTo(expected))
    }

    @Test
    fun keepsTileUntilItsEnd() {
        val expected = 0
        val position = 29_999L
        val index = SeekMath.tileIndex(tiles(), position)
        assertThat("до конца интервала — тот же кадр", index, equalTo(expected))
    }

    @Test
    fun switchesTileAtBoundary() {
        val expected = 1
        val position = 30_000L
        val index = SeekMath.tileIndex(tiles(), position)
        assertThat("на границе — следующий кадр", index, equalTo(expected))
    }

    @Test
    fun picksLastTileInsideLastInterval() {
        val expected = 3
        val position = 119_000L
        val index = SeekMath.tileIndex(tiles(), position)
        assertThat("последний интервал — последний кадр", index, equalTo(expected))
    }

    @Test
    fun keepsLastTileAfterLastInterval() {
        val expected = 3
        val position = 125_000L
        val index = SeekMath.tileIndex(tiles(), position)
        assertThat("хвост после последнего кадра — последний кадр", index, equalTo(expected))
    }

    @Test
    fun findsNoTileWithoutThumbnails() {
        val expected = -1
        val position = 5_000L
        val index = SeekMath.tileIndex(emptyList(), position)
        assertThat("без превью кадра нет", index, equalTo(expected))
    }

    @Test
    fun seeksOneStepOnPress() {
        val expected = 0
        val repeat = 0
        val steps = SeekMath.extraSteps(repeat)
        assertThat("нажатие — один шаг без ускорения", steps, equalTo(expected))
    }

    @Test
    fun seeksOneStepDuringFirstHalfSecondOfHold() {
        val expected = 0
        val repeat = 7
        val steps = SeekMath.extraSteps(repeat)
        assertThat("первые полсекунды удержания — по шагу", steps, equalTo(expected))
    }

    @Test
    fun acceleratesWhenHeldLonger() {
        val short = SeekMath.extraSteps(10)
        val longer = SeekMath.extraSteps(30)
        assertThat("дольше держишь — быстрее", longer, greaterThan(short))
    }

    @Test
    fun keepsAcceleratingOnLongHold() {
        val longer = SeekMath.extraSteps(30)
        val longest = SeekMath.extraSteps(100)
        assertThat("ускорение продолжает расти", longest, greaterThan(longer))
    }
}
