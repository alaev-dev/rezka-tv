package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeNavTest {

    private val episodes = listOf(
        Episode(1, 1, "Серия 1"), Episode(1, 2, "Серия 2"),
        Episode(2, 0, "Серия 0"), Episode(2, 1, "Серия 1"), Episode(2, 2, "Серия 2"),
        Episode(3, 1, "Серия 1"),
    )

    @Test
    fun nextWithinSeason() = assertEquals(Episode(2, 2, "Серия 2"), EpisodeNav.next(episodes, 2, 1))

    @Test
    fun nextCrossesSeasonBoundary() {
        assertEquals(Episode(2, 0, "Серия 0"), EpisodeNav.next(episodes, 1, 2))
        assertEquals(Episode(3, 1, "Серия 1"), EpisodeNav.next(episodes, 2, 2))
    }

    @Test
    fun previousCrossesSeasonBoundary() {
        assertEquals(Episode(1, 2, "Серия 2"), EpisodeNav.previous(episodes, 2, 0))
        assertEquals(Episode(2, 2, "Серия 2"), EpisodeNav.previous(episodes, 3, 1))
    }

    @Test
    fun edges() {
        assertNull(EpisodeNav.previous(episodes, 1, 1))
        assertNull(EpisodeNav.next(episodes, 3, 1))
        assertNull(EpisodeNav.next(emptyList(), 1, 1))
    }

    @Test
    fun missingCurrentFallsBackToOrder() {
        // У другой озвучки серии 1×3 нет: следующая — первая после неё, предыдущая — последняя до неё
        assertEquals(Episode(2, 0, "Серия 0"), EpisodeNav.next(episodes, 1, 3))
        assertEquals(Episode(1, 2, "Серия 2"), EpisodeNav.previous(episodes, 1, 3))
    }

    @Test
    fun find() {
        assertEquals(Episode(2, 0, "Серия 0"), EpisodeNav.find(episodes, 2, 0))
        assertNull(EpisodeNav.find(episodes, 4, 1))
    }
}
