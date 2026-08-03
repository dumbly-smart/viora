package app.viora.network

import okhttp3.OkHttpClient
import java.time.Duration

object VioraHttpClient {
    fun create(cookieStore: SessionCookieStore): OkHttpClient = create(IsolatedCookieJar(cookieStore))

    fun create(cookieJar: IsolatedCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(VtopOnlyInterceptor())
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
}
