package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class LoginResultTest {

    @Test
    fun readsSuccessfulLogin() {
        val expected = LoginResult(true, "")
        val json = """{"success":true}"""
        assertThat("успешный вход без сообщения", LoginResult.parse(json), equalTo(expected))
    }

    @Test
    fun readsErrorMessageFromObject() {
        val message = "Введен неверный email или пароль"
        val expected = LoginResult(false, message)
        val json = """{"success":false,"message":{"email":"$message"}}"""
        assertThat("сообщение внутри объекта по полям", LoginResult.parse(json), equalTo(expected))
    }

    @Test
    fun stripsHtmlFromErrorMessage() {
        val expected = LoginResult(false, "Аккаунт заблокирован до завтра")
        val json = """{"success":false,"message":"<b>Аккаунт</b> заблокирован<br>до завтра"}"""
        assertThat("теги убраны, <br> стал пробелом", LoginResult.parse(json), equalTo(expected))
    }

    @Test
    fun reportsDefaultErrorForNonJsonResponse() {
        val expected = LoginResult(false, LoginResult.DEFAULT_ERROR)
        val html = "<html>502</html>"
        assertThat("страница ошибки вместо JSON", LoginResult.parse(html), equalTo(expected))
    }

    @Test
    fun reportsDefaultErrorWhenMessageIsMissing() {
        val expected = LoginResult(false, LoginResult.DEFAULT_ERROR)
        val json = """{"success":false}"""
        assertThat("отказ без сообщения", LoginResult.parse(json), equalTo(expected))
    }
}
