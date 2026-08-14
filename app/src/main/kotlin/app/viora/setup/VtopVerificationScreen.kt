package app.viora.setup

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.viora.ui.VioraBlue
import app.viora.ui.VioraCoral
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun VtopVerificationScreen(loading: Boolean, error: String?, onVerified: (String) -> Unit, onError: (String) -> Unit, onCancel: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().apply { removeAllCookies(null); flush() } }
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onCancel, enabled = !loading) { Text("← Back") }
            Column(Modifier.weight(1f)) {
                Text("One quick VTOP check", style = MaterialTheme.typography.titleLarge)
                Text("Finish it here, then you’re back in Viora.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = VioraBlue.copy(alpha = 0.12f)) {
                Icon(Icons.Outlined.Lock, null, Modifier.padding(9.dp).size(18.dp), tint = VioraBlue)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VioraBlue)
        error?.let {
            Surface(
                color = VioraCoral.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, VioraCoral.copy(alpha = 0.28f)),
            ) { Text(it, color = VioraCoral, modifier = Modifier.fillMaxWidth().padding(12.dp)) }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
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
                                if (request.url.isTrustedVtopOrigin()) null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
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
}

private const val ROOT = "https://vtop.vit.ac.in/vtop"
private const val LOGIN = "$ROOT/open/page"
private fun android.net.Uri.isTrustedVtopOrigin(): Boolean = scheme == "https" && host == "vtop.vit.ac.in"
