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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `DateRangePicker Story` by story {
    val allowPastDates by parameter(false)

    Row(modifier = Modifier.sizeIn(maxWidth = 450.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DateRangePicker with Selectable Dates",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (allowPastDates) {
                        "Allows all dates."
                    } else {
                        "Allows today and future dates."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                val selectableDates = if (!allowPastDates) {
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            return true
                        }

                        override fun isSelectableYear(year: Int): Boolean {
                            val currentYear = 2025
                            return year >= currentYear
                        }
                    }
                } else {
                    DatePickerDefaults.AllDates
                }

                val dateRangePickerState = rememberDateRangePickerState(
                    selectableDates = selectableDates
                )

                CompositionLocalProvider(LocalDensity provides scaledStoryDensity()) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier.sizeIn(maxWidth = 450.dp).height(450.dp)
                    )
                }
            }
        }
    }
}
