package app.viora.widget

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.domain.ExamWindow
import app.viora.domain.ExamCalendarDate
import app.viora.domain.AcademicClassCalendar
import java.time.ZonedDateTime

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
        val rules = AcademicClassCalendar(calendar.map {
            ExamCalendarDate(java.time.LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType)
        }, exams, now.zone)
        for (offset in 0..searchDays) {
            val date = now.toLocalDate().plusDays(offset)
            val day = rules.timetableDay(date) ?: continue
            val daySlots = slots.filter { it.dayOfWeek == day.value }.sortedBy(SlotWithCourse::startMinute)
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
}
