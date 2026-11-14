package app.viora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.viora.setup.SetupAction
import app.viora.setup.SetupScreen
import app.viora.setup.SetupState
import app.viora.ui.VioraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mobileEditorSignInKeepsCredentialLinesAccessible() {
        compose.setContent {
            VioraTheme {
                SetupScreen(SetupState()) {}
            }
        }

        compose.onAllNodesWithText("auth.viora")[0].assertIsDisplayed()
        compose.onAllNodesWithText("createVtopClient", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("VTOP username").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun manualCaptchaAppearsOnlyAsNativeFallbackAndSubmitsItsAnswer() {
        var state by mutableStateOf(
            SetupState(
                username = "SYNTHETIC",
                password = "not-a-real-password",
                captchaImageDataUri = ONE_PIXEL_PNG,
            ),
        )
        var submittedAnswer = ""
        compose.setContent {
            VioraTheme {
                SetupScreen(state) { action ->
                    when (action) {
                        is SetupAction.CaptchaAnswerChanged -> state = state.copy(captchaAnswer = action.value)
                        SetupAction.SubmitCaptcha -> submittedAnswer = state.captchaAnswer
                        else -> Unit
                    }
                }
            }
        }

        compose.onNodeWithText("// CAPTCHA fallback required by VTOP").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("VTOP CAPTCHA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("CAPTCHA").performScrollTo().performTextInput("ABC234")
        compose.onNodeWithContentDescription("Run sign in").performClick()

        compose.runOnIdle { assertEquals("ABC234", submittedAnswer) }
    }

    private companion object {
        const val ONE_PIXEL_PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
