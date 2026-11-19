package app.viora.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AgentSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<AgentActivity>()

    @Test fun openAiDefaultsToLunaLowAndProvidersAreSelectable() {
        compose.onNodeWithText("Viora Agent").assertIsDisplayed()
        compose.onNodeWithText("gpt-6-luna").assertIsDisplayed()
        compose.onNodeWithText("Low").assertIsDisplayed()
        compose.onNodeWithText("OpenAI").performClick()
        compose.onNodeWithText("Gemini").performClick()
        compose.onNodeWithText("gemini-4-hydrogen").assertIsDisplayed()
    }
}
