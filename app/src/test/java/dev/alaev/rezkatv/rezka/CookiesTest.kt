package dev.alaev.rezkatv.rezka

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class CookiesTest {

    private fun expired(header: String, now: Long) = requireNotNull(Cookies.parseSetCookie(header, now)).expired

    @Test
    fun parsesCookieHeaderWithEqualsInsideValue() {
        val expected = mapOf("dle_user_id" to "12345", "PHPSESSID" to "abc=def", "empty" to "")
        val cookies = Cookies.parseHeader("dle_user_id=12345; PHPSESSID=abc=def;  empty=")
        assertThat("значение с = и пустое значение читаются", cookies, equalTo(expected))
    }

    @Test
    fun writesCookieHeaderInOrder() {
        val expected = "a=1; b=2"
        val header = Cookies.header(linkedMapOf("a" to "1", "b" to "2"))
        assertThat("заголовок Cookie в том же порядке", header, equalTo(expected))
    }

    @Test
    fun parsesMissingHeaderAsNoCookies() {
        val cookies = Cookies.parseHeader(null)
        assertThat("нет заголовка — нет cookies", cookies, anEmptyMap())
    }

    @Test
    fun readsValueOfSetCookieWithAttributes() {
        val expected = Cookies.SetCookie("rzk_last_refresh", "1792", expired = false)
        val header = "rzk_last_refresh=1792; expires=Fri, 19-Nov-2027 08:52:00 GMT; " +
            "path=/; domain=.rezka.test; HttpOnly"
        val now = 1_800_000_000_000L
        assertThat("имя и значение без атрибутов", Cookies.parseSetCookie(header, now), equalTo(expected))
    }

    @Test
    fun treatsDeletionByPastExpiresAsExpired() {
        val header = "dle_password=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT; path=/"
        val now = 1_800_000_000_000L
        assertThat("удаление через дату в прошлом", expired(header, now))
    }

    @Test
    fun treatsZeroMaxAgeAsExpired() {
        val header = "PHPSESSID=x; Max-Age=0; path=/"
        val now = 1_800_000_000_000L
        assertThat("удаление через Max-Age=0", expired(header, now))
    }

    @Test
    fun treatsPastExpiresInRfcFormatAsExpired() {
        val header = "a=b; expires=Thu, 19 Nov 1981 08:52:00 GMT"
        val now = 1_800_000_000_000L
        assertThat("дата в прошлом в формате RFC 1123", expired(header, now))
    }

    @Test
    fun prefersMaxAgeOverPastExpires() {
        val header = "a=b; Max-Age=3600; expires=Thu, 19 Nov 1981 08:52:00 GMT"
        val now = 1_800_000_000_000L
        assertThat("Max-Age важнее expires", !expired(header, now))
    }

    @Test
    fun ignoresSetCookieWithoutName() {
        val header = "=novalue"
        val now = 1_800_000_000_000L
        assertThat("без имени — не cookie", Cookies.parseSetCookie(header, now), nullValue())
    }

    @Test
    fun ignoresEmptySetCookie() {
        val header = ""
        val now = 1_800_000_000_000L
        assertThat("пустой Set-Cookie — не cookie", Cookies.parseSetCookie(header, now), nullValue())
    }
}
