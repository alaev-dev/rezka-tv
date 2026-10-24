package dev.alaev.rezkatv.rezka

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.withContext
import dev.alaev.rezkatv.R
import kotlin.coroutines.cancellation.CancellationException

class Loader(private val activity: Activity, private val status: TextView?) {

    private var loginShown = false

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
        if (status?.visibility == View.VISIBLE) status.setText(R.string.load_failed)
        null
    }

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
