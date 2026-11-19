package app.viora.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.viora.VioraGraph
import app.viora.domain.AcademicClassCalendar
import app.viora.domain.ExamCalendarDate
import app.viora.domain.ExamWindow
import java.time.LocalDate
import java.time.ZoneId
import app.viora.MainActivity
import app.viora.database.AcademicDao
import app.viora.database.NotificationLedgerEntity
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VioraNotifications(
    private val context: Context,
    private val dao: AcademicDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CLASSES, "Classes", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(EXAMS, "Examinations", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(UPDATES, "Academic updates", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    suspend fun publishUpcoming(semesterId: String) {
        if (!canNotify() || inQuietHours()) return
        publishMutex.withLock {
            val now = clock()
            val changes = dao.changesSince(now - Duration.ofDays(7).toMillis())
            val attendancePlan = AttendanceNotificationPolicy.plan(
                semesterId = semesterId,
                cgpa = dao.academicSummarySnapshot()?.cgpa,
                attendance = dao.attendanceSnapshot(semesterId),
                attendanceChanges = changes.filter { it.category == "attendance" },
                publishedKeys = dao.notificationLedgerKeys().toSet(),
            )
            attendancePlan?.let { publishAttendance(it) }
            changes.filterNot { it.category == "materials" || it.category == "attendance" || (it.category == "exams" && !preferences.getBoolean("notify_exams", true)) || (it.category == "assignments" && !preferences.getBoolean("notify_deadlines", true)) }.forEach { change ->
                notifyOnce("change:${change.id}", if (change.category == "exams") EXAMS else UPDATES, change.title, change.detail, when (change.category) {
                    "exams", "timetable" -> "schedule"
                    "messages" -> "more"
                    "assignments" -> "tasks"
                    "marks", "grades" -> "marks"
                    else -> "courses"
                })
            }
        }
    }

    private suspend fun publishAttendance(plan: AttendanceNotificationPlan) {
        val launch = PendingIntent.getActivity(
            context,
            ATTENDANCE_UPDATE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .setPackage(context.packageName)
                .putExtra("viora_destination", "attendance")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = android.app.Notification.InboxStyle()
            .setBigContentTitle(plan.title)
            .setSummaryText(plan.summary)
        plan.expandedLines.forEach(style::addLine)
        val notification = android.app.Notification.Builder(context, UPDATES)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(plan.title)
            .setContentText(plan.summary)
            .setStyle(style)
            .setCategory(android.app.Notification.CATEGORY_STATUS)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(ATTENDANCE_UPDATE_NOTIFICATION_ID, notification)
        dao.insertNotificationLedgers(plan.ledgerKeys.map { NotificationLedgerEntity(it, clock()) })
    }

    private suspend fun notifyOnce(key: String, channel: String, title: String, text: String, destination: String) {
        if (dao.insertNotificationLedger(NotificationLedgerEntity(key, clock())) == -1L) return
        val launch = PendingIntent.getActivity(
            context,
            key.hashCode(),
            Intent(context, MainActivity::class.java)
                .setPackage(context.packageName)
                .putExtra("viora_destination", destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(key.hashCode(), notification)
    }

    suspend fun deliverScheduled(
        key: String,
        channel: String,
        title: String,
        text: String,
        destination: String,
        semesterId: String,
        classDateEpochDay: Long? = null,
    ) {
        if (!canNotify() || inQuietHours()) return
        if (preferences.getString(VioraGraph.KEY_SEMESTER_ID, null) != semesterId) return
        if (channel == EXAMS && !preferences.getBoolean("notify_exams", true)) return
        if (channel == DEADLINES && !preferences.getBoolean("notify_deadlines", true)) return
        if (channel == CLASSES) {
            val date = classDateEpochDay?.let(LocalDate::ofEpochDay) ?: return
            val rules = AcademicClassCalendar(
                dao.calendarSnapshot(semesterId).map { ExamCalendarDate(LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType) },
                dao.examSnapshot(semesterId).map { ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType) },
                ZoneId.of("Asia/Kolkata"),
                preferences.getString(VioraGraph.KEY_SEMESTER_NAME, null),
            )
            if (rules.timetableDay(date) == null) return
        }
        if (channel !in setOf(CLASSES, EXAMS, DEADLINES)) return
        if ((channel == DEADLINES && destination != "tasks") || (channel != DEADLINES && destination != "schedule")) return
        notifyOnce("scheduled:$key", channel, title, text, destination)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private val preferences get() = context.getSharedPreferences("viora_local_settings", Context.MODE_PRIVATE)
    private fun inQuietHours(): Boolean { if (!preferences.getBoolean("quiet_hours", true)) return false; val hour = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.of("Asia/Kolkata")).hour; return hour >= 22 || hour < 7 }

    companion object {
        const val DEADLINES = "viora-deadlines"
        const val CLASSES = "viora-classes"
        const val EXAMS = "viora-exams"
        const val UPDATES = "viora-updates"
        const val ATTENDANCE_UPDATE_NOTIFICATION_ID = 0x56494f41
        private val publishMutex = Mutex()
    }
}
