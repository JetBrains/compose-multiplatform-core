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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import org.jetbrains.compose.storytale.story

val `HorizontalDivider Story` by story {
    // Parameters for customization
    val thickness by parameter(1f)
    val useCustomColor by parameter(true)
    val customColor by parameter<Color>(MaterialTheme.colorScheme.primary)

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Content above divider", color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            modifier = Modifier.width(200.dp),
            thickness = thickness.dp,
            color = if (useCustomColor) customColor else Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Content below divider", color = MaterialTheme.colorScheme.onSurface)
    }
}

val `VerticalDivider Story` by story {
    // Parameters for customization
    val thickness by parameter(1f)
    val useCustomColor by parameter(true)
    val customColor by parameter(Color.Blue)

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Left content", color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(16.dp))

        VerticalDivider(
            modifier = Modifier.height(100.dp),
            thickness = thickness.dp,
            color = if (useCustomColor) customColor else DividerDefaults.color
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text("Right content", color = MaterialTheme.colorScheme.onSurface)
    }
}
