package dev.alaev.rezkatv

import java.net.URI
import java.net.URISyntaxException

class UrlPolicy(siteHost: String) {

    private val site = siteHost.lowercase().removePrefix("www.")

    fun allowNavigation(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme in WEB_SCHEMES && matches(host, site)
    }

    fun isBlocked(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return BLOCKED_DOMAINS.any { matches(h, it) }
    }

    private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun parse(url: String) = try {
        URI(url)
    } catch (e: URISyntaxException) {
        null
    }

    companion object {
        private val WEB_SCHEMES = setOf("http", "https")

        val BLOCKED_DOMAINS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "adservice.google.com",
            "mc.yandex.ru", "an.yandex.ru", "adfox.ru", "adriver.ru", "ad.mail.ru",
            "top-fwz1.mail.ru", "counter.yadro.ru", "scorecardresearch.com",
            "propellerads.com", "popads.net", "onclickads.net", "exoclick.com",
            "adsterra.com", "hilltopads.net",
        )
    }
}
