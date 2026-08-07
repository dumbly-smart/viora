package app.viora.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.viora.database.CourseMaterialEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CourseMaterialManager(private val context: Context, private val gateway: VtopGateway) {
    suspend fun open(material: CourseMaterialEntity, share: Boolean): Result<Unit> = runCatching {
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, "course-materials").apply { mkdirs() }
            val safe = material.fileName.ifBlank { material.title }.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "material-${material.id}.bin" }
            val target = File(directory, "${material.id.take(24)}-$safe")
            if (!target.exists()) target.writeBytes(gateway.downloadCourseMaterial(material.downloadPath))
            target
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            type = mime(file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (share) putExtra(Intent.EXTRA_STREAM, uri) else setDataAndType(uri, type)
        }
        context.startActivity(if (share) Intent.createChooser(intent, "Share course material").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
    }
    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) { "pdf" -> "application/pdf"; "ppt", "pptx" -> "application/vnd.ms-powerpoint"; "doc", "docx" -> "application/msword"; "xls", "xlsx" -> "application/vnd.ms-excel"; else -> "application/octet-stream" }
}
