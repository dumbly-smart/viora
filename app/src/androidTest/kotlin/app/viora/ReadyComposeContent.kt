package app.viora

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Wait for the test activity's first semantics root on physical devices. */
internal fun ComposeContentTestRule.setReadyContent(content: @Composable () -> Unit) {
    setContent(content)
    waitUntil(10_000) {
        runCatching { onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
}
