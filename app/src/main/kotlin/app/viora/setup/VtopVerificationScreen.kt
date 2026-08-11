package app.viora.setup

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun VtopVerificationScreen(loading: Boolean, error: String?, onVerified: (String) -> Unit, onError: (String) -> Unit, onCancel: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onCancel, enabled = !loading) { Text("Back") }
        Text("Complete the VTOP check once, then Viora will keep its own encrypted session.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp)) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CookieManager.getInstance().apply { setAcceptCookie(true); removeAllCookies(null); flush() }
                WebView(context).apply {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !request.url.isTrustedVtopOrigin()
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            if (request.url.isTrustedVtopOrigin()) null
                            else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) onError("VTOP page failed to load (${error.errorCode})") }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); onError("VTOP certificate verification failed") }
                        override fun onPageFinished(view: WebView, url: String) {
                            val lower = url.lowercase()
                            if (url.startsWith(ROOT) && listOf("/login", "/prelogin", "/init/page").none(lower::contains)) {
                                CookieManager.getInstance().getCookie(ROOT)?.takeIf { it.contains("JSESSIONID", true) }?.let { cookies ->
                                    CookieManager.getInstance().apply { removeAllCookies(null); flush() }
                                    onVerified(cookies)
                                }
                            }
                        }
                    }
                    loadUrl(LOGIN)
                }
            },
        )
    }
}

private const val ROOT = "https://vtop.vit.ac.in/vtop"
private const val LOGIN = "$ROOT/open/page"
private fun android.net.Uri.isTrustedVtopOrigin(): Boolean = scheme == "https" && host == "vtop.vit.ac.in"
