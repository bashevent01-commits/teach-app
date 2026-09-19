package com.knowapp.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat

/**
 * Shows the real K.N.O.W. web app in a WebView, so the Android app is
 * visually identical to the site by construction rather than a hand-matched
 * native recreation. External links (e.g. the APK download button) and
 * anything off this app's own domains open in the system browser instead of
 * inside the WebView.
 */
class MainActivity : ComponentActivity() {
    // The frontend (Cloudflare Worker) and backend (Render) hosts — page
    // navigation and API calls between these stay inside the WebView;
    // anything else (e.g. the GitHub APK download link) opens externally.
    private val appHosts = setOf("teach.bash-ke.workers.dev", "teach-backend-roza.onrender.com")

    private lateinit var webView: WebView
    private var filePickerCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = filePickerCallback
        filePickerCallback = null
        if (callback == null) return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }
        val uris = mutableListOf<Uri>()
        result.data?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } ?: result.data?.data?.let { uris.add(it) }
        callback.onReceiveValue(uris.toTypedArray())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val root = FrameLayout(this)
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER)
        }
        root.addView(webView)
        root.addView(progressBar)
        setContentView(root)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host
                if (host != null && appHosts.contains(host)) return false // load inside the WebView
                startActivity(Intent(Intent.ACTION_VIEW, request.url)) // anything else (e.g. APK download) opens externally
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progressBar.visibility = android.view.View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Backs <input type="file"> on Stock/Posts/Institutions/Settings pages
            // (item photos, post photos, institution/portal icons).
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                filePickerCallback?.onReceiveValue(null)
                filePickerCallback = filePathCallback
                val intent = fileChooserParams.createIntent().apply {
                    if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                return try {
                    filePickerLauncher.launch(intent)
                    true
                } catch (e: android.content.ActivityNotFoundException) {
                    filePickerCallback = null
                    false
                }
            }
        }

        // Downloads (e.g. tapping the "Download Android app" button while
        // already inside the app) go to the system Download Manager /
        // browser rather than trying to render binary content in the WebView.
        webView.setDownloadListener { url, _, _, _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl("https://teach.bash-ke.workers.dev/")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
