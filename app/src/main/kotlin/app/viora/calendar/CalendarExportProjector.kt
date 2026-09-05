package app.viora.calendar

import app.viora.AssignmentUi
import app.viora.ExamUi
import app.viora.VioraUiState
import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.slotsForDate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

object CalendarExportProjector {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val examFallback = Duration.ofHours(2)
    private val deadlineDuration = Duration.ofMinutes(30)

    fun project(
        semesterId: String,
        fromDate: LocalDate,
        slots: List<SlotWithCourse>,
        calendar: List<AcademicCalendarEntity>,
        exams: List<ExamUi>,
        assignments: List<AssignmentUi>,
    ): List<CalendarInterchangeEvent> {
        val state = VioraUiState(slots = slots, calendar = calendar, exams = exams, assignments = assignments)
        val endDate = fromDate.plusDays(179)
        val semesterKey = stablePart(semesterId)
        val events = mutableListOf<CalendarInterchangeEvent>()
        var date = fromDate
        while (!date.isAfter(endDate)) {
            state.slotsForDate(date).forEach { slot ->
                if (slot.startMinute !in 0 until 24 * 60 || slot.endMinute <= slot.startMinute || slot.endMinute > 24 * 60) return@forEach
                val start = date.atStartOfDay(zone).plusMinutes(slot.startMinute.toLong()).toInstant()
                val end = date.atStartOfDay(zone).plusMinutes(slot.endMinute.toLong()).toInstant()
                events += CalendarInterchangeEvent(
                    uid = "viora:$semesterKey:class:${date.toEpochDay()}:${stablePart(slot.slotId)}",
                    title = listOf(slot.code, slot.title).filter(String::isNotBlank).joinToString(" · "),
                    details = listOfNotNull(
                        slot.faculty.takeIf(String::isNotBlank)?.let { "Faculty: $it" },
                        slot.type.takeIf(String::isNotBlank)?.let { "Type: $it" },
                    ).joinToString("\n"),
                    location = slot.venue,
                    startsAt = start,
                    endsAt = end,
                )
            }
            date = date.plusDays(1)
        }
        exams.forEach { exam ->
            val start = java.time.Instant.ofEpochMilli(exam.startsEpochMillis)
            val localDate = start.atZone(zone).toLocalDate()
            if (localDate in fromDate..endDate) {
                val suppliedEnd = exam.endsEpochMillis?.let(java.time.Instant::ofEpochMilli)
                events += CalendarInterchangeEvent(
                    uid = "viora:$semesterKey:exam:${stablePart(exam.id)}",
                    title = listOf(exam.examType, exam.courseCode, exam.courseTitle).filter(String::isNotBlank).joinToString(" · "),
                    details = listOfNotNull(
                        exam.seatNumber.takeIf(String::isNotBlank)?.let { "Seat $it" },
                        exam.courseTitle.takeIf(String::isNotBlank),
                    ).distinct().joinToString("\n"),
                    location = exam.venue,
                    startsAt = start,
                    endsAt = suppliedEnd?.takeIf { it.isAfter(start) } ?: start.plus(examFallback),
                )
            }
        }
        assignments.forEach { assignment ->
            val due = assignment.dueEpochMillis?.let(java.time.Instant::ofEpochMilli) ?: return@forEach
            val localDate = due.atZone(zone).toLocalDate()
            if (localDate in fromDate..endDate) {
                events += CalendarInterchangeEvent(
                    uid = "viora:$semesterKey:assignment:${stablePart(assignment.id)}",
                    title = listOf(assignment.title, assignment.courseTitle).filter(String::isNotBlank).joinToString(" · "),
                    details = listOf(assignment.courseCode, assignment.status).filter(String::isNotBlank).joinToString("\n"),
                    startsAt = due,
                    endsAt = due.plus(deadlineDuration),
                )
            }
        }
        return events.sortedWith(compareBy<CalendarInterchangeEvent> { it.startsAt }.thenBy { it.uid })
    }

    private fun stablePart(value: String): String = value
        .trim()
        .lowercase()
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .trim('-')
        .ifBlank { "item" }
}
