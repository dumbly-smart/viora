package app.viora.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public release transport: deliberately has no connection to the VTOP client or account. */
class GithubUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        checkMutex.withLock { checkRelease() }
    }

    private fun checkRelease(): Result {
        try {
            if (lastCheckMillis != 0L && SystemClock.elapsedRealtime() - lastCheckMillis < 30_000) return Result.success()
            val store = UpdateStore(applicationContext)
            val staged = store.ready()
            if (staged) store.notifyReady()
            val client = releaseClient()
            val json = JSONObject(readSmall(client, LATEST, 512 * 1024))
            lastCheckMillis = SystemClock.elapsedRealtime()
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return Result.success()
            val assets = json.getJSONArray("assets")
            val tag = json.getString("tag_name").removePrefix("v")
            if (!tag.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return Result.success()
            // A release APK never replaces a differently named development package.
            if (applicationContext.packageName != "app.viora") return Result.success()
            @Suppress("DEPRECATION")
            val installed = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            if (!UpdatePolicy.newerRelease(tag, installed.versionName)) return Result.success()
            if (staged && store.preferences.getString("version", null) == tag) return Result.success()
            val available = (0 until assets.length()).map { assets.getJSONObject(it) }
            val normal = "Viora-$tag.apk"
            val migration = "Viora-$tag-migration.apk"
            val development = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val names = if (Build.VERSION.SDK_INT < 28) listOf(normal)
                else if (development) listOf(migration, normal) else listOf(normal, migration)
            for (name in names) {
                val apk = available.singleOrNull { it.optString("name") == name } ?: continue
                if (download(client, apk, available, store, tag)) return Result.success()
            }
            return Result.success()
        } catch (_: IOException) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: org.json.JSONException) { return Result.failure() }
    }

    private fun download(client: OkHttpClient, apk: JSONObject, assets: List<JSONObject>, store: UpdateStore, tag: String): Boolean {
        val apkName = apk.getString("name")
        val size = apk.getLong("size")
        if (size !in 1..UpdatePolicy.MAX_APK_BYTES) return false
        val url = apk.getString("browser_download_url")
        if (!UpdatePolicy.releaseUrl(url)) return false
        val checksumAsset = assets.singleOrNull { it.optString("name") == "$apkName.sha256" }
        val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            ?: checksumAsset?.getString("browser_download_url")?.takeIf(UpdatePolicy::releaseUrl)?.let {
                readSmall(client, it, 4096).trim().split(Regex("\\s+")).first()
            } ?: return false
        if (!digest.matches(Regex("[a-fA-F0-9]{64}"))) return false
        val partial = File(store.directory, "download.part")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Release download unavailable")
                val body = response.body ?: throw IOException("Empty release")
                if (body.contentLength() > size) throw IOException("Oversized release")
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > size) throw IOException("Oversized release")
                        output.write(buffer, 0, read)
                    }
                } }
            }
            if (!UpdatePolicy.matches(partial.inputStream(), size, digest) || !store.compatible(partial)) {
                return false
            }
            store.stage(partial, digest, size, tag)
            store.notifyReady()
        } finally { partial.delete() }
        return true
    }

    companion object {
        const val WORK_NAME = "viora-github-update"
        private val checkMutex = Mutex()
        private var lastCheckMillis = 0L
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_NAME-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GithubUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build(),
            )
        }
        private const val LATEST = "https://api.github.com/repos/dumbly-smart/viora/releases/latest"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GithubUpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build(),
            )
        }
        internal fun releaseClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (url.scheme != "https" || url.port != 443 || url.host !in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
                    throw IOException("Disallowed release host")
                }
                chain.proceed(chain.request().newBuilder().header("User-Agent", "Viora-Updater")
                    .header("Accept", "application/vnd.github+json").build())
            }.build()
        private fun readSmall(client: OkHttpClient, url: String, max: Int): String =
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Release metadata unavailable")
                val body = response.body ?: throw IOException("Empty metadata")
                body.byteStream().use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (buffer.size() <= max) {
                        val count = input.read(chunk, 0, minOf(chunk.size, max + 1 - buffer.size()))
                        if (count == -1) break
                        buffer.write(chunk, 0, count)
                    }
                    val bytes = buffer.toByteArray()
                    if (bytes.size > max) throw IOException("Oversized metadata")
                    bytes.toString(Charsets.UTF_8)
                }
            }
    }
}
