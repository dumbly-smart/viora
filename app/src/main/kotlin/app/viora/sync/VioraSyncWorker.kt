package app.viora.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.viora.VioraGraph
import app.viora.auth.LocalAccountManager
import java.util.concurrent.TimeUnit

class VioraSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = VioraGraph(applicationContext)
        val semesterId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null) ?: return Result.success()
        val semesterName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null) ?: semesterId
        return when (graph.timetableSync.refresh(semesterId, semesterName)) {
            SyncOutcome.Updated -> {
                val results = listOf(
                    graph.attendance.refresh(semesterId),
                    graph.assignments.refresh(semesterId),
                    graph.exams.refresh(semesterId),
                    graph.results.refresh(semesterId),
                    graph.extras.refresh(semesterId, graph.database.academicDao().courses(semesterId).map { it.code to it.faculty }),
                )
                graph.notifications.publishUpcoming(semesterId)
                if (results.all { it.isSuccess }) Result.success() else Result.retry()
            }
            SyncOutcome.SignInRequired, SyncOutcome.VerificationRequired -> Result.failure()
            is SyncOutcome.Failed -> Result.retry()
        }
    }
}

class VioraSyncScheduler(private val context: Context) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<VioraSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(LocalAccountManager.VIORA_SYNC_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object { const val UNIQUE_WORK = "viora-periodic-academic-sync" }
}
