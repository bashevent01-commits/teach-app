package com.knowapp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Settings-page updater: checks the rolling GitHub release, downloads the APK and hands it to the system installer
class UpdateBridge(private val activity: ComponentActivity, private val webView: WebView) {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    // Held so the print WebView is not garbage collected before the dialog finishes
    private var pendingPrintView: WebView? = null

    @JavascriptInterface
    fun info(): String = JSONObject()
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("sha", BuildConfig.GIT_SHA)
        .toString()

    // Statements print (or save as PDF) through Android's own print dialog, since WebView cannot save blob downloads
    @JavascriptInterface
    fun printHtml(html: String, title: String) {
        activity.runOnUiThread {
            val printView = WebView(activity)
            printView.webViewClient = object : WebViewClient() {
                private var started = false

                override fun onPageFinished(view: WebView, url: String?) {
                    if (started) return
                    started = true
                    val name = title.ifBlank { "Statement" }
                    val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    manager.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
                }
            }
            printView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            pendingPrintView = printView
        }
    }

    @JavascriptInterface
    fun checkForUpdate() {
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                emit("checking")
                val latest = fetchLatestSha()
                if (latest != null && latest == BuildConfig.GIT_SHA) emit("uptodate") else emit("available")
            } catch (e: Exception) {
                emit("error", e.message ?: "Could not reach the update server.")
            } finally {
                busy.set(false)
            }
        }
    }

    @JavascriptInterface
    fun installUpdate() {
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    emit("need_permission")
                    activity.runOnUiThread {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                        activity.startActivity(intent)
                    }
                    return@execute
                }
                val apk = download()
                emit("installing")
                activity.runOnUiThread { launchInstaller(apk) }
            } catch (e: Exception) {
                emit("error", e.message ?: "Update download failed.")
            } finally {
                busy.set(false)
            }
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "know-android-updater")
        return conn
    }

    private fun fetchLatestSha(): String? {
        val conn = open(RELEASE_API)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode != 200) throw RuntimeException("Update server returned ${conn.responseCode}.")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val text = JSONObject(body).optString("body")
        return Regex("commit ([0-9a-f]{40})").find(text)?.groupValues?.get(1)
    }

    private fun download(): File {
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "know-update.apk")
        val conn = open(APK_URL)
        if (conn.responseCode != 200) throw RuntimeException("Download failed (${conn.responseCode}).")
        val total = conn.contentLengthLong
        var done = 0L
        var lastPct = -1
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct && pct % 5 == 0) {
                            lastPct = pct
                            emit("downloading", pct.toString())
                        }
                    }
                }
            }
        }
        if (target.length() < 100_000L || (total > 0 && target.length() != total)) {
            target.delete()
            throw RuntimeException("Downloaded file was incomplete. Try again.")
        }
        return target
    }

    private fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun emit(state: String, detail: String = "") {
        val payload = JSONObject().put("state", state).put("detail", detail).toString()
        webView.post { webView.evaluateJavascript("window.onKnowUpdate && window.onKnowUpdate($payload)", null) }
    }

    companion object {
        private const val RELEASE_API = "https://api.github.com/repos/bashevent01-commits/teach-app/releases/tags/android-latest"
        private const val APK_URL = "https://github.com/bashevent01-commits/teach-app/releases/download/android-latest/app-debug.apk"
    }
}
