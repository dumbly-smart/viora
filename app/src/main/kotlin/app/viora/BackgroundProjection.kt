package app.viora

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rebuild a screen's read-only projection when its inputs change. */
@Composable
internal fun <T : Any> rememberBackgroundProjection(vararg inputs: Any?, calculate: () -> T): T? {
    val result = remember(*inputs) { mutableStateOf<T?>(null) }
    LaunchedEffect(result) {
        result.value = withContext(Dispatchers.Default) { calculate() }
    }
    return result.value
}
