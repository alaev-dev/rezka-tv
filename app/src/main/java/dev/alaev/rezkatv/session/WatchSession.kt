package dev.alaev.rezkatv.session

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Что сайт знает о просмотре: озвучка, сезон, серия (время сайт не отдаёт). */
data class SitePosition(val translatorId: String, val season: Int, val episode: Int)

/**
 * Последний просмотр — чтобы после выключения телевизора продолжить с того же места.
 * Файл, а не prefs: атомарная запись переживает выдёргивание вилки.
 */
data class WatchSession(
    val app: String,
    /** страница тайтла */
    val url: String,
    val title: String,
    val poster: String = "",
    val translatorId: String = "",
    val translatorName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long,
    val bootCount: Int,
    /** false — вышли сами (Назад, досмотрели): тогда не предлагаем продолжить */
    val interrupted: Boolean,
    /** плеер остановился, потому что погас экран (телевизор выключили пультом) */
    val stoppedByScreenOff: Boolean = false,
    /** когда последний раз показали вопрос «Продолжить?»; 0 — не показывали */
    val promptedAt: Long = 0,
    /** что последним успешно отправили на сайт: если сайт показывает другое — смотрели на другом устройстве */
    val reported: SitePosition? = null,
) {
    val isSeries get() = season > 0

    /** «S2:E8» или пусто */
    val episodeCode get() = if (isSeries) "S$season:E$episode" else ""

    fun toJson(): String = JSONObject()
        .put("app", app).put("url", url).put("title", title).put("poster", poster)
        .put("translatorId", translatorId).put("translatorName", translatorName)
        .put("season", season).put("episode", episode)
        .put("positionMs", positionMs).put("durationMs", durationMs)
        .put("updatedAt", updatedAt).put("bootCount", bootCount)
        .put("interrupted", interrupted).put("stoppedByScreenOff", stoppedByScreenOff)
        .put("promptedAt", promptedAt)
        .put("reported", reported?.let { JSONObject().put("t", it.translatorId).put("s", it.season).put("e", it.episode) } ?: JSONObject.NULL)
        .toString()

    companion object {
        const val APP_REZKA = "rezka"

        fun fromJson(json: String): WatchSession? = runCatching {
            val o = JSONObject(json)
            WatchSession(
                app = o.getString("app"),
                url = o.getString("url"),
                title = o.getString("title"),
                poster = o.optString("poster"),
                translatorId = o.optString("translatorId"),
                translatorName = o.optString("translatorName"),
                season = o.optInt("season"),
                episode = o.optInt("episode"),
                positionMs = o.optLong("positionMs"),
                durationMs = o.optLong("durationMs"),
                updatedAt = o.getLong("updatedAt"),
                bootCount = o.optInt("bootCount", -1),
                interrupted = o.getBoolean("interrupted"),
                stoppedByScreenOff = o.optBoolean("stoppedByScreenOff"),
                promptedAt = o.optLong("promptedAt"),
                reported = o.optJSONObject("reported")?.let { SitePosition(it.getString("t"), it.getInt("s"), it.getInt("e")) },
            )
        }.getOrNull()
    }
}

/** Атомарная запись (временный файл + fsync + rename): выключение питания посреди записи файл не портит. */
class SessionStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "watch_session.json"))

    fun read(): WatchSession? = synchronized(LOCK) {
        runCatching { WatchSession.fromJson(String(file.readFully())) }.getOrNull()
    }

    fun write(session: WatchSession): Unit = synchronized(LOCK) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.w(TAG, "session write failed: $e")
            return
        }
        try {
            out.write(session.toJson().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "session write failed: $e")
        }
    }

    /** Изменить сохранённое, если оно про тот же просмотр. */
    fun update(transform: (WatchSession) -> WatchSession): Unit = synchronized(LOCK) {
        read()?.let { write(transform(it)) }
    }

    fun clear(): Unit = synchronized(LOCK) { file.delete() }

    companion object {
        private const val TAG = "RezkaTv"
        private val LOCK = Any()

        /** Номер загрузки системы: меняется при каждом включении после полного выключения. */
        fun bootCount(context: Context): Int =
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }
}
