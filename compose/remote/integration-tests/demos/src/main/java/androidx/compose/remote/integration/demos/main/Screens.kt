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

package androidx.compose.remote.integration.demos.main

import androidx.compose.material3.Text
import androidx.compose.remote.integration.demos.layout.RemoteBoxAlignmentsDemo
import androidx.compose.remote.integration.demos.layout.StateLayoutSimpleDemo
import androidx.compose.remote.integration.demos.settings.SettingsScreen
import androidx.compose.runtime.Composable

private object ScreenKeys {
    const val REMOTE_BOX_ALIGNMENT = "RemoteBox alignment"
    const val STATE_LAYOUT = "StateLayout"
    const val SETTINGS = "Settings"
}

@Composable
fun ComposableScreenNavigation(key: String, onNavigateUp: () -> Unit) {
    when (key) {
        ScreenKeys.REMOTE_BOX_ALIGNMENT -> RemoteBoxAlignmentsDemo()
        ScreenKeys.STATE_LAYOUT -> StateLayoutSimpleDemo()
        ScreenKeys.SETTINGS -> SettingsScreen()
        else -> Text("Unknown screen: $key")
    }
}

val Screens =
    Category(
        key = "root",
        title = "Remote Compose Demos",
        screens =
            listOf(
                Category(
                    key = "layout_category",
                    title = "Layout",
                    screens =
                        listOf(
                            ComposableScreen(
                                key = ScreenKeys.REMOTE_BOX_ALIGNMENT,
                                title = "RemoteBox alignment",
                            ),
                            ComposableScreen(key = ScreenKeys.STATE_LAYOUT, title = "StateLayout"),
                        ),
                )
            ),
    )

val SettingsScreen = ComposableScreen(key = ScreenKeys.SETTINGS, title = "Settings")
