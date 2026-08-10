package app.viora.share

import app.viora.database.SlotWithCourse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableQrPayloadTest {
    @Test fun `payload is ordered compact and scanner readable`() {
        val slots = listOf(slot("late", 2, 600), slot("early", 1, 540))
        val payload = TimetableQrPayload.encode("Fall | Semester", slots)
        assertTrue(payload.startsWith("VIORA TIMETABLE | Fall / Semester"))
        assertTrue(payload.indexOf("MON|09:00") < payload.indexOf("TUE|10:00"))
        assertTrue(payload.length <= 2000)
    }
    private fun slot(id: String, day: Int, start: Int) = SlotWithCourse(id, id, "RED1001", "Redacted Course", "Faculty", day, start, start + 50, "AB-1", "THEORY")
}
