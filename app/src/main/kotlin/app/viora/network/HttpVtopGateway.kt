package app.viora.network

import app.viora.parser.ParseResult
import app.viora.parser.SemesterParser
import app.viora.parser.TimetableParser
import app.viora.parser.VtopDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

class HttpVtopGateway(
    private val client: OkHttpClient,
    private val cookieJar: IsolatedCookieJar,
    private val timetableParser: TimetableParser = TimetableParser(),
    private val semesterParser: SemesterParser = SemesterParser(),
) : VtopGateway {
    @Volatile private var authorizedId: String? = null
    @Volatile private var csrf: String? = null

    override suspend fun sessionState(): SessionState = withContext(Dispatchers.IO) {
        val html = get(INIT_PAGE)
        updateTokens(html)
        if (isAuthenticated(html)) SessionState.Active else SessionState.Missing
    }

    override suspend fun login(username: String, password: CharArray): SessionState = withContext(Dispatchers.IO) {
        authorizedId = username.trim()
        val setup = get(SETUP_PAGE)
        updateTokens(setup)
        if (VtopDocument.requiresVerification(Jsoup.parse(setup))) {
            return@withContext SessionState.VerificationRequired
        }
        val token = csrf ?: return@withContext SessionState.Missing
        val body = FormBody.Builder()
            .add("_csrf", token)
            .add("username", username.trim())
            .add("password", String(password))
            .add("captchaStr", "")
            .build()
        val html = execute(Request.Builder().url(LOGIN).post(body).build())
        updateTokens(html)
        when {
            VtopDocument.requiresVerification(Jsoup.parse(html)) -> SessionState.VerificationRequired
            isAuthenticated(html) -> SessionState.Active
            else -> SessionState.Missing
        }
    }

    override suspend fun semesters(): List<SemesterOption> = withContext(Dispatchers.IO) {
        val html = get(TIMETABLE_PAGE)
        updateTokens(html)
        when (val result = semesterParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun timetable(semesterId: String): TimetableSnapshot = withContext(Dispatchers.IO) {
        val landing = get(TIMETABLE_PAGE)
        updateTokens(landing)
        if (!isAuthenticated(landing)) throw AuthenticationException()
        val token = csrf ?: throw IOException("VTOP did not provide a CSRF token")
        val id = authorizedId ?: extractAuthorizedId(landing)
            ?: throw IOException("VTOP did not provide an authorized student ID")
        val body = FormBody.Builder()
            .add("_csrf", token)
            .add("authorizedID", id)
            .add("semesterSubId", semesterId)
            .add("x", System.currentTimeMillis().toString())
            .build()
        val html = execute(Request.Builder().url(TIMETABLE_PROCESS).post(body).build())
        when (val result = timetableParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun clearLocalSession() {
        cookieJar.clear()
        csrf = null
        authorizedId = null
    }

    private fun get(url: String): String = execute(Request.Builder().url(url).get().build())

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("VTOP returned HTTP ${response.code}")
        response.body.string()
    }

    private fun updateTokens(html: String) {
        val document = Jsoup.parse(html)
        document.selectFirst("input[name=_csrf]")?.attr("value")?.takeIf(String::isNotBlank)?.let { csrf = it }
        Regex("var\\s+csrfValue\\s*=\\s*[\"']([^\"']+)").find(html)?.groupValues?.get(1)?.let { csrf = it }
        extractAuthorizedId(html)?.let { authorizedId = it }
    }

    private fun extractAuthorizedId(html: String): String? = Jsoup.parse(html)
        .selectFirst("input[name=authorizedID]")?.attr("value")?.takeIf(String::isNotBlank)

    private fun isAuthenticated(html: String): Boolean {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return false
        return document.selectFirst("a[href*=logout], #MenuBlock, #DataBlock, input[name=authorizedID]") != null ||
            html.contains("Student Profile", ignoreCase = true)
    }

    companion object {
        private const val BASE = "https://vtop.vit.ac.in/vtop"
        private const val INIT_PAGE = "$BASE/init/page"
        private const val SETUP_PAGE = "$BASE/prelogin/setup"
        private const val LOGIN = "$BASE/login"
        private const val TIMETABLE_PAGE = "$BASE/academics/common/StudentTimeTable"
        private const val TIMETABLE_PROCESS = "$BASE/processViewTimeTable"
    }
}

class AuthenticationException : IOException("VTOP authentication is required")
