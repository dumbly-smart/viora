package app.viora.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.viora.network.SessionState
import app.viora.network.VtopGateway
import app.viora.security.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(
    private val gateway: VtopGateway,
    private val credentials: CredentialStore,
    private val onAuthenticated: () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = mutableState.asStateFlow()

    fun onAction(action: SetupAction) {
        when (action) {
            is SetupAction.UsernameChanged -> mutableState.update { it.copy(username = action.value, error = null) }
            is SetupAction.PasswordChanged -> mutableState.update { it.copy(password = action.value, error = null) }
            is SetupAction.RememberLoginChanged -> mutableState.update { it.copy(rememberLogin = action.value) }
            SetupAction.Submit -> submit()
        }
    }

    private fun submit() {
        val snapshot = state.value
        if (snapshot.username.isBlank() || snapshot.password.isBlank()) {
            mutableState.update { it.copy(error = "Enter your VTOP username and password") }
            return
        }
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val password = snapshot.password.toCharArray()
            try {
                when (gateway.login(snapshot.username.trim(), password)) {
                    SessionState.Active -> {
                        if (snapshot.rememberLogin) credentials.save(snapshot.username.trim(), password)
                        else credentials.clear()
                        mutableState.update { it.copy(password = "", loading = false) }
                        onAuthenticated()
                    }
                    SessionState.VerificationRequired -> mutableState.update {
                        it.copy(loading = false, error = "VTOP requires verification before setup can continue")
                    }
                    SessionState.Missing -> mutableState.update {
                        it.copy(loading = false, error = "VTOP rejected the sign-in details")
                    }
                }
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "Could not connect to VTOP") }
            } finally {
                password.fill('\u0000')
            }
        }
    }
}
