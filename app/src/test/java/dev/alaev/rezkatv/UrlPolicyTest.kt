package dev.alaev.rezkatv

import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class UrlPolicyTest {

    private fun policy() = UrlPolicy("rezka.fi")

    @Test
    fun allowsSitePage() {
        val url = "https://rezka.fi/films/"
        val allowed = policy().allowNavigation(url)
        assertThat("страница сайта открывается", allowed)
    }

    @Test
    fun allowsWwwSubdomain() {
        val url = "https://www.rezka.fi/"
        val allowed = policy().allowNavigation(url)
        assertThat("поддомен www — тот же сайт", allowed)
    }

    @Test
    fun allowsSubdomainInAnyLetterCase() {
        val url = "https://static.REZKA.fi/x"
        val allowed = policy().allowNavigation(url)
        assertThat("регистр домена не важен", allowed)
    }

    @Test
    fun blocksForeignHost() {
        val url = "https://ads.example.com/click"
        val allowed = policy().allowNavigation(url)
        assertThat("чужой сайт не открывается", !allowed)
    }

    @Test
    fun blocksLookalikeHostStartingWithSiteName() {
        val url = "https://rezka.fi.evil.com/"
        val allowed = policy().allowNavigation(url)
        assertThat("rezka.fi в начале чужого домена — не сайт", !allowed)
    }

    @Test
    fun blocksIntentScheme() {
        val url = "intent://foo#Intent;scheme=market;end"
        val allowed = policy().allowNavigation(url)
        assertThat("intent:// не уводит из приложения", !allowed)
    }

    @Test
    fun blocksMarketScheme() {
        val url = "market://details?id=x"
        val allowed = policy().allowNavigation(url)
        assertThat("market:// не открывает магазин", !allowed)
    }

    @Test
    fun blocksMalformedUrl() {
        val url = "not a url"
        val allowed = policy().allowNavigation(url)
        assertThat("не адрес — не открывается", !allowed)
    }

    @Test
    fun blocksDoubleClickAdHost() {
        val host = "securepubads.g.doubleclick.net"
        val blocked = policy().isBlocked(host)
        assertThat("реклама DoubleClick режется", blocked)
    }

    @Test
    fun blocksYandexCounterHost() {
        val host = "mc.yandex.ru"
        val blocked = policy().isBlocked(host)
        assertThat("счётчик Яндекса режется", blocked)
    }

    @Test
    fun passesHostThatOnlyEndsLikeAdDomain() {
        val host = "notdoubleclick.net"
        val blocked = policy().isBlocked(host)
        assertThat("похожий домен не режется", !blocked)
    }

    @Test
    fun passesParentDomainOfCounter() {
        val host = "yandex.ru"
        val blocked = policy().isBlocked(host)
        assertThat("сам yandex.ru не режется", !blocked)
    }

    @Test
    fun allowsBareDomainForWwwHome() {
        val policy = UrlPolicy("www.rezka.fi")
        val allowed = policy.allowNavigation("https://rezka.fi/")
        assertThat("домашний www.rezka.fi пускает и rezka.fi", allowed)
    }

    @Test
    fun passesMissingHost() {
        val blocked = policy().isBlocked(null)
        assertThat("без хоста нечего резать", !blocked)
    }
}
