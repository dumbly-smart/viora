package app.viora

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.viora.database.AcademicCalendarEntity
import app.viora.database.ImportedCalendarEventEntity
import app.viora.database.SlotWithCourse
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import app.viora.network.SemesterOption
import java.time.LocalDate
import java.time.ZoneId

class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scheduleCalendarShowsTheSelectedDatesExam() {
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val state = markerState(today)

        compose.setContent {
            VioraTheme {
                ScheduleScreen(state, {}, { _, _ -> }, {})
            }
        }

        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Academic calendar").assertExists()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText(today.plusMonths(1).month.name.lowercase().replaceFirstChar(Char::titlecase), substring = true).assertExists()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText(today.month.name.lowercase().replaceFirstChar(Char::titlecase), substring = true).assertExists()
        compose.onNodeWithText("Exam · CSE1001").assertExists()
        compose.onNodeWithText("Timetable").performClick()
        compose.onNodeWithContentDescription("Share timetable QR").assertIsEnabled()
    }

    @Test
    fun selectingADayShowsOnlyThatDatesEvents() {
        val selectedDate = LocalDate.of(2026, 8, 20)
        val state = markerState(selectedDate)

        compose.setContent {
            VioraTheme {
                CalendarScreen(state, initialDate = LocalDate.of(2026, 8, 1))
            }
        }

        val selectedDay = compose.onAllNodesWithContentDescription(
            "Thursday, 20 August 2026, Exam, Assignment, Class, Calendar",
        ).assertCountEquals(1)[0]
        selectedDay.performClick()
        selectedDay.assertIsSelected()
        compose.onNodeWithText("Exam · CSE1001").assertExists()

        val holiday = compose.onAllNodesWithContentDescription(
            "Friday, 21 August 2026, Holiday",
        ).assertCountEquals(1)[0]
        holiday.performClick()
        holiday.assertIsSelected()
        compose.onAllNodesWithText("Class").assertCountEquals(1)
    }

    @Test
    fun calendarShowsEveryMarkerInTheLegendAndDayCell() {
        val markedDate = LocalDate.of(2026, 8, 20)
        compose.setContent {
            VioraTheme {
                CalendarScreen(markerState(markedDate), initialDate = LocalDate.of(2026, 8, 1))
            }
        }

        listOf("Holiday", "Exam", "Assignment", "Class", "Day order", "Calendar").forEach { label ->
            compose.onAllNodesWithText(label)[0].assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Calendar marker: $label", useUnmergedTree = true)[0].assertIsDisplayed()
        }
    }

    @Test
    fun scheduleOffersCalendarInterchangeAndLabelsImportedEvents() {
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val start = today.atTime(14, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = markerState(today).copy(
            importedCalendarEvents = listOf(
                ImportedCalendarEventEntity("imported", "Imported class", "Details", "Room 1", start, start + 3_600_000),
            ),
            calendarInterchangeMessage = "Imported 1 event",
        )

        compose.setContent {
            VioraTheme {
                ScheduleScreen(state, {}, { _, _ -> }, {})
            }
        }

        listOf("Export to Viora calendar", "Export ICS", "Import ICS", "Share timetable").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Imported class").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Imported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Imported 1 event").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun scheduleShowsCurrentTimetableBeforeInterchangeWithoutSemesterChips() {
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val state = markerState(today).copy(
            semesters = listOf(SemesterOption("current", "Fall 2026"), SemesterOption("previous", "Winter 2026")),
            activeSemester = SemesterOption("current", "Fall 2026"),
        )

        compose.setContent { VioraTheme { ScheduleScreen(state, {}, { _, _ -> }, {}) } }

        compose.onNodeWithText("Fall 2026").assertDoesNotExist()
        compose.onNodeWithText("Winter 2026").assertDoesNotExist()
        val timetableTop = compose.onNodeWithText("Synthetic Course").performScrollTo().fetchSemanticsNode().boundsInRoot.top
        val exportTop = compose.onNodeWithText("Export to Viora calendar").performScrollTo().fetchSemanticsNode().boundsInRoot.top
        assertTrue(timetableTop < exportTop)
    }

    private fun markerState(date: LocalDate): VioraUiState {
        val holidayDate = date.plusDays(1)
        val dayOrderDate = date.plusDays(2)
        val starts = date.atTime(9, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        return VioraUiState(
            slots = listOf(
                SlotWithCourse("slot", "course", "CSE1001", "Synthetic Course", "Faculty", date.dayOfWeek.value, 8 * 60, 9 * 60, "AB1", "Theory"),
                SlotWithCourse("holiday-slot", "course", "CSE1001", "Synthetic Course", "Faculty", holidayDate.dayOfWeek.value, 8 * 60, 9 * 60, "AB1", "Theory"),
            ),
            calendar = listOf(
                AcademicCalendarEntity("semester", "calendar", date.toEpochDay(), "Academic event", "Event", 0),
                AcademicCalendarEntity("semester", "holiday", holidayDate.toEpochDay(), "Holiday", "Holiday", 0),
                AcademicCalendarEntity("semester", "day-order", dayOrderDate.toEpochDay(), "${date.dayOfWeek.name.lowercase().replaceFirstChar(Char::titlecase)} order", "", 0),
            ),
            exams = listOf(ExamUi("exam", "CSE1001", "Synthetic Course", "Exam", starts, null, "AB1", "")),
            assignments = listOf(AssignmentUi("assignment", "CSE1001", "Synthetic assignment", starts, "Open")),
        )
    }
}
