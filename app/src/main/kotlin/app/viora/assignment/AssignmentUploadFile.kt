package app.viora.assignment

import java.io.ByteArrayOutputStream
import java.io.InputStream

data class AssignmentUploadFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    companion object {
        const val MAX_BYTES: Int = 10 * 1024 * 1024

        fun read(
            input: InputStream,
            fileName: String,
            mimeType: String,
            advertisedMaxBytes: Long?,
        ): AssignmentUploadFile {
            val limit = minOf(advertisedMaxBytes?.coerceAtLeast(1L) ?: MAX_BYTES.toLong(), MAX_BYTES.toLong()).toInt()
            val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (total <= limit) {
                val remainingProbe = limit + 1 - total
                val read = input.read(buffer, 0, minOf(buffer.size, remainingProbe))
                if (read < 0) break
                total += read
                require(total <= limit) { "The selected assessment file is too large" }
                output.write(buffer, 0, read)
            }
            return AssignmentUploadFile(sanitizeFileName(fileName), mimeType, output.toByteArray())
        }
    }
}

internal fun sanitizeFileName(value: String): String {
    val leaf = value.substringAfterLast('/').substringAfterLast('\\')
    val safe = leaf.replace(Regex("[^A-Za-z0-9._-]+"), "_").trimStart('.').take(120)
    return safe.ifBlank { "assignment" }
}
