package dev.alaev.rezkatv.rezka.login

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginResultTest {

    @Test
    fun success() = assertEquals(LoginResult(true, ""), LoginResult.parse("""{"success":true}"""))

    @Test
    fun messageAsObject() {
        val json = """{"success":false,"message":{"email":"Введен неверный email или пароль"}}"""
        assertEquals(LoginResult(false, "Введен неверный email или пароль"), LoginResult.parse(json))
    }

    @Test
    fun messageAsStringWithHtml() {
        val json = """{"success":false,"message":"<b>Аккаунт</b> заблокирован<br>до завтра"}"""
        assertEquals(LoginResult(false, "Аккаунт заблокирован до завтра"), LoginResult.parse(json))
    }

    @Test
    fun garbageOrEmptyMessage() {
        assertEquals(LoginResult(false, LoginResult.DEFAULT_ERROR), LoginResult.parse("<html>502</html>"))
        assertEquals(LoginResult(false, LoginResult.DEFAULT_ERROR), LoginResult.parse("""{"success":false}"""))
    }
}
