package app.viora.network

import app.viora.parser.ParseResult
import app.viora.parser.AttendanceParser
import app.viora.parser.DigitalAssignmentParser
import app.viora.parser.ExamParser
import app.viora.parser.MarksParser
import app.viora.parser.GradesParser
import app.viora.parser.CgpaParser
import app.viora.parser.AcademicCalendarParser
import app.viora.parser.ClassMessageParser
import app.viora.parser.CourseMaterialParser
import app.viora.parser.SemesterParser
import app.viora.parser.TimetableParser
import app.viora.parser.VtopDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException

class HttpVtopGateway(
    private val client: OkHttpClient,
    private val cookieJar: IsolatedCookieJar,
    private val timetableParser: TimetableParser = TimetableParser(),
    private val semesterParser: SemesterParser = SemesterParser(),
    private val attendanceParser: AttendanceParser = AttendanceParser(),
    private val assignmentParser: DigitalAssignmentParser = DigitalAssignmentParser(),
    private val examParser: ExamParser = ExamParser(),
    private val marksParser: MarksParser = MarksParser(),
    private val gradesParser: GradesParser = GradesParser(),
    private val cgpaParser: CgpaParser = CgpaParser(),
    private val calendarParser: AcademicCalendarParser = AcademicCalendarParser(),
    private val messageParser: ClassMessageParser = ClassMessageParser(),
    private val materialParser: CourseMaterialParser = CourseMaterialParser(),
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

    override suspend fun attendance(semesterId: String): AttendanceSnapshot = withContext(Dispatchers.IO) {
        val landing = get(ATTENDANCE_PAGE)
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
        val html = execute(Request.Builder().url(ATTENDANCE_PROCESS).post(body).build())
        when (val result = attendanceParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun digitalAssignments(): List<DigitalAssignmentRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(DA_PAGE)
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val body = FormBody.Builder()
            .add("_csrf", token)
            .add("authorizedID", id)
            .add("x", System.currentTimeMillis().toString())
            .build()
        val html = execute(Request.Builder().url(DA_PROCESS).post(body).build())
        when (val result = assignmentParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun exams(semesterId: String): List<ExamRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(EXAM_PAGE)
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val body = FormBody.Builder()
            .add("_csrf", token)
            .add("authorizedID", id)
            .add("semesterSubId", semesterId)
            .add("x", System.currentTimeMillis().toString())
            .build()
        val html = execute(Request.Builder().url(EXAM_PROCESS).post(body).build())
        when (val result = examParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun marks(semesterId: String): List<MarkRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(MARKS_PAGE)
        val html = academicPost(MARKS_PROCESS, token, semesterId)
        marksParser.parse(html).valueOrThrow()
    }

    override suspend fun grades(semesterId: String): GradeSnapshot = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(GRADES_PAGE)
        val html = academicPost(GRADES_PROCESS, token, semesterId)
        gradesParser.parse(html).valueOrThrow()
    }

    override suspend fun cgpa(): CgpaSnapshot = withContext(Dispatchers.IO) {
        val html = get(GRADES_PAGE)
        updateTokens(html)
        cgpaParser.parse(html).valueOrThrow()
    }

    override suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(CALENDAR_PAGE)
        calendarParser.parse(academicPost(CALENDAR_PROCESS, token, semesterId)).valueOrThrow()
    }

    override suspend fun classMessages(): List<ClassMessageRecord> = withContext(Dispatchers.IO) {
        val html = get(MESSAGES_PAGE); updateTokens(html); messageParser.parse(html).valueOrThrow()
    }

    override suspend fun courseMaterials(semesterId: String, courseCode: String, faculty: String): List<CourseMaterialRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(COURSE_PAGE)
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        fun lookup(url: String, fields: Map<String, String>): String {
            val builder = FormBody.Builder().add("_csrf", token).add("authorizedID", id).add("semesterSubId", semesterId).add("x", System.currentTimeMillis().toString())
            fields.forEach { (key, value) -> builder.add(key, value) }
            return execute(Request.Builder().url(url).post(builder.build()).build())
        }
        val courseHtml = lookup(COURSE_LIST, emptyMap())
        val course = Jsoup.parse(courseHtml).select("option").firstOrNull { it.text().contains(courseCode, true) }
        val classId = course?.attr("value")?.takeIf(String::isNotBlank).orEmpty()
        val facultyHtml = lookup(FACULTY_LIST, mapOf("courseCode" to courseCode, "classId" to classId))
        val facultyId = Jsoup.parse(facultyHtml).select("option").firstOrNull { it.text().contains(faculty, true) }?.attr("value")?.takeIf(String::isNotBlank) ?: faculty
        val body = FormBody.Builder().add("_csrf", token).add("authorizedID", id).add("semesterSubId", semesterId).add("courseCode", courseCode).add("classId", classId).add("facultyId", facultyId).add("x", System.currentTimeMillis().toString()).build()
        materialParser.parse(execute(Request.Builder().url(COURSE_DETAIL).post(body).build()), courseCode).valueOrThrow()
    }

    override suspend fun importInteractiveSession(cookieHeader: String): SessionState = withContext(Dispatchers.IO) {
        val cookies = cookieHeader.split(';').mapNotNull { part ->
            val pieces = part.trim().split('=', limit = 2)
            if (pieces.size != 2 || pieces[0].isBlank()) null else Cookie.Builder().name(pieces[0]).value(pieces[1]).domain("vtop.vit.ac.in").path("/vtop").secure().build()
        }
        if (cookies.isEmpty()) return@withContext SessionState.Missing
        cookieJar.replace(cookies)
        sessionState()
    }

    override suspend fun downloadCourseMaterial(downloadPath: String): ByteArray = withContext(Dispatchers.IO) {
        val candidate = Regex("['\"]([^'\"]*(?:download|Material)[^'\"]*)['\"]", RegexOption.IGNORE_CASE).find(downloadPath)?.groupValues?.get(1) ?: downloadPath
        val url = BASE.toHttpUrl().resolve(candidate) ?: throw IOException("VTOP provided an invalid material link")
        if (url.host != "vtop.vit.ac.in") throw IOException("Blocked a non-VTOP material link")
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("VTOP returned HTTP ${response.code}")
            response.body.bytes().also { require(it.size <= MAX_MATERIAL_BYTES) { "Course material is too large" } }
        }
    }

    override suspend fun clearLocalSession() {
        cookieJar.clear()
        csrf = null
        authorizedId = null
    }

    private fun get(url: String): String = execute(Request.Builder().url(url).get().build())

    private fun academicPost(url: String, token: String, semesterId: String): String {
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val body = FormBody.Builder().add("_csrf", token).add("authorizedID", id).add("semesterSubId", semesterId).add("x", System.currentTimeMillis().toString()).build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private fun <T> ParseResult<T>.valueOrThrow(): T = when (this) {
        is ParseResult.Success -> value
        ParseResult.AuthenticationRequired -> throw AuthenticationException()
        is ParseResult.InvalidDocument -> throw IOException(reason)
    }

    private fun ensureAuthenticatedPage(url: String): String {
        val html = get(url)
        updateTokens(html)
        if (!isAuthenticated(html)) throw AuthenticationException()
        return csrf ?: throw IOException("VTOP did not provide a CSRF token")
    }

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
        private const val MAX_MATERIAL_BYTES = 50 * 1024 * 1024
        private const val INIT_PAGE = "$BASE/init/page"
        private const val SETUP_PAGE = "$BASE/prelogin/setup"
        private const val LOGIN = "$BASE/login"
        private const val TIMETABLE_PAGE = "$BASE/academics/common/StudentTimeTable"
        private const val TIMETABLE_PROCESS = "$BASE/processViewTimeTable"
        private const val ATTENDANCE_PAGE = "$BASE/academics/common/StudentAttendance"
        private const val ATTENDANCE_PROCESS = "$BASE/processViewStudentAttendance"
        private const val DA_PAGE = "$BASE/examinations/doDigitalAssignment"
        private const val DA_PROCESS = "$BASE/examinations/processDigitalAssignment"
        private const val EXAM_PAGE = "$BASE/examinations/StudentExamSchedule"
        private const val EXAM_PROCESS = "$BASE/examinations/doSearchExamScheduleForStudent"
        private const val MARKS_PAGE = "$BASE/examinations/StudentMarkView"
        private const val MARKS_PROCESS = "$BASE/examinations/doStudentMarkView"
        private const val GRADES_PAGE = "$BASE/examinations/examGradeView/StudentGradeHistory"
        private const val GRADES_PROCESS = "$BASE/examinations/examGradeView/doStudentGradeView"
        private const val CALENDAR_PAGE = "$BASE/academics/common/CalendarPreview"
        private const val CALENDAR_PROCESS = "$BASE/processViewCalendar"
        private const val MESSAGES_PAGE = "$BASE/academics/common/StudentClassMessage"
        private const val COURSE_PAGE = "$BASE/academics/common/StudentCoursePage"
        private const val COURSE_DETAIL = "$BASE/processViewStudentCourseDetail"
        private const val COURSE_LIST = "$BASE/getCourseForCoursePage"
        private const val FACULTY_LIST = "$BASE/getFacultyForCoursePage"
    }
}

class AuthenticationException : IOException("VTOP authentication is required")
