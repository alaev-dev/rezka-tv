package dev.alaev.rezkatv.rezka.lists

import org.json.JSONArray
import org.json.JSONObject


interface RezkaTitle {
    val id: String?
    val path: String?
    val title: String
    val poster: String?
}

enum class TopBucket(val key: String, val title: String) {
    FILMS("films", "Фильмы"),
    SERIES("series", "Сериалы"),
    CARTOONS("cartoons", "Мультфильмы"),
    ANIME("anime", "Аниме"),
}

data class TopItem(
    val rank: Int,
    val imdb: String,
    val rating: Double,
    val votes: Int,
    val year: Int,
    val genres: List<String>,
    val original: String,
    override val id: String,
    override val path: String,
    override val title: String,
    override val poster: String,
) : RezkaTitle {
    val decade get() = year / 10 * 10
}

class TopLists(val generated: String, val buckets: Map<TopBucket, List<TopItem>>)

enum class OscarCategory(val key: String, val title: String) {
    PICTURE("picture", "Лучший фильм"),
    ANIMATED("animated", "Анимационный"),
    INTERNATIONAL("international", "Международный"),
}

data class Nominee(
    val imdb: String?,
    override val title: String,
    val original: String,
    val year: Int,
    val won: Boolean,
    override val id: String?,
    override val path: String?,
    override val poster: String?,
) : RezkaTitle {
    val onRezka get() = id != null

    val key get() = imdb ?: id ?: title
}

data class Ceremony(
    val number: Int,
    val year: Int,
    val films: String,
    val nominees: Map<OscarCategory, List<Nominee>>,
)

class Oscars(val generated: String, val ceremonies: List<Ceremony>) {
    val categories: List<OscarCategory> = OscarCategory.entries.filter { c -> ceremonies.any { c in it.nominees } }
}

object ListsJson {

    fun top(json: String): TopLists {
        val root = JSONObject(json)
        val buckets = root.getJSONObject("buckets")
        return TopLists(
            generated = root.optString("generated"),
            buckets = TopBucket.entries.associateWith { b -> buckets.optJSONArray(b.key)?.let(::topItems).orEmpty() },
        )
    }

    fun oscars(json: String): Oscars {
        val root = JSONObject(json)
        val ceremonies = root.getJSONArray("ceremonies")
        return Oscars(
            generated = root.optString("generated"),
            ceremonies = (0 until ceremonies.length()).map { i ->
                val c = ceremonies.getJSONObject(i)
                val nominees = c.getJSONObject("nominees")
                Ceremony(
                    number = c.getInt("n"),
                    year = c.getInt("year"),
                    films = c.getString("films"),
                    nominees = OscarCategory.entries
                        .mapNotNull { cat -> nominees.optJSONArray(cat.key)?.let { cat to nomineeList(it) } }
                        .toMap(),
                )
            },
        )
    }

    private fun topItems(array: JSONArray): List<TopItem> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        TopItem(
            rank = o.getInt("rank"),
            imdb = o.getString("imdb"),
            rating = o.getDouble("rating"),
            votes = o.getInt("votes"),
            year = o.optInt("year"),
            genres = o.optJSONArray("genres")?.strings().orEmpty(),
            original = o.optString("orig"),
            id = o.getString("id"),
            path = o.getString("url"),
            title = o.getString("title"),
            poster = o.optString("poster"),
        )
    }

    private fun nomineeList(array: JSONArray): List<Nominee> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Nominee(
            imdb = o.optStringOrNull("imdb"),
            title = o.getString("title"),
            original = o.optString("orig"),
            year = o.optInt("year"),
            won = o.optBoolean("won"),
            id = o.optStringOrNull("id"),
            path = o.optStringOrNull("url"),
            poster = o.optStringOrNull("poster"),
        )
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun JSONObject.optStringOrNull(key: String) = if (!has(key) || isNull(key)) null else optString(key)
}
