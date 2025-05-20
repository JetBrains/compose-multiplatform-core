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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `RadioButton Story` by story {
    // Parameters for customization
    var selected by parameter(false)
    val useCustomColors by parameter(false)
    val selectedColor by parameter(Color.Black)
    val unselectedColor by parameter(Color.Gray)

    // Custom colors
    val colors = if (useCustomColors) {
        RadioButtonDefaults.colors(
            selectedColor = selectedColor,
            unselectedColor = unselectedColor
        )
    } else {
        RadioButtonDefaults.colors()
    }

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = { selected = !selected },
            colors = colors
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (selected) "Selected" else "Not selected",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(120.dp), // Fixed width to prevent movement
            color = MaterialTheme.colorScheme.primary
        )
    }
}
