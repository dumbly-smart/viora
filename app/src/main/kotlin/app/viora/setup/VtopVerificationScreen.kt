package app.viora.setup

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VtopVerificationScreen(loading: Boolean, error: String?, onVerified: (String) -> Unit, onCancel: () -> Unit) {
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
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.host != "vtop.vit.ac.in"
                        override fun onPageFinished(view: WebView, url: String) {
                            val lower = url.lowercase()
                            if (url.startsWith(ROOT) && listOf("/login", "/prelogin", "/init/page").none(lower::contains)) {
                                CookieManager.getInstance().getCookie(ROOT)?.takeIf { it.contains("JSESSIONID", true) }?.let(onVerified)
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
