package app.viora.notifications

import app.viora.database.AcademicCalendarEntity
import app.viora.database.ExamEntity
import app.viora.database.SlotWithCourse
import app.viora.domain.overlapsExam
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class ReminderPlan(
    val id: String,
    val triggerEpochMillis: Long,
    val channel: String,
    val title: String,
    val text: String,
    val destination: String,
)

object ReminderPlanner {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun create(
        slots: List<SlotWithCourse>,
        exams: List<ExamEntity>,
        calendar: List<AcademicCalendarEntity>,
        now: ZonedDateTime,
        includeExamReminders: Boolean,
        classLookAheadDays: Long = 8,
    ): List<ReminderPlan> {
        val nowMillis = now.toInstant().toEpochMilli()
        val classPlans = (0..classLookAheadDays).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            val description = calendar.firstOrNull { it.dateEpochDay == date.toEpochDay() }
                ?.let { "${it.title} ${it.dayType}" }
                .orEmpty()
            if (description.contains("holiday", true)) return@flatMap emptyList()
            val dayOrder = DayOfWeek.entries.firstOrNull {
                description.contains("${it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} order", true)
            } ?: date.dayOfWeek
            val dateExams = exams.filter {
                it.startsEpochMillis.toZoned(now).toLocalDate() == date
            }
            slots.filter { slot ->
                slot.dayOfWeek == dayOrder.value && dateExams.none { exam ->
                    val examStart = exam.startsEpochMillis.toZoned(now)
                    val examTime = examStart.toLocalTime()
                    val examStartMinute = examTime.hour * 60 + examTime.minute
                    val examEndMinute = exam.endsEpochMillis?.let { end ->
                        examStartMinute + Duration.between(examStart, end.toZoned(now)).toMinutes().toInt()
                    }
                    overlapsExam(slot.startMinute, slot.endMinute, examStartMinute, examEndMinute)
                }
            }.mapNotNull { slot ->
                val starts = date.atStartOfDay(now.zone).plusMinutes(slot.startMinute.toLong())
                val trigger = starts.minusMinutes(10).toInstant().toEpochMilli()
                if (trigger <= nowMillis) return@mapNotNull null
                ReminderPlan(
                    id = "class:${date.toEpochDay()}:${slot.slotId}:${starts.toInstant().toEpochMilli()}",
                    triggerEpochMillis = trigger,
                    channel = VioraNotifications.CLASSES,
                    title = "Class in 10 minutes",
                    text = details(starts, slot.code, slot.venue),
                    destination = "schedule",
                )
            }
        }
        val examPlans = if (!includeExamReminders) emptyList() else exams.flatMap { exam ->
            val starts = exam.startsEpochMillis.toZoned(now)
            listOf(
                12L * 60 to "12 hours",
                40L to "40 minutes",
            ).mapNotNull { (minutes, label) ->
                val trigger = starts.minusMinutes(minutes).toInstant().toEpochMilli()
                if (trigger <= nowMillis) return@mapNotNull null
                ReminderPlan(
                    id = "exam:${exam.id}:${exam.startsEpochMillis}:$minutes",
                    triggerEpochMillis = trigger,
                    channel = VioraNotifications.EXAMS,
                    title = "${exam.examType} in $label",
                    text = details(starts, exam.courseCode, exam.venue),
                    destination = "schedule",
                )
            }
        }
        return (classPlans + examPlans).sortedBy(ReminderPlan::triggerEpochMillis)
    }

    private fun details(starts: ZonedDateTime, course: String, venue: String): String =
        listOf(starts.format(timeFormatter), course, venue.takeIf(String::isNotBlank)?.let { "Room $it" })
            .filterNotNull()
            .joinToString(" · ")

    private fun Long.toZoned(reference: ZonedDateTime): ZonedDateTime =
        java.time.Instant.ofEpochMilli(this).atZone(reference.zone)
}
