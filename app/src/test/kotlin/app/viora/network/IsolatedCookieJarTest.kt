package app.viora.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedCookieJarTest {
    private val vtop = "https://vtop.vit.ac.in/vtop/".toHttpUrl()

    @Test fun `clear deletes only cookies in the injected Viora store`() {
        val vioraStore = InMemoryCookieStore()
        val unrelatedStore = InMemoryCookieStore()
        val cookie = Cookie.Builder()
            .name("SESSION")
            .value("local-test-value")
            .hostOnlyDomain("vtop.vit.ac.in")
            .path("/")
            .build()
        vioraStore.save(listOf(cookie))
        unrelatedStore.save(listOf(cookie))

        IsolatedCookieJar(vioraStore).clear()

        assertTrue(vioraStore.load().isEmpty())
        assertEquals(1, unrelatedStore.load().size)
    }

    @Test fun `does not send VTOP cookie to another host`() {
        val store = InMemoryCookieStore()
        val jar = IsolatedCookieJar(store)
        jar.saveFromResponse(
            vtop,
            listOf(
                Cookie.Builder()
                    .name("SESSION")
                    .value("local-test-value")
                    .hostOnlyDomain("vtop.vit.ac.in")
                    .path("/")
                    .build(),
            ),
        )

        assertTrue(jar.loadForRequest("https://example.com/".toHttpUrl()).isEmpty())
    }
}
