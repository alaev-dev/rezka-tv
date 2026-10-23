package dev.alaev.rezkatv.rezka.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpRequestTest {

    private fun read(raw: String) = HttpRequest.read(ByteArrayInputStream(raw.toByteArray()))

    @Test
    fun getWithToken() {
        val request = read("GET /?k=abc HTTP/1.1\r\nHost: 192.168.1.20:8765\r\n\r\n")!!
        assertEquals("GET", request.method)
        assertEquals("/", request.path)
        assertEquals("abc", request.query["k"])
        assertEquals("", request.body)
    }

    @Test
    fun postBodyByContentLength() {
        val body = "k=abc&email=a%40b.test&password=%D0%BF"
        val request = read("POST /login HTTP/1.1\r\ncontent-length: ${body.toByteArray().size}\r\n\r\n${body}EXTRA")!!
        assertEquals("POST", request.method)
        assertEquals("/login", request.path)
        assertEquals(body, request.body)
        assertEquals("п", FormData.parse(request.body)["password"])
    }

    @Test
    fun rejectsOversizedTruncatedOrBroken() {
        assertNull(read("POST /login HTTP/1.1\r\nContent-Length: ${HttpRequest.MAX_BODY + 1}\r\n\r\n"))
        assertNull(read("POST /login HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"))
        assertNull(read("POST /login HTTP/1.1\r\nContent-Length: x\r\n\r\n"))
        assertNull(read("garbage\r\n\r\n"))
        assertNull(read(""))
    }
}
