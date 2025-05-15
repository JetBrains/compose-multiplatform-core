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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.storytale.story
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
val `TabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Size parameters
    val containerWidth by parameter(300f)

    // Display parameters
    val showIcons by parameter(true)

    // Hardcoded tab data
    val tabs = listOf(
        "Home" to Icons.Filled.Home,
        "Favorites" to Icons.Filled.Favorite,
        "Settings" to Icons.Filled.Settings
    )

    // Fixed height container to prevent layout shifts
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .height(120.dp)
            .width(containerWidth.dp)
    ) {
        // Fixed height for the tab row
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .height(56.dp)     // Standard Material3 tab height
                .fillMaxWidth()     // Fill the width of the parent
        ) {
            // Multiple hardcoded tabs
            tabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier
                        .height(56.dp)  // Standard Material3 tab height
                        .weight(1f),    // Equal width for all tabs
                    text = { 
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = if (showIcons) {
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `PrimaryTabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Size parameters
    val containerWidth by parameter(380f)
    val tabRowHeight by parameter(56f)

    // Tab count and configuration
    val tabCount by parameter(listOf("Three", "Four", "Five"), 0)
    val showIcons by parameter(true)
    val iconPosition by parameter(listOf("Leading", "Top"), 0)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(TabRowDefaults.primaryContainerColor)
    val contentColor by parameter(TabRowDefaults.primaryContentColor)

    // Indicator parameters
    val showIndicator by parameter(true)
    val useCustomIndicator by parameter(false)
    val indicatorColor by parameter(MaterialTheme.colorScheme.primary)
    val indicatorHeight by parameter(3f)

    // Divider parameters
    val showDivider by parameter(true)
    val dividerColor by parameter(MaterialTheme.colorScheme.outlineVariant)
    val dividerThickness by parameter(1f)

    // Determine number of tabs based on selection
    val tabs = when (tabCount) {
        "Five" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
        "Four" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
        else -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Settings" to Icons.Filled.Settings
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(containerWidth.dp)
    ) {
        // The tab row
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .height(tabRowHeight.dp)
                .fillMaxWidth(),
            containerColor = if (useCustomColors) containerColor else TabRowDefaults.primaryContainerColor,
            contentColor = if (useCustomColors) contentColor else TabRowDefaults.primaryContentColor,
            indicator = if (useCustomIndicator) {
                {
                    if (showIndicator) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
                            width = Dp.Unspecified,
                            height = indicatorHeight.dp,
                            color = indicatorColor
                        )
                    }
                }
            } else {
                {
                    if (showIndicator) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
                            width = Dp.Unspecified
                        )
                    }
                }
            },
            divider = {
                if (showDivider) {
                    HorizontalDivider(
                        thickness = dividerThickness.dp,
                        color = dividerColor
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                if (iconPosition == "Leading" && showIcons) {
                    LeadingIconTab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                } else {
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = if (showIcons) {
                            { Icon(icon, contentDescription = null) }
                        } else null
                    )
                }
            }
        }

        // Content area to see the effect of tab selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = tabs[selectedTabIndex].second,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${tabs[selectedTabIndex].first} Content",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `ScrollableTabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Size parameters
    val containerWidth by parameter(360f)
    val tabRowHeight by parameter(56f)

    // Edge padding parameter
    val edgePadding by parameter(52f)

    // Tab configuration
    val tabCount by parameter(listOf("Five", "Seven", "Ten"), 0)
    val showIcons by parameter(true)
    val iconPosition by parameter(listOf("Leading", "Top"), 0)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(TabRowDefaults.primaryContainerColor)
    val contentColor by parameter(TabRowDefaults.primaryContentColor)

    // Indicator parameters
    val showIndicator by parameter(true)
    val useCustomIndicator by parameter(false)
    val indicatorColor by parameter(MaterialTheme.colorScheme.secondary)
    val indicatorHeight by parameter(2f)

    // Divider parameters
    val showDivider by parameter(true)
    val dividerColor by parameter(MaterialTheme.colorScheme.outlineVariant)
    val dividerThickness by parameter(1f)

    // Determine number of tabs based on selection
    val tabs = when (tabCount) {
        "Ten" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person,
            "Tab 8" to Icons.Filled.Settings,
            "Tab 9" to Icons.Filled.Email,
            "Tab 10" to Icons.Filled.Person
        )
        "Seven" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person
        )
        else -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(containerWidth.dp)
    ) {
        // The scrollable tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .height(tabRowHeight.dp)
                .fillMaxWidth(),
            containerColor = if (useCustomColors) containerColor else TabRowDefaults.primaryContainerColor,
            contentColor = if (useCustomColors) contentColor else TabRowDefaults.primaryContentColor,
            edgePadding = edgePadding.dp,
            indicator = if (useCustomIndicator) {
                { tabPositions ->
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = indicatorHeight.dp,
                            color = indicatorColor
                        )
                    }
                }
            } else {
                { tabPositions ->
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
                        )
                    }
                }
            },
            divider = {
                if (showDivider) {
                    HorizontalDivider(
                        thickness = dividerThickness.dp,
                        color = dividerColor
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                if (iconPosition == "Leading" && showIcons) {
                    LeadingIconTab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                } else {
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = if (showIcons) {
                            { Icon(icon, contentDescription = null) }
                        } else null
                    )
                }
            }
        }

        // Content area to see the effect of tab selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = tabs[selectedTabIndex].second,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${tabs[selectedTabIndex].first} Content",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `SecondaryTabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Size parameters
    val containerWidth by parameter(380f)
    val tabRowHeight by parameter(56f)

    // Tab count and configuration
    val tabCount by parameter(listOf("Three", "Four", "Five"), 0)
    val showIcons by parameter(true)
    val iconPosition by parameter(listOf("Leading", "Top"), 0)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(TabRowDefaults.secondaryContainerColor)
    val contentColor by parameter(TabRowDefaults.secondaryContentColor)

    // Indicator parameters
    val showIndicator by parameter(true)
    val useCustomIndicator by parameter(false)
    val indicatorColor by parameter(MaterialTheme.colorScheme.primary)
    val indicatorHeight by parameter(2f)

    // Divider parameters
    val showDivider by parameter(true)
    val dividerColor by parameter(MaterialTheme.colorScheme.outlineVariant)
    val dividerThickness by parameter(1f)

    // Determine number of tabs based on selection
    val tabs = when (tabCount) {
        "Five" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
        "Four" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
        else -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Settings" to Icons.Filled.Settings
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(containerWidth.dp)
    ) {
        // The tab row
        SecondaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .height(tabRowHeight.dp)
                .fillMaxWidth(),
            containerColor = if (useCustomColors) containerColor else TabRowDefaults.secondaryContainerColor,
            contentColor = if (useCustomColors) contentColor else TabRowDefaults.secondaryContentColor,
            indicator = if (useCustomIndicator) {
                {
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false),
                            height = indicatorHeight.dp,
                            color = indicatorColor
                        )
                    }
                }
            } else {
                {
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                        )
                    }
                }
            },
            divider = {
                if (showDivider) {
                    HorizontalDivider(
                        thickness = dividerThickness.dp,
                        color = dividerColor
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                if (iconPosition == "Leading" && showIcons) {
                    LeadingIconTab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                } else {
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = if (showIcons) {
                            { Icon(icon, contentDescription = null) }
                        } else null
                    )
                }
            }
        }

        // Content area to see the effect of tab selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = tabs[selectedTabIndex].second,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${tabs[selectedTabIndex].first} Content",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `SecondaryScrollableTabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    // Size parameters
    val containerWidth by parameter(360f)
    val tabRowHeight by parameter(56f)

    // Edge padding parameter
    val edgePadding by parameter(52f)

    // Tab configuration
    val tabCount by parameter(listOf("Five", "Seven", "Ten"), 0)
    val showIcons by parameter(true)
    val iconPosition by parameter(listOf("Leading", "Top"), 0)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(TabRowDefaults.secondaryContainerColor)
    val contentColor by parameter(TabRowDefaults.secondaryContentColor)

    // Indicator parameters
    val showIndicator by parameter(true)
    val useCustomIndicator by parameter(false)
    val indicatorColor by parameter(MaterialTheme.colorScheme.primary)
    val indicatorHeight by parameter(2f)

    // Divider parameters
    val showDivider by parameter(true)
    val dividerColor by parameter(MaterialTheme.colorScheme.outlineVariant)
    val dividerThickness by parameter(1f)

    // Determine number of tabs based on selection
    val tabs = when (tabCount) {
        "Ten" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person,
            "Tab 8" to Icons.Filled.Settings,
            "Tab 9" to Icons.Filled.Email,
            "Tab 10" to Icons.Filled.Person
        )
        "Seven" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person
        )
        else -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(containerWidth.dp)
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            scrollState = scrollState,
            modifier = Modifier
                .height(tabRowHeight.dp)
                .fillMaxWidth(),
            containerColor = if (useCustomColors) containerColor else TabRowDefaults.secondaryContainerColor,
            contentColor = if (useCustomColors) contentColor else TabRowDefaults.secondaryContentColor,
            edgePadding = edgePadding.dp,
            indicator = if (useCustomIndicator) {
                {
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false),
                            height = indicatorHeight.dp,
                            color = indicatorColor
                        )
                    }
                }
            } else {
                {
                    if (showIndicator) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = false)
                        )
                    }
                }
            },
            divider = {
                if (showDivider) {
                    HorizontalDivider(
                        thickness = dividerThickness.dp,
                        color = dividerColor
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                if (iconPosition == "Leading" && showIcons) {
                    LeadingIconTab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                } else {
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = if (showIcons) {
                            { Icon(icon, contentDescription = null) }
                        } else null
                    )
                }
            }
        }

        // Content area to see the effect of tab selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = tabs[selectedTabIndex].second,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${tabs[selectedTabIndex].first} Content",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
