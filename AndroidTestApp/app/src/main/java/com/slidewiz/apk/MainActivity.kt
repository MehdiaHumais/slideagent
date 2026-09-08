package com.slidewiz.apk

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WebAppInterface
    private val TAG = "SlideWiz"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowContentAccess = true
                allowFileAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = settings.userAgentString + " SlideWizAPK/1.0"
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "Page loaded: $url")
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.e(TAG, "Error ($errorCode): $description - $failingUrl")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS [${msg?.messageLevel()}]: ${msg?.message()}")
                    return true
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                Log.d(TAG, "Download requested: $url ($mimeType)")
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setMimeType(mimeType ?: "application/octet-stream")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setTitle("SlideWiz Presentation")
                        .setDescription("Downloading presentation...")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                    // Let DownloadManager figure out the filename from Content-Disposition
                    val filename = contentDisposition?.let { cd ->
                        Regex("filename\\*?=(?:UTF-8'')?([^;\\s]+)")
                            .find(cd)?.groupValues?.get(1)?.replace("\"", "")
                    } ?: "presentation.pptx"
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                    Toast.makeText(this@MainActivity, "Downloading $filename", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${e.message}")
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }

        bridge = WebAppInterface(this, webView)
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        webView.loadUrl("https://slides.britsyncai.com")

        setContentView(webView)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(
                "(function() { " +
                "  var o = document.getElementById('loading-state'); " +
                "  var s = document.getElementById('success-state'); " +
                "  var e = document.getElementById('error-state'); " +
                "  if (o && !o.classList.contains('hidden')) return 'loading'; " +
                "  if (s && !s.classList.contains('hidden')) return 'success'; " +
                "  if (e && !e.classList.contains('hidden')) return 'error'; " +
                "  return 'form'; " +
                "})()"
            ) { result ->
                val state = result?.replace("\"", "") ?: "form"
                when (state) {
                    "loading", "success", "error" -> {
                        webView.evaluateJavascript(
                            "(function() { " +
                            "  var f = document.getElementById('generator-form'); " +
                            "  var o = document.getElementById('loading-state'); " +
                            "  var s = document.getElementById('success-state'); " +
                            "  var e = document.getElementById('error-state'); " +
                            "  if (f) f.classList.remove('hidden'); " +
                            "  if (o) o.classList.add('hidden'); " +
                            "  if (s) s.classList.add('hidden'); " +
                            "  if (e) e.classList.add('hidden'); " +
                            "})()",
                            null
                        )
                    }
                    else -> super.onBackPressed()
                }
            }
        } else {
            super.onBackPressed()
        }
    }
}
