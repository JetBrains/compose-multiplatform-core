/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.tooling.recomposition.RecompositionInspector.Companion.recomposer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val openWindows = HashMap<RecompositionInspector, Window>()

/**
 * Opens (or brings to front) the window showing [RecompositionInspectorPanel] for this inspector,
 * starting the inspector if needed. Closing the window stops the inspector again.
 *
 * Call on the application's UI thread, outside any composition and frame transaction, in the
 * coroutine context the application's own actions run in: the window's content composes
 * synchronously here and needs that context.
 */
@OptIn(InternalComposeUiApi::class, ComposeToolingApi::class)
fun RecompositionInspector.openInspectorWindow(): Window {
    val session = window.inspection?.session
        ?: error("The inspected window does not expose its application session")
    synchronized(openWindows) {
        openWindows[this]?.let { existing ->
            existing.requestFocusAndBringToFront()
            return existing
        }
    }
    val inspector = this
    if (!isStarted) start()
    lateinit var created: Window
    created = application.createWindow(session) {
        synchronized(openWindows) { openWindows.remove(inspector) }
        // Delivered inside the window's frame transaction, where the scene cannot be disposed yet.
        application.invokeOnUiThread {
            created.dispose()
            inspector.close()
        }
    }
    created.title = "Recomposition Inspector — ${inspector.window.title}"
    created.requestSize(DpSize(1100.dp, 720.dp))
    created.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
        application.withCompositionLocal {
            RecompositionInspectorPanel(inspector)
        }
    }
    synchronized(openWindows) { openWindows[inspector] = created }
    created.requestFocusAndBringToFront()
    // Resilient mode swallows composition failures into the error state; print them.
    created.inspection?.frameRecomposer?.recomposer?.let { recomposer ->
        CoroutineScope(recomposer.effectCoroutineContext).launch {
            recomposer.asRecomposerInfo().errorState.collect { error ->
                if (error != null) {
                    System.err.println("Recomposition inspector window failed to compose: ${error.cause}")
                    error.cause.printStackTrace()
                }
            }
        }
    }
    return created
}

/** The inspector whose [openInspectorWindow] created [window], or `null` for any other window. */
internal fun ownerOfInspectorWindow(window: Window): RecompositionInspector? =
    synchronized(openWindows) { openWindows.entries.firstOrNull { it.value === window }?.key }

/** Closes the inspector window, if open, and stops the inspector. Call on the UI thread. */
fun RecompositionInspector.closeInspectorWindow() {
    val window = synchronized(openWindows) { openWindows.remove(this) } ?: return
    window.dispose()
    close()
}
