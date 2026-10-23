package dev.alaev.rezkatv.rezka

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.rezka.login.LoginResult
import org.jsoup.Jsoup
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

class NotLoggedInException : IOException("rezka: not logged in")

/**
 * Страницы и API плеера rezka. Блокирующие вызовы — только из фона ([io]).
 * Разобранные страницы кэшируются в памяти и на диске, чтобы экраны открывались сразу.
 */
class RezkaClient private constructor(context: Context, val baseUrl: String) {

    private val cookies = CookieJar(context)
    private val disk = PageCache(File(context.cacheDir, "pages"))
    private val pages = LruCache<String, PageCache.Entry<Page>>(24)
    private val details = LruCache<String, PageCache.Entry<Details>>(30)

    val isLoggedIn get() = cookies.has(LOGIN_COOKIE)

    fun shelfUrl(shelf: Shelf) = resolve(shelf.path)
    fun categoryUrl(category: Category, sort: Sort) = resolve(category.path(sort))
    fun searchUrl(query: String) = resolve("/search/?do=search&subaction=search&q=" + URLEncoder.encode(query, "UTF-8"))

    /** Страница из кэша (память, затем диск) — без сети, можно звать с главного потока. */
    fun cachedPage(url: String): PageCache.Entry<Page>? =
        pages.get(url) ?: disk.page(url)?.also { pages.put(url, it) }

    fun page(url: String): Page {
        val html = fetch(url)
        val page = timed("parse ${path(url)}") {
            if (url.contains("/continue/")) RezkaParser.continueWatching(html, url) else RezkaParser.cards(html, url)
        }
        pages.put(url, PageCache.Entry(page, System.currentTimeMillis()))
        disk.putPage(url, page)
        return page
    }

    fun cachedDetails(url: String): PageCache.Entry<Details>? {
        val key = url.substringBefore('#')
        return details.get(key) ?: disk.details(key)?.also { details.put(key, it) }
    }

    /** Свежая страница тайтла: позиция просмотра и озвучки меняются, поэтому всегда из сети. */
    fun details(url: String): Details {
        val key = url.substringBefore('#')
        val html = fetch(key)
        val parsed = timed("parse details") { RezkaParser.details(html, key) }
        details.put(key, PageCache.Entry(parsed, System.currentTimeMillis()))
        disk.putDetails(key, parsed)
        return parsed
    }

    /** Для плеера: только что загруженная страница тайтла годится, иначе — заново. */
    fun recentDetails(url: String): Details =
        cachedDetails(url)?.takeIf { it.age() < RECENT_MS }?.value ?: details(url)

    /** Потоки серии (episode != null) или фильма. */
    fun streams(d: Details, translator: Translator, episode: Episode?): Streams {
        val params = mutableListOf("id" to d.id, "translator_id" to translator.id)
        if (episode != null) {
            params += listOf("season" to "${episode.season}", "episode" to "${episode.episode}", "action" to "get_stream")
        } else {
            params += listOf(
                "is_camrip" to translator.camrip.flag(), "is_ads" to translator.ads.flag(),
                "is_director" to translator.director.flag(), "action" to "get_movie",
            )
        }
        params += "favs" to d.favs.orEmpty()
        val json = post(ajax("get_cdn_series"), form(params), d.url)
        return StreamParser.streams(StreamParser.response(json))
    }

    /** Список серий у другой озвучки (у разных озвучек он разный). */
    fun translatorEpisodes(d: Details, translator: Translator): List<Episode> {
        val params = listOf("id" to d.id, "translator_id" to translator.id, "favs" to d.favs.orEmpty(), "action" to "get_episodes")
        val obj = StreamParser.response(post(ajax("get_cdn_series"), form(params), d.url))
        return RezkaParser.episodes(obj.optString("episodes"))
    }

    fun thumbnails(path: String): List<Tile> {
        val url = resolve(path)
        return StreamParser.thumbnails(request(url, null, null), url)
    }

    /** Отметка в «Досмотреть», как это делает плеер сайта. */
    fun saveProgress(d: Details, translatorId: String, episode: Episode?, positionMs: Long, durationMs: Long) {
        val body = Progress.saveForm(d.id, translatorId, episode?.season ?: 0, episode?.episode ?: 0, positionMs, durationMs)
        post(ajax("send_save"), body, d.url)
    }

