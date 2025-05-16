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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story


@OptIn(ExperimentalMaterial3Api::class)
val `TimeInput Story` by story {
    // Always use 24-hour format
    val is24Hour = true

    // State for time picker
    val timePickerState = rememberTimePickerState(
        initialHour = 15, // Use a value > 12 to test 24-hour format
        initialMinute = 4,
        is24Hour = is24Hour
    )

    // Format selected time for display
    fun formatTime(hour: Int, minute: Int): String {
        val hourFormatted = hour.toString().padStart(2, '0')
        val minuteFormatted = minute.toString().padStart(2, '0')
        return "$hourFormatted:$minuteFormatted"
    }

    val formattedTime = formatTime(timePickerState.hour, timePickerState.minute)

    Surface(modifier = Modifier.width(280.dp).padding(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display the current time
            Text(
                text = "Selected time: $formattedTime",
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show TimeInput component
            TimeInput(
                state = timePickerState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `TimePicker Story` by story {
    val is24Hour = true

    // Layout type selection
    val layoutOptions = listOf("Vertical", "Horizontal")
    val layoutTypeIndex by parameter(layoutOptions, 0)
    val layoutTypeIndexInt = layoutOptions.indexOf(layoutTypeIndex)

    // State for time picker
    val timePickerState = rememberTimePickerState(
        initialHour = 15, // Use a value > 12 to test 24-hour format
        initialMinute = 4,
        is24Hour = is24Hour
    )

    // Format selected time for display
    fun formatTime(hour: Int, minute: Int): String {
        val hourFormatted = hour.toString().padStart(2, '0')
        val minuteFormatted = minute.toString().padStart(2, '0')
        return "$hourFormatted:$minuteFormatted"
    }

    val formattedTime = formatTime(timePickerState.hour, timePickerState.minute)

    // Determine layout type
    val layoutType = when (layoutTypeIndexInt) {
        1 -> TimePickerLayoutType.Horizontal
        else -> TimePickerLayoutType.Vertical
    }

    CompositionLocalProvider(LocalDensity provides scaledStoryDensity()) {
        Surface(modifier = Modifier.padding(16.dp).width(600.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Display the current time
                Text(
                    text = "Selected time: $formattedTime",
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Show TimePicker component
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    layoutType = layoutType
                )
            }
        }
    }
}
