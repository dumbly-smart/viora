package app.viora.network

import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpVtopGatewayLoginTest {
    @Test fun `text captcha mode is used when page also contains dormant recaptcha element`() = runTest {
        val challenge = """
            <html><body>
            <script>var captchaType = 1;</script>
            <form action="/vtop/login" method="post">
                <input name="_csrf" value="synthetic-token">
                <input name="username">
                <input name="password" type="password">
                <input name="captchaStr">
                <img src="data:image/jpeg;base64,synthetic">
                <div id="recaptcha" class="g-recaptcha" data-sitekey="synthetic-site-key"></div>
            </form>
            </body></html>
        """.trimIndent()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { buffer -> request.body?.writeTo(buffer) }.readUtf8()
            val answer = Regex("(?:^|&)captchaStr=([^&]+)").find(body)?.groupValues?.get(1)
            val html = if (answer == "ABC234") {
                "<html><body><div id=\"MenuBlock\"></div></body></html>"
            } else {
                challenge
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(html.toResponseBody("text/html".toMediaType()))
                .build()
        }
        val gateway = HttpVtopGateway(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            cookieJar = IsolatedCookieJar(MemoryCookieStore()),
            captchaSolver = CaptchaSolver { "ABC234" },
        )

        assertEquals(SessionState.Active, gateway.login("SYNTHETIC", "not-a-real-password".toCharArray()))
    }

    @Test fun `best prediction is used for every fresh captcha before native fallback`() = runTest {
        val automaticAnswers = listOf("FIRST2", "SECOND", "THIRD3", "FOUR44")
        var solveCount = 0
        val submittedAnswers = mutableListOf<String>()
        val challenge = """
            <html><body>
            <input name="_csrf" value="synthetic-token">
            <input name="captchaStr">
            <img src="data:image/png;base64,synthetic">
            </body></html>
        """.trimIndent()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { buffer -> request.body?.writeTo(buffer) }.readUtf8()
            val html = if (request.method == "POST" && request.url.encodedPath == "/vtop/login") {
                val answer = Regex("(?:^|&)captchaStr=([^&]+)").find(body)?.groupValues?.get(1).orEmpty()
                submittedAnswers += answer
                if (answer == "ABC234") "<html><body><div id=\"MenuBlock\"></div></body></html>" else challenge
            } else if (request.url.encodedPath == "/vtop/login" || request.url.encodedPath == "/vtop/openPage") {
                challenge
            } else {
                "<html><body><input name=\"_csrf\" value=\"synthetic-token\"></body></html>"
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(html.toResponseBody("text/html".toMediaType()))
                .build()
        }
        val cookieJar = IsolatedCookieJar(MemoryCookieStore())
        val gateway = HttpVtopGateway(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            cookieJar = cookieJar,
            captchaSolver = CaptchaSolver {
                automaticAnswers[solveCount++]
            },
        )

        val automaticResult = gateway.login("SYNTHETIC", "not-a-real-password".toCharArray())

        assertTrue(automaticResult is SessionState.CaptchaRequired)
        assertEquals(automaticAnswers, submittedAnswers)
        assertEquals(
            SessionState.Active,
            gateway.submitCaptcha("SYNTHETIC", "not-a-real-password".toCharArray(), "ABC234"),
        )
    }

    @Test fun `successful login redirect is verified through authenticated content`() = runTest {
        val challenge = """
            <html><body>
            <input name="_csrf" value="login-token">
            <input name="captchaStr">
            <img src="data:image/png;base64,synthetic">
            </body></html>
        """.trimIndent()
        var contentRequests = 0
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val isLoginSubmission = request.method == "POST" && request.url.encodedPath == "/vtop/login"
            val isContentRequest = request.url.encodedPath == "/vtop/content"
            val responseRequest = if (isLoginSubmission) {
                request.newBuilder().url("https://vtop.vit.ac.in/vtop/init/page").build()
            } else {
                request
            }
            val html = when {
                isLoginSubmission -> "<html><body>Redirect landing page</body></html>"
                isContentRequest -> {
                    contentRequests += 1
                    """
                        <html><body>
                        <input name="_csrf" value="authenticated-token">
                        <input name="authorizedID" value="SYNTHETIC">
                        </body></html>
                    """.trimIndent()
                }
                request.url.encodedPath == "/vtop/login" || request.url.encodedPath == "/vtop/openPage" -> challenge
                else -> "<html><body><input name=\"_csrf\" value=\"setup-token\"></body></html>"
            }
            Response.Builder()
                .request(responseRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(html.toResponseBody("text/html".toMediaType()))
                .build()
        }
        val gateway = HttpVtopGateway(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            cookieJar = IsolatedCookieJar(MemoryCookieStore()),
            captchaSolver = CaptchaSolver { "ABC234" },
        )

        assertEquals(SessionState.Active, gateway.login("SYNTHETIC", "not-a-real-password".toCharArray()))
        assertEquals(1, contentRequests)
    }

    private class MemoryCookieStore : SessionCookieStore {
        private var cookies = emptyList<Cookie>()
        override fun load() = cookies
        override fun save(cookies: List<Cookie>) { this.cookies = cookies }
        override fun clear() { cookies = emptyList() }
    }
}
