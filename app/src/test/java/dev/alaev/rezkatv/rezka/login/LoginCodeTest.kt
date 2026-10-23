package dev.alaev.rezkatv.rezka.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginCodeTest {

    @Test
    fun tokenIs128BitHexAndRandom() {
        val token = LoginCode.newToken()
        assertTrue(token.matches(Regex("[0-9a-f]{32}")))
        assertNotEquals(token, LoginCode.newToken())
    }

    @Test
    fun tokenCheck() {
        val code = LoginCode(token = "0123456789abcdef0123456789abcdef")
        assertTrue(code.matches("0123456789abcdef0123456789abcdef"))
        assertFalse(code.matches("0123456789abcdef0123456789abcdee"))
        assertFalse(code.matches("0123456789abcdef"))
        assertFalse(code.matches(""))
        assertFalse(code.matches(null))
    }

    @Test
    fun fiveWrongPasswordsExhaustTheCode() {
        val code = LoginCode()
        repeat(LoginCode.MAX_FAILURES - 1) { assertFalse(code.registerFailure()) }
        assertFalse(code.exhausted)
        assertTrue(code.registerFailure())
        assertTrue(code.exhausted)
    }

    @Test
    fun expiresAfterTenMinutes() {
        val code = LoginCode(createdAt = 1_000_000)
        assertFalse(code.expired(1_000_000 + LoginCode.LIFETIME_MS - 1))
        assertTrue(code.expired(1_000_000 + LoginCode.LIFETIME_MS))
    }

    @Test
    fun qrUrl() {
        val code = LoginCode(token = "abc123")
        assertEquals("http://192.168.1.20:8765/?k=abc123", code.url("192.168.1.20", 8765))
    }

    @Test
    fun phoneUrlKeepsSecretsInFragment() {
        val code = LoginCode(token = "abc123")
        val url = code.phoneUrl("192.168.1.20", 8765)
        assertEquals(LoginCode.PHONE_PAGE, url.substringBefore('#'))
        assertTrue(LoginCode.PHONE_PAGE.startsWith("https://"))
        assertEquals("tv=192.168.1.20:8765&k=abc123&pk=${code.crypto.publicKey}", url.substringAfter('#'))
    }
}
