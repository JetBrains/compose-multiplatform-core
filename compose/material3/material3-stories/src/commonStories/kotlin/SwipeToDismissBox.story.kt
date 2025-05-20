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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.storytale.story

val `SwipeToDismissBox Story` by story {
    // Direction parameters
    val dismissFromStartToEnd by parameter(true)
    val dismissFromEndToStart by parameter(true)

    // Background colors
    val startToEndColor by parameter(Color(0xFF1B5E20))
    val endToStartColor by parameter(Color.Red)

    // Content parameters
    val contentText by parameter("Swipe me left or right")

    // State
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    // Reset state function
    fun resetState() {
        scope.launch {
            state.reset()
        }
    }

    Column(
        modifier = Modifier.padding(16.dp).width(300.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "SwipeToDismissBox Demonstration",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Status display
        val currentDirection = state.dismissDirection
        val directionText = when (currentDirection) {
            SwipeToDismissBoxValue.StartToEnd -> "Swiping Start to End"
            SwipeToDismissBoxValue.EndToStart -> "Swiping End to Start"
            SwipeToDismissBoxValue.Settled -> "Settled"
        }

        // Fixed height container for status text to prevent layout shifts
        Box(modifier = Modifier.height(24.dp)) {
            Text(
                "Current state: $directionText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // The SwipeToDismissBox component with fixed height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp) // Fixed height for the SwipeToDismissBox container
        ) {
            SwipeToDismissBox(
                state = state,
                backgroundContent = {
                    val direction = state.dismissDirection
                    // Only show the green background with checkmark for StartToEnd direction
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        // Use Card to match the main content's styling
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = startToEndColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                        }
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        // Use Card to match the main content's styling for EndToStart direction
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = endToStartColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        // Transparent background when settled
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                enableDismissFromStartToEnd = dismissFromStartToEnd,
                enableDismissFromEndToStart = dismissFromEndToStart,
                content = {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = contentText,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            )
        }

        // Reset button - always reserve space for it to prevent layout shifts
        Box(
            modifier = Modifier
                .height(48.dp) // Fixed height for button container
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled) {
                androidx.compose.material3.Button(
                    onClick = { resetState() },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Configuration information
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(
                "Configuration:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Start to End: ${if (dismissFromStartToEnd) "Enabled" else "Disabled"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "End to Start: ${if (dismissFromEndToStart) "Enabled" else "Disabled"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
