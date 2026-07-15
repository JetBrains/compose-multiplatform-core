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

package androidx.compose.desktop.examples.frameconsistency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runApplicationBlocking
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Entry point of the frame-consistency sample (see [FrameConsistencyApp]). The isolation
 * mode comes from `-Dcompose.frameIsolation`, which the Gradle run tasks set:
 * `runFrameConsistency` (off) and `runFrameConsistencyIsolated` (on).
 *
 * The [MapDataSource] is handed in ONCE, at launch, as the application-wide
 * [DataSourceContext] — windows no longer take sources. `-Dframeconsistency.windows=2`
 * opens two windows SHARING the source: an external commit's invalidations are released
 * only once every pinning window has rotated (the cooperative wake-up), while each window
 * still renders at its own pace.
 */
fun main() {
    val isolationEnabled = System.getProperty("compose.frameIsolation").toBoolean()
    val windows = System.getProperty("frameconsistency.windows")?.toIntOrNull() ?: 1
    println("[frame-consistency] compose.frameIsolation=$isolationEnabled windows=$windows")
    val mapSource = MapDataSource<String, Long>()
    runApplicationBlocking(
        identifier = System.getProperty("kdt.application.identifier")
            ?: "compose-frame-consistency",
        dataSourceContext = DataSourceContext(mapSource),
    ) {
        repeat(windows) { index ->
            AppWindow(isolationEnabled, index + 1, mapSource)
        }
    }
}

@Composable
private fun AppWindow(
    isolationEnabled: Boolean,
    index: Int,
    mapSource: MapDataSource<String, Long>,
) {
    var isWindowShown by remember { mutableStateOf(true) }
    // App-level read-tracking probe: this read happens in the APPLICATION composition,
    // outside any window scene. It only ever updates because the launch-time context makes
    // app-level source reads observable — before that, this value froze at first composition.
    val appLevelLeft = mapSource.read("left") ?: 0L
    if (isWindowShown) {
        Window(
            onCloseRequested = { _ ->
                isWindowShown = false
            },
            configure = {
                title =
                    (if (isolationEnabled) "Frame consistency - ISOLATION ON"
                    else "Frame consistency - ISOLATION OFF") + " - w$index"
                // Tall enough for the banner, the stats block, all probe rows, the
                // probe host, and the interactive strip without scrolling or clipping.
                requestSize(DpSize(780.dp, 580.dp))
            },
        ) {
            FrameConsistencyApp(
                isolationEnabled,
                mapSource,
                logPrefix = "w$index",
                appLevelLeft = appLevelLeft,
            )
        }
    }
}
