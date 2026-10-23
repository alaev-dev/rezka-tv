package dev.alaev.rezkatv.rezka

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class WatchedStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = Executor { it.run() }

    private fun file() = File(tmp.root, "watched.json")

    @Test
    fun marksSurviveRestart() {
        val store = WatchedStore(file(), now)
        assertFalse(store.isWatched("1167"))
        assertTrue(store.set("1167", true))
        assertFalse(store.set("1167", true)) // уже отмечен — файл не трогаем
        assertTrue(store.set("829", true))
        assertTrue(store.isWatched("1167"))

        val reopened = WatchedStore(file(), now)
        assertTrue(reopened.isWatched("1167"))
        assertTrue(reopened.isWatched("829"))
        assertFalse(reopened.isWatched(null))
    }

    @Test
    fun toggleAndUnmark() {
        val store = WatchedStore(file(), now)
        assertTrue(store.toggle("5"))
        assertFalse(store.toggle("5"))
        assertFalse(store.set("5", false))
        assertFalse(WatchedStore(file(), now).isWatched("5"))
        assertFalse(File(file().path + ".tmp").exists())
    }

    @Test
    fun brokenFileStartsEmpty() {
        file().writeText("{не json")
        val store = WatchedStore(file(), now)
        assertFalse(store.isWatched("1"))
        assertTrue(store.set("1", true))
        assertTrue(WatchedStore(file(), now).isWatched("1"))
    }

    @Test
    fun writesHappenInOrderOffTheCaller() {
        val queued = mutableListOf<Runnable>()
        val store = WatchedStore(file(), Executor { queued += it })
        store.set("1", true)
        store.set("2", true)
        store.set("1", false)
        assertTrue(store.isWatched("2")) // в памяти — сразу
        assertFalse(file().exists()) // на диск — в фоне
        queued.forEach { it.run() }
        val reopened = WatchedStore(file(), now)
        assertFalse(reopened.isWatched("1"))
        assertTrue(reopened.isWatched("2"))
    }

    @Test
    fun filmCountsAsWatchedAtNinetyPercent() {
        val film = 120 * 60_000L
        assertFalse(WatchedStore.reached(film * 89 / 100, film))
        assertTrue(WatchedStore.reached(film * 9 / 10, film))
        assertTrue(WatchedStore.reached(film, film))
        assertFalse(WatchedStore.reached(1000, 0))
    }
}
