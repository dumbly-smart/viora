package app.viora.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class VioraHttpClientTest {
    @Test
    fun `VTOP certificate chain is pinned`() {
        val client = VioraHttpClient.create(InMemoryCookieStore())

        assertEquals(3, client.certificatePinner.findMatchingPins("vtop.vit.ac.in").size)
    }

    @Test
    fun `VTOP requests carry the browser profile required by the login page`() {
        val requiredHeaders = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Cache-Control" to "max-age=0",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        )
        val vtopBoundary = Interceptor { chain ->
            val accepted = requiredHeaders.all { (name, value) -> chain.request().header(name) == value }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (accepted) 200 else 403)
                .message(if (accepted) "OK" else "Forbidden")
                .body("synthetic".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val client = VioraHttpClient.create(InMemoryCookieStore())
            .newBuilder()
            .addInterceptor(vtopBoundary)
            .build()

        client.newCall(Request.Builder().url("https://vtop.vit.ac.in/vtop/openPage").build())
            .execute()
            .use { response -> assertEquals(200, response.code) }
    }
}
