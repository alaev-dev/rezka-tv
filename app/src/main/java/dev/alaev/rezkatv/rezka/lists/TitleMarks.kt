package dev.alaev.rezkatv.rezka.lists

import android.content.Context
import dev.alaev.rezkatv.rezka.ResumeStore
import dev.alaev.rezkatv.rezka.RezkaClient
import dev.alaev.rezkatv.rezka.Shelf
import dev.alaev.rezkatv.rezka.WatchedStore

/** Отметка на постере: смотрели целиком, начали (есть в «Досмотреть») или ничего. */
enum class Mark { NONE, STARTED, WATCHED }

/**
 * Отметки на постерах: «просмотрено» — своя отметка приложения, «начато» — тайтл в «Досмотреть»
 * (его уже загрузила главная) или фильм, недосмотренный в нашем плеере.
 */
class TitleMarks(context: Context, private val client: RezkaClient) {

    private val context = context.applicationContext
    // Файл отметок читается при первом обращении (обычно — в фоне, см. ListsAssets.preload)
    private val watched by lazy { WatchedStore.get(this.context) }
    private val resume = ResumeStore(context)
    private var started: Set<String> = emptySet()

    /** Перечитать «Досмотреть» из кэша главной — без сети. */
    fun refresh() {
        started = client.cachedPage(client.shelfUrl(Shelf.CONTINUE))?.value?.cards?.mapTo(HashSet()) { it.id }.orEmpty()
    }

    fun isWatched(id: String?) = watched.isWatched(id)

    /** [progress] — если уже посчитан для этого id ([progress]), чтобы не читать его второй раз. */
    fun of(id: String?, progress: Int? = progress(id)): Mark = when {
        id == null -> Mark.NONE
        watched.isWatched(id) -> Mark.WATCHED
        id in started || progress != null -> Mark.STARTED
        else -> Mark.NONE
    }

    /** Доля просмотра фильма в нашем плеере, 1..99; null — не начинали или досмотрели. */
    fun progress(id: String?): Int? {
        if (id == null || watched.isWatched(id)) return null
        val entry = resume.get(id, 0, 0)?.takeIf { it.positionMs > 0 && !it.finished && it.durationMs > 0 } ?: return null
        return (entry.positionMs * 100 / entry.durationMs).toInt().coerceIn(1, 99)
    }

    /** Новое состояние */
    fun toggle(id: String): Boolean = watched.toggle(id)
}
