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
                .add(VTOP_HOST, "sha256/wt21+lo3oTOb4CjIA1JwAA5vQ61SK+6em8bK+gybGzw=")
                .add(VTOP_HOST, "sha256/a9khLOZJxlnJyrxstg/P+seiDCm+Yf3OsrXyFocBaI0=")
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
        val original = chain.request()
        val path = original.url.encodedPath
        val requestBuilder = original.newBuilder()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "max-age=0")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", if (path == "/") "none" else "same-origin")
            .header("Sec-Fetch-User", "?1")
            .header("Sec-Ch-Ua", "\"Chromium\";v=\"120\", \"Not_A Brand\";v=\"8\"")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Linux\"")
            .header("Priority", "u=0, i")
            .header("Origin", VTOP_ORIGIN)
            .header("Upgrade-Insecure-Requests", "1")
            .header("User-Agent", USER_AGENT)
        when {
            path == "/" -> requestBuilder.removeHeader("Referer")
            path == "/vtop/prelogin/setup" -> requestBuilder.header("Referer", "$VTOP_ORIGIN/vtop/open/page")
            path == "/vtop/login" || path == "/vtop/login/error" -> requestBuilder.header("Referer", "$VTOP_ORIGIN/vtop/login")
            else -> requestBuilder.header("Referer", "$VTOP_ORIGIN/vtop/content")
        }
        val request = requestBuilder.build()
        return chain.proceed(request)
    }

    private companion object {
        const val VTOP_ORIGIN = "https://vtop.vit.ac.in"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
