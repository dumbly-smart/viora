package app.viora.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.viora.database.CourseMaterialEntity
import app.viora.network.VtopGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class MaterialDownloadState(val materialId: String, val status: String, val attempt: Int = 0, val localBytes: Long = 0, val error: String? = null)
private data class DownloadedMaterial(val uri: Uri, val name: String, val bytes: Long)

class CourseMaterialManager(private val context: Context, private val gateway: VtopGateway) {
    private val mutableStates = MutableStateFlow<Map<String, MaterialDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, MaterialDownloadState>> = mutableStates.asStateFlow()
    private val downloads = context.getSharedPreferences("viora_material_downloads", Context.MODE_PRIVATE)

    suspend fun open(material: CourseMaterialEntity, courseName: String, share: Boolean): Result<Unit> = runCatching {
        val downloaded = downloadInternal(material, courseName).getOrThrow()
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            type = mime(downloaded.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (share) putExtra(Intent.EXTRA_STREAM, downloaded.uri) else setDataAndType(downloaded.uri, type)
        }
        context.startActivity(if (share) Intent.createChooser(intent, "Share course material").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
    }

    suspend fun download(material: CourseMaterialEntity, courseName: String): Result<Long> =
        downloadInternal(material, courseName).map(DownloadedMaterial::bytes)

    private suspend fun downloadInternal(material: CourseMaterialEntity, courseName: String): Result<DownloadedMaterial> = runCatching {
        withContext(Dispatchers.IO) {
            existing(material.id)?.let {
                update(MaterialDownloadState(material.id, "READY", localBytes = it.bytes))
                return@withContext it
            }
            val safeCourse = safeName(courseName, material.courseCode.ifBlank { "Course" }, 80)
            var last: Throwable? = null
            repeat(3) { attempt ->
                update(MaterialDownloadState(material.id, "DOWNLOADING", attempt + 1))
                try {
                    val bytes = gateway.downloadCourseMaterial(material.downloadPath)
                    val requestedName = safeName(material.fileName.ifBlank { material.title }, "material-${material.id}", 120)
                    val safeFile = ensureExtension(requestedName, bytes)
                    val saved = save(material.id, safeCourse, safeFile, bytes)
                    update(MaterialDownloadState(material.id, "READY", attempt + 1, saved.bytes))
                    return@withContext saved
                } catch (error: Throwable) {
                    last = error
                    if (attempt < 2) delay(500L * (attempt + 1))
                }
            }
            update(MaterialDownloadState(material.id, "ERROR", 3, error = "Download failed after 3 attempts"))
            throw last ?: IllegalStateException("Download failed")
        }
    }

    private fun save(materialId: String, course: String, fileName: String, bytes: ByteArray): DownloadedMaterial {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/$course")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "Android could not create Downloads/$ROOT/$course"
            }
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("Android could not open the downloaded file")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                remember(materialId, uri.toString(), fileName, bytes.size.toLong())
                DownloadedMaterial(uri, fileName, bytes.size.toLong())
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT/$course")
            check(directory.exists() || directory.mkdirs()) { "Android could not create Downloads/$ROOT/$course" }
            val target = uniqueFile(directory, fileName)
            target.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
            remember(materialId, "file:${target.absolutePath}", target.name, target.length())
            DownloadedMaterial(uri, target.name, target.length())
        }
    }

    private fun existing(materialId: String): DownloadedMaterial? {
        val stored = downloads.getString("$materialId.uri", null) ?: return null
        val name = downloads.getString("$materialId.name", null) ?: return null
        return if (stored.startsWith("file:")) {
            val file = File(stored.removePrefix("file:"))
            if (!file.isFile) null else DownloadedMaterial(
                FileProvider.getUriForFile(context, "${context.packageName}.files", file),
                file.name,
                file.length(),
            )
        } else {
            val uri = Uri.parse(stored)
            val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
                ?.takeIf { it >= 0 } ?: return null
            DownloadedMaterial(uri, name, size)
        }
    }

    suspend fun clearDownloads(): Long = withContext(Dispatchers.IO) {
        val ids = downloads.all.keys.filter { it.endsWith(".uri") }.map { it.substringBeforeLast('.') }.distinct()
        var removed = 0L
        ids.forEach { id ->
            existing(id)?.let { item ->
                removed += item.bytes
                val stored = downloads.getString("$id.uri", null).orEmpty()
                if (stored.startsWith("file:")) File(stored.removePrefix("file:")).delete()
                else context.contentResolver.delete(item.uri, null, null)
            }
        }
        downloads.edit().clear().apply()
        mutableStates.value = emptyMap()
        removed
    }

    fun storageBytes(): Long = downloads.all.keys.asSequence()
        .filter { it.endsWith(".bytes") }
        .sumOf { downloads.getLong(it, 0L) }

    private fun remember(id: String, uri: String, name: String, bytes: Long) {
        downloads.edit().putString("$id.uri", uri).putString("$id.name", name).putLong("$id.bytes", bytes).apply()
    }
    private fun update(state: MaterialDownloadState) { mutableStates.value = mutableStates.value + (state.materialId to state) }
    private fun safeName(value: String, fallback: String, limit: Int): String = value
        .replace(Regex("[\\x00-\\x1f\\x7f/\\\\:*?\"<>|]"), "_").trim().trim('.').take(limit).ifBlank { fallback }
    private fun uniqueFile(directory: File, requested: String): File {
        val initial = File(directory, requested)
        if (!initial.exists()) return initial
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        val stem = requested.removeSuffix(extension)
        return generateSequence(2) { it + 1 }.map { File(directory, "$stem ($it)$extension") }.first { !it.exists() }
    }
    private fun mime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private fun ensureExtension(name: String, bytes: ByteArray): String {
        if (name.substringAfterLast('.', "").lowercase() in KNOWN_EXTENSIONS) return name
        val extension = when {
            bytes.size >= 5 && bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray()) -> "pdf"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "png"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> "zip"
            else -> "bin"
        }
        return "$name.$extension"
    }

    private companion object {
        const val ROOT = "Viora-VIT"
        val KNOWN_EXTENSIONS = setOf("pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "zip", "jpg", "jpeg", "png", "mp4")
    }
}
