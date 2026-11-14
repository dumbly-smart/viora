package app.viora.network

import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    @Test fun `prelogin setup response supplies captcha challenge without GET login page`() = runTest {
        val setupChallenge = """
            <html><body>
            <div id="captchaBlock"><img src="data:image/jpeg;base64,synthetic"></div>
            <input name="captchaStr">
            <input name="_csrf" value="setup-token">
            </body></html>
        """.trimIndent()
        val requests = mutableListOf<Pair<String, String>>()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            requests += request.method to request.url.encodedPath
            val html = when {
                request.method == "GET" && request.url.encodedPath == "/" ->
                    "<html><script>var csrfValue = /*root-token*/'fallback';</script></html>"
                request.method == "POST" && request.url.encodedPath == "/vtop/prelogin/setup" -> setupChallenge
                request.method == "POST" && request.url.encodedPath == "/vtop/login" ->
                    "<html><body><div id=\"MenuBlock\"></div></body></html>"
                else -> "<html><body></body></html>"
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
        assertTrue("GET /vtop/login" !in requests.map { "${it.first} ${it.second}" })
        assertTrue("POST /vtop/prelogin/setup" in requests.map { "${it.first} ${it.second}" })
    }

    @Test fun `credential failure reported by VTOP error endpoint is not treated as captcha failure`() = runTest {
        val challenge = """
            <html><body>
            <input name="_csrf" value="synthetic-token">
            <input name="captchaStr">
            <img src="data:image/png;base64,synthetic">
            </body></html>
        """.trimIndent()
        var loginSubmissions = 0
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val html = when {
                request.method == "POST" && request.url.encodedPath == "/vtop/prelogin/setup" -> challenge
                request.method == "POST" && request.url.encodedPath == "/vtop/login" -> {
                    loginSubmissions++
                    "<html><body>Login response</body></html>"
                }
                request.url.encodedPath == "/vtop/login/error" ->
                    "<html><body>Invalid LoginId/Password</body></html>"
                else -> "<html><body><input name=\"_csrf\" value=\"synthetic-token\"></body></html>"
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

        assertEquals(SessionState.Missing, gateway.login("SYNTHETIC", "not-a-real-password".toCharArray()))
        assertEquals(1, loginSubmissions)
    }

    @Test fun `incorrect credential response is not retried as a captcha failure`() = runTest {
        val challenge = """
            <html><body>
            <input name="_csrf" value="synthetic-token">
            <input name="captchaStr">
            <img src="data:image/png;base64,synthetic">
            </body></html>
        """.trimIndent()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val html = if (request.method == "POST" && request.url.encodedPath == "/vtop/login") {
                "<html><body><div class=\"alert\">Username or password is incorrect.</div></body></html>"
            } else if (request.method == "POST" && request.url.encodedPath == "/vtop/prelogin/setup") {
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
        val gateway = HttpVtopGateway(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            cookieJar = IsolatedCookieJar(MemoryCookieStore()),
            captchaSolver = CaptchaSolver { "ABC234" },
        )

        assertEquals(SessionState.Missing, gateway.login("SYNTHETIC", "not-a-real-password".toCharArray()))
    }

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
            } else if (request.method == "POST" && request.url.encodedPath == "/vtop/prelogin/setup") {
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
                request.method == "POST" && request.url.encodedPath == "/vtop/prelogin/setup" -> challenge
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

    @Test fun `CLI redirect login initializes home and credential rejection rolls back before retry`() = runTest {
        var rejected = true
        var roots = 0
        var homeRequests = 0
        val jar = IsolatedCookieJar(MemoryCookieStore())
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            var code = 200
            val html = when (request.url.encodedPath) {
                "/" -> { roots++; "<input name=_csrf value=root-token>" }
                "/vtop/prelogin/setup" -> "<input name=_csrf value=challenge-token><img src='data:image/png;base64,synthetic'>"
                "/vtop/login" -> { code = 302; "" }
                "/vtop/login/error" -> if (rejected) "Invalid LoginId/Password" else ""
                "/vtop/init/page" -> { homeRequests++; "<input name=authorizedID value=SYNTHETIC><input name=_csrf value=home-token>" }
                else -> error("Unexpected request path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                .message("Synthetic").header("Location", "/vtop/init/page")
                .body(html.toResponseBody("text/html".toMediaType())).build()
        }
        val gateway = HttpVtopGateway(OkHttpClient.Builder().addInterceptor(interceptor).build(), jar, CaptchaSolver { "ABC234" })
        assertEquals(SessionState.Missing, gateway.login("SYNTHETIC", "wrong".toCharArray()))
        assertTrue(jar.loadForRequest("https://vtop.vit.ac.in/".toHttpUrl()).isEmpty())
        rejected = false
        assertEquals(SessionState.Active, gateway.login("SYNTHETIC", "correct".toCharArray()))
        assertEquals(2, roots)
        assertEquals(1, homeRequests)
    }

    @Test fun `solver failure offers a fresh manual challenge instead of failing connection`() = runTest {
        var roots = 0
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val html = if (request.url.encodedPath == "/") {
                roots++; "<input name=_csrf value=root-token>"
            } else "<input name=_csrf value=challenge-token><img src='data:image/png;base64,synthetic'>"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html.toResponseBody("text/html".toMediaType())).build()
        }
        val gateway = HttpVtopGateway(OkHttpClient.Builder().addInterceptor(interceptor).build(),
            IsolatedCookieJar(MemoryCookieStore()), CaptchaSolver { throw java.io.IOException("Synthetic decoder failure") })
        assertTrue(gateway.login("SYNTHETIC", "synthetic".toCharArray()) is SessionState.CaptchaRequired)
        assertEquals(2, roots)
    }

    private class MemoryCookieStore : SessionCookieStore {
        private var cookies = emptyList<Cookie>()
        override fun load() = cookies
        override fun save(cookies: List<Cookie>) { this.cookies = cookies }
        override fun clear() { cookies = emptyList() }
    }
}
