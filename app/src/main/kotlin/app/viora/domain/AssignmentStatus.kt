package app.viora.domain

import java.util.Locale

fun isAssignmentSubmitted(status: String, lastUpload: String): Boolean {
    val combined = "$status $lastUpload".lowercase(Locale.ENGLISH)
    if (combined.contains("not upload") || combined.contains("not submit")) return false
    if (combined.contains("uploaded") || combined.contains("submitted")) return true
    val upload = lastUpload.trim().lowercase(Locale.ENGLISH)
    return upload.isNotBlank() && upload !in setOf("-", "--", "na", "n/a", "none")
}
