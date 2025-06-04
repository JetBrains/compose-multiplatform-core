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

package androidx.xr.compose.spatial

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.testTag
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [SpatialDialog]. */
@RunWith(AndroidJUnit4::class)
class SpatialDialogTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    @Test
    fun spatialDialog_dismissOnBackPress_true() {
        val showDialog = mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Column {
                            Text("SpatialPanel Content")
                            if (showDialog.value) {
                                SpatialDialog(onDismissRequest = { showDialog.value = false }) {
                                    Text("Spatial Dialog")
                                    val dispatcher =
                                        LocalOnBackPressedDispatcherOwner.current!!
                                            .onBackPressedDispatcher
                                    Button(onClick = { dispatcher.onBackPressed() }) {
                                        Text(text = "Press Back")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        composeTestRule.onNodeWithText("Spatial Dialog").assertExists()

        composeTestRule.onNodeWithText("Press Back").performClick()

        composeTestRule.onNodeWithText("Spatial Dialog").assertDoesNotExist()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
    }

    @Test
    fun spatialDialog_dismissOnBackPress_false() {
        val showDialog = mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Column {
                            Text("SpatialPanel Content")
                            if (showDialog.value) {
                                SpatialDialog(
                                    onDismissRequest = { showDialog.value = false },
                                    properties = SpatialDialogProperties(dismissOnBackPress = false),
                                ) {
                                    Text("Spatial Dialog")
                                    val dispatcher =
                                        LocalOnBackPressedDispatcherOwner.current!!
                                            .onBackPressedDispatcher
                                    Button(onClick = { dispatcher.onBackPressed() }) {
                                        Text(text = "Press Back")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        composeTestRule.onNodeWithText("Spatial Dialog").assertExists()

        composeTestRule.onNodeWithText("Press Back").performClick()

        composeTestRule.onNodeWithText("Spatial Dialog").assertExists()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
    }
}
