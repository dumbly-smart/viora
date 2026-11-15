package app.viora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.viora.database.SlotWithCourse
import app.viora.ui.VioraTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AcademicRulesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentExamPeriodShowsExamInsteadOfClassesInCustomHome() {
        val zone = ZoneId.of("Asia/Kolkata")
        val date = LocalDate.of(2026, 8, 17)
        val now = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val start = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val state = VioraUiState(
            slots = listOf(SlotWithCourse("slot", "course", "CSE1001", "Synthetic Class", "Faculty", 1, 540, 600, "Room", "Theory")),
            exams = listOf(ExamUi("exam", "CSE1001", "Synthetic Exam", "FAT", start, start + 7_200_000, "Room", "")),
        )
        compose.setReadyContent { VioraTheme { HomeScreen(state, {}, nowEpochMillis = now) } }
        compose.onNodeWithContentDescription("Next exam: Synthetic Exam", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next class:", substring = true).assertDoesNotExist()
    }
}
