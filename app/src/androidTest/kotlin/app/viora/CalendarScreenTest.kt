package app.viora

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.viora.database.AcademicCalendarEntity
import app.viora.database.SlotWithCourse
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
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
                ScheduleScreen(state, {}, {}, { _, _ -> }, {})
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
        compose.onNodeWithContentDescription("Share timetable").assertIsEnabled()
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

        val selectedDay = compose.onNodeWithContentDescription(
            "Thursday, 20 August 2026, Holiday, Exam, Assignment, Class, Day order, Calendar",
        )
        selectedDay.performClick()
        selectedDay.assertIsSelected()
        compose.onNodeWithText("Exam · CSE1001").assertExists()
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
            compose.onNodeWithText(label).assertExists()
            compose.onNodeWithContentDescription("Calendar marker: $label", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun markerState(date: LocalDate): VioraUiState {
        val starts = date.atTime(9, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        return VioraUiState(
            slots = listOf(SlotWithCourse("slot", "course", "CSE1001", "Synthetic Course", "Faculty", date.dayOfWeek.value, 8 * 60, 9 * 60, "AB1", "Theory")),
            calendar = listOf(
                AcademicCalendarEntity("semester", "holiday", date.toEpochDay(), "Holiday", "Holiday", 0),
                AcademicCalendarEntity("semester", "day-order", date.toEpochDay(), "Monday order", "", 0),
                AcademicCalendarEntity("semester", "calendar", date.toEpochDay(), "Academic event", "Event", 0),
            ),
            exams = listOf(ExamUi("exam", "CSE1001", "Synthetic Course", "Exam", starts, null, "AB1", "")),
            assignments = listOf(AssignmentUi("assignment", "CSE1001", "Synthetic assignment", starts, "Open")),
        )
    }
}
