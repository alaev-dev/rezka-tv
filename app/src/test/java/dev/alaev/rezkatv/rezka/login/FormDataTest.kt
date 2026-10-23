package dev.alaev.rezkatv.rezka.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormDataTest {

    @Test
    fun plusAndPercentEscapes() {
        val form = FormData.parse("k=abc&email=user%40mail.test&password=a+b%2Bc%26d%3De")
        assertEquals("abc", form["k"])
        assertEquals("user@mail.test", form["email"])
        assertEquals("a b+c&d=e", form["password"])
    }

    @Test
    fun utf8() {
        val form = FormData.parse("password=%D0%BF%D0%B0%D1%80%D0%BE%D0%BB%D1%8C+%F0%9F%94%91")
        assertEquals("пароль 🔑", form["password"])
    }

    @Test
    fun emptyAndMissingValues() {
        val form = FormData.parse("a=&b&=x&&c=1")
        assertEquals("", form["a"])
        assertEquals("", form["b"])
        assertEquals("1", form["c"])
        assertEquals(3, form.size) // пара без имени отброшена
        assertTrue(FormData.parse("").isEmpty())
    }

    @Test
    fun brokenEscapeIsEmptyNotCrash() {
        assertEquals("", FormData.parse("password=100%")["password"])
        assertEquals("ok", FormData.parse("password=100%&k=ok")["k"])
    }
}