val `PrimaryScrollableTabRow Story` by story {
    // State for tab selection
    var selectedTabIndex by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    // Size parameters
    val containerWidth by parameter(360f)
    val tabRowHeight by parameter(56f)

    // Edge padding parameter
    val edgePadding by parameter(52f)

    // Tab configuration
    val tabCount by parameter(listOf("Five", "Seven", "Ten"), 0)
    val showIcons by parameter(true)
    val iconPosition by parameter(listOf("Leading", "Top"), 0)

    // Color parameters
    val useCustomColors by parameter(false)
    val containerColor by parameter(TabRowDefaults.primaryContainerColor)
    val contentColor by parameter(TabRowDefaults.primaryContentColor)

    // Indicator parameters
    val showIndicator by parameter(true)
    val useCustomIndicator by parameter(false)
    val indicatorColor by parameter(MaterialTheme.colorScheme.primary)
    val indicatorHeight by parameter(3f)

    // Divider parameters
    val showDivider by parameter(true)
    val dividerColor by parameter(MaterialTheme.colorScheme.outlineVariant)
    val dividerThickness by parameter(1f)

    // Determine number of tabs based on selection
    val tabs = when (tabCount) {
        "Ten" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person,
            "Tab 8" to Icons.Filled.Settings,
            "Tab 9" to Icons.Filled.Email,
            "Tab 10" to Icons.Filled.Person
        )
        "Seven" -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings,
            "Tab 6" to Icons.Filled.Email,
            "Tab 7" to Icons.Filled.Person
        )
        else -> listOf(
            "Home" to Icons.Filled.Home,
            "Favorites" to Icons.Filled.Favorite,
            "Messages" to Icons.Filled.Email,
            "Profile" to Icons.Filled.Person,
            "Settings" to Icons.Filled.Settings
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .width(containerWidth.dp)
    ) {
        // The primary scrollable tab row
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            scrollState = scrollState,
            modifier = Modifier
                .height(tabRowHeight.dp)
                .fillMaxWidth(),
            containerColor = if (useCustomColors) containerColor else TabRowDefaults.primaryContainerColor,
            contentColor = if (useCustomColors) contentColor else TabRowDefaults.primaryContentColor,
            edgePadding = edgePadding.dp,
            indicator = if (useCustomIndicator) {
                {
                    if (showIndicator) {
                        TabRowDefaults.PrimaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
                            width = Dp.Unspecified,
                            height = indicatorHeight.dp,
                            color = indicatorColor
                        )
                    }
                }
            } else {
                {
                    if (showIndicator) {
                        TabRowDefaults.PrimaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex, matchContentSize = true),
                            width = Dp.Unspecified
                        )
                    }
                }
            },
            divider = {
                if (showDivider) {
                    HorizontalDivider(
                        thickness = dividerThickness.dp,
                        color = dividerColor
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                if (iconPosition == "Leading" && showIcons) {
                    LeadingIconTab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                } else {
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                        icon = if (showIcons) {
                            { Icon(icon, contentDescription = null) }
                        } else null
                    )
                }
            }
        }

        // Content area to see the effect of tab selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = tabs[selectedTabIndex].second,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "${tabs[selectedTabIndex].first} Content",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
