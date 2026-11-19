package app.viora.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.viora.VioraGraph
import app.viora.database.VioraDatabase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    @Test fun classAlarmPostsOnceAndRejectsBreakOrOldSemester() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val settings = context.getSharedPreferences(VioraGraph.SETTINGS_NAME, Context.MODE_PRIVATE)
        // This test runs only on an isolated app installation, without an account.
        assumeTrue(!settings.getBoolean(VioraGraph.KEY_CONFIGURED, false))
        val manager = context.getSystemService(NotificationManager::class.java)
        val date = LocalDate.of(2026, 12, 2)
        val now = date.atTime(9, 50).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val database = Room.inMemoryDatabaseBuilder(context, VioraDatabase::class.java).build()
        try {
            val notifications = VioraNotifications(context, database.academicDao()) { now }
            suspend fun deliver(id: String, semester: String, day: LocalDate) {
                notifications.deliverScheduled(id, VioraNotifications.CLASSES,
                    "Class in 10 minutes", "Synthetic course", "schedule", semester, day.toEpochDay())
            }
            try {
                settings.edit().putString(VioraGraph.KEY_SEMESTER_ID, "synthetic-winter")
                    .putString(VioraGraph.KEY_SEMESTER_NAME, "Winter Semester 2026-27").commit()
                deliver("before-start", "synthetic-winter", date.minusDays(1))
                deliver("old-term", "synthetic-fall", date)
                assertEquals(0, manager.activeNotifications.count { it.notification.channelId == VioraNotifications.CLASSES })
                deliver("new-term", "synthetic-winter", date)
                deliver("new-term", "synthetic-winter", date)
                withTimeout(2000) {
                    while (manager.activeNotifications.none { it.notification.channelId == VioraNotifications.CLASSES }) delay(25)
                }
                assertEquals(1, manager.activeNotifications.count { it.notification.channelId == VioraNotifications.CLASSES })
                assertEquals(listOf("scheduled:new-term"), database.academicDao().notificationLedgerKeys())
            } finally {
                manager.cancel("scheduled:new-term".hashCode())
                settings.edit().remove(VioraGraph.KEY_SEMESTER_ID).remove(VioraGraph.KEY_SEMESTER_NAME).commit()
            }
        } finally {
            database.close()
        }
    }
}
