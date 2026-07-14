package com.slidewiz.apk

import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WebAppInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowContentAccess = true
                allowFileAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = settings.userAgentString + " SlideWizAPK/1.0"
            }
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
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
                        // Back to generator form
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
                    else -> {
                        // On form, login, or settings — close app
                        super.onBackPressed()
                    }
                }
            }
        } else {
            super.onBackPressed()
        }
    }
}
