package dev.alaev.rezkatv.rezka

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Уже разобранные страницы на диске: при запуске ряды показываются сразу, сеть только обновляет. */
class PageCache(private val dir: File) {

    class Entry<T>(val value: T, val savedAt: Long) {
        fun age(now: Long = System.currentTimeMillis()) = now - savedAt
    }

    fun page(url: String): Entry<Page>? = read("p:$url")?.let { (json, time) -> runCatching { Entry(CacheJson.page(json), time) }.getOrNull() }

    fun putPage(url: String, page: Page) = write("p:$url", CacheJson.page(page))

    fun details(url: String): Entry<Details>? =
        read("d:$url")?.let { (json, time) -> runCatching { Entry(CacheJson.details(json), time) }.getOrNull() }

    fun putDetails(url: String, details: Details) = write("d:$url", CacheJson.details(details))

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }

    @Synchronized
    private fun read(key: String): Pair<String, Long>? {
        val file = file(key)
        if (!file.exists()) return null
        return runCatching { file.readText() to file.lastModified() }.getOrNull()
    }

    @Synchronized
    private fun write(key: String, content: String) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "tmp")
            tmp.writeText(content)
            tmp.renameTo(file(key))
            prune()
        }
    }

    private fun prune() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val MAX_FILES = 300
    }
}

object CacheJson {

    fun page(page: Page): String = JSONObject()
        .put("next", page.nextUrl ?: JSONObject.NULL)
        .put("cards", JSONArray(page.cards.map(::card)))
        .toString()

    fun page(json: String): Page {
        val obj = JSONObject(json)
        val cards = obj.getJSONArray("cards")
        return Page((0 until cards.length()).map { card(cards.getJSONObject(it)) }, obj.optStringOrNull("next"))
    }

    fun details(d: Details): String = JSONObject()
        .put("id", d.id).put("url", d.url).put("title", d.title).put("orig", d.originalTitle ?: JSONObject.NULL)
        .put("poster", d.poster).put("desc", d.description).put("meta", d.meta).put("ratings", d.ratings ?: JSONObject.NULL)
        .put("favs", d.favs ?: JSONObject.NULL)
        .put("translators", JSONArray(d.translators.map {
            JSONObject().put("id", it.id).put("name", it.name).put("camrip", it.camrip).put("ads", it.ads).put("director", it.director)
        }))
        .put("episodes", JSONArray(d.episodes.map { JSONObject().put("s", it.season).put("e", it.episode).put("n", it.name) }))
        .put("position", d.position?.let { JSONObject().put("t", it.translatorId).put("s", it.season).put("e", it.episode) } ?: JSONObject.NULL)
        .toString()

    fun details(json: String): Details {
        val o = JSONObject(json)
        val translators = o.getJSONArray("translators")
        val episodes = o.getJSONArray("episodes")
        return Details(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.getString("title"),
            originalTitle = o.optStringOrNull("orig"),
            poster = o.getString("poster"),
            description = o.getString("desc"),
            meta = o.getString("meta"),
            ratings = o.optStringOrNull("ratings"),
            translators = (0 until translators.length()).map {
                val t = translators.getJSONObject(it)
                Translator(t.getString("id"), t.getString("name"), t.optBoolean("camrip"), t.optBoolean("ads"), t.optBoolean("director"))
            },
            episodes = (0 until episodes.length()).map {
                val e = episodes.getJSONObject(it)
                Episode(e.getInt("s"), e.getInt("e"), e.getString("n"))
            },
            position = o.optJSONObject("position")?.let { Position(it.getString("t"), it.getInt("s"), it.getInt("e")) },
            favs = o.optStringOrNull("favs"),
        )
    }

    private fun card(c: Card) = JSONObject()
        .put("id", c.id).put("url", c.url).put("title", c.title).put("poster", c.poster)
        .put("sub", c.subtitle).put("badge", c.badge ?: JSONObject.NULL)

    private fun card(o: JSONObject) = Card(
        id = o.getString("id"),
        url = o.getString("url"),
        title = o.getString("title"),
        poster = o.getString("poster"),
        subtitle = o.getString("sub"),
        badge = o.optStringOrNull("badge"),
    )

    private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else optString(key)
}
