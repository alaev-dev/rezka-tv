package dev.alaev.rezkatv.rezka

/** Соседние серии в порядке сайта: через границу сезона, null на краях. */
object EpisodeNav {

    fun next(episodes: List<Episode>, season: Int, episode: Int): Episode? {
        val index = indexOf(episodes, season, episode)
        return if (index >= 0) episodes.getOrNull(index + 1)
        else episodes.firstOrNull { compare(it, season, episode) > 0 }
    }

    fun previous(episodes: List<Episode>, season: Int, episode: Int): Episode? {
        val index = indexOf(episodes, season, episode)
        return if (index >= 0) episodes.getOrNull(index - 1)
        else episodes.lastOrNull { compare(it, season, episode) < 0 }
    }

    fun find(episodes: List<Episode>, season: Int, episode: Int): Episode? =
        episodes.getOrNull(indexOf(episodes, season, episode))

    private fun indexOf(episodes: List<Episode>, season: Int, episode: Int) =
        episodes.indexOfFirst { it.season == season && it.episode == episode }

    private fun compare(e: Episode, season: Int, episode: Int) =
        compareValuesBy(e, Episode(season, episode, ""), { it.season }, { it.episode })
}
