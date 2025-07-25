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

package androidx.xr.compose.subspace

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.platform.DialogManager
import androidx.xr.compose.platform.LocalDialogManager
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.testTag
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.unit.Meter.Companion.meters
import androidx.xr.scenecore.BasePanelEntity
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import com.android.extensions.xr.ShadowXrExtensions
import com.android.extensions.xr.space.ShadowActivityPanel
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [SpatialPanel]. */
@RunWith(AndroidJUnit4::class)
class SpatialPanelTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    @Test
    fun spatialPanel_internalElementsAreLaidOutProperly() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(SubspaceModifier.width(100.dp).testTag("panel")) {
                        // Row with 2 elements, one is 3x as large as the other
                        Row {
                            Spacer(Modifier.testTag("spacer1").weight(1f))
                            Spacer(Modifier.testTag("spacer2").weight(3f))
                        }
                    }
                }
            }
        }
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        composeTestRule.onNodeWithTag("spacer1").assertWidthIsEqualTo(25.dp)
        composeTestRule.onNodeWithTag("spacer2").assertWidthIsEqualTo(75.dp)
    }

    @Test
    fun spatialPanel_textTooLong_panelDoesNotGrowBeyondSpecifiedWidth() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    // Panel with 10dp width, way too small for the text we're putting into it
                    SpatialPanel(SubspaceModifier.width(10.dp).testTag("panel")) {
                        // Panel contains a column.
                        Column {
                            Text(
                                "Hello World long text",
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }
                }
            }
        }
        // Text element stays 10dp long, even though it needs more space, as the Panel will not grow
        // for the text.
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        composeTestRule.onNodeWithText("Hello World long text").assertWidthIsEqualTo(10.dp)
    }

    @Test
    fun spatialPanel_viewBasedPanelComposes() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    val context = LocalContext.current
                    val textView = remember { TextView(context).apply { text = "Hello World" } }
                    SpatialPanel(view = textView, SubspaceModifier.testTag("panel"))
                    // The View is not inserted in the compose tree, we need to test it differentlly
                    assertEquals(View.VISIBLE, textView.visibility)
                }
            }
        }
        // TODO: verify that the TextView is add to the Panel
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
    }

    @Test
    fun mainPanel_renders() {
        val text = "Main Window Text"

        composeTestRule.setContent {
            Text(text)
            TestSetup { Subspace { MainPanel(SubspaceModifier.testTag("panel")) } }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        composeTestRule.onNodeWithText(text).assertExists()
    }

    @Test
    fun mainPanel_addedTwice_asserts() {
        val text = "Main Window Text"

        assertThrows(IllegalStateException::class.java) {
            composeTestRule.setContent {
                Text(text)
                TestSetup {
                    Subspace {
                        MainPanel(SubspaceModifier.testTag("panel"))
                        MainPanel(SubspaceModifier.testTag("panel2"))
                    }
                }
            }
        }
    }

    @Test
    fun mainPanel_addedTwiceInDifferentSubtrees_asserts() {
        val text = "Main Window Text"

        assertThrows(IllegalStateException::class.java) {
            composeTestRule.setContent {
                Text(text)
                TestSetup {
                    Subspace {
                        MainPanel(SubspaceModifier.testTag("panel"))
                        MainPanel(SubspaceModifier.testTag("panel2"))
                    }
                }
            }
        }
    }

    @Test
    fun spatialPanel_cornerRadius_dp() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(
                        modifier = SubspaceModifier.width(200.dp).height(300.dp).testTag("panel"),
                        shape = SpatialRoundedCornerShape(CornerSize(32.dp)),
                    ) {}
                }
            }
        }
        assertThat(getBasePanelEntity("panel")?.getCornerRadius()?.meters?.toDp()).isEqualTo(32.dp)
    }

    @Test
    fun spatialPanel_cornerRadius_percent() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(
                        modifier = SubspaceModifier.width(200.dp).height(300.dp).testTag("panel"),
                        shape = SpatialRoundedCornerShape(CornerSize(50)),
                    ) {}
                }
            }
        }

        assertThat(getBasePanelEntity("panel")?.getCornerRadius()?.meters?.toDp()).isEqualTo(100.dp)
    }

    @Test
    fun activityPanel_launchesIntent() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        modifier = SubspaceModifier.width(200.dp).height(300.dp),
                        shape = SpatialRoundedCornerShape(CornerSize(50)),
                    )
                }
            }
        }
        // Since SubspaceTestingActivity uses FakeXrExtensions, the intent is stored in a map
        // instead of
        // being launched.
        val launchIntent =
            ShadowActivityPanel.extract(
                    ShadowXrExtensions.extract(composeTestRule.activity.extensions)
                        .getActivityPanelForHost(composeTestRule.activity)
                )
                .launchIntent

        assertThat(launchIntent?.component?.className)
            .isEqualTo(SpatialPanelActivity::class.java.name)
    }

    @Test
    fun activityPanel_scrimAdds() {
        var dialogManager: DialogManager? = null

        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        modifier = SubspaceModifier.width(200.dp).height(300.dp),
                        shape = SpatialRoundedCornerShape(CornerSize(50)),
                    )
                    dialogManager = LocalDialogManager.current
                }
            }
        }
        val session = composeTestRule.activity.session

        // For activity panels, the added scrim is represented by a PanelEntity, so the total entity
        // count should increase by 1.
        assertThat(session.scene.getEntitiesOfType(PanelEntity::class.java).size).isEqualTo(2)

        dialogManager!!.isSpatialDialogActive.value = true
        composeTestRule.waitForIdle()

        assertThat(session.scene.getEntitiesOfType(PanelEntity::class.java).size).isEqualTo(3)
    }

    @Test
    fun activityPanel_scrimRemoves() {
        var dialogManager: DialogManager? = null

        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        modifier = SubspaceModifier.width(200.dp).height(300.dp),
                        shape = SpatialRoundedCornerShape(CornerSize(50)),
                    )
                    dialogManager = LocalDialogManager.current
                }
            }
        }
        val session = composeTestRule.activity.session
        dialogManager!!.isSpatialDialogActive.value = true
        composeTestRule.waitForIdle()

        // For activity panels, the added scrim is represented by a PanelEntity, so the total entity
        // count should decrease by 1.
        assertThat(session.scene.getEntitiesOfType(PanelEntity::class.java).size).isEqualTo(3)

        dialogManager!!.isSpatialDialogActive.value = false
        composeTestRule.waitForIdle()

        assertThat(session.scene.getEntitiesOfType(PanelEntity::class.java).size).isEqualTo(2)
    }

    private fun getBasePanelEntity(tag: String): BasePanelEntity<*>? {
        return composeTestRule.onSubspaceNodeWithTag(tag).fetchSemanticsNode().semanticsEntity
            as BasePanelEntity<*>
    }

    private class SpatialPanelActivity : ComponentActivity() {}
}
