package dev.alaev.rezkatv.rezka.login

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class HttpRequestTest {

    private fun read(raw: String) = HttpRequest.read(ByteArrayInputStream(raw.toByteArray()))

    private fun get() = read("GET /?k=abc HTTP/1.1\r\nHost: 192.168.1.20:8765\r\n\r\n")

    private fun post(body: String) =
        read("POST /login HTTP/1.1\r\ncontent-length: ${body.toByteArray().size}\r\n\r\n${body}EXTRA")

    @Test
    fun readsMethodOfGetRequest() {
        val expected = "GET"
        assertThat("метод GET", get()?.method, equalTo(expected))
    }

    @Test
    fun readsPathWithoutQuery() {
        val expected = "/"
        assertThat("путь без параметров", get()?.path, equalTo(expected))
    }

    @Test
    fun readsTokenFromQuery() {
        val expected = "abc"
        val token = get()?.query?.get("k")
        assertThat("токен из параметров адреса", token, equalTo(expected))
    }

    @Test
    fun readsEmptyBodyOfGetRequest() {
        assertThat("у GET тела нет", get()?.body, emptyString())
    }

    @Test
    fun readsPathOfPostRequest() {
        val expected = "/login"
        val request = post("k=abc")
        assertThat("путь POST", request?.path, equalTo(expected))
    }

    @Test
    fun readsBodyExactlyByContentLength() {
        val body = "k=abc&email=a%40b.test&password=%D0%BF"
        assertThat("тело ровно по Content-Length, лишнее отброшено", post(body)?.body, equalTo(body))
    }

    @Test
    fun keepsUtf8PasswordInBody() {
        val expected = "п"
        val password = FormData.parse(post("password=%D0%BF")?.body.orEmpty())["password"]
        assertThat("пароль в UTF-8 доходит целым", password, equalTo(expected))
    }

    @Test
    fun rejectsOversizedBody() {
        val raw = "POST /login HTTP/1.1\r\nContent-Length: ${HttpRequest.MAX_BODY + 1}\r\n\r\n"
        assertThat("слишком большое тело не читается", read(raw), nullValue())
    }

    @Test
    fun rejectsTruncatedBody() {
        val raw = "POST /login HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"
        assertThat("обрезанное тело — запрос битый", read(raw), nullValue())
    }

    @Test
    fun rejectsNonNumericContentLength() {
        val raw = "POST /login HTTP/1.1\r\nContent-Length: x\r\n\r\n"
        assertThat("длина не число — запрос битый", read(raw), nullValue())
    }

    @Test
    fun rejectsGarbageRequestLine() {
        val raw = "garbage\r\n\r\n"
        assertThat("мусор вместо запроса", read(raw), nullValue())
    }

    @Test
    fun rejectsEmptyConnection() {
        val raw = ""
        assertThat("пустое соединение", read(raw), nullValue())
    }
}
