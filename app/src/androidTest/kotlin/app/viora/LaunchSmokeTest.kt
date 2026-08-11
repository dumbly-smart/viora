package app.viora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LaunchSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshInstallShowsLocalSetup() {
        compose.onNodeWithText("Welcome to Viora").assertIsDisplayed()
        compose.onNodeWithText("VTOP username").assertIsDisplayed()
        compose.onNodeWithText("VTOP password").assertIsDisplayed()
    }
}
