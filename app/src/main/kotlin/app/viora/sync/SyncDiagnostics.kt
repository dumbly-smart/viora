package app.viora.sync

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.Trace
import androidx.work.WorkManager
import app.viora.VioraGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class SyncDiagnosticsSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean,
    val batteryOptimizationActive: Boolean,
    val backgroundRestricted: Boolean,
    val workState: String,
    val runAttemptCount: Int,
    val lastRunEpochMillis: Long?,
    val lastDurationMillis: Long?,
    val lastOutcome: String?,
    val lastSource: String?,
)

class SyncProfileToken internal constructor(
    internal val source: String,
    internal val startedEpochMillis: Long,
    internal val startedElapsedMillis: Long,
    internal val traceCookie: Int,
)

class SyncDiagnostics(private val context: Context) {
    private val settings = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE)

    fun start(source: String): SyncProfileToken {
        val cookie = nextCookie.incrementAndGet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(TRACE_NAME, cookie)
        }
        return SyncProfileToken(source, System.currentTimeMillis(), SystemClock.elapsedRealtime(), cookie)
    }

    fun finish(token: SyncProfileToken, outcome: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(TRACE_NAME, token.traceCookie)
        }
        settings.edit()
            .putLong(KEY_LAST_RUN, token.startedEpochMillis)
            .putLong(KEY_LAST_DURATION, (SystemClock.elapsedRealtime() - token.startedElapsedMillis).coerceAtLeast(0))
            .putString(KEY_LAST_OUTCOME, outcome.take(40))
            .putString(KEY_LAST_SOURCE, token.source.take(24))
            .apply()
    }

    suspend fun snapshot(): SyncDiagnosticsSnapshot = withContext(Dispatchers.IO) {
        val battery = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val work = runCatching {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(VioraSyncScheduler.UNIQUE_WORK).get()
                .maxByOrNull { it.runAttemptCount }
        }.getOrNull()
        val status = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
        val percent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        SyncDiagnosticsSnapshot(
            batteryPercent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            powerSaveMode = power?.isPowerSaveMode == true,
            batteryOptimizationActive = power?.isIgnoringBatteryOptimizations(context.packageName) == false,
            backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity?.isBackgroundRestricted == true else false,
            workState = work?.state?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Not scheduled",
            runAttemptCount = work?.runAttemptCount ?: 0,
            lastRunEpochMillis = settings.optionalLong(KEY_LAST_RUN),
            lastDurationMillis = settings.optionalLong(KEY_LAST_DURATION),
            lastOutcome = settings.getString(KEY_LAST_OUTCOME, null),
            lastSource = settings.getString(KEY_LAST_SOURCE, null),
        )
    }

    private fun android.content.SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    companion object {
        private const val TRACE_NAME = "Viora academic sync"
        private const val KEY_LAST_RUN = "diagnostics_last_run"
        private const val KEY_LAST_DURATION = "diagnostics_last_duration"
        private const val KEY_LAST_OUTCOME = "diagnostics_last_outcome"
        private const val KEY_LAST_SOURCE = "diagnostics_last_source"
        private val nextCookie = AtomicInteger()
    }
}
