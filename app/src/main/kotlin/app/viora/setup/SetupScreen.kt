package app.viora.setup

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.viora.ui.VioraBlue
import app.viora.ui.VioraCanvas
import app.viora.ui.VioraCoral
import app.viora.ui.VioraSurface
import app.viora.ui.VioraSurfaceHigh

@Composable
fun SetupScreen(state: SetupState, onAction: (SetupAction) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    val captchaImage = remember(state.captchaImageDataUri) {
        state.captchaImageDataUri?.substringAfter("base64,", "")
            ?.takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() }
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?.asImageBitmap()
    }
    val canSubmit = !state.loading && state.username.isNotBlank() && state.password.isNotBlank() &&
        (state.captchaImageDataUri == null || state.captchaAnswer.length == 6)
    val submit = {
        onAction(if (state.captchaImageDataUri == null) SetupAction.Submit else SetupAction.SubmitCaptcha)
    }

    Surface(Modifier.fillMaxSize(), color = VioraCanvas, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            EditorCommandBar(canSubmit, state.loading, submit)
            EditorTab()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("// VTOP credentials — stored encrypted on this device", modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                EditorCodeLine("1", "import { createVtopClient } from \"@viora/vtop\";", VioraBlue)
                EditorCodeLine("2", "")
                EditorCodeLine("3", "type Dashboard = { profile: Profile; timeline: Event[] };", MaterialTheme.colorScheme.onSurfaceVariant)
                EditorCodeLine("4", "")
                EditorCodeLine("5", "const vtop = createVtopClient({", VioraBlue)
                EditorCodeLine("6", "  host: \"vtop.vit.ac.in\",", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("7", "  persistSession: true,", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("8", "  timezone: \"Asia/Kolkata\",", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("9", "});", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("10", "")
                EditorCodeLine("11", "export async function loadDashboard(): Promise<Dashboard> {", VioraBlue)
                EditorCodeLine("12", "  const session = await vtop.authenticate({", VioraBlue)
                CodeEditorInput(
                    line = "13",
                    prefix = "    username: \"",
                    value = state.username,
                    onValueChange = { onAction(SetupAction.UsernameChanged(it)) },
                    placeholder = "VTOP username",
                    suffix = "\",",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    enabled = !state.loading,
                )
                CodeEditorInput(
                    line = "14",
                    prefix = "    password: \"",
                    value = state.password,
                    onValueChange = { onAction(SetupAction.PasswordChanged(it)) },
                    placeholder = "Password",
                    suffix = "\",",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    enabled = !state.loading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.loading) {
                            Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (passwordVisible) "Hide password" else "Show password")
                        }
                    },
                )
                EditorCodeLine("15", "  });", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("16", "")
                EditorCodeLine("17", "  const [profile, timetable, assessments] = await Promise.all([", VioraBlue)
                EditorCodeLine("18", "    vtop.profile(session),", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("19", "    vtop.timetable(session),", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("20", "    vtop.assessments(session),", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("21", "  ]);", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("22", "")
                EditorCodeLine("23", "  return {", VioraBlue)
                EditorCodeLine("24", "    profile,", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("25", "    timeline: timetable.events,", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("26", "    assessments,", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("27", "  };", MaterialTheme.colorScheme.onSurface)
                EditorCodeLine("28", "}", MaterialTheme.colorScheme.onSurface)
                if (state.captchaImageDataUri != null) {
                    Text("// CAPTCHA fallback required by VTOP", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = VioraCoral, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    captchaImage?.let { image ->
                        Image(bitmap = image, contentDescription = "VTOP CAPTCHA", modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 18.dp))
                    }
                    CodeEditorInput(
                        line = "30",
                        prefix = "const captcha = \"",
                        value = state.captchaAnswer,
                        onValueChange = { value -> onAction(SetupAction.CaptchaAnswerChanged(value.uppercase().filter(Char::isLetterOrDigit).take(6))) },
                        placeholder = "CAPTCHA",
                        suffix = "\";",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        enabled = !state.loading,
                    )
                }
                state.error?.let { error ->
                    Text("// error: $error", modifier = Modifier.padding(horizontal = 18.dp), color = VioraCoral, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
            EditorStatusBar(state.rememberLogin, !state.loading) { onAction(SetupAction.RememberLoginChanged(it)) }
        }
    }
}

@Composable
private fun EditorCommandBar(canSubmit: Boolean, loading: Boolean, submit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☰", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        Text("auth.viora", modifier = Modifier.weight(1f).padding(start = 18.dp), style = MaterialTheme.typography.headlineMedium)
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = VioraBlue)
        } else {
            Text(
                "▶",
                modifier = Modifier.padding(12.dp).clickable(enabled = canSubmit, onClick = submit).semantics { contentDescription = "Run sign in" },
                color = if (canSubmit) VioraBlue else MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text("⋮", modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun EditorTab() {
    Surface(color = VioraSurfaceHigh) {
        Row(Modifier.fillMaxWidth().height(42.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("auth.viora", color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.fillMaxWidth().height(2.dp).background(VioraBlue))
}

@Composable
private fun CodeEditorInput(
    line: String,
    prefix: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suffix: String,
    keyboardOptions: KeyboardOptions,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val codeStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line, modifier = Modifier.width(30.dp), style = codeStyle, color = MaterialTheme.colorScheme.outline)
        Text(prefix, style = codeStyle, color = VioraBlue)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            enabled = enabled,
            singleLine = true,
            textStyle = codeStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(VioraBlue),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = codeStyle, color = MaterialTheme.colorScheme.outline)
                    innerTextField()
                }
            },
        )
        Text(suffix, style = codeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.invoke()
    }
}

@Composable
private fun EditorCodeLine(line: String, code: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text(line, modifier = Modifier.width(30.dp), color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        Text(code, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditorStatusBar(rememberLogin: Boolean, enabled: Boolean, onRememberChange: (Boolean) -> Unit) {
    Surface(color = VioraSurfaceHigh) {
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ln 14, Col 1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Checkbox(checked = rememberLogin, onCheckedChange = onRememberChange, enabled = enabled, modifier = Modifier.size(30.dp))
            Text("remember", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(14.dp))
            Text("UTF-8", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
        }
    }
}
