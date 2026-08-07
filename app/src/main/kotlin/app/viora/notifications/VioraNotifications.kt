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
                NotificationChannel(EXAMS, "Examinations", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    suspend fun publishUpcoming(semesterId: String) {
        if (!canNotify()) return
        val now = clock()
        val horizon = now + Duration.ofHours(24).toMillis()
        dao.assignmentsDueBetween(semesterId, now, horizon).forEach { assignment ->
            if (!preferences.getBoolean("notify_deadlines", true)) return@forEach
            notifyOnce(
                key = "da-24h:$semesterId:${assignment.id}:${assignment.dueEpochMillis}",
                channel = DEADLINES,
                title = "Assignment due soon",
                text = "${assignment.courseCode} · ${assignment.title}",
            )
        }
        dao.examsBetween(semesterId, now, horizon).forEach { exam ->
            if (!preferences.getBoolean("notify_exams", true)) return@forEach
            notifyOnce(
                key = "exam-24h:$semesterId:${exam.id}:${exam.startsEpochMillis}",
                channel = EXAMS,
                title = "${exam.examType} exam within 24 hours",
                text = listOf(exam.courseCode, exam.venue).filter(String::isNotBlank).joinToString(" · "),
            )
        }
    }

    private suspend fun notifyOnce(key: String, channel: String, title: String, text: String) {
        if (dao.insertNotificationLedger(NotificationLedgerEntity(key, clock())) == -1L) return
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        manager.notify(key.hashCode(), notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private val preferences get() = context.getSharedPreferences("viora_local_settings", Context.MODE_PRIVATE)

    companion object {
        const val DEADLINES = "viora-deadlines"
        const val EXAMS = "viora-exams"
    }
}
