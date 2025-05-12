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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

/** Basic modal bottom sheet */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicModalBottomSheetExample() {
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showBottomSheet = true }) {
            Text("Show Bottom Sheet")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Bottom Sheet Content",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This is a modal bottom sheet with basic content.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/** Modal bottom sheet with list items */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheetWithListExample() {
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showBottomSheet = true }) {
            Text("Show Bottom Sheet with List")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Select an option",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(
                            onClick = { showBottomSheet = false },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    for (i in 1..5) {
                        ListItem(
                            headlineContent = { Text("Option $i") },
                            supportingContent = { Text("Description for option $i") },
                            leadingContent = { 
                                Icon(Icons.Default.Info, contentDescription = null)
                            },
                            modifier = Modifier.clickable { showBottomSheet = false }
                        )
                    }
                }
            }
        }
    }
}

/** Modal bottom sheet with scrollable content */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheetWithScrollableContentExample() {
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showBottomSheet = true }) {
            Text("Show Scrollable Bottom Sheet")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Scrollable Content",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    for (i in 1..15) {
                        ListItem(
                            headlineContent = { Text("Item $i") },
                            supportingContent = { Text("Description for item $i") }
                        )
                    }

                    Button(
                        onClick = { showBottomSheet = false },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(vertical = 16.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/** Modal bottom sheet with customize appearance */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizedModalBottomSheetExample() {
    var showBottomSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { showBottomSheet = true }) {
            Text("Show Customized Bottom Sheet")
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                dragHandle = { /* No drag handle */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Custom Bottom Sheet",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This bottom sheet has custom colors and no drag handle.")
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("DISMISS")
                    }
                }
            }
        }
    }
} 
