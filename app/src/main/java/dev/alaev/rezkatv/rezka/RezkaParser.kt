package dev.alaev.rezkatv.rezka

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Разбор HTML-страниц rezka (вёрстка HDrezka). */
object RezkaParser {

    // initCDNSeriesEvents(id, translator, season, episode, ...) / initCDNMoviesEvents(id, translator, 0, 0, ...)
    private val PLAYER_INIT = Regex("""initCDN(?:Series|Movies)Events\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)""")
    private val FAVS = Regex("""id="ctrl_favs" value="([^"]*)"""")
    private val TITLE_ID = Regex("""/(\d+)-[^/]*\.html""")
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")
    private val SEASON_EPISODE = Regex("""(\d+)\s*сезон\D+(\d+)\s*серия""")

    fun isLoginPage(html: String) = html.contains("id=\"loginForm\"")

    fun titleId(url: String) = TITLE_ID.find(url)?.groupValues?.get(1).orEmpty()

    /** «2 сезон 7 серия (Озвучка)» → (2, 7) */
    fun seasonEpisode(badge: String?): Pair<Int, Int>? =
        badge?.let { SEASON_EPISODE.find(it) }?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** Каталог, закладки, поиск: карточки .b-content__inline_item и ссылка на следующую страницу. */
    fun cards(html: String, baseUrl: String): Page {
        // Шапка сайта с меню — половина страницы, Jsoup на слабом процессоре тратит на неё сотни мс
        val doc = Jsoup.parseBodyFragment(slice(html, "<div class=\"b-content__inline_items\"", null), baseUrl)
        val cards = doc.select(".b-content__inline_item").mapNotNull { item ->
            val link = item.selectFirst(".b-content__inline_item-link a") ?: return@mapNotNull null
            Card(
                id = item.attr("data-id"),
                url = item.absUrl("data-url").ifEmpty { link.absUrl("href") },
                title = link.text(),
                poster = item.selectFirst(".b-content__inline_item-cover img")?.absUrl("src").orEmpty(),
                subtitle = item.selectFirst(".b-content__inline_item-link div")?.text().orEmpty(),
                badge = item.selectFirst(".b-content__inline_item-cover .info")?.text()?.ifBlank { null },
            )
        }
        val next = doc.selectFirst(".b-navigation__next")?.parent()?.takeIf { it.tagName() == "a" }?.absUrl("href")
        return Page(cards, next?.ifEmpty { null })
    }

    /** «Досмотреть»: таблица сохранённых просмотров, только первые [limit] строк (всего их бывают сотни). */
    fun continueWatching(html: String, baseUrl: String, limit: Int = CONTINUE_LIMIT): Page {
        val start = html.indexOf("id=\"videosave-")
        val end = nthIndexOf(html, "id=\"videosave-", limit + 1)
        val part = if (start < 0) "" else html.substring(maxOf(0, start - 64), if (end > 0) end else html.length)
        val doc = Jsoup.parseBodyFragment(part, baseUrl)
        val cards = doc.select(".b-videosaves__list_item[id^=videosave-]").mapNotNull { row ->
            val link = row.selectFirst(".td.title a") ?: return@mapNotNull null
            val url = link.absUrl("href")
            Card(
                id = titleId(url),
                url = url,
                title = link.text(),
                poster = link.absUrl("data-cover_url"),
                subtitle = row.selectFirst(".td.title small")?.text()?.removeSurrounding("(", ")").orEmpty(),
                badge = row.selectFirst(".td.info")?.ownText()?.trim()?.ifBlank { null },
            )
        }
        return Page(cards, null)
    }

    fun details(html: String, url: String): Details {
        // Всё нужное — в блоке .b-post; похожие тайтлы, комментарии и шапку не разбираем
        val doc = Jsoup.parseBodyFragment(slice(html, "<div class=\"b-post\"", "class=\"b-sidelist"), url)
        val info = infoTable(doc)
        val init = PLAYER_INIT.find(html)?.groupValues

        val translators = doc.select("#translators-list .b-translator__item").map(::translator).ifEmpty {
            // Одна озвучка: списка нет, id только в инициализации плеера
            listOfNotNull(init?.let { Translator(it[2], info["В переводе"] ?: "Оригинал") })
        }

        val ratings = listOfNotNull(
            doc.selectFirst(".b-post__info_rates.imdb .bold")?.text()?.let { "IMDb $it" },
            doc.selectFirst(".b-post__info_rates.kp .bold")?.text()?.let { "КП $it" },
        ).joinToString(" · ").ifEmpty { null }

        val year = info["Дата выхода"]?.let { YEAR.find(it)?.value }
        val meta = listOfNotNull(year, info["Страна"], info["Жанр"], info["Время"])
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        return Details(
            id = init?.get(1) ?: titleId(url),
            url = url.substringBefore('#'),
            title = doc.selectFirst(".b-post__title h1")?.text().orEmpty(),
            originalTitle = doc.selectFirst(".b-post__origtitle")?.text()?.ifBlank { null },
            poster = doc.selectFirst(".b-sidecover img")?.absUrl("src").orEmpty(),
            description = doc.selectFirst(".b-post__description_text")?.text().orEmpty(),
            meta = meta,
            ratings = ratings,
            translators = translators,
            episodes = episodes(doc),
            position = init?.let { Position(it[2], it[3].toInt(), it[4].toInt()) },
            favs = FAVS.find(html)?.groupValues?.get(1)?.ifEmpty { null },
        )
    }

    /** Список серий из ответа get_episodes (фрагмент разметки #simple-episodes-tabs). */
    fun episodes(fragment: String): List<Episode> = episodes(Jsoup.parseBodyFragment(fragment))

    private fun episodes(doc: Element): List<Episode> =
        doc.select(".b-simple_episode__item").mapNotNull {
            val season = it.attr("data-season_id").toIntOrNull() ?: return@mapNotNull null
            val episode = it.attr("data-episode_id").toIntOrNull() ?: return@mapNotNull null
            Episode(season, episode, it.text())
        }

    private fun translator(item: Element) = Translator(
        id = item.attr("data-translator_id"),
        name = item.attr("title").ifEmpty { item.text() }.trim(),
        camrip = item.attr("data-camrip") == "1",
        ads = item.attr("data-ads") == "1",
        director = item.attr("data-director") == "1",
    )

    /** Таблица .b-post__info: «Страна» → «США» и т. д. */
    private fun infoTable(doc: Document): Map<String, String> =
        doc.select(".b-post__info tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 2) return@mapNotNull null
            cells[0].text().removeSuffix(":").trim() to cells[1].text().trim()
        }.toMap()

    /** Кусок страницы от [startMarker] до [endMarker]; если маркера нет — граница страницы. */
    private fun slice(html: String, startMarker: String, endMarker: String?): String {
        val start = html.indexOf(startMarker).coerceAtLeast(0)
        val end = endMarker?.let { html.indexOf(it, start) }?.takeIf { it > 0 } ?: html.length
        return html.substring(start, end)
    }

    private fun nthIndexOf(text: String, needle: String, n: Int): Int {
        var index = -1
        repeat(n) {
            index = text.indexOf(needle, index + 1)
            if (index < 0) return -1
        }
        return index
    }

    const val CONTINUE_LIMIT = 60
}
