package app.viora.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Prevents accidental transmission of authenticated requests to any non-VTOP host. */
class VtopOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.scheme != "https" || request.url.host != VTOP_HOST) {
            throw IOException("Viora blocked a request outside the configured VTOP origin")
        }
        return chain.proceed(request)
    }

    private companion object {
        const val VTOP_HOST = "vtop.vit.ac.in"
    }
}
