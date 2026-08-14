package app.viora.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.viora.ui.VioraBlue
import app.viora.ui.VioraCoral

@Composable
fun SetupScreen(state: SetupState, onAction: (SetupAction) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("VIORA", style = MaterialTheme.typography.labelMedium, color = VioraBlue, fontFamily = FontFamily.Monospace)
                    Text("Your semester,\nwithout the chaos.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Timetable, attendance, exams and deadlines in one calm place.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Sign in to VTOP", style = MaterialTheme.typography.titleLarge)
                            Text("Use the same details you use on VTOP.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = { onAction(SetupAction.UsernameChanged(it)) },
                            label = { Text("Registration number") },
                            leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                            enabled = !state.loading,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { onAction(SetupAction.PasswordChanged(it)) },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.loading) {
                                    Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (passwordVisible) "Hide password" else "Show password")
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            enabled = !state.loading,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.rememberLogin,
                                onCheckedChange = { onAction(SetupAction.RememberLoginChanged(it)) },
                                enabled = !state.loading,
                            )
                            Text("Keep me signed in on this phone", modifier = Modifier.weight(1f))
                        }
                        state.error?.let { message ->
                            Surface(
                                color = VioraCoral.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, VioraCoral.copy(alpha = 0.28f)),
                            ) {
                                Text(message, color = VioraCoral, modifier = Modifier.fillMaxWidth().padding(12.dp))
                            }
                        }
                        Button(
                            onClick = { onAction(SetupAction.Submit) },
                            enabled = !state.loading && state.username.isNotBlank() && state.password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Talking to VTOP…")
                            } else Text("Continue  →")
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = VioraBlue.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, VioraBlue.copy(alpha = 0.18f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = VioraBlue.copy(alpha = 0.14f)) {
                            Icon(Icons.Outlined.Lock, null, Modifier.padding(9.dp).size(18.dp), tint = VioraBlue)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Stays on this device", style = MaterialTheme.typography.labelLarge)
                            Text("Encrypted locally and sent only to VTOP.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Unofficial. Built for students.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
