package app.viora.notifications

import app.viora.database.AcademicChangeEntity
import app.viora.database.AttendanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceNotificationPolicyTest {
    @Test
    fun `nine point rule cutoff is inclusive and missing cgpa is not eligible`() {
        assertFalse(AttendanceNotificationPolicy.hasNinePointRule(null))
        assertFalse(AttendanceNotificationPolicy.hasNinePointRule(8.999))
        assertTrue(AttendanceNotificationPolicy.hasNinePointRule(9.00))
        assertTrue(AttendanceNotificationPolicy.hasNinePointRule(9.01))
    }

    @Test
    fun `fifteen changes produce one bounded plan with every ledger key`() {
        val changes = (1..15).map { index ->
            AcademicChangeEntity("attendance:$index", "attendance", "Attendance updated", "Course $index: $index/20", index.toLong())
        }

        val plan = requireNotNull(
            AttendanceNotificationPolicy.plan("semester", 9.0, emptyList(), changes, emptySet()),
        )

        assertEquals("Attendance updated", plan.title)
        assertEquals("15 courses updated", plan.summary)
        assertEquals(15, plan.ledgerKeys.size)
        assertEquals(6, plan.expandedLines.size)
        assertEquals("+10 more", plan.expandedLines.last())
        assertFalse(plan.summary.contains("75%"))
    }

    @Test
    fun `below cutoff includes unpublished threshold warnings in the same plan`() {
        val attendance = listOf(attendance("low", 7, 10), attendance("safe", 8, 10))
        val change = AcademicChangeEntity("attendance:low:7:10", "attendance", "Attendance updated", "Low course: 7/10", 1)

        val plan = requireNotNull(
            AttendanceNotificationPolicy.plan("semester", 8.99, attendance, listOf(change), emptySet()),
        )

        assertEquals("1 course updated · 1 below 75%", plan.summary)
        assertTrue(plan.ledgerKeys.any { it.startsWith("attendance-warning:semester:low") })
    }

    @Test
    fun `qualifying cgpa omits warnings but keeps ordinary changes`() {
        val attendance = listOf(attendance("low", 1, 10))
        val change = AcademicChangeEntity("attendance:low:1:10", "attendance", "Attendance updated", "Low course: 1/10", 1)

        val plan = requireNotNull(
            AttendanceNotificationPolicy.plan("semester", 9.0, attendance, listOf(change), emptySet()),
        )

        assertEquals(listOf("change:${change.id}"), plan.ledgerKeys)
        assertFalse(plan.summary.contains("75%"))
    }

    @Test
    fun `already published state without changes produces no plan`() {
        val attendance = attendance("low", 7, 10)
        val warningKey = "attendance-warning:semester:low:7:10:75"

        val plan = AttendanceNotificationPolicy.plan("semester", null, listOf(attendance), emptyList(), setOf(warningKey))

        assertNull(plan)
    }

    private fun attendance(id: String, attended: Int, held: Int) = AttendanceEntity(
        semesterId = "semester",
        id = id,
        courseCode = id.uppercase(),
        courseTitle = "$id course",
        courseType = "Theory",
        faculty = "Faculty",
        attended = attended,
        held = held,
        sourceEpochMillis = 0,
    )
}
