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
import app.viora.domain.sameCourseCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException

class HttpVtopGateway(
    private val client: OkHttpClient,
    private val cookieJar: IsolatedCookieJar,
    private val captchaSolver: CaptchaSolver,
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
    @Volatile private var pendingLoginChallenge: LoginChallenge? = null

    override suspend fun sessionState(): SessionState = withContext(Dispatchers.IO) {
        val html = get(INIT_PAGE)
        updateTokens(html)
        if (isAuthenticated(html)) SessionState.Active else SessionState.Missing
    }

    override suspend fun login(username: String, password: CharArray): SessionState = withContext(Dispatchers.IO) {
        authorizedId = username.trim()
        pendingLoginChallenge = null
        repeat(MAX_CAPTCHA_ATTEMPTS) {
            val challenge = prepareLoginChallenge() ?: return@withContext SessionState.VerificationRequired
            val answer = captchaSolver.solve(challenge.imageDataUri)
            when (submitLoginChallenge(challenge, username, password, answer)) {
                LoginAttempt.ACTIVE -> return@withContext SessionState.Active
                LoginAttempt.INVALID_CREDENTIALS -> return@withContext SessionState.Missing
                LoginAttempt.VERIFICATION_REQUIRED -> return@withContext SessionState.VerificationRequired
                LoginAttempt.RETRY_CAPTCHA -> Unit
            }
        }
        manualCaptchaState()
    }

    override suspend fun submitCaptcha(username: String, password: CharArray, answer: String): SessionState = withContext(Dispatchers.IO) {
        val normalizedAnswer = answer.trim().uppercase().filter(Char::isLetterOrDigit)
        val challenge = pendingLoginChallenge
        if (challenge == null || normalizedAnswer.length != CAPTCHA_LENGTH) return@withContext manualCaptchaState()
        pendingLoginChallenge = null
        when (submitLoginChallenge(challenge, username, password, normalizedAnswer)) {
            LoginAttempt.ACTIVE -> SessionState.Active
            LoginAttempt.INVALID_CREDENTIALS -> SessionState.Missing
            LoginAttempt.VERIFICATION_REQUIRED -> SessionState.VerificationRequired
            LoginAttempt.RETRY_CAPTCHA -> manualCaptchaState()
        }
    }

    override suspend fun semesters(): List<SemesterOption> = withContext(Dispatchers.IO) {
        val html = menuPage(TIMETABLE_PAGE)
        when (val result = semesterParser.parse(html)) {
            is ParseResult.Success -> result.value
            ParseResult.AuthenticationRequired -> throw AuthenticationException()
            is ParseResult.InvalidDocument -> throw IOException(result.reason)
        }
    }

    override suspend fun timetable(semesterId: String): TimetableSnapshot = withContext(Dispatchers.IO) {
        val landing = menuPage(TIMETABLE_PAGE)
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
        val landing = menuPage(ATTENDANCE_PAGE)
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

    override suspend fun digitalAssignments(semesterId: String): List<DigitalAssignmentRecord> = withContext(Dispatchers.IO) {
        val token = currentToken()
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val subjectsHtml = execute(Request.Builder().url(DA_PAGE).post(academicBody(token, id, semesterId)).build())
        val subjects = assignmentParser.parseSubjects(subjectsHtml).valueOrThrow()
        val attempts = subjects.map { subject ->
            runCatching {
                val body = FormBody.Builder()
                    .add("_csrf", token)
                    .add("authorizedID", id)
                    .add("classId", subject.classId)
                    .add("x", System.currentTimeMillis().toString())
                    .build()
                assignmentParser.parseDetails(
                    execute(Request.Builder().url(DA_PROCESS).post(body).build()),
                    subject,
                ).valueOrThrow()
            }
        }
        val records = attempts.mapNotNull { it.getOrNull() }.flatten().distinctBy(DigitalAssignmentRecord::id)
        if (subjects.isNotEmpty() && attempts.none { it.isSuccess }) throw attempts.first().exceptionOrNull() ?: IOException("DA details could not be fetched")
        records
    }

    override suspend fun uploadDigitalAssignment(
        semesterId: String,
        assignmentId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        require(bytes.size <= 10 * 1024 * 1024) { "The selected assessment file is too large" }
        val locator = requireAssignmentUploadLocator(digitalAssignments(semesterId), assignmentId)
        require(locator.accepts(mimeType, fileName)) { "VTOP does not accept this file type for the assessment" }
        locator.maxBytes?.let { maximum -> require(bytes.size.toLong() <= maximum) { "The selected assessment file is too large" } }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            locator.fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart(locator.fileField, fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
        }.build()
        val html = execute(Request.Builder().url(resolveAssignmentUploadAction(locator.requestPath)).post(body).build())
        if (VtopDocument.isAuthenticationPage(Jsoup.parse(html))) throw AuthenticationException()
    }

    override suspend fun exams(semesterId: String): List<ExamRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(EXAM_PAGE)
        val html = academicPost(EXAM_PROCESS, token, semesterId)
        examParser.parse(html).valueOrThrow().sortedBy(ExamRecord::startsAt)
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
        cgpaParser.parse(menuPage(GRADES_PAGE)).valueOrThrow()
    }

    override suspend fun academicCalendar(semesterId: String): List<AcademicCalendarRecord> = withContext(Dispatchers.IO) {
        val token = ensureAuthenticatedPage(CALENDAR_PAGE)
        calendarParser.parse(academicPost(CALENDAR_PROCESS, token, semesterId)).valueOrThrow()
    }

    override suspend fun classMessages(): List<ClassMessageRecord> = withContext(Dispatchers.IO) {
        messageParser.parse(menuPage(MESSAGES_PAGE)).valueOrThrow()
    }

    override suspend fun courseMaterials(semesterId: String, courseCode: String, courseTitle: String, faculty: String): List<CourseMaterialRecord> = withContext(Dispatchers.IO) {
        val token = currentToken()
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val pageBody = FormBody.Builder()
            .add("_csrf", token).add("authorizedID", id).add("verifyMenu", "true")
            .add("x", System.currentTimeMillis().toString()).build()
        val courseHtml = execute(Request.Builder().url(COURSE_PAGE_CONSOLIDATED).post(pageBody).build())
        val courses = Jsoup.parse(courseHtml).select("select#courseId option")
            .filter { option ->
                option.text().contains(courseCode, true) ||
                    sameCourseCode(option.text(), courseCode) ||
                    courseTitle.courseTitleKey().let { title -> title.isNotBlank() && option.text().courseTitleKey().contains(title) }
            }
            .filter { it.attr("value").isNotBlank() }
            .distinctBy { "${it.attr("value")}|${it.text()}" }
        if (courses.isEmpty()) throw IOException("VTOP did not return $courseCode on the consolidated course page")
        courses.flatMap { course ->
            val parts = course.text().split(" - ").map(String::trim)
            val courseType = parts.getOrNull(parts.size - 3).orEmpty()
            val detailBody = FormBody.Builder()
                .add("_csrf", token).add("authorizedID", id).add("CourseId", course.attr("value"))
                .add("CoursType", courseType).add("x", System.currentTimeMillis().toString()).build()
            materialParser.parse(
                execute(Request.Builder().url(COURSE_CONSOLIDATED_DETAIL).post(detailBody).build()),
                courseCode,
                faculty,
            ).valueOrThrow()
        }.distinctBy { it.id }
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
        if (downloadPath.startsWith("fileId:")) {
            val fileId = downloadPath.removePrefix("fileId:").takeIf(String::isNotBlank)
                ?: throw IOException("VTOP provided an invalid material ID")
            val token = currentToken()
            val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
            val body = FormBody.Builder().add("_csrf", token).add("authorizedID", id).add("fileId", fileId).build()
            return@withContext client.newCall(Request.Builder().url(COURSE_MATERIAL_DOWNLOAD).post(body).build()).execute().use { response ->
                if (response.code == 404) throw AuthenticationException()
                if (!response.isSuccessful) throw IOException("VTOP returned HTTP ${response.code}")
                response.body.bytes().also {
                    require(it.size <= MAX_MATERIAL_BYTES) { "Course material is too large" }
                    if (it.looksLikeHtml()) throw IOException("VTOP returned a page instead of the requested material")
                }
            }
        }
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
        pendingLoginChallenge = null
    }

    private fun get(url: String): String = execute(Request.Builder().url(url).get().build())

    private fun prepareLoginChallenge(): LoginChallenge? {
        cookieJar.clear()
        csrf = null
        get(SITE_ROOT)
        get(VTOP_ROOT)
        val openPage = get(OPEN_PAGE)
        updateTokens(openPage)
        csrf?.let { setupToken ->
            val setupBody = FormBody.Builder()
                .add("_csrf", setupToken)
                .add("flag", "VTOP")
                .build()
            execute(Request.Builder().url(SETUP_PAGE).post(setupBody).build())
        }

        repeat(MAX_CAPTCHA_PAGE_ATTEMPTS) {
            val loginPage = get(LOGIN)
            updateTokens(loginPage)
            val document = Jsoup.parse(loginPage)
            if (!isRecaptchaPage(loginPage, document)) {
                val image = extractCaptchaDataUri(document)
                val token = csrf
                if (image != null && token != null) return LoginChallenge(token, image)
            }
            if (it + 1 < MAX_CAPTCHA_PAGE_ATTEMPTS) Thread.sleep(600L * (it + 1))
        }
        return null
    }

    private fun manualCaptchaState(): SessionState {
        val challenge = prepareLoginChallenge() ?: return SessionState.VerificationRequired
        pendingLoginChallenge = challenge
        return SessionState.CaptchaRequired(challenge.imageDataUri)
    }

    private fun submitLoginChallenge(
        challenge: LoginChallenge,
        username: String,
        password: CharArray,
        answer: String,
    ): LoginAttempt {
        val body = FormBody.Builder()
            .add("_csrf", challenge.csrf)
            .add("username", username.trim())
            .add("password", String(password))
            .add("captchaStr", answer)
            .build()
        val response = executeDocument(Request.Builder().url(LOGIN).post(body).build())
        val html = response.html
        updateTokens(html)
        return when {
            isAuthenticated(html) -> LoginAttempt.ACTIVE
            isInvalidCredentials(html) -> LoginAttempt.INVALID_CREDENTIALS
            isMandatoryAction("${response.url}\n$html") || isRecaptchaPage(html, Jsoup.parse(html)) -> LoginAttempt.VERIFICATION_REQUIRED
            response.url.encodedPath != "/vtop/login" -> {
                val content = get(CONTENT)
                updateTokens(content)
                if (isAuthenticated(content)) LoginAttempt.ACTIVE else LoginAttempt.RETRY_CAPTCHA
            }
            else -> LoginAttempt.RETRY_CAPTCHA
        }
    }

    private fun extractCaptchaDataUri(document: org.jsoup.nodes.Document): String? {
        if (document.selectFirst("input[name=captchaStr], input#captchaStr") == null) return null
        return document.select("img[src]")
            .asSequence()
            .map { it.attr("src").trim() }
            .firstOrNull { it.startsWith("data:image/", ignoreCase = true) }
    }

    private fun isRecaptchaPage(html: String, document: org.jsoup.nodes.Document): Boolean {
        val activeCaptchaType = Regex("captchaType\\s*[:=]\\s*[\"']?([12])", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.get(1)
        return when (activeCaptchaType) {
            "1" -> false
            "2" -> true
            else -> document.selectFirst("#recaptcha, #g-recaptcha, .g-recaptcha") != null
        }
    }

    private fun isInvalidCredentials(html: String): Boolean =
        Regex("invalid\\s+(?:username|password|credentials)", RegexOption.IGNORE_CASE).containsMatchIn(html)

    private fun isMandatoryAction(html: String): Boolean =
        Regex("mandatory/data/off|feedback|studentFeedback|redressal|hostel.*instruction", RegexOption.IGNORE_CASE)
            .containsMatchIn(html)

    private fun academicPost(url: String, token: String, semesterId: String): String {
        val id = authorizedId ?: throw IOException("VTOP did not provide an authorized student ID")
        val body = academicBody(token, id, semesterId)
        return execute(Request.Builder().url(url).post(body).build())
    }

    private fun academicBody(token: String, id: String, semesterId: String) = FormBody.Builder()
        .add("_csrf", token)
        .add("authorizedID", id)
        .add("semesterSubId", semesterId)
        .add("x", System.currentTimeMillis().toString())
        .build()

    private fun currentToken(): String {
        csrf?.let { return it }
        updateTokens(get(INIT_PAGE))
        return csrf ?: throw IOException("VTOP did not provide a CSRF token")
    }

    private fun <T> ParseResult<T>.valueOrThrow(): T = when (this) {
        is ParseResult.Success -> value
        ParseResult.AuthenticationRequired -> throw AuthenticationException()
        is ParseResult.InvalidDocument -> throw IOException(reason)
    }

    private fun ensureAuthenticatedPage(url: String): String {
        menuPage(url)
        return csrf ?: throw IOException("VTOP did not provide a CSRF token")
    }

    /** VTOP academic menu links are AJAX POSTs, not ordinary browser GETs. */
    private fun menuPage(url: String): String {
        val token = csrf ?: throw AuthenticationException()
        val id = authorizedId ?: throw AuthenticationException()
        val body = FormBody.Builder()
            .add("_csrf", token)
            .add("authorizedID", id)
            .add("verifyMenu", "true")
            .add("nocache", System.currentTimeMillis().toString())
            .build()
        val html = execute(Request.Builder().url(url).post(body).build())
        updateTokens(html)
        if (VtopDocument.isAuthenticationPage(Jsoup.parse(html))) throw AuthenticationException()
        return html
    }

    private fun execute(request: Request): String = executeDocument(request).html

    private fun executeDocument(request: Request): HttpDocument = client.newCall(request).execute().use { response ->
        if (response.code == 404) {
            cookieJar.clear()
            csrf = null
            authorizedId = null
            throw AuthenticationException()
        }
        if (!response.isSuccessful) throw IOException("VTOP returned HTTP ${response.code}")
        HttpDocument(response.body.string(), response.request.url)
    }

    private fun updateTokens(html: String) {
        val document = Jsoup.parse(html)
        document.selectFirst("input[name=_csrf]")?.attr("value")?.takeIf(String::isNotBlank)?.let { csrf = it }
        document.selectFirst("meta[name=_csrf], meta#_csrf")?.attr("content")?.takeIf(String::isNotBlank)?.let { csrf = it }
        Regex("(?:_csrf|csrfToken|csrfValue)\\s*[:=]\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)?.let { csrf = it }
        extractAuthorizedId(html)?.let { authorizedId = it }
    }

    private fun extractAuthorizedId(html: String): String? {
        val document = Jsoup.parse(html)
        return document.selectFirst("input[name=authorizedID], input[name=authorizedIDX]")
            ?.attr("value")?.takeIf(String::isNotBlank)
            ?: Regex("authorizedIDX?\\s*[=:]\\s*[\"']?([A-Za-z0-9_.@-]+)")
                .find(html)?.groupValues?.get(1)?.takeIf(String::isNotBlank)
    }

    private fun isAuthenticated(html: String): Boolean {
        val document = Jsoup.parse(html)
        if (VtopDocument.isAuthenticationPage(document)) return false
        return document.selectFirst("a[href*=logout], #MenuBlock, input[name=authorizedID]") != null ||
            html.contains("Student Profile", ignoreCase = true)
    }

    private fun String.courseTitleKey(): String = substringBefore(" - ")
        .lowercase()
        .replace(Regex("\\b(theory|lab|embedded|only|project)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    companion object {
        private const val SITE_ROOT = "https://vtop.vit.ac.in/"
        private const val VTOP_ROOT = "https://vtop.vit.ac.in/vtop/"
        private const val BASE = "https://vtop.vit.ac.in/vtop"
        private const val MAX_MATERIAL_BYTES = 50 * 1024 * 1024
        private const val MAX_CAPTCHA_ATTEMPTS = 4
        private const val MAX_CAPTCHA_PAGE_ATTEMPTS = 6
        private const val CAPTCHA_LENGTH = 6
        private const val OPEN_PAGE = "$BASE/openPage"
        private const val INIT_PAGE = "$BASE/init/page"
        private const val SETUP_PAGE = "$BASE/prelogin/setup"
        private const val LOGIN = "$BASE/login"
        private const val CONTENT = "$BASE/content"
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
        private const val COURSE_PAGE_CONSOLIDATED = "$BASE/academics/common/CoursePageConsolidated"
        private const val COURSE_CONSOLIDATED_DETAIL = "$BASE/academics/CoursePageConsolidated/getCourseDetail"
        private const val COURSE_MATERIAL_DOWNLOAD = "$BASE/downloadCourseMaterialFacultyPdf"
    }

    private data class LoginChallenge(val csrf: String, val imageDataUri: String)
    private data class HttpDocument(val html: String, val url: okhttp3.HttpUrl)
    private enum class LoginAttempt { ACTIVE, INVALID_CREDENTIALS, VERIFICATION_REQUIRED, RETRY_CAPTCHA }
}

private fun ByteArray.looksLikeHtml(): Boolean {
    val prefix = take(128).toByteArray().toString(Charsets.UTF_8).trimStart().lowercase()
    return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
}

class AuthenticationException : IOException("VTOP authentication is required")
