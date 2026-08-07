package app.viora.auth

import app.viora.network.SessionState
import app.viora.network.TimetableSnapshot
import app.viora.network.VtopGateway
import app.viora.security.CredentialStore
import app.viora.security.StoredCredentials
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
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

    private class FakeGateway(
        private val initial: SessionState,
        private val loginResult: SessionState = SessionState.Missing,
    ) : VtopGateway {
        var loginCalls = 0
        override suspend fun sessionState() = initial
        override suspend fun login(username: String, password: CharArray): SessionState {
            loginCalls++
            return loginResult
        }
        override suspend fun semesters() = emptyList<app.viora.network.SemesterOption>()
        override suspend fun timetable(semesterId: String) = TimetableSnapshot(emptyList(), emptyList())
        override suspend fun attendance(semesterId: String) = app.viora.network.AttendanceSnapshot(emptyList())
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
