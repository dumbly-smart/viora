package app.viora.widget

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.domain.ExamWindow
import app.viora.domain.ExamCalendarDate
import app.viora.domain.examSuppressionWindows
import app.viora.domain.suppresses
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

data class NextClass(
    val slot: SlotWithCourse,
    val startsAt: ZonedDateTime,
    val happeningNow: Boolean,
)

object NextClassResolver {
    fun resolve(
        slots: List<SlotWithCourse>,
        calendar: List<AcademicCalendarEntity>,
        now: ZonedDateTime,
        searchDays: Long = 7,
        exams: List<ExamWindow> = emptyList(),
    ): NextClass? {
        val suppression = examSuppressionWindows(exams, calendar.map {
            ExamCalendarDate(java.time.LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType)
        }, now.zone)
        for (offset in 0..searchDays) {
            val date = now.toLocalDate().plusDays(offset)
            if (suppression.any { it.suppresses(date) }) continue
            val daySlots = slotsForDate(slots, calendar, date.toEpochDay(), date.dayOfWeek)
            for (slot in daySlots) {
                val start = date.atStartOfDay(now.zone).plusMinutes(slot.startMinute.toLong())
                val end = date.atStartOfDay(now.zone).plusMinutes(slot.endMinute.toLong())
                if (now.isBefore(end)) {
                    return NextClass(slot, start, !now.isBefore(start))
                }
            }
        }
        return null
    }

    private fun slotsForDate(
        slots: List<SlotWithCourse>,
        calendar: List<AcademicCalendarEntity>,
        epochDay: Long,
        normalDay: DayOfWeek,
    ): List<SlotWithCourse> {
        val description = calendar.filter { it.dateEpochDay == epochDay }.joinToString(" ") { "${it.title} ${it.dayType}" }
        if (description.contains("holiday", true) || description.contains("no instructional day", true) || description.contains("exam day", true)) {
            return emptyList()
        }
        val order = DayOfWeek.entries.firstOrNull { day ->
            description.contains("${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true)
        }
        return slots.filter { it.dayOfWeek == (order ?: normalDay).value }
            .sortedBy(SlotWithCourse::startMinute)
    }
}
