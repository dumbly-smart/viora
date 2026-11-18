package app.viora

import androidx.test.platform.app.InstrumentationRegistry
import app.viora.network.*
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in probe: no credentials, response bodies, images or cookies are logged. */
class LiveVtopConnectionTest {
    @Test fun expiredSessionRecoversWithoutReturningToSetup() = kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopRecovery") == "true")
        val graph = VioraGraph(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Account was successfully configured", graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false))
        val credentials = graph.credentials.load()
        assertNotNull("Remembered login is available", credentials)
        credentials?.destroy()
        val semester = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        graph.gateway.clearLocalSession()
        assertEquals("Expired session recovers without typed credentials", app.viora.auth.SessionResolution.Ready, graph.sessions.ensureActive())
        var reads = 0
        val refresh = graph.sessions.refreshResources {
            reads++
            if (reads == 1) {
                graph.gateway.clearLocalSession()
                listOf(Result.failure(AuthenticationException()))
            } else listOf(runCatching { graph.gateway.semesters(); Unit })
        }
        assertEquals("Mid-refresh recovery succeeds", app.viora.auth.SessionResolution.Ready, refresh.resolution)
        assertEquals("Resource reads retry once", 2, reads)
        assertTrue("Resource refresh completes", refresh.resources.all { it.isSuccess })
        assertTrue("Recovery keeps account configured", graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false))
        assertEquals("Recovery keeps selected semester", semester, graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null))
    }

    @Test fun refreshAcademicResourcesUsingSavedAccount() = kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopSync") == "true")
        val graph = VioraGraph(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals("Saved account reauthenticates", app.viora.auth.SessionResolution.Ready, graph.sessions.ensureActive())
        val options = graph.gateway.semesters()
        val decision = app.viora.data.CurrentSemesterResolver(graph.gateway).resolve(options,
            graph.database.academicDao().semesterSnapshot().map { it.id }.toSet(),
            graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null))
        val selected = options.firstOrNull { it.id == decision.selectedId }
        assertNotNull("Current academic semester is available", selected)
        val semester = requireNotNull(selected).id
        val name = selected.name
        graph.settings.edit().putString(VioraGraph.KEY_SEMESTER_ID, semester).putString(VioraGraph.KEY_SEMESTER_NAME, name).commit()
        val outcomes = listOf(
            "exams" to graph.exams.refresh(semester),
            "timetable" to graph.timetable.refresh(semester, name),
            "attendance" to graph.attendance.refresh(semester),
            "assignments" to graph.assignments.refresh(semester),
            "results" to graph.results.refresh(semester),
            "calendar and messages" to graph.extras.refresh(semester, graph.database.academicDao().courses(semester).map { it.code to it.faculty }),
        )
        val safeReasons = setOf("VTOP did not provide academic calendar months", "Calendar grid contained no valid dates", "Academic calendar layout was not recognized", "Calendar month context was not provided", "Timetable grid was not found", "No assessment marks were found", "Grade table was not found", "No grade records were found", "CGPA was not found", "Exam schedule table was not found", "Exam headers were not found", "Attendance table was not found", "Attendance rows were not found", "Attendance headers were not found", "No semesters were found", "Digital assignment detail table was not found", "Digital assignment headers were not found", "Digital assignment table was not found", "Digital assignment headers were not found")
        val failed = outcomes.filter { it.second.isFailure }.map {
            val error = it.second.exceptionOrNull()
            val message = error?.message.orEmpty()
            val reason = safeReasons.firstOrNull { known -> message == known || message.contains("calendar: $known") }
                ?: Regex("VTOP returned HTTP [0-9]{3}").find(message)?.value
                ?: error?.javaClass?.simpleName
            "${it.first}: $reason"
        }
        assertTrue("Academic refresh failures: ${failed.joinToString()}", failed.isEmpty())
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.ZonedDateTime.now(zone)
        val dao = graph.database.academicDao()
        val exams = dao.examSnapshot(semester)
        val calendar = dao.calendarSnapshot(semester)
        val windows = app.viora.domain.examSuppressionWindows(
            exams.map { app.viora.domain.ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType) },
            calendar.map { app.viora.domain.ExamCalendarDate(java.time.LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType) }, zone)
        val plans = app.viora.notifications.ReminderPlanner.create(dao.timetableSnapshot(semester), exams,
            assignments = dao.assignmentSnapshot(semester), calendar = calendar, now = now, includeExamReminders = true)
        assertTrue("Class reminders respect live exam suppression", plans.filter { it.channel == app.viora.notifications.VioraNotifications.CLASSES }.none { plan ->
            val date = java.time.Instant.ofEpochMilli(plan.triggerEpochMillis).atZone(zone).toLocalDate()
            windows.any { !date.isBefore(it.startDate) && date.isBefore(it.resumeDate) }
        })
        graph.reminders.schedule(semester)
    }

    @Test fun authenticatedSessionAndCacheSurviveProcessRestart() = kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopAuthenticated") == "true")
        val graph = VioraGraph(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("Sign-in completed", graph.settings.getBoolean(VioraGraph.KEY_CONFIGURED, false))
        val database = graph.database.openHelper.readableDatabase
        for (table in listOf("semesters", "courses", "class_slots")) {
            database.query("SELECT EXISTS(SELECT 1 FROM $table)").use {
                assertTrue("Cached $table query", it.moveToFirst())
                assertTrue("Initial sync cached $table", it.getInt(0) == 1)
            }
        }
        var session: SessionState = SessionState.Missing
        for (attempt in 0..2) {
            try {
                session = graph.gateway.sessionState()
                break
            } catch (failure: java.io.IOException) {
                if (attempt == 2) throw failure
                kotlinx.coroutines.delay(1000)
            }
        }
        assertEquals("Persisted cookies authenticate after process restart", SessionState.Active, session)
    }

    @Test fun rootSessionCaptchaAndLocalSolver() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVtopProbe") == "true")
        val store = object : SessionCookieStore {
            var values = emptyList<Cookie>()
            override fun load() = values
            override fun save(cookies: List<Cookie>) { values = cookies }
            override fun clear() { values = emptyList() }
        }
        val client = VioraHttpClient.create(store)
        repeat(3) {
            store.clear()
            val root = client.newCall(Request.Builder().url("https://vtop.vit.ac.in/").build()).execute().use {
                assertEquals("Root session HTTP status", 200, it.code)
                Jsoup.parse(it.body.string())
            }
            val token = Regex("""var csrfValue = /\*(.*?)\*/'.*';""").find(root.html())?.groupValues?.get(1)
                ?: root.selectFirst("input[name=_csrf]")?.attr("value")
                ?: root.selectFirst("meta[name=_csrf]")?.attr("content")
            assertFalse("Root session provides CSRF", token.isNullOrBlank())
            val setup = client.newCall(Request.Builder().url("https://vtop.vit.ac.in/vtop/prelogin/setup")
                .post(FormBody.Builder().add("_csrf", requireNotNull(token)).add("flag", "VTOP").build()).build()).execute().use {
                assertEquals("Prelogin setup HTTP status", 200, it.code)
                Jsoup.parse(it.body.string())
            }
            val image = setup.select("#captchaBlock img[src], img[src]").map { it.attr("src") }
                .firstOrNull { it.startsWith("data:image/") }
            if (image == null) {
                val recaptcha = setup.selectFirst(".g-recaptcha, #recaptcha") != null
                val password = setup.selectFirst("input[type=password]") != null
                if (it < 2) return@repeat
                fail("No text CAPTCHA after 3 fresh sessions: recaptcha=$recaptcha passwordForm=$password images=${setup.select("img").size}")
            }
            val answer = VtopCaptchaSolver.fromAssets(InstrumentationRegistry.getInstrumentation().targetContext.assets)
                .solve(requireNotNull(image))
            assertEquals("Local solver produces six characters", 6, answer.length)
            store.clear()
            return
        }
    }
}
