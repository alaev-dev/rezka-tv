package dev.alaev.rezkatv.rezka

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class EpisodeNavTest {

    private fun episodes() = listOf(
        Episode(1, 1, "Серия 1"), Episode(1, 2, "Серия 2"),
        Episode(2, 0, "Серия 0"), Episode(2, 1, "Серия 1"), Episode(2, 2, "Серия 2"),
        Episode(3, 1, "Серия 1"),
    )

    @Test
    fun movesToNextEpisodeWithinSeason() {
        val expected = Episode(2, 2, "Серия 2")
        val season = 2
        val found = EpisodeNav.next(episodes(), season, 1)
        assertThat("следующая серия того же сезона", found, equalTo(expected))
    }

    @Test
    fun movesFromSeasonFinaleToNextSeasonSpecial() {
        val expected = Episode(2, 0, "Серия 0")
        val season = 1
        val found = EpisodeNav.next(episodes(), season, 2)
        assertThat("после последней — нулевая серия следующего сезона", found, equalTo(expected))
    }

    @Test
    fun movesToFirstEpisodeOfNextSeason() {
        val expected = Episode(3, 1, "Серия 1")
        val season = 2
        val found = EpisodeNav.next(episodes(), season, 2)
        assertThat("после последней — первая следующего сезона", found, equalTo(expected))
    }

    @Test
    fun movesBackFromSpecialToPreviousSeasonFinale() {
        val expected = Episode(1, 2, "Серия 2")
        val season = 2
        val found = EpisodeNav.previous(episodes(), season, 0)
        assertThat("перед нулевой — последняя прошлого сезона", found, equalTo(expected))
    }

    @Test
    fun movesBackToLastEpisodeOfPreviousSeason() {
        val expected = Episode(2, 2, "Серия 2")
        val season = 3
        val found = EpisodeNav.previous(episodes(), season, 1)
        assertThat("перед первой — последняя прошлого сезона", found, equalTo(expected))
    }

    @Test
    fun findsNothingBeforeFirstEpisode() {
        val season = 1
        val found = EpisodeNav.previous(episodes(), season, 1)
        assertThat("перед самой первой ничего нет", found, nullValue())
    }

    @Test
    fun findsNothingAfterLastEpisode() {
        val season = 3
        val found = EpisodeNav.next(episodes(), season, 1)
        assertThat("после самой последней ничего нет", found, nullValue())
    }

    @Test
    fun findsNothingInEmptyList() {
        val season = 1
        val found = EpisodeNav.next(emptyList(), season, 1)
        assertThat("без серий переходить некуда", found, nullValue())
    }

    @Test
    fun movesToFirstEpisodeAfterMissingCurrent() {
        val expected = Episode(2, 0, "Серия 0")
        val season = 1
        val found = EpisodeNav.next(episodes(), season, 3)
        assertThat("у другой озвучки нет 1×3: следующая — первая после неё", found, equalTo(expected))
    }

    @Test
    fun movesBackToLastEpisodeBeforeMissingCurrent() {
        val expected = Episode(1, 2, "Серия 2")
        val season = 1
        val found = EpisodeNav.previous(episodes(), season, 3)
        assertThat("у другой озвучки нет 1×3: предыдущая — последняя до неё", found, equalTo(expected))
    }

    @Test
    fun findsEpisodeBySeasonAndNumber() {
        val expected = Episode(2, 0, "Серия 0")
        val season = 2
        val found = EpisodeNav.find(episodes(), season, 0)
        assertThat("серия находится по сезону и номеру", found, equalTo(expected))
    }

    @Test
    fun findsNothingForMissingSeason() {
        val season = 4
        val found = EpisodeNav.find(episodes(), season, 1)
        assertThat("несуществующего сезона нет", found, nullValue())
    }
}
