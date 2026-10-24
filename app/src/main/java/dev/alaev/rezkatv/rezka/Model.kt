package dev.alaev.rezkatv.rezka

data class Card(
    val id: String,
    val url: String,
    val title: String,
    val poster: String,
    val subtitle: String,
    val badge: String?,
)

data class Page(val cards: List<Card>, val nextUrl: String?)

data class Translator(
    val id: String,
    val name: String,
    val camrip: Boolean = false,
    val ads: Boolean = false,
    val director: Boolean = false,
)

data class Episode(val season: Int, val episode: Int, val name: String) {
    val code get() = "S$season:E$episode"
}

data class Position(val translatorId: String, val season: Int, val episode: Int)

data class Details(
    val id: String,
    val url: String,
    val title: String,
    val originalTitle: String?,
    val poster: String,
    val description: String,
    val meta: String,
    val ratings: String?,
    val translators: List<Translator>,
    val episodes: List<Episode>,
    val position: Position?,
    val favs: String? = null,
) {
    val isSeries get() = episodes.isNotEmpty()
    val seasons get() = episodes.map { it.season }.distinct()

    val currentEpisode: Episode?
        get() = position?.let { p -> episodes.find { it.season == p.season && it.episode == p.episode } }
}

enum class Shelf(val title: String, val path: String, val inLiteMode: Boolean) {
    CONTINUE("Продолжить просмотр", "/continue/", true),
    FAVORITES("Закладки", "/favorites/", true),
    NEW("Новинки", "/new/?filter=last", true),
    SERIES("Популярные сериалы", "/series/?filter=popular", false),
    FILMS("Популярные фильмы", "/films/?filter=popular", false),
    CARTOONS("Мультфильмы", "/cartoons/?filter=popular", false),
    ANIME("Аниме", "/animation/?filter=popular", false),
}

enum class Category(val title: String, val path: String, val sortable: Boolean) {
    SERIES("Сериалы", "/series/", true),
    FILMS("Фильмы", "/films/", true),
    CARTOONS("Мультфильмы", "/cartoons/", true),
    ANIME("Аниме", "/animation/", true),
    FAVORITES("Закладки", "/favorites/", false);

    fun path(sort: Sort) = if (sortable) "$path?filter=${sort.filter}" else path
}

enum class Sort(val title: String, val filter: String) {
    POPULAR("Популярные", "popular"),
    LAST("Новинки", "last"),
    WATCHING("Сейчас смотрят", "watching"),
}

data class Quality(val label: String, val urls: List<String>) {
    val height: Int get() = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val ultra: Boolean get() = !label.trimEnd().endsWith("p")
}

data class Subtitle(val label: String, val url: String, val language: String?)

data class Streams(
    val qualities: List<Quality>,
    val subtitles: List<Subtitle>,
    val defaultSubtitle: String?,
    val thumbnails: String?,
)

data class Tile(val startMs: Long, val endMs: Long, val url: String, val x: Int, val y: Int, val w: Int, val h: Int)
