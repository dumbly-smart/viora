package app.viora.widget

import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
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
    ): NextClass? {
        for (offset in 0..searchDays) {
            val date = now.toLocalDate().plusDays(offset)
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
        val exception = calendar.firstOrNull { it.dateEpochDay == epochDay }
        val description = listOfNotNull(exception?.title, exception?.dayType).joinToString(" ")
        if (description.contains("holiday", true) || description.contains("exam day", true)) {
            return emptyList()
        }
        val order = DayOfWeek.entries.firstOrNull { day ->
            description.contains("${day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true)
        }
        return slots.filter { it.dayOfWeek == (order ?: normalDay).value }
            .sortedBy(SlotWithCourse::startMinute)
    }
}
