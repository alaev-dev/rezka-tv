package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiesTest {

    private val now = 1_800_000_000_000L // 2027 год

    @Test
    fun headerRoundTrip() {
        val cookies = Cookies.parseHeader("dle_user_id=12345; PHPSESSID=abc=def;  empty=")
        assertEquals(mapOf("dle_user_id" to "12345", "PHPSESSID" to "abc=def", "empty" to ""), cookies)
        assertEquals("a=1; b=2", Cookies.header(linkedMapOf("a" to "1", "b" to "2")))
        assertTrue(Cookies.parseHeader(null).isEmpty())
    }

    @Test
    fun setCookieValue() {
        val c = Cookies.parseSetCookie("rzk_last_refresh=1792; expires=Fri, 19-Nov-2027 08:52:00 GMT; path=/; domain=.rezka.test; HttpOnly", now)
        assertEquals(Cookies.SetCookie("rzk_last_refresh", "1792", expired = false), c)
    }

    @Test
    fun deletionsAreExpired() {
        assertTrue(Cookies.parseSetCookie("dle_password=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT; path=/", now)!!.expired)
        assertTrue(Cookies.parseSetCookie("PHPSESSID=x; Max-Age=0; path=/", now)!!.expired)
        assertTrue(Cookies.parseSetCookie("a=b; expires=Thu, 19 Nov 1981 08:52:00 GMT", now)!!.expired)
        assertFalse(Cookies.parseSetCookie("a=b; Max-Age=3600; expires=Thu, 19 Nov 1981 08:52:00 GMT", now)!!.expired)
    }

    @Test
    fun garbageIgnored() {
        assertNull(Cookies.parseSetCookie("=novalue", now))
        assertNull(Cookies.parseSetCookie("", now))
    }
}
