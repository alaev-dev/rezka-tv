package dev.alaev.rezkatv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import dev.alaev.rezkatv.rezka.RezkaClient
import java.io.ByteArrayInputStream
import java.net.URI

class RezkaActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var overlay: TextView
    private lateinit var policy: UrlPolicy
    private var customView: View? = null
    private var swallowKeyUp = NO_KEY
    private var playerOnly = false

    @Volatile private var playerMode = false

    private val tvScript by lazy { assets.open("tv.js").bufferedReader().use { it.readText() } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        playerOnly = intent.getBooleanExtra(EXTRA_PLAYER, false)
        val url = intent.getStringExtra(EXTRA_URL) ?: RezkaClient.baseUrl(this)
        policy = UrlPolicy(URI(RezkaClient.baseUrl(this)).host)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        overlay = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            if (playerOnly) setText(R.string.loading) else visibility = View.GONE
        }
        root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        if (playerOnly) overlay.postDelayed({ overlay.visibility = View.GONE }, PLAYER_TIMEOUT_MS)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            userAgentString = RezkaClient.USER_AGENT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.addJavascriptInterface(Bridge(), "TVBridge")
        web.webViewClient = Client()
        web.webChromeClient = Chrome()

        RezkaClient.get(this).exportCookiesToWebView()
        web.loadUrl(url)
        web.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        if (!down && code == swallowKeyUp) {
            swallowKeyUp = NO_KEY
            return true
        }
        if (playerOnly && code == KeyEvent.KEYCODE_BACK) {
            if (down) {
                swallowKeyUp = code
                finish()
            }
            return true
        }
        if (overlay.visibility == View.VISIBLE && !playerOnly && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (down) {
                swallowKeyUp = code
                overlay.visibility = View.GONE
                web.reload()
            }
            return true
        }
        if (down && PlayerKeys.handles(code, playerMode)) {
            swallowKeyUp = code
            PlayerKeys.command(code, event.repeatCount)?.let { web.evaluateJavascript(it.toJs(), null) }
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) goBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun goBack() {
        when {
            customView != null -> web.evaluateJavascript("document.exitFullscreen&&document.exitFullscreen()", null)
            web.canGoBack() -> web.goBack()
            else -> finish()
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.evaluateJavascript(PlayerCommand("pause").toJs(), null)
        web.onPause()
        CookieManager.getInstance().flush()
        RezkaClient.get(this).importCookiesFromWebView()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun setPlayerMode(on: Boolean) {
            playerMode = on
            if (on && playerOnly) runOnUiThread { overlay.visibility = View.GONE }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame || policy.allowNavigation(request.url.toString())) return false
            Log.i(TAG, "blocked navigation: ${request.url}")
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!policy.isBlocked(request.url.host)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageCommitVisible(view: WebView, url: String) = inject(view)

        override fun onPageFinished(view: WebView, url: String) = inject(view)

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            overlay.text = getString(R.string.load_error, request.url.host, error.description)
            overlay.visibility = View.VISIBLE
        }

        private fun inject(view: WebView) {
            val flags = if (playerOnly) "window.__tvAutoplay=true;" else ""
            view.evaluateJavascript(flags + tvScript, null)
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView = view
            root.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        override fun onHideCustomView() {
            customView?.let(root::removeView)
            customView = null
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val TAG = "RezkaTv"
        private const val EXTRA_URL = "url"
        private const val EXTRA_PLAYER = "player"
        private const val NO_KEY = -1
        private const val PLAYER_TIMEOUT_MS = 20_000L

        fun siteIntent(context: Context, url: String? = null): Intent =
            Intent(context, RezkaActivity::class.java).putExtra(EXTRA_URL, url)

        fun playerIntent(context: Context, url: String): Intent =
            Intent(context, RezkaActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_PLAYER, true)
    }
}
