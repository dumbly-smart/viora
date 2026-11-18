package app.viora.update

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Entry is explicit: notification tap or the foreground update prompt. Never clears app state. */
class UpdateInstallActivity : ComponentActivity() {
    private var checking = false
    private var settingsOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsOpened = savedInstanceState?.getBoolean("settingsOpened") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settingsOpened", settingsOpened)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (checking) return
        checking = true
        lifecycleScope.launch {
            val store = UpdateStore(this@UpdateInstallActivity)
            val ready = withContext(Dispatchers.IO) { store.ready() }
            if (!ready) {
                Toast.makeText(this@UpdateInstallActivity, "No compatible update is ready", Toast.LENGTH_LONG).show()
                finish()
            } else if (!packageManager.canRequestPackageInstalls()) {
                if (settingsOpened) {
                    Toast.makeText(this@UpdateInstallActivity, "Allow Viora to install updates, then tap the update again", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    AlertDialog.Builder(this@UpdateInstallActivity).setTitle("Allow Viora updates")
                        .setMessage("Enable “Allow from this source” for Viora. Android will then ask you to confirm the update. Your login and saved data will stay on this device.")
                        .setPositiveButton("Open settings") { _, _ ->
                            settingsOpened = true
                            checking = false
                            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        }.setNegativeButton("Later") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
                }
            } else {
                val uri = FileProvider.getUriForFile(this@UpdateInstallActivity, "$packageName.files", store.apk)
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@UpdateInstallActivity, "The Android package installer is unavailable", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }
}
