package app.viora.auth

import app.viora.network.SessionState
import app.viora.network.TimetableSnapshot
import app.viora.network.VtopGateway
import app.viora.security.CredentialStore
import app.viora.security.StoredCredentials
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionManagerTest {
    @Test fun `expiry during resource reads reconnects and retries once`() = runTest {
        val gateway = FakeGateway(SessionState.Active, SessionState.Active)
        val manager = SessionManager(gateway, FakeCredentials("synthetic", "synthetic".toCharArray()))
        var reads = 0
        val result = manager.refreshResources {
            reads++
            listOf(if (reads == 1) Result.failure(app.viora.network.AuthenticationException()) else Result.success(Unit))
        }
        assertEquals(SessionResolution.Ready, result.resolution)
        assertEquals(2, reads)
        assertEquals(1, gateway.loginCalls)
        assertTrue(result.resources.all { it.isSuccess })
    }

    @Test fun `repeated expiry is deferred without an endless login loop`() = runTest {
        val gateway = FakeGateway(SessionState.Active, SessionState.Active)
        val manager = SessionManager(gateway, FakeCredentials("synthetic", "synthetic".toCharArray()))
        var reads = 0
        val result = manager.refreshResources { reads++; listOf(Result.failure(app.viora.network.AuthenticationException())) }
        assertEquals(SessionResolution.RecoveryPending, result.resolution)
        assertEquals(2, reads)
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun `rejected saved credentials do not cause repeated automatic attempts`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, SessionState.Missing)
        val result = SessionManager(gateway, FakeCredentials("synthetic", "synthetic".toCharArray())).ensureActive()
        assertEquals(SessionResolution.SignInRequired, result)
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun `exhausted captcha stays retryable without requesting credentials`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, SessionState.CaptchaRequired("data:image/png;base64,synthetic"))
        val store = FakeCredentials("synthetic", "synthetic".toCharArray())
        assertEquals(SessionResolution.RecoveryPending, SessionManager(gateway, store).ensureActive())
        assertEquals(1, gateway.loginCalls)
        assertTrue(store.loadedPasswordDestroyed())
    }

    @Test fun `expired session reported as authentication error silently reconnects`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, SessionState.Active, app.viora.network.AuthenticationException())
        val store = FakeCredentials("synthetic", "synthetic".toCharArray())
        assertEquals(SessionResolution.Ready, SessionManager(gateway, store).ensureActive())
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun `simultaneous recovery requests perform a single sign in`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, SessionState.Active)
        val manager = SessionManager(gateway, FakeCredentials("synthetic", "synthetic".toCharArray()))
        val first = async { manager.ensureActive() }
        val second = async { manager.ensureActive() }
        assertEquals(SessionResolution.Ready, first.await())
        assertEquals(SessionResolution.Ready, second.await())
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun `active cookie session does not submit credentials again`() = runTest {
        val gateway = FakeGateway(SessionState.Active)
        val store = FakeCredentials("student", "secret".toCharArray())

        val result = SessionManager(gateway, store).ensureActive()

        assertEquals(SessionResolution.Ready, result)
        assertEquals(0, gateway.loginCalls)
    }

    @Test fun `expired cookie session silently signs in with local credentials`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, loginResult = SessionState.Active)
        val store = FakeCredentials("student", "secret".toCharArray())

        val result = SessionManager(gateway, store).ensureActive()

        assertEquals(SessionResolution.Ready, result)
        assertEquals(1, gateway.loginCalls)
        assertTrue(store.loadedPasswordDestroyed())
    }

    @Test fun `missing credentials requests sign in instead of retrying`() = runTest {
        val gateway = FakeGateway(SessionState.Missing)

        val result = SessionManager(gateway, FakeCredentials()).ensureActive()

        assertEquals(SessionResolution.SignInRequired, result)
        assertEquals(0, gateway.loginCalls)
    }

    @Test fun `network reset reports unavailable instead of escaping`() = runTest {
        val gateway = FakeGateway(SessionState.Missing, sessionError = IOException("connection reset"))

        val result = SessionManager(gateway, FakeCredentials()).ensureActive()

        assertEquals(SessionResolution.Unavailable, result)
        assertEquals(0, gateway.loginCalls)
    }

    private class FakeGateway(
        private val initial: SessionState,
        private val loginResult: SessionState = SessionState.Missing,
        private val sessionError: IOException? = null,
    ) : VtopGateway {
        var loginCalls = 0
        private var current = initial
        override suspend fun sessionState() = sessionError?.let { throw it } ?: current
        override suspend fun login(username: String, password: CharArray): SessionState {
            loginCalls++
            kotlinx.coroutines.delay(10)
            current = loginResult
            return loginResult
        }
        override suspend fun semesters() = emptyList<app.viora.network.SemesterOption>()
        override suspend fun timetable(semesterId: String) = TimetableSnapshot(emptyList(), emptyList())
        override suspend fun attendance(semesterId: String) = app.viora.network.AttendanceSnapshot(emptyList())
        override suspend fun digitalAssignments(semesterId: String) = emptyList<app.viora.network.DigitalAssignmentRecord>()
        override suspend fun uploadDigitalAssignment(semesterId: String, assignmentId: String, fileName: String, mimeType: String, bytes: ByteArray) = Unit
        override suspend fun exams(semesterId: String) = emptyList<app.viora.network.ExamRecord>()
        override suspend fun marks(semesterId: String) = emptyList<app.viora.network.MarkRecord>()
        override suspend fun grades(semesterId: String) = app.viora.network.GradeSnapshot(emptyList(), null)
        override suspend fun cgpa() = app.viora.network.CgpaSnapshot(null, null, null, emptyMap())
        override suspend fun academicCalendar(semesterId: String) = emptyList<app.viora.network.AcademicCalendarRecord>()
        override suspend fun classMessages() = emptyList<app.viora.network.ClassMessageRecord>()
        override suspend fun courseMaterials(semesterId: String, courseCode: String, courseTitle: String, faculty: String) = emptyList<app.viora.network.CourseMaterialRecord>()
        override suspend fun importInteractiveSession(cookieHeader: String) = SessionState.Active
        override suspend fun downloadCourseMaterial(downloadPath: String) = ByteArray(0)
        override suspend fun clearLocalSession() = Unit
    }

    private class FakeCredentials(
        private val username: String? = null,
        private val sourcePassword: CharArray? = null,
    ) : CredentialStore {
        private var loaded: StoredCredentials? = null
        override fun save(username: String, password: CharArray) = Unit
        override fun load(): StoredCredentials? {
            val user = username ?: return null
            return StoredCredentials(user, sourcePassword!!.copyOf()).also { loaded = it }
        }
        override fun clear() = Unit
        fun loadedPasswordDestroyed() = loaded?.password?.all { it == '\u0000' } == true
    }
}
