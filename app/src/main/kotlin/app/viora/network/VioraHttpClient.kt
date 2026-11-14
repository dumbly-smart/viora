package app.viora.network

import okhttp3.OkHttpClient
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Duration

object VioraHttpClient {
    fun create(cookieStore: SessionCookieStore): OkHttpClient = create(IsolatedCookieJar(cookieStore))

    fun create(cookieJar: IsolatedCookieJar): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(
            CertificatePinner.Builder()
                // Pin VTOP's current certificate chain at multiple levels so a routine
                // leaf-certificate renewal does not lock existing installs out.
                .add(VTOP_HOST, "sha256/PqF0uOmuFtOZcGp9pKVa74qiNJv87Kf62NhZuhKfd/E=")
                .add(VTOP_HOST, "sha256/4a6cPehI7OG6cuDZka5NDZ7FR8a60d3auda+sKfg4Ng=")
                .add(VTOP_HOST, "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td4=")
                .build(),
        )
        .cookieJar(cookieJar)
        .addInterceptor(VtopOnlyInterceptor())
        .addInterceptor(VtopBrowserHeadersInterceptor())
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private const val VTOP_HOST = "vtop.vit.ac.in"
}

private class VtopBrowserHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "max-age=0")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .header("User-Agent", USER_AGENT)
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
