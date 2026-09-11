package app.viora.auth

import android.content.Context
import androidx.work.WorkManager
import app.viora.database.VioraDatabase
import app.viora.network.VtopGateway
import app.viora.security.CredentialVault
import app.viora.security.EncryptedPreferencesBlobStore
import app.viora.storage.VioraFileStore

class LocalAccountManager(
    private val context: Context,
    private val gateway: VtopGateway,
    private val credentials: CredentialVault,
    private val secureBlobs: EncryptedPreferencesBlobStore,
) {
    /**
     * Local logout by design. No VTOP server logout request is made, so other
     * browser/device sessions are not intentionally invalidated.
     */
    suspend fun eraseVioraAccount() {
        WorkManager.getInstance(context).cancelAllWorkByTag(VIORA_SYNC_TAG)
        gateway.clearLocalSession()
        credentials.clear()
        secureBlobs.clear()
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().clear().commit()
        VioraDatabase.closeAndForget()
        context.deleteDatabase("viora.db")
        VioraFileStore(context.filesDir).deleteAll()
        context.filesDir.resolve("course-materials").deleteRecursively()
        context.filesDir.resolve("shared").deleteRecursively()
    }

    companion object {
        const val VIORA_SYNC_TAG = "viora-academic-sync"
        const val SETTINGS = "viora_local_settings"
    }
}
