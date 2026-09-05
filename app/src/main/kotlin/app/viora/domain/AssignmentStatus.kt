package app.viora.domain

import java.util.Locale

private enum class SubmissionSignal { POSITIVE, NEGATIVE, UNKNOWN }

fun isAssignmentSubmitted(status: String, lastUpload: String): Boolean = when (signal(status)) {
    SubmissionSignal.POSITIVE -> true
    SubmissionSignal.NEGATIVE -> signal(lastUpload) == SubmissionSignal.POSITIVE || hasUploadValue(lastUpload)
    SubmissionSignal.UNKNOWN -> signal(lastUpload) == SubmissionSignal.POSITIVE || hasUploadValue(lastUpload)
}

private fun signal(value: String): SubmissionSignal {
    val normalized = value.trim().lowercase(Locale.ENGLISH).replace(Regex("\\s+"), " ")
    if (normalized.contains("not uploaded") || normalized.contains("not submitted") ||
        normalized.contains("not received") || normalized == "pending" || normalized == "missing" || normalized == "no"
    ) return SubmissionSignal.NEGATIVE
    if (normalized.contains("submitted") || normalized.contains("uploaded") ||
        normalized.contains("upload successful") || normalized.contains("completed") ||
        normalized.contains("received") || normalized == "yes"
    ) return SubmissionSignal.POSITIVE
    return SubmissionSignal.UNKNOWN
}

private fun hasUploadValue(value: String): Boolean {
    val normalized = value.trim().lowercase(Locale.ENGLISH).replace(Regex("\\s+"), " ")
    return signal(value) != SubmissionSignal.NEGATIVE && normalized.isNotBlank() && normalized !in setOf(
        "-", "--", "na", "n/a", "none", "file not uploaded", "not uploaded",
    )
}
