package app.viora.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.viora.MainActivity
import app.viora.database.AcademicDao
import app.viora.database.NotificationLedgerEntity
import java.time.Duration
import java.time.LocalTime

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
        val now = clock()
        val horizon = now + Duration.ofHours(24).toMillis()
        dao.assignmentsDueBetween(semesterId, now, horizon).forEach { assignment ->
            if (!preferences.getBoolean("notify_deadlines", true)) return@forEach
            val withinThreeHours = assignment.dueEpochMillis?.minus(now)?.let { it <= Duration.ofHours(3).toMillis() } == true
            notifyOnce(
                key = "da-${if (withinThreeHours) "3h" else "24h"}:$semesterId:${assignment.id}:${assignment.dueEpochMillis}",
                channel = DEADLINES,
                title = if (withinThreeHours) "Assignment due within 3 hours" else "Assignment due soon",
                text = "${assignment.courseCode} · ${assignment.title}",
                destination = "tasks",
            )
        }
        val target = preferences.getInt("attendance_target", 75)
        dao.attendanceSnapshot(semesterId).filter { it.held > 0 && it.attended * 100 < target * it.held }.forEach { attendance ->
            notifyOnce("attendance:$semesterId:${attendance.id}:${attendance.attended}:${attendance.held}:$target", DEADLINES, "Attendance below $target%", attendance.courseTitle.ifBlank { attendance.courseCode }, "courses")
        }
        dao.changesSince(now - Duration.ofDays(7).toMillis()).forEach { change ->
            notifyOnce("change:${change.id}", if (change.category == "exams") EXAMS else UPDATES, change.title, change.detail, when (change.category) { "exams" -> "schedule"; "messages" -> "more"; else -> "courses" })
        }
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

    suspend fun deliverScheduled(key: String, channel: String, title: String, text: String, destination: String) {
        if (!canNotify()) return
        if (channel !in setOf(CLASSES, EXAMS) || destination != "schedule") return
        notifyOnce("scheduled:$key", channel, title, text, destination)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private val preferences get() = context.getSharedPreferences("viora_local_settings", Context.MODE_PRIVATE)
    private fun inQuietHours(): Boolean { if (!preferences.getBoolean("quiet_hours", true)) return false; val hour = LocalTime.now().hour; return hour >= 22 || hour < 7 }

    companion object {
        const val DEADLINES = "viora-deadlines"
        const val CLASSES = "viora-classes"
        const val EXAMS = "viora-exams"
        const val UPDATES = "viora-updates"
    }
}
