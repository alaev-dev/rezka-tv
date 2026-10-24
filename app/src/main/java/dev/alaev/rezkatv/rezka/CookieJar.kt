package dev.alaev.rezkatv.rezka

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.text.SimpleDateFormat
import java.util.Locale

class CookieJar(context: Context) {

    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val lock = Any()

    fun isEmpty() = synchronized(lock) { prefs.all.isEmpty() }

    fun header(): String? = synchronized(lock) {
        Cookies.header(prefs.all.mapValues { it.value.toString() }).ifEmpty { null }
    }

    fun has(name: String) = synchronized(lock) { prefs.contains(name) }

    fun update(setCookieHeaders: List<String>): Unit = synchronized(lock) {
        if (setCookieHeaders.isEmpty()) return
        val editor = prefs.edit()
        setCookieHeaders.mapNotNull { Cookies.parseSetCookie(it, System.currentTimeMillis()) }.forEach {
            if (it.expired) editor.remove(it.name) else editor.putString(it.name, it.value)
        }
        editor.apply()
    }

    fun clear() = synchronized(lock) { prefs.edit().clear().apply() }

    fun replaceWith(setCookieHeaders: List<String>) = synchronized(lock) {
        val editor = prefs.edit().clear()
        setCookieHeaders.mapNotNull { Cookies.parseSetCookie(it, System.currentTimeMillis()) }
            .filter { !it.expired }
            .forEach { editor.putString(it.name, it.value) }
        editor.apply()
    }

    fun importFromWebView(baseUrl: String): Unit = synchronized(lock) {
        val cookies = Cookies.parseHeader(CookieManager.getInstance().getCookie(baseUrl))
        if (cookies.isEmpty()) return
        val editor = prefs.edit().clear()
        cookies.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    }

    fun exportToWebView(baseUrl: String) = synchronized(lock) {
        val manager = CookieManager.getInstance()
        val current = Cookies.parseHeader(manager.getCookie(baseUrl))
        val domain = "." + Uri.parse(baseUrl).host.orEmpty().removePrefix("www.")
        prefs.all.forEach { (name, value) ->
            if (current[name] != value) {
                manager.setCookie(baseUrl, "$name=$value; Domain=$domain; Path=/; Max-Age=$YEAR_S; HttpOnly")
            }
        }
        manager.flush()
    }

    private companion object {
        const val YEAR_S = 365 * 24 * 3600
    }
}

object Cookies {

    data class SetCookie(val name: String, val value: String, val expired: Boolean)

    fun parseHeader(header: String?): Map<String, String> =
        header.orEmpty().split(';').mapNotNull { part ->
            val name = part.substringBefore('=', "").trim()
            if (name.isEmpty()) null else name to part.substringAfter('=').trim()
        }.toMap()

    fun header(cookies: Map<String, String>) = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun parseSetCookie(line: String, nowMs: Long): SetCookie? {
        val parts = line.split(';')
        val name = parts[0].substringBefore('=', "").trim()
        if (name.isEmpty()) return null
        val value = parts[0].substringAfter('=').trim()
        val attributes = parts.drop(1).associate {
            it.substringBefore('=').trim().lowercase() to it.substringAfter('=', "").trim()
        }
        val maxAge = attributes["max-age"]?.toLongOrNull()
        val expires = attributes["expires"]?.let(::parseDate)
        val expired = value.isEmpty() || value == "deleted" ||
            (maxAge != null && maxAge <= 0) || (maxAge == null && expires != null && expires <= nowMs)
        return SetCookie(name, value, expired)
    }

    private val DATE_PARTS = Regex("""(\d{1,2})[ -](\w{3})[ -](\d{4})""")

    private fun parseDate(text: String): Long? = runCatching {
        SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.US).parse(DATE_PARTS.replace(text.trim(), "$1-$2-$3"))?.time
    }.getOrNull()
}
