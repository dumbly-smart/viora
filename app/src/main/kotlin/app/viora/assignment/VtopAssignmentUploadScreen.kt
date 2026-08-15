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
import org.json.JSONObject

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
                    var openedAssignmentPage = false
                    cookies.setAcceptThirdPartyCookies(this, false)
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
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            val body = session.postBody ?: return
                            val finishedUri = Uri.parse(url)
                            if (openedAssignmentPage || session.shellUrl == null || !finishedUri.isTrustedVtop() || finishedUri.path != "/vtop/content") return
                            openedAssignmentPage = true
                            val target = JSONObject.quote(session.url)
                            val data = JSONObject.quote(body)
                            view.evaluateJavascript(
                                """
                                (function openDigitalAssignments(attempt) {
                                  if (typeof ConfirmBox !== 'function') {
                                    if (attempt < 40) setTimeout(function () { openDigitalAssignments(attempt + 1); }, 100);
                                    return;
                                  }
                                  new ConfirmBox().withParameters({
                                    submitTo: { url: $target, data: $data },
                                    updateResponseTo: 'vtop-body-content',
                                    onSuccess: function () { if (typeof unblockGUI === 'function') unblockGUI(); },
                                    onError: function () { if (typeof unblockGUI === 'function') unblockGUI(); }
                                  }).call();
                                })(0);
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                    cookies.removeAllCookies {
                        session.cookies.forEach { cookies.setCookie(session.url, it) }
                        cookies.flush()
                        post {
                            val initialUrl = session.shellUrl ?: session.url
                            if (session.postBody == null || session.shellUrl != null) loadUrl(initialUrl)
                            else postUrl(session.url, session.postBody.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            },
        )
    }
}

private fun Uri.isTrustedVtop(): Boolean = scheme == "https" && host == "vtop.vit.ac.in"
