package app.viora.auth

import app.viora.network.SessionState
import app.viora.network.VtopGateway
import app.viora.security.CredentialStore
import java.io.IOException

sealed interface SessionResolution {
    data object Ready : SessionResolution
    data object SignInRequired : SessionResolution
    data object VerificationRequired : SessionResolution
    data object Unavailable : SessionResolution
}

class SessionManager(
    private val gateway: VtopGateway,
    private val credentials: CredentialStore,
) {
    suspend fun ensureActive(): SessionResolution = try {
        when (gateway.sessionState()) {
            SessionState.Active -> SessionResolution.Ready
            SessionState.VerificationRequired -> SessionResolution.VerificationRequired
            SessionState.Missing -> silentlySignIn()
        }
    } catch (_: IOException) {
        SessionResolution.Unavailable
    }

    private suspend fun silentlySignIn(): SessionResolution {
        val saved = credentials.load() ?: return SessionResolution.SignInRequired
        return try {
            when (gateway.login(saved.username, saved.password)) {
                SessionState.Active -> SessionResolution.Ready
                SessionState.VerificationRequired -> SessionResolution.VerificationRequired
                SessionState.Missing -> SessionResolution.SignInRequired
            }
        } finally {
            saved.destroy()
        }
    }
}
