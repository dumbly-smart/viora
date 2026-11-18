package app.viora.update

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.os.Bundle
import app.viora.MainActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VioraApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var prompted = false
    override fun onCreate() {
        super.onCreate()
        GithubUpdateWorker.schedule(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity || prompted) return
        prompted = true
        activity.lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) { UpdateStore(activity).ready() }
            if (!ready) { prompted = false; return@launch }
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { prompted = false; return@launch }
            AlertDialog.Builder(activity).setTitle("Viora update ready")
                .setMessage("Install the latest version? Your login and saved data will be kept.")
                .setPositiveButton("Install") { _, _ -> activity.startActivity(Intent(activity, UpdateInstallActivity::class.java)) }
                .setNegativeButton("Later", null).show()
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
