package app.viora

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        val starts = today.atTime(9, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            exams = listOf(ExamUi("exam", "CSE1001", "Synthetic Course", "Exam", starts, null, "AB1", "")),
        )

        compose.setContent {
            VioraTheme {
                ScheduleScreen(state, {}, {}, { _, _ -> }, {})
            }
        }

        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Academic calendar").assertExists()
        compose.onNodeWithContentDescription("Previous month").assertExists()
        compose.onNodeWithContentDescription("Next month").assertExists()
        compose.onNodeWithText("Exam").assertExists()
    }

    @Test
    fun selectingADayShowsOnlyThatDatesEvents() {
        val selectedDate = LocalDate.of(2026, 8, 20)
        val starts = selectedDate.atTime(9, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val state = VioraUiState(
            exams = listOf(ExamUi("exam", "CSE1001", "Synthetic Course", "Exam", starts, null, "AB1", "")),
        )

        compose.setContent {
            VioraTheme {
                CalendarScreen(state, initialDate = LocalDate.of(2026, 8, 1))
            }
        }

        compose.onNodeWithContentDescription("Thursday, 20 August 2026, Exam").performClick()
        compose.onNodeWithText("Exam · CSE1001").assertExists()
    }
}
