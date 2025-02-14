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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `ListItem Story` by story {
    val headlineText by parameter("One-line list item")
    
    // Leading content options
    val showLeadingContent by parameter(true)
    val leadingIconOptions = listOf("Star", "Face", "Favorite", "Info")
    val leadingIconType by parameter(leadingIconOptions, 0)
    
    Column(modifier = Modifier.padding(8.dp).width(200.dp)) {
        ListItem(
            headlineContent = { Text(headlineText) },
            leadingContent = if (showLeadingContent) {
                {
                    Icon(
                        when (leadingIconType) {
                            "Face" -> Icons.Filled.Face
                            "Favorite" -> Icons.Filled.Favorite
                            "Info" -> Icons.Filled.Info
                            else -> Icons.Filled.Star
                        },
                        contentDescription = "$leadingIconType Icon"
                    )
                }
            } else null
        )
    }
}
