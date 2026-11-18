package app.viora.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UpdaterIntegrationTest {
    @Test fun scheduleIsUniqueAndEverySixHours() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        GithubUpdateWorker.schedule(context)
        GithubUpdateWorker.schedule(context)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(GithubUpdateWorker.WORK_NAME).get(10, TimeUnit.SECONDS)
        assertEquals(1, work.size)
        assertEquals(TimeUnit.HOURS.toMillis(6), work.single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test fun installedApkCannotBeReinstalledAsANewerVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UpdateStore(context)
        assertFalse(store.compatible(File(context.applicationInfo.sourceDir)))
        val invalid = File(context.cacheDir, "invalid-update-test.apk")
        try {
            invalid.writeText("synthetic invalid APK")
            assertFalse(store.compatible(invalid))
        } finally { invalid.delete() }
    }
}
