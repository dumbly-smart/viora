package app.viora

import app.viora.domain.isAssignmentSubmitted
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class HomeTimelineKind { CLASS, ASSIGNMENT, EXAM }

internal data class HomeTimelineItem(
    val id: String,
    val at: Long,
    val kind: HomeTimelineKind,
    val title: String,
    val subtitle: String,
    val status: String,
    val detailKind: String? = null,
    val detailId: String? = null,
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
        )
    }

    return (classItems + assignmentItems + examItems).sortedWith(compareBy(HomeTimelineItem::at, HomeTimelineItem::id))
}

internal fun List<HomeTimelineItem>.academicDates(): Set<LocalDate> =
    mapTo(linkedSetOf()) { Instant.ofEpochMilli(it.at).atZone(academicZone).toLocalDate() }

internal fun homeCalendarDayDescription(date: LocalDate, today: LocalDate, hasEvent: Boolean): String = buildString {
    append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)))
    if (date == today) append(", today")
    append(if (hasEvent) ", events scheduled" else ", no events scheduled")
}
