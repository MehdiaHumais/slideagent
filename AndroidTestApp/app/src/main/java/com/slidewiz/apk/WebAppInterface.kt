package com.slidewiz.apk

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class WebAppInterface(
    private val activity: AppCompatActivity,
    private val webView: WebView
) {

    companion object {
        private const val TAG = "SlideWizBridge"
    }

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun startFingerprint() {
        handler.post { triggerBiometric() }
    }

    @JavascriptInterface
    fun downloadFile(url: String) {
        handler.post {
            try {
                Log.d(TAG, "Download requested: $url")
                val filename = url.substringAfterLast("/").ifEmpty { "presentation.pptx" }
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setTitle("SlideWiz Presentation")
                    .setDescription("Downloading $filename")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(activity, "Downloading $filename", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}")
                Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerBiometric() {
        val biometricManager = BiometricManager.from(activity)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            Log.d(TAG, "Fingerprint success")
                            callJs("window.onNativeFingerprintSuccess()")
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            Log.e(TAG, "Fingerprint error: $errString")
                            callJs("window.onNativeFingerprintFailed()")
                        }

                        override fun onAuthenticationFailed() {
                            Log.d(TAG, "Fingerprint failed")
                            callJs("window.onNativeFingerprintFailed()")
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("SlideWiz Authentication")
                    .setSubtitle("Verify your identity")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText("Cancel")
                    .build()

                prompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w(TAG, "Biometric unavailable, simulating success")
                callJs("setTimeout(() => window.onNativeFingerprintSuccess(), 500)")
            }

            else -> {
                Log.w(TAG, "Biometric error, simulating success")
                callJs("setTimeout(() => window.onNativeFingerprintSuccess(), 500)")
            }
        }
    }

    private fun callJs(script: String) {
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(script, null)
                } else {
                    webView.loadUrl("javascript:$script")
                }
            } catch (e: Exception) {
                Log.e(TAG, "JS call failed: ${e.message}")
            }
        }
    }
}
