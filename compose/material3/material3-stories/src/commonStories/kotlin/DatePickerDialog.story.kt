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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `DatePickerDialog Story` by story {
    // State to control whether the dialog is shown
    var showDialog by remember { mutableStateOf(false) }

    // State to store the selected date
    var selectedDate by remember { mutableStateOf<Long?>(null) }

    // Simple function to convert timestamp to readable format
    fun formatDate(timestamp: Long?): String {
        if (timestamp == null) return "No date selected"

        // Calculate days since epoch (Jan 1, 1970)
        val daysSinceEpoch = timestamp / (1000 * 60 * 60 * 24)

        // Calculate year, month and day
        var remainingDays = daysSinceEpoch
        var year = 1970

        // Account for leap years
        while (true) {
            val daysInYear = if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 366 else 365
            if (remainingDays < daysInYear) break
            remainingDays -= daysInYear
            year++
        }

        // Determine month and day
        val daysInMonth = arrayOf(31, if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 0

        while (month < 12) {
            if (remainingDays < daysInMonth[month]) break
            remainingDays -= daysInMonth[month]
            month++
        }

        val day = remainingDays.toInt() + 1
        month += 1  // Adjust month to be 1-based

        // Month names
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", 
                                "July", "August", "September", "October", "November", "December")

        return "${monthNames[month-1]} $day, $year"
    }

    Surface(
        modifier = Modifier.width(400.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DatePickerDialog Demo",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The DatePickerDialog component displays a DatePicker in a modal dialog.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Button to show the dialog
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Date Picker Dialog", color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display the selected date in a human-readable format
            Text(
                text = "Selected date: ${formatDate(selectedDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Show the DatePickerDialog when showDialog is true
            if (showDialog) {
                val datePickerState = rememberDatePickerState()

                DatePickerDialog(
                    onDismissRequest = { showDialog = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedDate = datePickerState.selectedDateMillis
                                showDialog = false
                            }
                        ) {
                            Text("OK", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDialog = false }
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        // Make DatePicker smaller
                        modifier = Modifier.sizeIn(maxWidth = 350.dp)
                    )
                }
            }
        }
    }
}
