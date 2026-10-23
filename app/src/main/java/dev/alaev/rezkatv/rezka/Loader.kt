package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.withContext
import dev.alaev.rezkatv.R
import kotlin.coroutines.cancellation.CancellationException

/** Загрузка в фоне с общей для экрана обработкой ошибок: вход в аккаунт, «нет сети». */
class Loader(private val activity: Activity, private val status: TextView?) {

    private var loginShown = false

    /** null — ошибка (уже показана пользователю). */
    suspend fun <T> load(block: () -> T): T? = try {
        withContext(RezkaClient.io) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotLoggedInException) {
        if (!loginShown) {
            loginShown = true
            Toast.makeText(activity, R.string.login_required, Toast.LENGTH_LONG).show()
            activity.openLogin()
        }
        null
    } catch (e: Exception) {
        Log.w(TAG, "load failed", e)
        // Статус виден, только пока на экране ничего нет
        if (status?.visibility == View.VISIBLE) status.setText(R.string.load_failed)
        null
    }

    /** Без сообщений: подгрузка деталей, которые не обязательны. */
    suspend fun <T> quietly(block: () -> T): T? = try {
        withContext(RezkaClient.io) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "background load failed: $e")
        null
    }

    fun showStatus(text: Int) {
        status?.setText(text)
        status?.visibility = View.VISIBLE
    }

    fun hideStatus() {
        status?.visibility = View.GONE
    }

    private companion object {
        const val TAG = "RezkaTv"
    }
}