    /**
     * Вход по email и паролю (с телефона). Отдельный запрос без наших cookies: на неверный пароль сайт
     * отвечает удалением cookies сессии, и текущий вход не должен от этого пострадать.
     * Пароль никуда не пишется.
     */
    fun login(email: String, password: String): LoginResult {
        val url = resolve("/ajax/login/")
        val body = form(listOf("login_name" to email, "login_password" to password, "login_not_save" to "0"))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.setRequestProperty("Origin", baseUrl.trimEnd('/'))
        conn.setRequestProperty("Referer", baseUrl)
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for login")
            val result = LoginResult.parse(decode(conn.inputStream))
            if (!result.success) return result
            val setCookies = conn.headerFields["Set-Cookie"].orEmpty()
            val session = setCookies.mapNotNull { Cookies.parseSetCookie(it, System.currentTimeMillis()) }
                .any { it.name == LOGIN_COOKIE && !it.expired }
            // «Успех» без cookie сессии — не затираем текущий вход
            if (!session) return LoginResult(false, LoginResult.DEFAULT_ERROR)
            cookies.replaceWith(setCookies)
            clearCaches()
            return result
        } finally {
            conn.disconnect()
        }
    }

    fun clearCaches() {
        pages.evictAll()
        details.evictAll()
        disk.clear()
    }

    fun logout() {
        cookies.clear()
        clearCaches()
    }

    /** Перед открытием сайта в WebView: там должны быть те же cookies, что и у нас. */
    fun exportCookiesToWebView() = cookies.exportToWebView(baseUrl)

    /** После WebView (вход, выход, сайт): забираем cookies себе. */
    fun importCookiesFromWebView() = cookies.importFromWebView(baseUrl)

    private fun ajax(name: String) = resolve("/ajax/$name/?t=${System.currentTimeMillis()}")

    /** Адрес на текущем зеркале: «/films/…» → «https://rezka.fi/films/…» */
    fun resolve(path: String) = URL(URL(baseUrl), path).toString()

    private fun fetch(url: String): String {
        val html = request(url, null, null)
        if (RezkaParser.isLoginPage(html)) throw NotLoggedInException()
        return html
    }

    private fun post(url: String, body: String, referer: String) = request(url, body, referer)

    private fun request(url: String, body: String?, referer: String?): String = timed("net ${path(url)}") {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", USER_AGENT)
        // Cookies сессии — только своему сайту, не CDN
        if (isSiteUrl(url)) cookies.header()?.let { conn.setRequestProperty("Cookie", it) }
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("Origin", baseUrl.trimEnd('/'))
            referer?.let { conn.setRequestProperty("Referer", it) }
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            if (isSiteUrl(url)) cookies.update(conn.headerFields["Set-Cookie"].orEmpty())
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for ${path(url)}")
            decode(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    // Сайт отдаёт gzip, даже если его не просили, и не всегда помечает это заголовком
    private fun decode(input: InputStream): String {
        val stream = BufferedInputStream(input)
        stream.mark(2)
        val gzip = stream.read() == 0x1f && stream.read() == 0x8b
        stream.reset()
        return (if (gzip) GZIPInputStream(stream) else stream).bufferedReader().use { it.readText() }
    }

    private inline fun <T> timed(what: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also { Log.d(TAG, "$what: ${SystemClock.elapsedRealtime() - start} ms") }
    }

    private fun isSiteUrl(url: String): Boolean {
        val host = URL(url).host.lowercase()
        val site = URL(baseUrl).host.lowercase().removePrefix("www.")
        return host == site || host.endsWith(".$site")
    }

    private fun path(url: String) = url.substringAfter("://").substringAfter('/').take(60)

    private fun Boolean.flag() = if (this) "1" else "0"

    private fun form(params: List<Pair<String, String>>) =
        params.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        const val DEFAULT_BASE = "https://rezka.fi/"
        private const val TAG = "RezkaTv"
        private const val TIMEOUT_MS = 20_000
        private const val RECENT_MS = 5 * 60_000L
        private const val LOGIN_COOKIE = "dle_user_id"

        /** Сеть и разбор HTML: слабому процессору больше трёх задач сразу во вред. */
        val io = Dispatchers.IO.limitedParallelism(3)

        @Volatile private var instance: RezkaClient? = null

        fun baseUrl(context: Context): String = Settings(context).baseUrl

        /** Смена зеркала: adb shell am start -n dev.alaev.rezkatv/.rezka.BrowseActivity --es base https://… */
        fun setBaseUrl(context: Context, url: String) {
            Settings(context).setBaseUrl(url)
            instance = null
        }

        /** С главного потока: при первом запуске cookies забираются из WebView, где выполнен вход. */
        fun get(context: Context): RezkaClient {
            val base = baseUrl(context)
            instance?.takeIf { it.baseUrl == base }?.let { return it }
            return RezkaClient(context.applicationContext, base).also {
                if (it.cookies.isEmpty()) it.importCookiesFromWebView()
                instance = it
                // Первый разбор Jsoup грузит таблицы сущностей (~1 с): пусть это случится, пока идёт сеть
                CoroutineScope(io).launch { Jsoup.parseBodyFragment("<p>&nbsp;</p>") }
            }
        }
    }
}
