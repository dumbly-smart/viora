package app.viora.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.viora.VioraGraph
import app.viora.database.AcademicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZonedDateTime

class VioraReminderScheduler(
    private val context: Context,
    private val dao: AcademicDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences("viora_reminder_schedule", Context.MODE_PRIVATE)

    suspend fun schedule(semesterId: String) = withContext(Dispatchers.IO) {
        schedulingMutex.withLock { scheduleAlarms(semesterId) }
    }

    private suspend fun scheduleAlarms(semesterId: String) {
        val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(clock()), ACADEMIC_ZONE)
        val settings = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE)
        val plans = ReminderPlanner.create(
            slots = dao.timetableSnapshot(semesterId),
            exams = dao.examSnapshot(semesterId),
            assignments = if (settings.getBoolean("notify_deadlines", true)) dao.assignmentSnapshot(semesterId) else emptyList(),
            calendar = dao.calendarSnapshot(semesterId),
            now = now,
            includeExamReminders = settings.getBoolean("notify_exams", true),
            semesterName = settings.getString(VioraGraph.KEY_SEMESTER_NAME, null)
                .takeIf { settings.getString(VioraGraph.KEY_SEMESTER_ID, null) == semesterId },
            quietHours = settings.getBoolean("quiet_hours", true),
        )
        val keyedPlans = plans.map { hash("$semesterId:${it.id}") to it }
        val plannedIds = keyedPlans.mapTo(mutableSetOf()) { it.first }
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().minus(plannedIds).forEach { id ->
            pendingIntent(id, null, PendingIntent.FLAG_NO_CREATE)?.let(alarms::cancel)
        }
        keyedPlans.forEach { (id, plan) ->
            val operation = requireNotNull(pendingIntent(id, plan, PendingIntent.FLAG_UPDATE_CURRENT, semesterId))
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerEpochMillis, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerEpochMillis, operation)
            }
        }
        preferences.edit().putStringSet(KEY_IDS, plannedIds).apply()
    }

    private fun pendingIntent(id: String, plan: ReminderPlan?, mode: Int, semesterId: String? = null): PendingIntent? {
        val intent = Intent()
            .setComponent(ComponentName(context, VioraReminderReceiver::class.java))
            .setPackage(context.packageName)
            .setAction(ACTION_REMINDER)
            .setData(Uri.parse("viora://reminder/$id"))
        if (plan != null) intent
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SEMESTER_ID, semesterId)
            .putExtra(EXTRA_CLASS_DATE, plan.classDateEpochDay ?: Long.MIN_VALUE)
            .putExtra(EXTRA_CHANNEL, plan.channel)
            .putExtra(EXTRA_TITLE, plan.title)
            .putExtra(EXTRA_TEXT, plan.text)
            .putExtra(EXTRA_DESTINATION, plan.destination)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            mode or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private val schedulingMutex = Mutex()
        private val ACADEMIC_ZONE = ZoneId.of("Asia/Kolkata")
        private const val KEY_IDS = "scheduled_ids"
        const val ACTION_REMINDER = "app.viora.action.REMINDER"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_SEMESTER_ID = "reminder_semester_id"
        const val EXTRA_CLASS_DATE = "reminder_class_date"
        const val EXTRA_CHANNEL = "reminder_channel"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_TEXT = "reminder_text"
        const val EXTRA_DESTINATION = "reminder_destination"
    }
}

class VioraReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VioraReminderScheduler.ACTION_REMINDER) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getStringExtra(VioraReminderScheduler.EXTRA_ID).orEmpty()
                val channel = intent.getStringExtra(VioraReminderScheduler.EXTRA_CHANNEL).orEmpty()
                val title = intent.getStringExtra(VioraReminderScheduler.EXTRA_TITLE).orEmpty()
                val text = intent.getStringExtra(VioraReminderScheduler.EXTRA_TEXT).orEmpty()
                val destination = intent.getStringExtra(VioraReminderScheduler.EXTRA_DESTINATION).orEmpty()
                if (id.isNotBlank() && title.length <= 120 && text.length <= 240) {
                    val semesterId = intent.getStringExtra(VioraReminderScheduler.EXTRA_SEMESTER_ID) ?: return@launch
                    val classDate = intent.getLongExtra(VioraReminderScheduler.EXTRA_CLASS_DATE, Long.MIN_VALUE)
                        .takeUnless { it == Long.MIN_VALUE }
                    VioraGraph(context).notifications.deliverScheduled(id, channel, title, text, destination, semesterId, classDate)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class VioraReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val semesterId = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE)
            .getString(VioraGraph.KEY_SEMESTER_ID, null) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = VioraGraph(context)
                graph.reminders.schedule(semesterId)
            } finally {
                pending.finish()
            }
        }
    }
}
