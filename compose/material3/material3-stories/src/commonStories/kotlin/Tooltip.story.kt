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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.storytale.story


@OptIn(ExperimentalMaterial3Api::class)
val `PlainTooltip Story` by story {
    // Basic parameters
    val tooltipText by parameter("Tooltip text")
    val showCaret by parameter(false)

    // State management
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    // Simple implementation
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tooltip implementation
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    caretSize = if (showCaret) DpSize(16.dp, 8.dp) else DpSize.Unspecified
                ) {
                    Text(tooltipText)
                }
            },
            state = tooltipState
        ) {
            IconButton(onClick = { /* Icon button's click event */ }) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `TooltipBox Story` by story {
    // Basic parameters
    val tooltipText by parameter("Tooltip text")
    val showCaret by parameter(false)

    // State management
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tooltip implementation
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip(
                    caretSize = if (showCaret) DpSize(16.dp, 8.dp) else DpSize.Unspecified
                ) {
                    Text(tooltipText)
                }
            },
            state = tooltipState
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        tooltipState.show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Show Tooltip",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `RichTooltip Story` by story {
    // Basic parameters
    val tooltipTitle by parameter("Tooltip Title")
    val tooltipText by parameter("This is a rich tooltip with more detailed information.")
    val showCaret by parameter(false)

    // State management
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    // Simple implementation
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tooltip implementation
        TooltipBox(
            positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
            tooltip = {
                RichTooltip(
                    title = { Text(tooltipTitle) },
                    caretSize = if (showCaret) TooltipDefaults.caretSize else DpSize.Unspecified
                ) {
                    Text(tooltipText)
                }
            },
            state = tooltipState
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        tooltipState.show()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Information",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
