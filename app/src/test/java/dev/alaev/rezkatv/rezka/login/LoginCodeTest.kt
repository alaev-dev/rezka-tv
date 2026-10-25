package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test

class LoginCodeTest {

    private fun code() = LoginCode(token = "0123456789abcdef0123456789abcdef")

    private fun failedAlmostAll(): LoginCode {
        val code = LoginCode()
        repeat(LoginCode.MAX_FAILURES - 1) { code.registerFailure() }
        return code
    }

    @Test
    fun generatesHexTokenOf128Bits() {
        val hex128 = Regex("[0-9a-f]{32}")
        val token = LoginCode.newToken()
        assertThat("токен — 32 шестнадцатеричных символа", token.matches(hex128))
    }

    @Test
    fun generatesNewTokenEachTime() {
        val first = LoginCode.newToken()
        val second = LoginCode.newToken()
        assertThat("токены случайны", second, not(equalTo(first)))
    }

    @Test
    fun acceptsSameToken() {
        val token = "0123456789abcdef0123456789abcdef"
        assertThat("тот же токен принимается", code().matches(token))
    }

    @Test
    fun rejectsTokenDifferingInLastCharacter() {
        val token = "0123456789abcdef0123456789abcdee"
        assertThat("отличие в последнем символе — отказ", !code().matches(token))
    }

    @Test
    fun rejectsTruncatedToken() {
        val token = "0123456789abcdef"
        assertThat("начало токена — отказ", !code().matches(token))
    }

    @Test
    fun rejectsEmptyToken() {
        val token = ""
        assertThat("пустой токен — отказ", !code().matches(token))
    }

    @Test
    fun rejectsMissingToken() {
        val token: String? = null
        assertThat("без токена — отказ", !code().matches(token))
    }

    @Test
    fun allowsRetriesBeforeLastFailure() {
        val code = LoginCode()
        val exhausted = (1 until LoginCode.MAX_FAILURES).map { code.registerFailure() }
        assertThat("до пятой ошибки попытки не кончаются", exhausted.none { it })
    }

    @Test
    fun keepsCodeUsableBeforeLastFailure() {
        val code = failedAlmostAll()
        assertThat("после четырёх ошибок код ещё действует", !code.exhausted)
    }

    @Test
    fun reportsExhaustionOnFifthFailure() {
        val code = failedAlmostAll()
        assertThat("пятый неверный пароль — попытки кончились", code.registerFailure())
    }

    @Test
    fun marksCodeExhaustedAfterFifthFailure() {
        val code = failedAlmostAll().also { it.registerFailure() }
        assertThat("после пятой ошибки код больше не принимается", code.exhausted)
    }

    @Test
    fun keepsCodeValidUntilLifetimeEnds() {
        val created = 1_000_000L
        val lastMoment = created + LoginCode.LIFETIME_MS - 1
        assertThat("до конца срока код действует", !LoginCode(createdAt = created).expired(lastMoment))
    }

    @Test
    fun expiresCodeAfterTenMinutes() {
        val created = 1_000_000L
        val deadline = created + LoginCode.LIFETIME_MS
        assertThat("через 10 минут код устаревает", LoginCode(createdAt = created).expired(deadline))
    }

    @Test
    fun buildsTvPageUrlWithToken() {
        val expected = "http://192.168.1.20:8765/?k=abc123"
        val url = LoginCode(token = "abc123").url("192.168.1.20", 8765)
        assertThat("страница телевизора с токеном", url, equalTo(expected))
    }

    @Test
    fun opensHttpsLoginPageFromQr() {
        val page = LoginCode(token = "abc123").phoneUrl("192.168.1.20", 8765).substringBefore('#')
        assertThat("до # — HTTPS-страница входа", page, equalTo(LoginCode.PHONE_PAGE))
    }

    @Test
    fun servesLoginPageOverHttps() {
        val https = "https://"
        assertThat("менеджер паролей подставляет пароль только на HTTPS", LoginCode.PHONE_PAGE, startsWith(https))
    }

    @Test
    fun keepsTvAddressTokenAndKeyInFragment() {
        val code = LoginCode(token = "abc123")
        val expected = "tv=192.168.1.20:8765&k=abc123&pk=${code.crypto.publicKey}"
        val fragment = code.phoneUrl("192.168.1.20", 8765).substringAfter('#')
        assertThat("адрес, токен и ключ — во фрагменте, на сервер не уходят", fragment, equalTo(expected))
    }
}
