package dev.alaev.rezkatv.rezka

import android.content.Context
import java.net.URLEncoder

/** Прогресс просмотра: запрос сайту («Досмотреть») и правила продолжения с места остановки. */
object Progress {

    /** Тело POST /ajax/send_save/ — те же поля, что шлёт плеер сайта (время в секундах). */
    fun saveForm(postId: String, translatorId: String, season: Int, episode: Int, positionMs: Long, durationMs: Long): String =
        listOf(
            "post_id" to postId,
            "translator_id" to translatorId,
            "season" to season.toString(),
            "episode" to episode.toString(),
            "current_time" to (positionMs / 1000).toString(),
            "duration" to (durationMs / 1000).toString(),
        ).joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    /** Досмотрено: остались титры (5% длительности, но не меньше полутора минут). */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs - maxOf(FINISH_MIN_TAIL_MS, durationMs / 20)

    /** С какого места начинать: начало, если смотрели совсем чуть-чуть или уже досмотрели. */
    fun startPosition(savedMs: Long, durationMs: Long): Long = when {
        savedMs < MIN_RESUME_MS -> 0
        durationMs > 0 && isFinished(savedMs, durationMs) -> 0
        else -> savedMs
    }

    private const val FINISH_MIN_TAIL_MS = 90_000L
    private const val MIN_RESUME_MS = 30_000L
}

/** Место остановки в каждой серии (сайт хранит его только в localStorage своего плеера). */
class ResumeStore(context: Context) {

    data class Entry(val positionMs: Long, val durationMs: Long) {
        val finished get() = Progress.isFinished(positionMs, durationMs)
        val remainingMs get() = (durationMs - positionMs).coerceAtLeast(0)
    }

    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    fun get(id: String, season: Int, episode: Int): Entry? =
        prefs.getString(key(id, season, episode), null)?.split(':')?.let { parts ->
            val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
            Entry(pos, parts.getOrNull(1)?.toLongOrNull() ?: 0)
        }

    fun put(id: String, season: Int, episode: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val editor = prefs.edit().putString(key(id, season, episode), "$positionMs:$durationMs:${System.currentTimeMillis()}")
        // Не даём файлу расти бесконечно: выкидываем самые старые записи
        val all = prefs.all
        if (all.size > MAX_ENTRIES) {
            all.entries.sortedBy { (it.value as? String)?.substringAfterLast(':')?.toLongOrNull() ?: 0 }
                .take(all.size - MAX_ENTRIES)
                .forEach { editor.remove(it.key) }
        }
        editor.apply()
    }

    private fun key(id: String, season: Int, episode: Int) = "$id:$season:$episode"

    private companion object {
        const val MAX_ENTRIES = 400
    }
}
