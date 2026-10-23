package dev.alaev.rezkatv.rezka.login

import org.json.JSONObject

/** Ответ /ajax/login/: {"success":true} или {"success":false,"message":"…" | {"поле":"…"}}. */
data class LoginResult(val success: Boolean, val message: String) {

    companion object {
        private val TAGS = Regex("<[^>]*>")
        const val DEFAULT_ERROR = "Не удалось войти"

        fun parse(json: String): LoginResult {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return LoginResult(false, DEFAULT_ERROR)
            if (obj.optBoolean("success")) return LoginResult(true, "")
            val raw = when (val message = obj.opt("message")) {
                is JSONObject -> message.keys().asSequence().map { message.optString(it) }.joinToString("\n")
                is String -> message
                else -> ""
            }
            val text = raw.replace(TAGS, " ").replace(Regex("\\s+"), " ").trim()
            return LoginResult(false, text.ifEmpty { DEFAULT_ERROR })
        }
    }
}
