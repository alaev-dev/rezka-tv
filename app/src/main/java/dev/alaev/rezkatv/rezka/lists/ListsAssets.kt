package dev.alaev.rezkatv.rezka.lists

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import dev.alaev.rezkatv.rezka.WatchedStore
import java.io.FileNotFoundException
import java.util.concurrent.Executors

object ListsAssets {

    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor { Thread(it, "lists") }.asCoroutineDispatcher())
    private var top: Deferred<TopLists?>? = null
    private var oscars: Deferred<Oscars?>? = null
    private var watched: Job? = null

    @Synchronized
    fun top(context: Context): Deferred<TopLists?> = top ?: load(context, "top.json", ListsJson::top).also { top = it }

    @Synchronized
    fun oscars(context: Context): Deferred<Oscars?> = oscars ?: load(context, "oscars.json", ListsJson::oscars).also { oscars = it }

    @Synchronized
    fun preload(context: Context) {
        if (watched == null) watched = scope.launch { WatchedStore.get(context) }
        oscars(context)
        top(context)
    }

    private fun <T> load(context: Context, name: String, parse: (String) -> T): Deferred<T?> {
        val assets = context.applicationContext.assets
        return scope.async {
            val start = SystemClock.elapsedRealtime()
            try {
                parse(assets.open("lists/$name").bufferedReader().use { it.readText() })
                    .also { Log.i(TAG, "lists: $name parsed in ${SystemClock.elapsedRealtime() - start} ms") }
            } catch (e: FileNotFoundException) {
                Log.i(TAG, "lists: no $name")
                null
            } catch (e: Exception) {
                Log.w(TAG, "lists: broken $name", e)
                null
            }
        }
    }

    private const val TAG = "RezkaTv"
}
