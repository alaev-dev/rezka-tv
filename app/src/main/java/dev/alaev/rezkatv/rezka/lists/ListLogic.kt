package dev.alaev.rezkatv.rezka.lists

data class TopFilter(val decade: Int? = null, val genre: String? = null, val hideWatched: Boolean = false) {

    fun apply(items: List<TopItem>, isWatched: (String) -> Boolean): List<TopItem> = items.filter {
        (decade == null || it.decade == decade) &&
            (genre == null || genre in it.genres) &&
            !(hideWatched && isWatched(it.id))
    }

    companion object {
        fun decades(items: List<TopItem>): List<Int> = items.map { it.decade }.filter { it > 0 }.distinct().sortedDescending()

        fun genres(items: List<TopItem>): List<String> =
            items.flatMap { it.genres }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
    }
}

data class OscarProgress(val watched: Int, val total: Int, val missing: Int) {

    companion object {
        fun of(nominees: List<Nominee>, isWatched: (String) -> Boolean): OscarProgress {
            val unique = nominees.distinctBy { it.key }
            val available = unique.filter { it.onRezka }
            return OscarProgress(
                watched = available.count { isWatched(requireNotNull(it.id)) },
                total = available.size,
                missing = unique.size - available.size,
            )
        }

        fun ceremony(c: Ceremony, category: OscarCategory, isWatched: (String) -> Boolean) =
            of(c.nominees[category].orEmpty(), isWatched)

        fun overall(oscars: Oscars, category: OscarCategory, isWatched: (String) -> Boolean) =
            of(oscars.ceremonies.flatMap { it.nominees[category].orEmpty() }, isWatched)
    }
}

object ListText {


    fun votes(n: Int): String = when {
        n >= 1_000_000 -> ((n + 50_000) / 100_000).let { "${it / 10},${it % 10} млн" }
        n >= 1_000 -> "${n / 1000} тыс."
        else -> n.toString()
    }

    fun imdb(rating: Double, votes: Int): String {
        val tenths = Math.round(rating * 10).toInt()
        return "IMDb ${tenths / 10}.${tenths % 10} · ${votes(votes)}"
    }

    fun titles(n: Int): String {
        val word = when {
            n % 100 in 11..14 -> "тайтлов"
            n % 10 == 1 -> "тайтл"
            n % 10 in 2..4 -> "тайтла"
            else -> "тайтлов"
        }
        return "$n $word"
    }

    fun decade(decade: Int) = "$decade-е"

    fun ceremony(c: Ceremony): String {
        val years = if (c.films.contains('–')) "фильмы ${c.films} годов" else "фильмы ${c.films} года"
        return "${c.number}-я церемония · Оскар ${c.year} · $years"
    }
}
