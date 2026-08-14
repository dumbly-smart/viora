package app.viora.data

import app.viora.database.AcademicDao
import app.viora.database.SyncResourceEntity

internal suspend inline fun refreshResource(
    dao: AcademicDao,
    resource: String,
    clock: () -> Long,
    crossinline persist: suspend (Long) -> Unit,
): Result<Unit> {
    val attempt = clock()
    dao.upsertSyncResource(SyncResourceEntity(resource, "SYNCING", attempt, null, null))
    return runCatching { persist(attempt) }.onFailure { error ->
        dao.upsertSyncResource(
            SyncResourceEntity(resource, "ERROR", attempt, null, error.message?.take(300) ?: "$resource could not be refreshed"),
        )
    }
}
