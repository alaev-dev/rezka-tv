package dev.alaev.rezkatv.rezka

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * «Просмотрено» по id тайтла rezka. Своя пометка приложения: сайт такого не хранит.
 * Память — сразу (отметка на экране без задержки), файл — в фоне, атомарно: временный файл, fsync, rename.
 * Все экраны rezka живут в одном процессе :web, поэтому хватает одного экземпляра на процесс.
 */
class WatchedStore(private val file: File, private val writer: Executor = BACKGROUND) {

    /** id → когда отметили */
    private val marks = LinkedHashMap<String, Long>()

    init {
        runCatching {
            if (file.exists()) {
                val watched = JSONObject(file.readText()).getJSONObject("watched")
                // Испорченная запись не должна стоить остальных: следующая запись файла их бы потеряла
                watched.keys().forEach { id -> watched.optLong(id, -1).takeIf { it >= 0 }?.let { marks[id] = it } }
            }
        }.onFailure { Log.w(TAG, "watched: unreadable, starting empty: $it") }
    }

    @Synchronized
    fun isWatched(id: String?): Boolean = id != null && id in marks

    /** false — и так было отмечено (или не было) */
    @Synchronized
    fun set(id: String, watched: Boolean): Boolean {
        if (watched == (id in marks)) return false
        if (watched) marks[id] = System.currentTimeMillis() else marks.remove(id)
        // Снимок под той же блокировкой: записи уходят в файл в том же порядке, что и изменения
        val json = toJson()
        writer.execute { write(json) }
        return true
    }

    /** Новое состояние */
    fun toggle(id: String): Boolean {
        val now = !isWatched(id)
        set(id, now)
        return now
    }

    private fun toJson(): String {
        val watched = JSONObject()
        marks.forEach { (id, time) -> watched.put(id, time) }
        return JSONObject().put("watched", watched).toString()
    }

    private fun write(json: String) = synchronized(file) {
        val tmp = File(file.path + ".tmp")
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(tmp).use { out ->
                out.write(json.toByteArray())
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) throw java.io.IOException("rename failed")
        } catch (e: Exception) {
            Log.w(TAG, "watched: write failed: $e")
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "RezkaTv"
        private val BACKGROUND = Executors.newSingleThreadExecutor()

        @Volatile private var instance: WatchedStore? = null

        fun get(context: Context): WatchedStore =
            instance ?: synchronized(this) {
                instance ?: WatchedStore(File(context.applicationContext.filesDir, "watched.json")).also { instance = it }
            }

        /** Фильм досмотрен, если показано не меньше 90 %: титры обычно не досматривают. */
        fun reached(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs * 10 >= durationMs * 9
    }
}
