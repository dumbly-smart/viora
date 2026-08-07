package app.viora.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.viora.database.CourseMaterialEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import java.io.File

data class MaterialDownloadState(val materialId: String, val status: String, val attempt: Int = 0, val localBytes: Long = 0, val error: String? = null)

class CourseMaterialManager(private val context: Context, private val gateway: VtopGateway) {
    private val mutableStates = MutableStateFlow<Map<String, MaterialDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, MaterialDownloadState>> = mutableStates.asStateFlow()
    private val directory get() = File(context.filesDir, "course-materials")

    suspend fun open(material: CourseMaterialEntity, share: Boolean): Result<Unit> = runCatching {
        val file = download(material).getOrThrow()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            type = mime(file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (share) putExtra(Intent.EXTRA_STREAM, uri) else setDataAndType(uri, type)
        }
        context.startActivity(if (share) Intent.createChooser(intent, "Share course material").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
    }
    suspend fun download(material: CourseMaterialEntity): Result<File> = runCatching {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val safe = material.fileName.ifBlank { material.title }.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "material-${material.id}.bin" }
            val target = File(directory, "${material.id.take(24)}-$safe")
            if (target.exists()) { update(MaterialDownloadState(material.id, "READY", localBytes = target.length())); return@withContext target }
            var last: Throwable? = null
            repeat(3) { attempt ->
                update(MaterialDownloadState(material.id, "DOWNLOADING", attempt + 1))
                try {
                    val bytes = gateway.downloadCourseMaterial(material.downloadPath)
                    target.writeBytes(bytes); update(MaterialDownloadState(material.id, "READY", attempt + 1, target.length())); return@withContext target
                } catch (error: Throwable) { last = error; if (attempt < 2) delay(500L * (attempt + 1)) }
            }
            update(MaterialDownloadState(material.id, "ERROR", 3, error = "Download failed after 3 attempts")); throw last ?: IllegalStateException("Download failed")
        }
    }
    suspend fun clearDownloads(): Long = withContext(Dispatchers.IO) { val bytes = storageBytes(); directory.listFiles()?.forEach(File::delete); mutableStates.value = emptyMap(); bytes }
    fun storageBytes(): Long = directory.listFiles()?.sumOf(File::length) ?: 0L
    private fun update(state: MaterialDownloadState) { mutableStates.value = mutableStates.value + (state.materialId to state) }
    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) { "pdf" -> "application/pdf"; "ppt", "pptx" -> "application/vnd.ms-powerpoint"; "doc", "docx" -> "application/msword"; "xls", "xlsx" -> "application/vnd.ms-excel"; else -> "application/octet-stream" }
}
