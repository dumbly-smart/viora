package app.viora.storage

import java.io.File

data class StoredVioraFile(val file: File, val bytes: Long)

class VioraFileStore(private val filesDir: File) {
    val root: File get() = File(filesDir, ROOT_DIRECTORY)

    fun writeMaterial(courseName: String, requestedName: String, bytes: ByteArray): StoredVioraFile {
        val courseDirectory = File(File(root, MATERIALS_DIRECTORY), safeName(courseName, "Course", 80))
        check(courseDirectory.exists() || courseDirectory.mkdirs()) { "Could not create Viora's material folder" }
        val target = uniqueFile(courseDirectory, safeName(requestedName, "material.bin", 120))
        val temporary = File(courseDirectory, ".${target.name}.partial")
        temporary.writeBytes(bytes)
        check(temporary.length() == bytes.size.toLong()) { "Could not verify the saved material" }
        check(temporary.renameTo(target)) { "Could not finish saving the material" }
        return StoredVioraFile(target, target.length())
    }

    fun migrateLegacyMaterial(
        source: File,
        courseName: String,
        requestedName: String,
    ): Result<StoredVioraFile> = runCatching {
        require(source.isFile) { "Legacy material is not a readable file" }
        migrateLegacyMaterial(source.readBytes(), courseName, requestedName, source::delete).getOrThrow()
    }

    fun migrateLegacyMaterial(
        bytes: ByteArray,
        courseName: String,
        requestedName: String,
        deleteSource: () -> Boolean,
    ): Result<StoredVioraFile> = runCatching {
        val saved = writeMaterial(courseName, requestedName, bytes)
        check(saved.file.readBytes().contentEquals(bytes)) { "Could not verify the migrated material" }
        if (!deleteSource()) {
            saved.file.delete()
            error("Could not remove the legacy material after migration")
        }
        saved
    }

    fun sharedFile(requestedName: String): File {
        val directory = File(root, SHARED_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Could not create Viora's sharing folder" }
        return File(directory, safeName(requestedName, "shared-file", 120))
    }

    fun calendarFile(requestedName: String): File {
        val directory = File(root, CALENDAR_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Could not create Viora's calendar folder" }
        val leafName = requestedName.substringAfterLast('/').substringAfterLast('\\')
        return File(directory, safeName(leafName, "viora-calendar.ics", 120))
    }

    fun deleteAll(): Boolean = !root.exists() || root.deleteRecursively()

    private fun safeName(value: String, fallback: String, limit: Int): String = value
        .replace(Regex("[\\x00-\\x1f\\x7f/\\\\:*?\"<>|]"), "_")
        .trim()
        .trim('.')
        .take(limit)
        .ifBlank { fallback }

    private fun uniqueFile(directory: File, requested: String): File {
        val initial = File(directory, requested)
        if (!initial.exists()) return initial
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        val stem = requested.removeSuffix(extension)
        return generateSequence(2) { it + 1 }
            .map { File(directory, "$stem ($it)$extension") }
            .first { !it.exists() }
    }

    companion object {
        const val ROOT_DIRECTORY = "Viora"
        const val MATERIALS_DIRECTORY = "materials"
        const val SHARED_DIRECTORY = "shared"
        const val CALENDAR_DIRECTORY = "calendar"
    }
}
