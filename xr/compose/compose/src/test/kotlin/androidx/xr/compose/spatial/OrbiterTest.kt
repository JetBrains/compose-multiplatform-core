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

package androidx.xr.compose.spatial

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.spatial.EdgeOffset.Companion.inner
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.scenecore.scene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbiterTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    @Test
    fun orbiter_contentIsElevated() {
        composeTestRule.setContent {
            TestSetup { Parent { Orbiter(OrbiterEdge.Top) { Text("Main Content") } } }
        }

        composeTestRule.onNodeWithText("Main Content").assertExists()
        composeTestRule.onParent().onChild().assertDoesNotExist()
    }

    @Test
    fun orbiter_nonXr_contentIsInline() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                Parent { Orbiter(OrbiterEdge.Top) { Text("Main Content") } }
            }
        }

        composeTestRule.onParent().onChild().assertTextContains("Main Content")
    }

    @Test
    fun orbiter_homeSpaceMode_contentIsInline() {
        composeTestRule.setContent {
            TestSetup {
                Parent { Orbiter(OrbiterEdge.Top) { Text("Main Content") } }
                LocalSession.current?.scene?.spatialEnvironment?.requestHomeSpaceMode()
            }
        }

        composeTestRule.onParent().onChild().assertTextContains("Main Content")
    }

    @Test
    fun orbiter_nonSpatial_doesNotRenderContent() {
        composeTestRule.setContent {
            TestSetup {
                Parent {
                    Orbiter(
                        OrbiterEdge.Top,
                        settings = OrbiterSettings(shouldRenderInNonSpatial = false)
                    ) {
                        Text("Main Content")
                    }
                }
                LocalSession.current?.scene?.spatialEnvironment?.requestHomeSpaceMode()
            }
        }

        composeTestRule.onNodeWithText("Main Content").assertDoesNotExist()
    }

    @Test
    fun orbiter_multipleInstances_rendersInSpatial() {
        composeTestRule.setContent {
            TestSetup {
                Parent {
                    Orbiter(position = OrbiterEdge.Top) { Text("Top") }
                    Orbiter(position = OrbiterEdge.Start) { Text("Start") }
                    Orbiter(position = OrbiterEdge.End) { Text("End") }
                    Orbiter(position = OrbiterEdge.Bottom) { Text("Bottom") }
                }
            }
        }

        composeTestRule.onParent().onChild().assertDoesNotExist()
    }

    @Test
    fun orbiter_afterSwitchToFullSpaceMode_isSpatial() {
        composeTestRule.setContent {
            TestSetup {
                LocalSession.current?.scene?.spatialEnvironment?.requestHomeSpaceMode()
                Parent { Orbiter(position = OrbiterEdge.Bottom) { Text("Bottom") } }
                LocalSession.current?.scene?.spatialEnvironment?.requestFullSpaceMode()
            }
        }

        composeTestRule.onParent().onChild().assertDoesNotExist()
    }

    @Test
    fun orbiter_setting_contentIsNotInline() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                Parent {
                    Orbiter(
                        OrbiterEdge.Top,
                        settings = OrbiterSettings(shouldRenderInNonSpatial = false)
                    ) {
                        Text("Main Content")
                    }
                }
            }
        }

        composeTestRule.onParent().onChild().assertDoesNotExist()
    }

    @Test
    fun orbiter_settingChange_contentIsInline() {
        var shouldRenderInNonSpatial by mutableStateOf(false)
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                Parent {
                    Orbiter(
                        OrbiterEdge.Top,
                        settings =
                            OrbiterSettings(shouldRenderInNonSpatial = shouldRenderInNonSpatial),
                    ) {
                        Text("Main Content")
                    }
                }
            }
        }

        shouldRenderInNonSpatial = true

        composeTestRule.onParent().onChild().assertTextContains("Main Content")
    }

    @Test
    fun orbiter_orbiterRendered() {
        composeTestRule.setContent {
            TestSetup {
                LocalSession.current?.scene?.spatialEnvironment?.requestHomeSpaceMode()
                Box {
                    Text("Main Content")
                    Orbiter(OrbiterEdge.Start) { Text("Orbiter Content") }
                }
            }
        }

        composeTestRule.onNodeWithText("Main Content").assertExists()
        composeTestRule.onNodeWithText("Orbiter Content").assertExists()
    }

    @Test
    fun orbiter_orbiterCanBeRemoved() {
        var showOrbiter by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                LocalSession.current?.scene?.spatialEnvironment?.requestHomeSpaceMode()
                Box(modifier = Modifier.size(100.dp)) {
                    Text("Main Content")
                    if (showOrbiter) {
                        Orbiter(position = OrbiterEdge.Top) { Text("Top Orbiter Content") }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Top Orbiter Content").assertExists()
        showOrbiter = false
        composeTestRule.onNodeWithText("Top Orbiter Content").assertDoesNotExist()
    }

    @Test
    fun orbiter_orbiterRenderedInlineInHomeSpaceMode() {
        var isFullSpaceMode by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                val session = LocalSession.current

                LaunchedEffect(isFullSpaceMode) {
                    if (isFullSpaceMode) {
                        session?.scene?.spatialEnvironment?.requestFullSpaceMode()
                    } else {
                        session?.scene?.spatialEnvironment?.requestHomeSpaceMode()
                    }
                }

                Parent {
                    Box(modifier = Modifier.size(100.dp)) { Text("Main Content") }
                    Orbiter(position = OrbiterEdge.Top, offset = inner(0.dp)) {
                        Text("Top Orbiter Content")
                    }
                    Orbiter(position = OrbiterEdge.Start) { Text("Start Orbiter Content") }
                    Orbiter(position = OrbiterEdge.Bottom) { Text("Bottom Orbiter Content") }
                    Orbiter(position = OrbiterEdge.End) { Text("End Orbiter Content") }
                }
            }
        }

        composeTestRule.onParent().onChild().assertTextContains("Main Content")
        isFullSpaceMode = false
        // All orbiters become children of the Parent node
        composeTestRule.onParent().onChildren().assertCountEquals(5)
        isFullSpaceMode = true
        // Orbiters exist outside of the compose hierarchy
        composeTestRule.onParent().onChildren().assertCountEquals(1)
    }
}
