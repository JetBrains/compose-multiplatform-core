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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.storytale.story

val `Snackbar Story` by story {
    // Parameters for customization
    val message by parameter("Snackbar message")
    val actionLabel by parameter("OK")
    val showActionButton by parameter(true)
    val showDismissAction by parameter(false)
    val actionOnNewLine by parameter(false)

    // State for displaying Snackbar and tracking actions
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var actionPerformed by remember { mutableStateOf(false) }

    // Use BoxWithConstraints to create a stable layout
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Main content box that won't be affected by Snackbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight - 80.dp) // Reserve space for Snackbar
                .align(Alignment.TopCenter)
        ) {
            // Column to center our button vertically
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Use stable fixed width container for button
                Box(
                    modifier = Modifier.width(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                actionPerformed = false
                                val result = snackbarHostState.showSnackbar(
                                    message = message,
                                    actionLabel = if (showActionButton) actionLabel else null,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    actionPerformed = true
                                }
                            }
                        }
                    ) {
                        Text("Show Snackbar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fixed size box for action message
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (actionPerformed) {
                        Text(
                            text = "Action performed!",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Separate box for Snackbar with fixed height at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
        ) {
            // SnackbarHost positioned at the bottom
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                snackbar = { data ->
                    Snackbar(
                        action = if (showActionButton && data.visuals.actionLabel != null) {
                            {
                                TextButton(onClick = { data.performAction() }) {
                                    Text(data.visuals.actionLabel ?: "", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else null,
                        dismissAction = if (showDismissAction) {
                            {
                                IconButton(onClick = { data.dismiss() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss"
                                    )
                                }
                            }
                        } else null,
                        actionOnNewLine = actionOnNewLine
                    ) {
                        Text(data.visuals.message)
                    }
                }
            )
        }
    }
}
