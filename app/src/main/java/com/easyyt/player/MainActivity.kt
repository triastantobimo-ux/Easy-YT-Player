package com.easyyt.player

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_URL = "https://www.youtube.com/"
        const val DESKTOP_URL = "https://www.youtube.com/"
        const val MOBILE_URL = "https://m.youtube.com/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var webView: WebView
    private var mobileUa: String = ""
    private var desktopMode = true
    private var videoPlaying = false
    private var inPip = false
    private var wasInPip = false
    private val handler = Handler(Looper.getMainLooper())
    private val stopServiceRunnable = Runnable { PlaybackService.stop(this) }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview) as KeepAliveWebView
        PlaybackService.webViewRef = WeakReference(webView)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setupWebView()
        setupControls()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        resolveIntent(intent)
    }

    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            // Strip "; wv" so Google sign-in treats this as a normal browser
            mobileUa = userAgentString.replace("; wv", "")
            userAgentString = mobileUa
        }

        webView.setBackgroundColor(0xFF0F0F0F.toInt())
        webView.setKeepScreenOn(true)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return when (request.url.scheme?.lowercase()) {
                    "http", "https" -> false
                    "intent" -> {
                        request.url.getQueryParameter("browser_fallback_url")?.let {
                            view.loadUrl(it)
                        }
                        true
                    }
                    else -> true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectOverlay()
                CookieManager.getInstance().flush()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                recreate()
                return true
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
    }

    private fun setupControls() {
        findViewById<View>(R.id.btn_pip).setOnClickListener { enterPipMode() }
        findViewById<View>(R.id.btn_reload).setOnClickListener {
            injectOverlay()
            webView.reload()
        }
        findViewById<View>(R.id.btn_desktop).setOnClickListener { toggleDesktop() }
        findViewById<View>(R.id.btn_home).setOnClickListener {
            webView.loadUrl(if (desktopMode) DESKTOP_URL else HOME_URL)
        }
    }

    private fun toggleDesktop() {
        desktopMode = !desktopMode
        with(webView.settings) {
            if (desktopMode) {
                userAgentString = DESKTOP_UA
                useWideViewPort = true
                loadWithOverviewMode = true
                webView.loadUrl(DESKTOP_URL)
            } else {
                userAgentString = mobileUa
                useWideViewPort = false
                loadWithOverviewMode = false
                webView.loadUrl(HOME_URL)
            }
        }
        Toast.makeText(
            this,
            if (desktopMode) "Desktop mode" else "Mobile mode",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun injectOverlay() {
        val js = try {
            assets.open("overlay.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return
        }
        webView.evaluateJavascript(js, null)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= 31) {
                        setAutoEnterEnabled(videoPlaying)
                    }
                }
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(this, "PiP tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAutoPip() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(videoPlaying)
                        .build()
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveIntent(intent: Intent?) {
        var url: String? = null
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) {
                url = if (data.scheme == "vnd.youtube") {
                    "https://m.youtube.com/watch?v=${data.schemeSpecificPart}"
                } else {
                    data.toString()
                }
            }
        }
        webView.loadUrl(url ?: DESKTOP_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntent(intent)
    }

    inner class Bridge {
        @JavascriptInterface
        fun enterPip() {
            runOnUiThread { enterPipMode() }
        }

        @JavascriptInterface
        fun notifyPlaying(playing: Boolean) {
            runOnUiThread {
                videoPlaying = playing
                updateAutoPip()
                if (playing) {
                    handler.removeCallbacks(stopServiceRunnable)
                    PlaybackService.start(this@MainActivity)
                } else {
                    handler.removeCallbacks(stopServiceRunnable)
                    handler.postDelayed(stopServiceRunnable, 2000)
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in 26..30 && videoPlaying && !inPip) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        findViewById<View>(R.id.controls).visibility =
            if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        val visible = if (isInPictureInPictureMode) "false" else "true"
        webView.evaluateJavascript(
            "window.__eyp && window.__eyp.setVisible($visible)", null
        )
        if (!isInPictureInPictureMode) wasInPip = true
    }

    override fun onResume() {
        super.onResume()
        wasInPip = false
        // Keep WebView alive - don't call webView.onResume() to avoid resetting state
    }

    override fun onPause() {
        // CRITICAL: Do NOT call super.onPause() or webView.onPause()
        // This prevents WebView from pausing media playback when app goes to background
        // Only pause if explicitly requested by user (not by system)
    }

    override fun onStop() {
        // Don't call super.onStop() immediately to keep WebView rendering alive
        if (wasInPip) {
            wasInPip = false
            PlaybackService.pauseVideo()
        }
        // Call super but AFTER our logic
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PlaybackService.webViewRef = null
        webView.destroy()
        super.onDestroy()
    }
}
