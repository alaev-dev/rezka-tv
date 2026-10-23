package dev.alaev.rezkatv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    private val policy = UrlPolicy("rezka.fi")

    @Test
    fun allowsSiteAndSubdomains() {
        assertTrue(policy.allowNavigation("https://rezka.fi/films/"))
        assertTrue(policy.allowNavigation("https://www.rezka.fi/"))
        assertTrue(policy.allowNavigation("https://static.REZKA.fi/x"))
    }

    @Test
    fun blocksForeignHostsAndSchemes() {
        assertFalse(policy.allowNavigation("https://ads.example.com/click"))
        assertFalse(policy.allowNavigation("https://rezka.fi.evil.com/"))
        assertFalse(policy.allowNavigation("intent://foo#Intent;scheme=market;end"))
        assertFalse(policy.allowNavigation("market://details?id=x"))
        assertFalse(policy.allowNavigation("not a url"))
    }

    @Test
    fun wwwHomeMatchesBareDomain() {
        assertTrue(UrlPolicy("www.rezka.fi").allowNavigation("https://rezka.fi/"))
    }

    @Test
    fun blocksAdDomainsButNotLookalikes() {
        assertTrue(policy.isBlocked("securepubads.g.doubleclick.net"))
        assertTrue(policy.isBlocked("mc.yandex.ru"))
        assertFalse(policy.isBlocked("notdoubleclick.net"))
        assertFalse(policy.isBlocked("yandex.ru"))
        assertFalse(policy.isBlocked(null))
    }
}
