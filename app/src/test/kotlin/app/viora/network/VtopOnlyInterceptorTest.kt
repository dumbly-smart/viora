package app.viora.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class VtopOnlyInterceptorTest {
    @Test fun `blocks authenticated client from contacting another origin`() {
        val client = OkHttpClient.Builder().addInterceptor(VtopOnlyInterceptor()).build()
        val request = Request.Builder().url("https://example.com/private").build()

        assertThrows(IOException::class.java) {
            client.newCall(request).execute()
        }
    }
}
