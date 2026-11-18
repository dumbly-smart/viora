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

    @Test fun liveGithubReleaseMetadataIsReachable() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("liveGithub") == "true")
        val request = okhttp3.Request.Builder().url("https://api.github.com/repos/dumbly-smart/viora/releases/latest").build()
        GithubUpdateWorker.releaseClient().newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
            val json = org.json.JSONObject(response.body!!.string())
            assertTrue(json.getString("tag_name").startsWith("v"))
            assertTrue(json.getJSONArray("assets").length() >= 2)
        }
    }

    /** Optional live-upgrade fixture is app code only, supplied privately by the device runner. */
    @Test fun suppliedUpgradeIsVerifiedAndStaged() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("upgradeFixture") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = File(context.filesDir, "upgrade-fixture.apk")
        assertTrue(fixture.isFile)
        val store = UpdateStore(context)
        assertTrue(store.compatible(fixture))
        val size = fixture.length()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fixture.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        @Suppress("DEPRECATION")
        val version = context.packageManager.getPackageArchiveInfo(fixture.absolutePath, 0)?.versionName.orEmpty()
        store.stage(fixture, digest.digest().joinToString("") { "%02x".format(it) }, size, version)
        assertTrue(store.ready())
        store.notifyReady()
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
