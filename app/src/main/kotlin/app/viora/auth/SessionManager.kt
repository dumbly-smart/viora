package app.viora.auth

import app.viora.network.AuthenticationException
import app.viora.network.SessionState
import app.viora.network.VtopGateway
import app.viora.security.CredentialStore
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionResolution {
    data object Ready : SessionResolution
    data object SignInRequired : SessionResolution
    data object VerificationRequired : SessionResolution
    data object RecoveryPending : SessionResolution
    data object Unavailable : SessionResolution
}

data class SessionRefreshResult(
    val resolution: SessionResolution,
    val resources: List<Result<Unit>> = emptyList(),
)

class SessionManager(
    private val gateway: VtopGateway,
    private val credentials: CredentialStore,
) {
    suspend fun ensureActive(forceReauthenticate: Boolean = false): SessionResolution = authenticationMutex.withLock {
        try {
            val state = if (forceReauthenticate) SessionState.Missing else try {
                gateway.sessionState()
            } catch (_: AuthenticationException) {
                SessionState.Missing
            }
            when (state) {
                SessionState.Active -> SessionResolution.Ready
                is SessionState.CaptchaRequired -> SessionResolution.RecoveryPending
                SessionState.VerificationRequired -> SessionResolution.VerificationRequired
                SessionState.Missing -> silentlySignIn()
            }
        } catch (_: IOException) {
            SessionResolution.Unavailable
        }
    }

    /** Resource reads may be retried; assessment uploads must never use this helper. */
    suspend fun refreshResources(refresh: suspend () -> List<Result<Unit>>): SessionRefreshResult {
        var resolution = ensureActive()
        if (resolution != SessionResolution.Ready) return SessionRefreshResult(resolution)
        var resources = refresh()
        if (resources.none { it.exceptionOrNull() is AuthenticationException }) return SessionRefreshResult(resolution, resources)
        resolution = ensureActive(forceReauthenticate = true)
        if (resolution != SessionResolution.Ready) return SessionRefreshResult(resolution, resources)
        resources = refresh()
        return SessionRefreshResult(
            if (resources.any { it.exceptionOrNull() is AuthenticationException }) SessionResolution.RecoveryPending else SessionResolution.Ready,
            resources,
        )
    }

    private suspend fun silentlySignIn(): SessionResolution {
        val saved = credentials.load() ?: return SessionResolution.SignInRequired
        return try {
            when (gateway.login(saved.username, saved.password)) {
                SessionState.Active -> SessionResolution.Ready
                is SessionState.CaptchaRequired -> SessionResolution.RecoveryPending
                SessionState.VerificationRequired -> SessionResolution.VerificationRequired
                SessionState.Missing -> SessionResolution.SignInRequired
            }
        } finally {
            saved.destroy()
        }
    }

    private companion object {
        // Foreground and WorkManager can construct different graphs in the same process.
        val authenticationMutex = Mutex()
    }
}
