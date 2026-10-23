package dev.alaev.rezkatv.rezka

data class Card(
    val id: String,
    val url: String,
    val title: String,
    val poster: String,
    /** «2023, Малайзия, Боевики» */
    val subtitle: String,
    /** «1 сезон, 4 серия» в каталоге, «2 сезон 7 серия (Озвучка)» в «Досмотреть» */
    val badge: String?,
)

data class Page(val cards: List<Card>, val nextUrl: String?)

data class Translator(
    val id: String,
    val name: String,
    // Флаги из разметки, сайт передаёт их обратно при запросе потока фильма
    val camrip: Boolean = false,
    val ads: Boolean = false,
    val director: Boolean = false,
)

data class Episode(val season: Int, val episode: Int, val name: String) {
    /** «S2:E7» */
    val code get() = "S$season:E$episode"
}

/** Что откроет плеер сайта: последняя просмотренная серия или серия по умолчанию. Для фильмов season = episode = 0. */
data class Position(val translatorId: String, val season: Int, val episode: Int)

data class Details(
    val id: String,
    val url: String,
    val title: String,
    val originalTitle: String?,
    /** Обложка ~350×520 */
    val poster: String,
    val description: String,
    /** «2019 · США · Боевики, Драмы · 60 мин.» */
    val meta: String,
    /** «IMDb 8.5 · КП 8.21» */
    val ratings: String?,
    val translators: List<Translator>,
    /** Пусто у фильмов */
    val episodes: List<Episode>,
    val position: Position?,
    /** Скрытое поле ctrl_favs: сайт шлёт его в запросах плеера */
    val favs: String? = null,
) {
    val isSeries get() = episodes.isNotEmpty()
    val seasons get() = episodes.map { it.season }.distinct()

    /** Серия, на которой пользователь остановился (по данным сайта). */
    val currentEpisode: Episode?
        get() = position?.let { p -> episodes.find { it.season == p.season && it.episode == p.episode } }
}

/** Ряды главного экрана в порядке показа. */
enum class Shelf(val title: String, val path: String, val inLiteMode: Boolean) {
    CONTINUE("Продолжить просмотр", "/continue/", true),
    FAVORITES("Закладки", "/favorites/", true),
    NEW("Новинки", "/new/?filter=last", true),
    SERIES("Популярные сериалы", "/series/?filter=popular", false),
    FILMS("Популярные фильмы", "/films/?filter=popular", false),
    CARTOONS("Мультфильмы", "/cartoons/?filter=popular", false),
    ANIME("Аниме", "/animation/?filter=popular", false),
}

/** Разделы-сетки из меню слева. */
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

/** Одно качество потока: ссылки в порядке предпочтения (HLS, затем зеркала и MP4). */
data class Quality(val label: String, val urls: List<String>) {
    /** 1080 для «1080p Ultra» */
    val height: Int get() = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    val ultra: Boolean get() = !label.trimEnd().endsWith("p")
}

data class Subtitle(val label: String, val url: String, val language: String?)

data class Streams(
    val qualities: List<Quality>,
    val subtitles: List<Subtitle>,
    /** Код языка субтитров по умолчанию, например «ru» */
    val defaultSubtitle: String?,
    /** Относительная ссылка на WebVTT с превью для перемотки */
    val thumbnails: String?,
)

/** Кадр превью: участок спрайта [x, y, w, h] для интервала [startMs, endMs). */
data class Tile(val startMs: Long, val endMs: Long, val url: String, val x: Int, val y: Int, val w: Int, val h: Int)
