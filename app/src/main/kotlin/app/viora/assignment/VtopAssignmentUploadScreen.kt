package app.viora.assignment

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.viora.network.VtopWebSession
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
fun VtopAssignmentUploadScreen(session: VtopWebSession, close: (String?) -> Unit) {
    var chooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        chooser?.onReceiveValue(uri?.let { arrayOf(it) })
        chooser = null
    }
    val cookies = CookieManager.getInstance()
    fun finish() {
        val header = cookies.getCookie(session.url)
        cookies.removeAllCookies(null)
        cookies.flush()
        close(header)
    }
    BackHandler(onBack = ::finish)
    DisposableEffect(Unit) {
        onDispose {
            chooser?.onReceiveValue(null)
            cookies.removeAllCookies(null)
            cookies.flush()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = ::finish, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Back to Viora") }
        Text("Upload goes straight to VTOP. Check the final VTOP status before leaving.", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                cookies.setAcceptCookie(true)
                WebView(context).apply {
                    cookies.setAcceptThirdPartyCookies(this, false)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                            chooser?.onReceiveValue(null)
                            chooser = callback
                            val types = params.acceptTypes.filter { it.isNotBlank() }.toTypedArray().ifEmpty { arrayOf("application/pdf") }
                            picker.launch(types)
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !request.url.isTrustedVtop()
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            if (request.url.isTrustedVtop()) null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
                    }
                    cookies.removeAllCookies {
                        session.cookies.forEach { cookies.setCookie(session.url, it) }
                        cookies.flush()
                        post { loadUrl(session.url) }
                    }
                }
            },
        )
    }
}

private fun Uri.isTrustedVtop(): Boolean = scheme == "https" && host == "vtop.vit.ac.in"
