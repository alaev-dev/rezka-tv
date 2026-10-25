package dev.alaev.rezkatv.rezka.login

import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

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

    fun matches(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())

    fun registerFailure(): Boolean {
        failures++
        return exhausted
    }

    fun url(host: String, port: Int) = "http://$host:$port/?k=$token"

    fun phoneUrl(host: String, port: Int) = "$PHONE_PAGE#tv=$host:$port&k=$token&pk=${crypto.publicKey}"

    companion object {
        const val MAX_FAILURES = 5
        const val LIFETIME_MS = 10 * 60_000L
        const val PHONE_PAGE = "https://alaev-dev.github.io/rezka-tv/login/"

        private val random = SecureRandom()

        fun newToken(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

object FormData {

    fun parse(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val name = decode(pair.substringBefore('='))
            if (name.isEmpty()) null else name to decode(pair.substringAfter('=', ""))
        }.toMap()

    private fun decode(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: IllegalArgumentException) {
        ""
    }
}
