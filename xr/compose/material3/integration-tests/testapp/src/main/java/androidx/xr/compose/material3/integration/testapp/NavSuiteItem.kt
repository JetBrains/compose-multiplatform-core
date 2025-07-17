/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.xr.compose.material3.integration.testapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavSuiteItem {
    HOME,
    SETTINGS,
}

val NavSuiteItem.label: String
    get() =
        when (this) {
            NavSuiteItem.HOME -> "Home"
            NavSuiteItem.SETTINGS -> "Settings"
        }

val NavSuiteItem.icon: ImageVector
    get() =
        when (this) {
            NavSuiteItem.HOME -> Icons.Default.Home
            NavSuiteItem.SETTINGS -> Icons.Default.Settings
        }
