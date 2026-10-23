package dev.alaev.rezkatv.rezka.login

import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Крошечный HTTP-сервер в локальной сети, пока открыт экран входа: отдаёт телефону страницу
 * и принимает с неё email и пароль — открытым текстом с этой же страницы или зашифрованными
 * с HTTPS-страницы входа (см. [LoginCrypto]). Без верного токена из QR — 403.
 * Пароль не пишется ни в лог, ни на диск: он только передаётся в [login].
 */
class LoginServer(
    private val address: Inet4Address,
    private val code: LoginCode,
    private val listener: Listener,
    private val login: (email: String, password: String) -> LoginResult,
) {
    /** Вызывается из потока сервера. */
    interface Listener {
        fun onPhoneConnected()
        fun onChecking()
        fun onResult(result: LoginResult, codeExhausted: Boolean)
    }

    @Volatile private var socket: ServerSocket? = null

    /** Занимает первый свободный порт из [PORTS]; -1 — не вышло. Блокирующий вызов. */
    fun start(): Int {
        for (port in PORTS) {
            val server = ServerSocket()
            try {
                server.reuseAddress = true
                server.bind(InetSocketAddress(address, port), BACKLOG)
            } catch (e: IOException) {
                server.close()
                continue
            }
            socket = server
            thread(name = "login-server", isDaemon = true) { serve(server) }
            return port
        }
        return -1
    }

    fun stop() {
        socket?.close()
        socket = null
    }

    private fun serve(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            // По одному запросу за раз: телефон один, а вход и так идёт последовательно
            client.use { runCatching { handle(it) }.onFailure { e -> Log.w(TAG, "login request failed: ${e.javaClass.simpleName}") } }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MS
        val request = HttpRequest.read(BufferedInputStream(client.getInputStream())) ?: return
        val out = client.getOutputStream()
        when {
            request.method == "GET" && request.path == "/" -> {
                if (!code.matches(request.query["k"])) return respond(out, 403, TEXT, FORBIDDEN)
                listener.onPhoneConnected()
                respond(out, 200, HTML, PhonePage.HTML)
            }
            request.method == "POST" && request.path == "/login" -> handleLogin(out, FormData.parse(request.body))
            else -> respond(out, if (request.path == "/") 405 else 404, TEXT, "Not found")
        }
    }

    private fun handleLogin(out: OutputStream, form: Map<String, String>) {
        if (!code.matches(form["k"])) return respond(out, 403, TEXT, FORBIDDEN)
        if (code.exhausted || code.expired()) return respondJson(out, 410, ok = false, EXPIRED, final = true)
        val encrypted = form["c"]?.let { code.crypto.decrypt(form["p"].orEmpty(), form["iv"].orEmpty(), it, code.token) }
        if (form.containsKey("c") && encrypted == null) return respondJson(out, 400, ok = false, UNREADABLE, final = false)
        val email = (encrypted?.email ?: form["email"].orEmpty()).trim()
        val password = encrypted?.password ?: form["password"].orEmpty()
        if (email.isEmpty() || password.isEmpty()) return respondJson(out, 400, ok = false, EMPTY, final = false)

        listener.onChecking()
        val result = try {
            login(email, password)
        } catch (e: IOException) {
            LoginResult(false, NO_SITE)
        }
        val exhausted = !result.success && code.registerFailure()
        val message = if (exhausted) "${result.message}. $TOO_MANY" else result.message
        respondJson(out, 200, result.success, message, final = result.success || exhausted)
        listener.onResult(result, exhausted)
        if (result.success) stop()
    }

    private fun respondJson(out: OutputStream, status: Int, ok: Boolean, message: String, final: Boolean) {
        val json = JSONObject().put("ok", ok).put("message", message).put("final", final).toString()
        respond(out, status, JSON, json)
    }

    private fun respond(out: OutputStream, status: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $status ${REASONS[status] ?: "Error"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(bytes)
        out.flush()
    }

    companion object {
        val PORTS = 8765..8775
        private const val TAG = "RezkaTv"
        private const val BACKLOG = 4
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val TEXT = "text/plain; charset=utf-8"
        private const val HTML = "text/html; charset=utf-8"
        private const val JSON = "application/json; charset=utf-8"
        private const val FORBIDDEN = "Неверный или устаревший код. Отсканируйте QR-код на телевизоре ещё раз."
        private const val EXPIRED = "Код устарел. Нажмите «Новый код» на телевизоре и отсканируйте его снова."
        private const val EMPTY = "Введите email и пароль"
        private const val UNREADABLE = "Телевизор не смог прочитать данные. Отсканируйте QR-код ещё раз"
        private const val NO_SITE = "Телевизор не смог связаться с сайтом rezka"
        private const val TOO_MANY = "Попытки закончились — нажмите «Новый код» на телевизоре"
        private val REASONS = mapOf(200 to "OK", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed", 410 to "Gone")
    }
}

/** Разбор HTTP/1.1-запроса с жёсткими лимитами: в локальной сети тоже бывают странные клиенты. */
data class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val body: String) {

    companion object {
        private const val MAX_LINE = 4096
        private const val MAX_HEADERS = 64
        const val MAX_BODY = 8192

        /** null — запрос битый или слишком большой. */
        fun read(input: InputStream): HttpRequest? {
            val requestLine = readLine(input) ?: return null
            val parts = requestLine.split(' ')
            if (parts.size < 3) return null
            var length = 0
            repeat(MAX_HEADERS) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) {
                    if (length !in 0..MAX_BODY) return null
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(body, read, length - read)
                        if (n < 0) return null
                        read += n
                    }
                    val target = parts[1]
                    return HttpRequest(
                        method = parts[0].uppercase(),
                        path = target.substringBefore('?'),
                        query = FormData.parse(target.substringAfter('?', "")),
                        body = String(body, Charsets.UTF_8),
                    )
                }
                if (line.substringBefore(':').trim().equals("Content-Length", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: return null
                }
            }
            return null
        }

        private fun readLine(input: InputStream): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return null
                if (b == '\n'.code) break
                if (bytes.size() >= MAX_LINE) return null
                bytes.write(b)
            }
            return bytes.toString("UTF-8").trimEnd('\r')
        }
    }
}
