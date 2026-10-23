package dev.alaev.rezkatv.rezka.login

import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Одноразовый код входа с телефона: 128-битный токен в ссылке из QR, ключи для шифрования пароля,
 * срок жизни и счётчик неудачных попыток. После [MAX_FAILURES] неверных паролей код больше не принимается.
 */
class LoginCode(
    val token: String = newToken(),
    private val createdAt: Long = System.currentTimeMillis(),
    private val lifetimeMs: Long = LIFETIME_MS,
    val crypto: LoginCrypto = LoginCrypto(),
) {
    @Volatile var failures = 0
        private set

    val exhausted get() = failures >= MAX_FAILURES

    fun expired(now: Long = System.currentTimeMillis()) = now - createdAt >= lifetimeMs

    /** Сравнение за постоянное время: по времени ответа токен не подобрать. */
    fun matches(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())

    /** Учитывает неверный пароль; true — попытки кончились. */
    fun registerFailure(): Boolean {
        failures++
        return exhausted
    }

    /** Страница самого телевизора: работает и без интернета, но менеджеры паролей на http:// часто не подставляют пароль. */
    fun url(host: String, port: Int) = "http://$host:$port/?k=$token"

    /**
     * Ссылка для QR-кода: HTTPS-страница входа, где менеджер паролей телефона подставляет пароль.
     * Всё, что после «#», браузер на сервер не отправляет: адрес телевизора, токен и ключ видит только телефон.
     */
    fun phoneUrl(host: String, port: Int) = "$PHONE_PAGE#tv=$host:$port&k=$token&pk=${crypto.publicKey}"

    companion object {
        const val MAX_FAILURES = 5
        const val LIFETIME_MS = 10 * 60_000L
        /** Статичная страница из `docs/login/` репозитория на GitHub Pages; в форке — свой адрес. */
        const val PHONE_PAGE = "https://alaev-dev.github.io/rezka-tv/login/"

        private val random = SecureRandom()

        fun newToken(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** application/x-www-form-urlencoded и query string: «+» — пробел, %xx — байты UTF-8. */
object FormData {

    fun parse(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val name = decode(pair.substringBefore('='))
            if (name.isEmpty()) null else name to decode(pair.substringAfter('=', ""))
        }.toMap()

    /** Битую кодировку (одинокий «%») не роняем, а считаем пустым значением. */
    private fun decode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: IllegalArgumentException) {
        ""
    }
}
