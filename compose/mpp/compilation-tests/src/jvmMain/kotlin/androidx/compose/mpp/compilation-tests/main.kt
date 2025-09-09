package androidx.compose.mpp.`compilation-tests`

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "compilation-tests",
    ) {
        App()
    }
}