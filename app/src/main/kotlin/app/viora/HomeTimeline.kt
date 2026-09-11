package app.viora

import app.viora.domain.isAssignmentSubmitted
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal enum class HomeTimelineKind { CLASS, ASSIGNMENT, EXAM }

internal enum class HomeAttentionKind { ATTENDANCE }

internal data class HomeTimelineItem(
    val id: String,
    val at: Long,
    val kind: HomeTimelineKind,
    val title: String,
    val subtitle: String,
    val status: String,
    val detailKind: String? = null,
    val detailId: String? = null,
    val location: String = "",
)

internal data class HomeAttentionItem(
    val id: String,
    val kind: HomeAttentionKind,
    val timelineKind: HomeTimelineKind,
    val title: String,
    val subtitle: String,
    val status: String,
    val accessibilityLabel: String,
    val detailKind: String,
    val detailId: String,
    val at: Long,
)

internal fun VioraUiState.homeTimeline(
    nowEpochMillis: Long,
    lookAheadDays: Long = 14,
): List<HomeTimelineItem> {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(academicZone)
    val horizonExclusive = now.toLocalDate().plusDays(lookAheadDays).atStartOfDay(academicZone).toInstant().toEpochMilli()
    val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    val classItems = (0L until lookAheadDays).flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset)
        slotsForDate(date).mapNotNull { slot ->
            val start = date.atStartOfDay(academicZone).plusMinutes(slot.startMinute.toLong())
            val end = date.atStartOfDay(academicZone).plusMinutes(slot.endMinute.toLong())
            if (end.toInstant().toEpochMilli() <= nowEpochMillis || start.toInstant().toEpochMilli() >= horizonExclusive) return@mapNotNull null
            HomeTimelineItem(
                id = "class:${date.toEpochDay()}:${slot.slotId}",
                at = start.toInstant().toEpochMilli(),
                kind = HomeTimelineKind.CLASS,
                title = slot.title.ifBlank { slot.code },
                subtitle = listOf(slot.code, slot.venue).filter(String::isNotBlank).joinToString(" · "),
                status = start.format(timeFormat),
                detailKind = "course",
                detailId = slot.code,
                location = slot.venue,
            )
        }
    }
    val assignmentItems = assignments.mapNotNull { assignment ->
        val due = assignment.dueEpochMillis ?: return@mapNotNull null
        if (due <= nowEpochMillis || due >= horizonExclusive || isAssignmentSubmitted(assignment.status, assignment.lastUpload)) return@mapNotNull null
        HomeTimelineItem(
            id = "assignment:${assignment.id}",
            at = due,
            kind = HomeTimelineKind.ASSIGNMENT,
            title = assignment.title,
            subtitle = assignment.courseTitle.ifBlank { assignment.courseCode },
            status = "Due ${Instant.ofEpochMilli(due).atZone(academicZone).format(timeFormat)}",
            detailKind = "assignment",
            detailId = assignment.id,
        )
    }
    val examItems = exams.mapNotNull { exam ->
        val ends = exam.endsEpochMillis ?: exam.startsEpochMillis
        if (ends <= nowEpochMillis || exam.startsEpochMillis >= horizonExclusive) return@mapNotNull null
        HomeTimelineItem(
            id = "exam:${exam.id}:${exam.startsEpochMillis}",
            at = exam.startsEpochMillis,
            kind = HomeTimelineKind.EXAM,
            title = exam.courseTitle.ifBlank { exam.courseCode },
            subtitle = listOf(exam.examType, exam.venue.takeIf(String::isNotBlank)?.let { "Room $it" }).filterNotNull().joinToString(" · "),
            status = Instant.ofEpochMilli(exam.startsEpochMillis).atZone(academicZone).format(timeFormat),
            detailKind = "exam",
            detailId = exam.id,
            location = exam.venue.takeIf(String::isNotBlank)?.let { "Room $it" }.orEmpty(),
        )
    }

    return (classItems + assignmentItems + examItems).sortedWith(compareBy(HomeTimelineItem::at, HomeTimelineItem::id))
}

internal fun VioraUiState.homeNeedsAttention(nowEpochMillis: Long): List<HomeAttentionItem> {
    val attendanceRisks = attendance.mapNotNull { item ->
        if (item.recovery <= 0 && item.percentage >= attendanceTarget) return@mapNotNull null
        val course = item.courseTitle.ifBlank { item.courseCode }
        val action = item.attendanceRecoveryCopy(attendanceTarget)
        val rounded = item.percentage.roundToInt()
        HomeAttentionItem(
            id = "attention:attendance:${item.id}",
            kind = HomeAttentionKind.ATTENDANCE,
            timelineKind = HomeTimelineKind.CLASS,
            title = course,
            subtitle = item.courseCode,
            status = "$rounded% attendance · $action",
            accessibilityLabel = "Attendance risk: $course is $rounded percent. $action",
            detailKind = "attendance",
            detailId = item.id,
            at = Long.MIN_VALUE + rounded,
        )
    }
    return attendanceRisks.sortedWith(compareBy(HomeAttentionItem::at, HomeAttentionItem::id))
}

internal fun List<HomeTimelineItem>.academicDates(): Set<LocalDate> =
    mapTo(linkedSetOf()) { Instant.ofEpochMilli(it.at).atZone(academicZone).toLocalDate() }

internal fun homeCalendarDayDescription(date: LocalDate, today: LocalDate, hasEvent: Boolean): String = buildString {
    append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)))
    if (date == today) append(", today")
    append(if (hasEvent) ", events scheduled" else ", no events scheduled")
}

private fun AttendanceUi.attendanceRecoveryCopy(target: Int): String {
    val meetings = if (blockSize > 1) recoveryBlocks else recovery
    if (meetings <= 0) return "Do not skip the next class."
    val unit = when {
        blockSize > 1 && meetings == 1 -> "lab class"
        blockSize > 1 -> "lab classes"
        meetings == 1 -> "class"
        else -> "classes"
    }
    return "Attend next $meetings $unit to reach $target percent."
}
