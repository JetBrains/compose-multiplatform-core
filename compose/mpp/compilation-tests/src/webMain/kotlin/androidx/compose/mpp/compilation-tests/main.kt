package androidx.compose.mpp.`compilation-tests`

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport { App() }