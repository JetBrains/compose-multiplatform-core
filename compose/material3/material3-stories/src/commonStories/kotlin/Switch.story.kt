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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

// Basic Switch story
val `Switch Story` by story {
    // Parameters for customization
    var checked by parameter(false)
    val enabled by parameter(true)

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            enabled = enabled
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Switch with custom colors
val `Colored Switch Story` by story {
    // Parameters for customization
    var checked by parameter(false)
    val enabled by parameter(true)
    val useCustomColors by parameter(true)
    val checkedThumbColor by parameter(Color.Green)
    val checkedTrackColor by parameter(Color(0xFF90EE90)) // Light green
    val uncheckedThumbColor by parameter(Color.Red)
    val uncheckedTrackColor by parameter(Color(0xFFFFCCCB)) // Light red

    // Custom colors
    val colors = if (useCustomColors) {
        SwitchDefaults.colors(
            checkedThumbColor = checkedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedThumbColor = uncheckedThumbColor,
            uncheckedTrackColor = uncheckedTrackColor
        )
    } else {
        SwitchDefaults.colors()
    }

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            enabled = enabled,
            colors = colors
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Switch with thumb content (icons)
val `Switch With Icons Story` by story {
    // Parameters for customization
    var checked by parameter(false)
    val enabled by parameter(true)

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            enabled = enabled,
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Checked",
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Unchecked",
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
