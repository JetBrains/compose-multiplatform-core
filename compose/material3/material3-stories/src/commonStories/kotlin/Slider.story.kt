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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

val `Slider Story` by story {
    // Parameters for customization
    var sliderValue by parameter(0.5f)
    val enabled by parameter(true)
    val useCustomColors by parameter(true)
    val activeTrackColor by parameter(Color.Black)
    val inactiveTrackColor by parameter(Color.LightGray)
    val thumbColor by parameter(Color.Blue)

    // Custom colors
    val colors = if (useCustomColors) {
        SliderDefaults.colors(
            thumbColor = thumbColor,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor
        )
    } else {
        SliderDefaults.colors()
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Slider(
            modifier = Modifier.width(200.dp),
            value = sliderValue,
            onValueChange = { sliderValue = it },
            enabled = enabled,
            colors = colors
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current value: ${(sliderValue * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

val `RangeSlider Story` by story {
    // Parameters for customization
    val startValue by parameter(0.2f)
    val endValue by parameter(0.8f)
    val enabled by parameter(true)

    // State to track range slider values
    var sliderPosition by remember(startValue, endValue) { mutableStateOf(startValue..endValue) }

    Column(modifier = Modifier.padding(16.dp)) {
        RangeSlider(
            modifier = Modifier.width(200.dp),
            value = sliderPosition,
            onValueChange = { range -> sliderPosition = range },
            valueRange = 0f..1f,
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Selected range: ${(sliderPosition.start * 100).toInt()}% - ${(sliderPosition.endInclusive * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
