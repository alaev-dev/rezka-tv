package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.aMapWithSize
import org.hamcrest.Matchers.anEmptyMap
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class FormDataTest {

    private fun escaped() = FormData.parse("k=abc&email=user%40mail.test&password=a+b%2Bc%26d%3De")

    private fun gaps() = FormData.parse("a=&b&=x&&c=1")

    @Test
    fun readsPlainValue() {
        val expected = "abc"
        val value = escaped()["k"]
        assertThat("обычное значение", value, equalTo(expected))
    }

    @Test
    fun decodesPercentEscapes() {
        val expected = "user@mail.test"
        val value = escaped()["email"]
        assertThat("%40 — это @", value, equalTo(expected))
    }

    @Test
    fun decodesPlusAsSpaceAndEscapedSpecials() {
        val expected = "a b+c&d=e"
        val value = escaped()["password"]
        assertThat("+ — пробел, %2B %26 %3D — сами символы", value, equalTo(expected))
    }

    @Test
    fun decodesUtf8Bytes() {
        val expected = "пароль 🔑"
        val form = FormData.parse("password=%D0%BF%D0%B0%D1%80%D0%BE%D0%BB%D1%8C+%F0%9F%94%91")
        val value = form["password"]
        assertThat("кириллица и эмодзи в UTF-8", value, equalTo(expected))
    }

    @Test
    fun readsEmptyValueAsEmpty() {
        val value = gaps()["a"]
        assertThat("a= — пустое значение", value, emptyString())
    }

    @Test
    fun readsNameWithoutEqualsAsEmptyValue() {
        val value = gaps()["b"]
        assertThat("b без = — пустое значение", value, emptyString())
    }

    @Test
    fun readsValueAfterGaps() {
        val expected = "1"
        val value = gaps()["c"]
        assertThat("значение после пустых пар", value, equalTo(expected))
    }

    @Test
    fun dropsPairWithoutName() {
        val expected = 3
        assertThat("пара без имени отброшена, пустые пропущены", gaps(), aMapWithSize(expected))
    }

    @Test
    fun readsEmptyBodyAsEmptyForm() {
        val form = FormData.parse("")
        assertThat("пустое тело — пустая форма", form, anEmptyMap())
    }

    @Test
    fun readsBrokenEscapeAsEmptyValue() {
        val form = FormData.parse("password=100%")
        val value = form["password"]
        assertThat("одинокий % не роняет разбор", value, emptyString())
    }

    @Test
    fun keepsOtherFieldsAfterBrokenEscape() {
        val expected = "ok"
        val form = FormData.parse("password=100%&k=ok")
        val value = form["k"]
        assertThat("остальные поля читаются", value, equalTo(expected))
    }
}
