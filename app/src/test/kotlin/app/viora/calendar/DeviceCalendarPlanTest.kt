package app.viora.calendar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCalendarPlanTest {
    @Test
    fun `device plan keeps full detail and stable Viora ownership`() {
        val event = CalendarInterchangeEvent(
            uid = "viora:semester:class:1:slot",
            title = "CSE1001 · Algorithms",
            details = "Faculty: Synthetic Faculty",
            location = "AB1-201",
            startsAt = Instant.parse("2026-09-07T03:30:00Z"),
            endsAt = Instant.parse("2026-09-07T04:30:00Z"),
        )

        val plan = DeviceCalendarPlan.create(listOf(event))

        assertEquals("Viora", plan.accountName)
        assertEquals("Viora timetable", plan.displayName)
        assertEquals("Asia/Kolkata", plan.timeZone)
        assertEquals("viora://calendar/viora%3Asemester%3Aclass%3A1%3Aslot", plan.events.single().ownershipUri)
        assertEquals(event.title, plan.events.single().title)
        assertEquals(event.details, plan.events.single().details)
        assertEquals(event.location, plan.events.single().location)
        assertTrue(plan.events.single().endMillis > plan.events.single().startMillis)
    }
}
