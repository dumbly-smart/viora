package app.viora.network

import okhttp3.OkHttpClient
import java.time.Duration

object VioraHttpClient {
    fun create(cookieStore: SessionCookieStore): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(IsolatedCookieJar(cookieStore))
        .addInterceptor(VtopOnlyInterceptor())
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
}
