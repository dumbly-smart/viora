package app.viora.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

internal class UpdateStore(private val context: Context) {
    val directory = File(context.filesDir, "updates").apply { mkdirs() }
    val apk = File(directory, "release.apk")
    val preferences = context.getSharedPreferences("viora_updates", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun compatible(file: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        fun keys(info: PackageInfo): Set<String> = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        } else info.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return UpdatePolicy.accepts(current.packageName, version(current), keys(current), candidate.packageName, version(candidate), keys(candidate))
    }

    fun ready(): Boolean = synchronized(fileLock) {
        if (!apk.isFile) return@synchronized false
        val valid = runCatching {
            compatible(apk) && UpdatePolicy.matches(apk.inputStream(), preferences.getLong("size", 0), preferences.getString("sha256", "").orEmpty())
        }.getOrDefault(false)
        if (!valid) {
            apk.delete()
            preferences.edit().clear().apply()
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
        valid
    }

    fun stage(partial: File, digest: String, size: Long, version: String) = synchronized(fileLock) {
        if (!partial.renameTo(apk)) throw java.io.IOException("Cannot stage update")
        if (!preferences.edit().putString("sha256", digest).putLong("size", size).putString("version", version).commit()) {
            apk.delete()
            throw java.io.IOException("Cannot save update metadata")
        }
    }

    fun notifyReady() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("app_updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(context, 701, Intent(context, UpdateInstallActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, "app_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Viora update ready")
            .setContentText("Tap to install. Your login and saved data will be kept.")
            .setContentIntent(intent).setAutoCancel(false).build())
    }

    companion object { const val NOTIFICATION_ID = 6401; private val fileLock = Any() }
}
