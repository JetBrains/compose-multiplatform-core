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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story

@OptIn(ExperimentalMaterial3Api::class)
val `BottomSheetScaffold Story` by story {
    // Sheet parameters
    val sheetPeekHeightDp by parameter(56f)
    val sheetMaxWidthDp by parameter(440f)

    // Sheet appearance parameters
    val customSheetColors by parameter(false)
    val sheetContainerColor by parameter(MaterialTheme.colorScheme.surfaceVariant)
    val sheetContentColor by parameter(MaterialTheme.colorScheme.onSurfaceVariant)

    // Sheet state parameters

    // Scaffold parameters
    val showTopBar by parameter(true)
    val customScaffoldColors by parameter(false)
    val scaffoldContainerColor by parameter(MaterialTheme.colorScheme.background)
    val scaffoldContentColor by parameter(MaterialTheme.colorScheme.onBackground)

    // Content parameters
    val showDragHandle by parameter(true)
    val rowWidthDp by parameter(400f)

    // Create snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }

    // Create sheet state
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )

    // Create scaffold state
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
        snackbarHostState = snackbarHostState
    )

    Row(modifier = Modifier.width(rowWidthDp.dp)) {
        BottomSheetScaffold(
            sheetContent = {
                if (showDragHandle) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ){
                    Text(
                        text = "Bottom Sheet Content",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            },

            // Scaffold state
            scaffoldState = scaffoldState,

            // Sheet parameters
            sheetPeekHeight = sheetPeekHeightDp.dp,
            sheetMaxWidth = sheetMaxWidthDp.dp,
            sheetContainerColor = if (customSheetColors) sheetContainerColor else MaterialTheme.colorScheme.surfaceContainerHigh, // Using surfaceContainerHigh for better contrast in dark theme
            sheetContentColor = if (customSheetColors) sheetContentColor else MaterialTheme.colorScheme.onSurface, // Using onSurface for better visibility of content in dark theme
            sheetShadowElevation = 8.dp, // Increased from BottomSheetDefaults.Elevation for better visibility in dark theme
            sheetDragHandle = if (showDragHandle) {
                { BottomSheetDefaults.DragHandle() }
            } else {
                null
            },

            // Top bar
            topBar = if (showTopBar) {
                {
                    TopAppBar(
                        title = { Text("BottomSheetScaffold", color = MaterialTheme.colorScheme.onSurface) },
                        navigationIcon = {
                            IconButton(onClick = {}) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (customScaffoldColors)
                                scaffoldContainerColor
                            else
                                TopAppBarDefaults.topAppBarColors().containerColor
                        )
                    )
                }
            } else {
                null
            },

            // Snackbar host
            snackbarHost = { snackbarState ->
                SnackbarHost(hostState = snackbarState)
            },

            // Scaffold colors
            containerColor = if (customScaffoldColors) scaffoldContainerColor else MaterialTheme.colorScheme.surface, // Using surface for better contrast with the bottom sheet
            contentColor = if (customScaffoldColors) scaffoldContentColor else MaterialTheme.colorScheme.onSurface, // Using onSurface for better visibility of content in dark theme

            // Main content
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Main Content Area",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },

            // Modifier
            modifier = Modifier.fillMaxWidth()
        )
    }
}
