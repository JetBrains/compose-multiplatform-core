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

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.launch
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `TooltipBox Story` by story {
    // Common parameters
    val tooltipText by parameter("Tooltip text")
    val enableUserInput by parameter(true)
    val focusable by parameter(true)

    // Tooltip type parameters
    val isRichTooltip by parameter(false)
    val showCaret by parameter(false)
    val caretWidthFloat by parameter(16f)
    val caretHeightFloat by parameter(8f)

    // Rich tooltip parameters
    val richTooltipTitle by parameter("Tooltip Title")
    val showAction by parameter(true)
    val actionText by parameter("Close")

    // Persistent tooltip
    val isPersistent by parameter(false)

    // State management
    val tooltipState = rememberTooltipState(isPersistent = isPersistent)
    val scope = rememberCoroutineScope()

    // Create a surface to provide a background for the tooltip
    Surface(
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display information about the tooltip configuration
            if (isPersistent) {
                Text(
                    text = "Interactive Tooltip (isPersistent = true)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Tooltip will stay open when mouse moves away, allowing interaction with its content.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                )
            } else {
                Text(
                    text = "Standard Tooltip (isPersistent = false)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Tooltip will close when mouse moves away from the trigger element.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Tooltip implementation
                TooltipBox(
                    positionProvider = if (isRichTooltip) 
                        TooltipDefaults.rememberRichTooltipPositionProvider() 
                    else 
                        TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        if (isRichTooltip) {
                            RichTooltip(
                                title = { Text(richTooltipTitle) },
                                action = if (showAction) {
                                    {
                                        TextButton(onClick = { scope.launch { tooltipState.dismiss() } }) {
                                            Text(actionText)
                                        }
                                    }
                                } else null,
                                caretSize = if (showCaret) DpSize(caretWidthFloat.dp, caretHeightFloat.dp) else DpSize.Unspecified
                            ) {
                                Text(tooltipText)
                            }
                        } else {
                            PlainTooltip(
                                caretSize = if (showCaret) DpSize(caretWidthFloat.dp, caretHeightFloat.dp) else DpSize.Unspecified
                            ) { 
                                if (isPersistent) {
                                    Column {
                                        Text(tooltipText)
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(
                                            onClick = { scope.launch { tooltipState.dismiss() } },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(actionText)
                                        }
                                    }
                                } else {
                                    Text(tooltipText)
                                }
                            } 
                        }
                    },
                    state = tooltipState,
                    enableUserInput = enableUserInput,
                    focusable = focusable
                ) {
                    IconButton(onClick = { /* Icon button's click event */ }) {
                        Icon(
                            imageVector = if (isRichTooltip) Icons.Filled.Info else Icons.Filled.Favorite, 
                            contentDescription = if (isRichTooltip) "Info" else "Favorite",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Manual control buttons
                Row {
                    Button(
                        onClick = { scope.launch { tooltipState.show() } },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Show Tooltip")
                    }

                    Button(
                        onClick = { scope.launch { tooltipState.dismiss() } }
                    ) {
                        Text("Hide Tooltip")
                    }
                }
            }
        }
    }
}

// A hack for development needs:
// If we need to customize the parameters controller UI, for example for a missing parameter type,
// then we can do it here.
// This relies on the fact that Storytale compile plugin will invoke the initialization of all properties in any file with stories.
private val initialization: Int = initializationForParameters()
private fun initializationForParameters(): Int {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    org.jetbrains.compose.storytale.gallery.material3.parameterUiControllerCustomizer = null
    // org.jetbrains.compose.storytale.gallery.material3.ParameterUiControllerCustomizer { { Text(it.name) } }

    return 1
}
