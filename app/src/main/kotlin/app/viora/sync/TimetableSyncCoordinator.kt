package app.viora.sync

import app.viora.auth.SessionManager
import app.viora.auth.SessionResolution
import app.viora.data.TimetableRepository

sealed interface SyncOutcome {
    data object Updated : SyncOutcome
    data object SignInRequired : SyncOutcome
    data object VerificationRequired : SyncOutcome
    data class Failed(val safeMessage: String) : SyncOutcome
}

class TimetableSyncCoordinator(
    private val sessions: SessionManager,
    private val timetable: TimetableRepository,
) {
    suspend fun refresh(semesterId: String, semesterName: String): SyncOutcome {
        val refresh = sessions.refreshResources { listOf(timetable.refresh(semesterId, semesterName)) }
        return when (refresh.resolution) {
            SessionResolution.Ready -> refresh.resources.single().fold(
                onSuccess = { SyncOutcome.Updated },
                onFailure = { SyncOutcome.Failed("Your cached timetable is still available") },
            )
            SessionResolution.SignInRequired -> SyncOutcome.SignInRequired
            SessionResolution.VerificationRequired -> SyncOutcome.VerificationRequired
            SessionResolution.RecoveryPending -> SyncOutcome.Failed("Viora will retry session recovery; cached data is still available")
            SessionResolution.Unavailable -> SyncOutcome.Failed("VTOP could not be reached; cached data is still available")
        }
    }
}
