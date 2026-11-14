package app.viora.setup

data class SetupState(
    val username: String = "",
    val password: String = "",
    val rememberLogin: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val captchaImageDataUri: String? = null,
    val captchaAnswer: String = "",
)

sealed interface SetupAction {
    data class UsernameChanged(val value: String) : SetupAction
    data class PasswordChanged(val value: String) : SetupAction
    data class RememberLoginChanged(val value: Boolean) : SetupAction
    data class CaptchaAnswerChanged(val value: String) : SetupAction
    data object Submit : SetupAction
    data object SubmitCaptcha : SetupAction
}
