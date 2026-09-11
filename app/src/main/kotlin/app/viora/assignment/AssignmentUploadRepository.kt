package app.viora.assignment

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.viora.data.DigitalAssignmentRepository
import app.viora.network.VtopGateway
import app.viora.network.accepts
import app.viora.network.requireAssignmentUploadLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AssignmentUploadRepository(
    private val contentResolver: ContentResolver,
    private val gateway: VtopGateway,
    private val assignments: DigitalAssignmentRepository,
) {
    suspend fun upload(semesterId: String, assignmentId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val locator = requireAssignmentUploadLocator(gateway.digitalAssignments(semesterId), assignmentId)
            val mimeType = contentResolver.getType(uri)?.trim().orEmpty().ifBlank { "application/octet-stream" }
            val fileName = displayName(uri)
            require(locator.accepts(mimeType, fileName)) { "VTOP does not accept this file type for the assessment" }
            val upload = contentResolver.openInputStream(uri)?.use { input ->
                AssignmentUploadFile.read(input, fileName, mimeType, locator.maxBytes)
            } ?: throw IOException("The selected assessment file could not be opened")
            gateway.uploadDigitalAssignment(semesterId, assignmentId, upload.fileName, upload.mimeType, upload.bytes)
            assignments.refresh(semesterId).getOrThrow()
        }
    }

    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return queried?.takeIf(String::isNotBlank) ?: uri.lastPathSegment.orEmpty().ifBlank { "assignment" }
    }
}
