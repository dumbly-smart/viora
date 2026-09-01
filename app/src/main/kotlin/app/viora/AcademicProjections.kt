package app.viora

import java.util.Locale

data class MarkSectionUi(
    val courseCode: String,
    val courseTitle: String,
    val marks: List<MarkUi>,
)

internal fun List<MarkUi>.markSections(): List<MarkSectionUi> =
    groupBy { mark ->
        mark.courseCode.trim().takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT)
            ?: mark.courseTitle.trim()
    }
        .map { (key, rows) ->
            MarkSectionUi(
                courseCode = key,
                courseTitle = rows.first().courseTitle,
                marks = rows.sortedWith(compareBy<MarkUi> { assessmentRank(it.title) }.thenBy { it.title.lowercase(Locale.ROOT) }),
            )
        }
        .sortedBy { it.courseCode.lowercase(Locale.ROOT) }

private fun assessmentRank(title: String): Int = when (title.trim().lowercase(Locale.ROOT)) {
    "cat 1" -> 0
    "cat 2" -> 1
    else -> when {
        title.trim().startsWith("quiz", ignoreCase = true) -> 2
        title.trim().startsWith("assignment", ignoreCase = true) -> 3
        title.trim().equals("fat", ignoreCase = true) -> 4
        else -> 5
    }
}
