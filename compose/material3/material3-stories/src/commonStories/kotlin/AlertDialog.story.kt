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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.storytale.gallery.material3.openFullScreenStory
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `BasicAlertDialog Story` by story {
    // Content parameters
    val dialogText by parameter(
        "This area typically contains the supportive text " +
            "which presents the details regarding the Dialog's purpose."
    )
    val showButtons by parameter(true)
    val confirmButtonText by parameter("Confirm")
    val dismissButtonText by parameter("Dismiss")
    val showIcon by parameter(true)

    // Surface parameters
    val surfaceColor by parameter(MaterialTheme.colorScheme.surface)

    // Color parameters
    val textColor by parameter(AlertDialogDefaults.textContentColor)

    // State to control dialog visibility
    var showDialog by remember { mutableStateOf(false) }

    // Main content with button to show dialog
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { showDialog = true }
        ) {
            Text("Show Dialog")
        }
    }

    if (showDialog) {
        BasicAlertDialog(
            onDismissRequest = { showDialog = false }
        ) {
            // Dialog content
            Surface(
                modifier = Modifier.widthIn(max = 300.dp).wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                color = surfaceColor
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showIcon) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = dialogText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Show buttons if enabled
                    if (showButtons) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showDialog = false }
                            ) {
                                Text(dismissButtonText, color = textColor)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    showDialog = false
                                }
                            ) {
                                Text(confirmButtonText, color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Define an enum for icon types
enum class IconType {
    Info,
    Warning
}

val `AlertDialog Story` by story {
    val title by parameter("Dialog Title")
    val text by parameter(
        "This is the dialog message text that explains the purpose of the dialog."
    )
    val confirmButtonText by parameter("Confirm")
    val dismissButtonText by parameter("Dismiss")
    val showDismissButton by parameter(true)
    val showIcon by parameter(true)

    // Visual parameters
    val iconType by parameter(IconType.Info)
    val containerColor by parameter(AlertDialogDefaults.containerColor)
    val iconContentColor by parameter(AlertDialogDefaults.iconContentColor)
    val titleContentColor by parameter(AlertDialogDefaults.titleContentColor)
    val textContentColor by parameter(AlertDialogDefaults.textContentColor)
    val tonalElevation by parameter(AlertDialogDefaults.TonalElevation.value)

    // Dialog properties
    val dismissOnBackPress by parameter(true)
    val dismissOnClickOutside by parameter(true)

    // State to control dialog visibility
    var showDialog by remember { mutableStateOf(false) }

    // Main content with button to show dialog
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { showDialog = true }
        ) {
            Text("Show Dialog")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text(confirmButtonText, color = textContentColor)
                }
            },
            dismissButton = if (showDismissButton) {
                {
                    TextButton(
                        onClick = { showDialog = false }
                    ) {
                        Text(dismissButtonText, color = textContentColor)
                    }
                }
            } else null,
            icon = if (showIcon) {
                {
                    Icon(
                        imageVector = when (iconType) {
                            IconType.Info -> Icons.Filled.Info
                            IconType.Warning -> Icons.Filled.Warning
                        },
                        contentDescription = null,
                        tint = iconContentColor
                    )
                }
            } else null,
            title = { Text(title, color = titleContentColor) },
            text = { Text(text, color = textContentColor) },
            shape = MaterialTheme.shapes.large,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation.dp,
            properties = DialogProperties(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside
            )
        )
    }
}


// Doing some initialization for our gallery. It's a bit hacky, but works for our needs now.
// This relies on the fact that Storytale compiler plugin will invoke the initialization of all properties in any file with stories.
private val initialization = initializationForParameters()
private fun initializationForParameters(): Int {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    org.jetbrains.compose.storytale.gallery.material3.parameterUiControllerCustomizer = null
    // org.jetbrains.compose.storytale.gallery.material3.ParameterUiControllerCustomizer { { Text(it.name) } }

    // To open the full Stories Gallery from an embedded view:
    openFullScreenStory = { story, urlHandler ->
        urlHandler.openUri("./#story/${story.storyName}")
    }

    return 1
}
