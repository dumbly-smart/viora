package app.viora.network

import okhttp3.OkHttpClient
import okhttp3.CertificatePinner
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
        .connectTimeout(Duration.ofSeconds(20))
        .readTimeout(Duration.ofSeconds(30))
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private const val VTOP_HOST = "vtop.vit.ac.in"
}
