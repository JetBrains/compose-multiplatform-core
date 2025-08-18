/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.material3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.internal.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlinx.coroutines.launch

@OptIn(ExperimentalTestApi::class)
class WideNavigationRailTest {
    @Test
    fun changingOnValueChangeFinishedDoesNotTriggerFinish() = runComposeUiTest {
        setContent {
            var selectedItem by remember { mutableIntStateOf(0) }
            val items = listOf("Home", "Search", "Settings")
            val selectedIcons = listOf(Icons.Filled.ArrowDropDown, Icons.Filled.Edit, Icons.Filled.Check)
            val unselectedIcons = listOf(Icons.Outlined.Keyboard, Icons.Outlined.Schedule, Icons.Outlined.Keyboard)
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
                    val currentPage = items[selectedItem]
                    Spacer(modifier = Modifier.size(54.dp))
                    Text(text = "$currentPage Page", textAlign = TextAlign.Center)
                    Button(onClick = { scope.launch { state.expand() } }, Modifier.padding(32.dp)) {
                        Text(text = "Open modal rail", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}