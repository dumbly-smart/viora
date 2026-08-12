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
import app.viora.auth.SessionResolution
import app.viora.widget.NextClassWidgetProvider
import java.util.concurrent.TimeUnit

class VioraSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = VioraGraph(applicationContext)
        val profile = graph.syncDiagnostics.start("background")
        val (result, outcome) = runCatching { runSync(graph) }
            .getOrElse { Result.retry() to "exception retry" }
        graph.syncDiagnostics.finish(profile, outcome)
        return result
    }

    private suspend fun runSync(graph: VioraGraph): Pair<Result, String> {
        val semesterId = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
            ?: return Result.success() to "not configured"
        val semesterName = graph.settings.getString(VioraGraph.KEY_SEMESTER_NAME, null) ?: semesterId
        return when (graph.sessions.ensureActive()) {
            SessionResolution.Ready -> {
                val results = listOf(
                    graph.exams.refresh(semesterId),
                    graph.timetable.refresh(semesterId, semesterName),
                    graph.attendance.refresh(semesterId),
                    graph.assignments.refresh(semesterId),
                    graph.results.refresh(semesterId),
                    graph.extras.refresh(semesterId, graph.database.academicDao().courses(semesterId).map { it.code to it.faculty }),
                )
                graph.notifications.publishUpcoming(semesterId)
                NextClassWidgetProvider.updateAll(applicationContext)
                if (results.all { it.isSuccess }) Result.success() to "success"
                else Result.retry() to "partial retry"
            }
            SessionResolution.SignInRequired -> Result.failure() to "sign-in required"
            SessionResolution.VerificationRequired -> Result.failure() to "verification required"
            SessionResolution.Unavailable -> Result.retry() to "network retry"
        }
    }
}

class VioraSyncScheduler(private val context: Context) {
    fun schedule(hours: Long = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE).getInt("sync_hours", 6).toLong()) {
        val request = PeriodicWorkRequestBuilder<VioraSyncWorker>(hours.coerceIn(1, 24), TimeUnit.HOURS)
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
