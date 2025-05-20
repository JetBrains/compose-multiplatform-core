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

package androidx.xr.compose.subspace.layout

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.spatial.ApplicationSubspace
import androidx.xr.compose.subspace.MainPanel
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.node.SubspaceModifierNodeElement
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.junit.rules.ExpectedLogMessagesRule

@RunWith(AndroidJUnit4::class)
class CoreEntityTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()
    @get:Rule val expectedLogMessagesRule = ExpectedLogMessagesRule()

    private class SpatialPanelActivity : ComponentActivity() {}

    private class ForceZeroRenderSizeElement :
        SubspaceModifierNodeElement<ForceZeroRenderSizeNode>() {
        override fun create() = ForceZeroRenderSizeNode()

        override fun update(node: ForceZeroRenderSizeNode) {}

        override fun hashCode(): Int = javaClass.hashCode()

        override fun equals(other: Any?): Boolean = other is ForceZeroRenderSizeElement
    }

    private class ForceZeroRenderSizeNode : SubspaceModifier.Node(), CoreEntityNode {
        override fun CoreEntityScope.modifyCoreEntity() {
            setRenderedSize(IntVolumeSize.Zero)
        }
    }

    @Test
    fun coreEntity_coreContentlessEntity_shouldThrowIfNotContentless() {
        composeTestRule.setContent { TestSetup {} }

        val session = composeTestRule.activity.session
        assertNotNull(session)
        assertFailsWith<IllegalArgumentException> {
            CoreContentlessEntity(session.scene.activitySpace)
        }
    }

    @Test
    fun CoreBasePanelEntity_androidViewBasedSpatialPanelSizeNonZero_shouldNotBeHidden() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(100.dp).height(100.dp).testTag("panel"),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isFalse()
    }

    @Test
    fun CoreBasePanelEntity_contentBasedSpatialPanelSizeNonZero_shouldNotBeHidden() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(SubspaceModifier.width(100.dp).height(100.dp).testTag("panel")) {}
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isFalse()
    }

    @Test
    fun CoreBasePanelEntity_mainPanelSizeNonZero_shouldNotBeHidden() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    MainPanel(SubspaceModifier.width(100.dp).height(100.dp).testTag("mainPanel"))
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?
        assertNotNull(mainPanelSceneCoreEntity)
        assertThat(mainPanelSceneCoreEntity.isHidden()).isFalse()
    }

    @Test
    fun CoreBasePanelEntity_intentBasedSpatialPanelSizeNonZero_shouldNotBeHidden() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        SubspaceModifier.width(100.dp).height(100.dp).testTag("panel"),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isFalse()
    }

    @Test
    fun CoreBasePanelEntity_androidViewBasedPanelSizeZeroAfterMeasure_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_contentBasedSpatialPanelSizeZeroAfterMeasure_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_mainPanelSizeZeroAfterMeasurement_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    MainPanel(SubspaceModifier.width(0.dp).height(0.dp).testTag("mainPanel"))
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?
        assertNotNull(mainPanelSceneCoreEntity)
        assertThat(mainPanelSceneCoreEntity.isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_intentBasedPanelSizeZeroAfterMeasure_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_androidViewPanelresizableZeroSizeOverride_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        factory = { View(it) },
                        SubspaceModifier.testTag("panel")
                            .resizable()
                            .then(ForceZeroRenderSizeElement()),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as? PanelEntity
        assertThat(checkNotNull(panelSceneCoreEntity).isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_contentBasedPanelresizableZeroSizeOverride_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        SubspaceModifier.testTag("panel")
                            .resizable()
                            .then(ForceZeroRenderSizeElement())
                    ) {}
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as? PanelEntity
        assertThat(checkNotNull(panelSceneCoreEntity).isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_mainPanelresizableAndZeroSizeOverride_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    MainPanel(
                        SubspaceModifier.testTag("mainPanel")
                            .resizable()
                            .then(ForceZeroRenderSizeElement())
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as? PanelEntity
        assertThat(checkNotNull(mainPanelSceneCoreEntity).isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun CoreBasePanelEntity_intentBasedPanelresizableZeroSizeOverride_shouldBeHiddenAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        SubspaceModifier.testTag("panel")
                            .resizable()
                            .then(ForceZeroRenderSizeElement()),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as? PanelEntity
        assertThat(checkNotNull(panelSceneCoreEntity).isHidden()).isTrue()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }
}
