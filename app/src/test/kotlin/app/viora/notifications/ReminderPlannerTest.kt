package app.viora.notifications

import app.viora.database.ExamEntity
import app.viora.database.DigitalAssignmentEntity
import app.viora.database.SlotWithCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 8, 17, 8, 0, 0, 0, zone)

    @Test fun `class reminder fires ten minutes before with twelve hour time course and room`() {
        val plans = ReminderPlanner.create(
            slots = listOf(slot(startMinute = 10 * 60)),
            exams = emptyList(),
            calendar = emptyList(),
            now = now,
            includeExamReminders = true,
        )

        val reminder = plans.first()
        assertEquals(now.withHour(9).withMinute(50).toInstant().toEpochMilli(), reminder.triggerEpochMillis)
        assertEquals("Class in 10 minutes", reminder.title)
        assertEquals("10:00 AM · CSE1001 · Room AB-101", reminder.text)
    }

    @Test fun `exam receives twelve hour and forty minute reminders`() {
        val starts = now.plusDays(1).withHour(14).withMinute(0)
        val plans = ReminderPlanner.create(
            slots = emptyList(),
            exams = listOf(exam(starts)),
            calendar = emptyList(),
            now = now,
            includeExamReminders = true,
        )

        assertEquals(listOf("CAT 1 in 12 hours", "CAT 1 in 40 minutes"), plans.map { it.title })
        assertTrue(plans.all { it.text == "2:00 PM · CSE1001 · Room AB-101" })
    }

    @Test fun `disabled exam reminders do not affect class reminders`() {
        val plans = ReminderPlanner.create(
            slots = listOf(slot(startMinute = 10 * 60)),
            exams = listOf(exam(now.plusDays(1))),
            calendar = emptyList(),
            now = now,
            includeExamReminders = false,
        )

        assertTrue(plans.any { it.channel == VioraNotifications.CLASSES })
        assertFalse(plans.any { it.channel == VioraNotifications.EXAMS })
    }

    @Test fun `recurring class gets a separate reminder for every occurrence in the rolling window`() {
        val plans = ReminderPlanner.create(
            slots = listOf(slot(startMinute = 10 * 60)),
            exams = emptyList(),
            calendar = emptyList(),
            now = now,
            includeExamReminders = true,
            classLookAheadDays = 14,
        )

        assertEquals(3, plans.size)
        assertEquals(3, plans.map { it.id }.distinct().size)
        assertTrue(plans.all { it.title == "Class in 10 minutes" })
    }

    @Test fun `class reminders are skipped during exam dates`() {
        val plans = ReminderPlanner.create(
            slots = listOf(slot(startMinute = 10 * 60)),
            exams = listOf(exam(now.withHour(9))),
            calendar = emptyList(),
            now = now,
            includeExamReminders = true,
            classLookAheadDays = 14,
        )

        assertEquals(2, plans.count { it.channel == VioraNotifications.CLASSES })
    }

    @Test fun `unsubmitted assignment gets three exact reminders`() {
        val due = now.plusDays(3).withHour(23).withMinute(59)
        val plans = ReminderPlanner.create(
            slots = emptyList(), exams = emptyList(), assignments = listOf(assignment(due, "Not uploaded")),
            calendar = emptyList(), now = now, includeExamReminders = true,
        )

        assertEquals(listOf("DA due in 2 days", "DA due today", "DA due tonight"), plans.map { it.title })
        assertEquals(listOf(9, 9, 22), plans.map { java.time.Instant.ofEpochMilli(it.triggerEpochMillis).atZone(zone).hour })
    }

    @Test fun `submitted assignment gets only ten pm confirmation`() {
        val plans = ReminderPlanner.create(
            slots = emptyList(), exams = emptyList(), assignments = listOf(assignment(now.plusDays(1), "Uploaded")),
            calendar = emptyList(), now = now, includeExamReminders = true,
        )

        assertEquals(listOf("DA submitted"), plans.map { it.title })
        assertEquals(22, java.time.Instant.ofEpochMilli(plans.single().triggerEpochMillis).atZone(zone).hour)
    }

    private fun slot(startMinute: Int) = SlotWithCourse(
        slotId = "slot",
        courseId = "course",
        code = "CSE1001",
        title = "Course",
        faculty = "Faculty",
        dayOfWeek = 1,
        startMinute = startMinute,
        endMinute = startMinute + 50,
        venue = "AB-101",
        type = "Theory",
    )

    private fun exam(starts: ZonedDateTime) = ExamEntity(
        semesterId = "semester",
        id = "exam",
        courseCode = "CSE1001",
        courseTitle = "Course",
        examType = "CAT 1",
        startsEpochMillis = starts.toInstant().toEpochMilli(),
        endsEpochMillis = starts.plusMinutes(90).toInstant().toEpochMilli(),
        venue = "AB-101",
        seatNumber = "42",
        sourceEpochMillis = 0,
    )

    private fun assignment(due: ZonedDateTime, upload: String) = DigitalAssignmentEntity(
        "semester", "assignment", "CSE1001", "Course", "DA 1", due.toInstant().toEpochMilli(), upload, "Open", 0,
    )
}
