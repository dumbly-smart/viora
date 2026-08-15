package app.viora.network

import org.junit.Assert.assertEquals
import org.junit.Test

class VioraHttpClientTest {
    @Test
    fun `VTOP certificate chain is pinned`() {
        val client = VioraHttpClient.create(InMemoryCookieStore())

        assertEquals(3, client.certificatePinner.findMatchingPins("vtop.vit.ac.in").size)
    }
}
