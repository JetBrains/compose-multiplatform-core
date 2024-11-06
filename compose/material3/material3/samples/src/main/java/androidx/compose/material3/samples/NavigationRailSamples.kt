/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.material3.samples

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.Sampled
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailArrangement
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Sampled
@Composable
fun WideNavigationRailResponsiveSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxWidth()) {
        WideNavigationRail(
            state = state,
            header = {
                IconButton(
                    modifier =
                        Modifier.padding(start = 24.dp).semantics {
                            // The button must announce the expanded or collapsed state of the rail
                            // for accessibility.
                            stateDescription = if (state.isExpanded) "Expanded" else "Collapsed"
                        },
                    onClick = {
                        scope.launch { if (state.isExpanded) state.collapse() else state.expand() }
                    }
                ) {
                    if (state.isExpanded) Icon(Icons.AutoMirrored.Filled.MenuOpen, "Collapse rail")
                    else Icon(Icons.Filled.Menu, "Expand rail")
                }
            }
        ) {
            items.forEachIndexed { index, item ->
                WideNavigationRailItem(
                    railExpanded = state.isExpanded,
                    icon = {
                        Icon(
                            if (selectedItem == index) selectedIcons[index]
                            else unselectedIcons[index],
                            contentDescription = null
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }

        val textString = if (state.isExpanded) "expanded" else "collapsed"
        Column {
            Text(modifier = Modifier.padding(16.dp), text = "Is animating: " + state.isAnimating)
            Text(modifier = Modifier.padding(16.dp), text = "The rail is $textString.")
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Note: The orientation of this demo has been locked to portrait mode, because" +
                        " landscape mode may result in a compact height in certain devices. For" +
                        " any compact screen dimensions, use a Navigation Bar instead."
            )
        }
    }

    // Lock the orientation for this demo as the navigation rail may look cut off in landscape in
    // smaller screens.
    val context = LocalContext.current
    DisposableEffect(context) {
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Sampled
@Composable
fun ModalWideNavigationRailSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxWidth()) {
        ModalWideNavigationRail(
            state = state,
            // Note: the value of expandedHeaderTopPadding depends on the layout of your screen in
            // order to achieve the best alignment.
            expandedHeaderTopPadding = 64.dp,
            header = {
                IconButton(
                    modifier =
                        Modifier.padding(start = 24.dp).semantics {
                            // The button must announce the expanded or collapsed state of the rail
                            // for accessibility.
                            stateDescription = if (state.isExpanded) "Expanded" else "Collapsed"
                        },
                    onClick = {
                        scope.launch { if (state.isExpanded) state.collapse() else state.expand() }
                    }
                ) {
                    if (state.isExpanded) Icon(Icons.AutoMirrored.Filled.MenuOpen, "Collapse rail")
                    else Icon(Icons.Filled.Menu, "Expand rail")
                }
            }
        ) {
            items.forEachIndexed { index, item ->
                WideNavigationRailItem(
                    railExpanded = state.isExpanded,
                    icon = {
                        Icon(
                            if (selectedItem == index) selectedIcons[index]
                            else unselectedIcons[index],
                            contentDescription = item
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }

        val textString = if (state.isExpanded) "expanded" else "collapsed"
        Column {
            Text(modifier = Modifier.padding(16.dp), text = "The rail is $textString.")
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Note: The orientation of this demo has been locked to portrait mode, because" +
                        " landscape mode may result in a compact height in certain devices. For" +
                        " any compact screen dimensions, use a Navigation Bar instead."
            )
        }

        // Lock the orientation for this demo as the navigation rail may look cut off in landscape
        // in smaller screens.
        val context = LocalContext.current
        DisposableEffect(context) {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose {
                (context as? Activity)?.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Sampled
@Composable
fun DismissibleModalWideNavigationRailSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxSize()) {
        ModalWideNavigationRail(state = state, hideOnCollapse = true) {
            items.forEachIndexed { index, item ->
                WideNavigationRailItem(
                    railExpanded = true,
                    icon = {
                        Icon(
                            if (selectedItem == index) selectedIcons[index]
                            else unselectedIcons[index],
                            contentDescription = null
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = {
                        selectedItem = index
                        scope.launch { state.collapse() }
                    }
                )
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val currentPage = items.get(selectedItem)
            Button(onClick = { scope.launch { state.expand() } }, Modifier.padding(32.dp)) {
                Text(text = "$currentPage Page\nOpen modal rail", textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Sampled
@Composable
fun WideNavigationRailCollapsedSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    WideNavigationRail {
        items.forEachIndexed { index, item ->
            WideNavigationRailItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = null
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Sampled
@Composable
fun WideNavigationRailExpandedSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    WideNavigationRail(state = rememberWideNavigationRailState(WideNavigationRailValue.Expanded)) {
        items.forEachIndexed { index, item ->
            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = null
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun WideNavigationRailArrangementsSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    var arrangement by remember { mutableStateOf(WideNavigationRailArrangement.Center) }

    Row(Modifier.fillMaxWidth()) {
        WideNavigationRail(
            state = state,
            arrangement = arrangement,
            header = {
                IconButton(
                    modifier =
                        Modifier.padding(start = 24.dp).semantics {
                            // The button must announce the expanded or collapsed state of the rail
                            // for accessibility.
                            stateDescription = if (state.isExpanded) "Expanded" else "Collapsed"
                        },
                    onClick = {
                        scope.launch { if (state.isExpanded) state.collapse() else state.expand() }
                    }
                ) {
                    if (state.isExpanded) Icon(Icons.AutoMirrored.Filled.MenuOpen, "Collapse rail")
                    else Icon(Icons.Filled.Menu, "Expand rail")
                }
            }
        ) {
            items.forEachIndexed { index, item ->
                WideNavigationRailItem(
                    railExpanded = state.isExpanded,
                    icon = {
                        Icon(
                            if (selectedItem == index) selectedIcons[index]
                            else unselectedIcons[index],
                            contentDescription = null
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index }
                )
            }
        }

        val isArrangementCenter = arrangement == WideNavigationRailArrangement.Center
        val changeToString = if (isArrangementCenter) "Bottom" else "Center"
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(modifier = Modifier.padding(16.dp), text = "Change arrangement to:")
            Button(
                modifier = Modifier.padding(4.dp),
                onClick = {
                    if (isArrangementCenter) {
                        arrangement = WideNavigationRailArrangement.Bottom
                    } else {
                        arrangement = WideNavigationRailArrangement.Center
                    }
                }
            ) {
                Text(changeToString)
            }
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Note: The orientation of this Navigation Rail demo has been locked to" +
                        " portrait mode, because landscape mode may result in a compact height in" +
                        " certain devices. For any compact screen dimensions, use a Navigation" +
                        "Bar instead."
            )
        }
    }

    // Lock the orientation for this demo as the navigation rail may look cut off in landscape in
    // smaller screens.
    val context = LocalContext.current
    DisposableEffect(context) {
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Preview
@Sampled
@Composable
fun NavigationRailSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    NavigationRail {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = item
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}

@Composable
fun NavigationRailWithOnlySelectedLabelsSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    NavigationRail {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = item
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
fun NavigationRailBottomAlignSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)

    NavigationRail {
        // A Spacer that pushes the NavigationRail items to the bottom of the NavigationRail.
        Spacer(Modifier.weight(1f))
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = item
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
                alwaysShowLabel = false
            )
        }
    }
}
