/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Icon Story` by story {
    // Parameters for the icon
    val iconSize by parameter(24f)
    val contentDescription by parameter<String?>("Favorite")

    // Choose which icon to display
    val iconType by parameter(0) // 0 = Favorite, 1 = Settings, 2 = Home, 3 = Star, 4 = Info

    // Select the icon based on the parameter
    val icon = when (iconType) {
        1 -> Icons.Filled.Settings
        2 -> Icons.Filled.Home
        3 -> Icons.Filled.Star
        4 -> Icons.Outlined.Info
        else -> Icons.Filled.Favorite
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(iconSize.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}
