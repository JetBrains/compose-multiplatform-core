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

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.View.MeasureSpec
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.spatial.ApplicationSubspace
import androidx.xr.compose.subspace.SpatialActivityPanel
import androidx.xr.compose.subspace.SpatialAndroidViewPanel
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.compose.subspace.node.ComposeSubspaceNode
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCompositionLocalMap
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetCoreEntity
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetMeasurePolicy
import androidx.xr.compose.subspace.node.ComposeSubspaceNode.Companion.SetModifier
import androidx.xr.compose.subspace.rememberCorePanelEntity
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.runtime.math.FloatSize2d
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.scenecore.GroupEntity
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.hamcrest.Matchers.containsString
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.junit.rules.ExpectedLogMessagesRule

@RunWith(AndroidJUnit4::class)
class CoreEntityTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()
    @get:Rule val expectedLogMessagesRule = ExpectedLogMessagesRule()

    private class SpatialPanelActivity : ComponentActivity() {}

    @Test
    fun coreEntity_coreGroupEntity_shouldThrowIfNotGroupEntity() {
        composeTestRule.setContent { TestSetup {} }

        val session = composeTestRule.activity.session
        assertNotNull(session)
        assertFailsWith<IllegalArgumentException> { CoreGroupEntity(session.scene.activitySpace) }
    }

    @Test
    @Ignore("b/430291253 - behavior is different in presubmit after moving to targetSdk 35")
    fun coreEntity_size_shouldNotTriggerRecomposition() {
        var size = 100
        var sizeCount = 0
        var mutableSizeCount = 0

        composeTestRule.setContent {
            TestSetup {
                val coreEntity = remember {
                    CoreGroupEntity(
                            GroupEntity.create(
                                session = assertNotNull(composeTestRule.activity.session),
                                name = "Test",
                            )
                        )
                        .apply { this.size = IntVolumeSize(size, size, size) }
                }

                SizeWatcher(coreEntity) { sizeCount++ }
                MutableSizeWatcher(coreEntity) { mutableSizeCount++ }

                Button(
                    onClick = {
                        size += 100
                        coreEntity.size = IntVolumeSize(size, size, size)
                    }
                ) {
                    Text("Increase")
                }
            }
        }

        composeTestRule.onNodeWithText("Increase").performClick()
        composeTestRule.onNodeWithText("Increase").performClick()
        composeTestRule.onNodeWithText("Increase").performClick()
        composeTestRule.onNodeWithText("Increase").performClick()
        composeTestRule.waitForIdle()

        assertThat(sizeCount).isEqualTo(1)
        assertThat(mutableSizeCount).isEqualTo(5)
    }

    @Composable
    private fun SizeWatcher(coreEntity: CoreEntity, onSizeChanged: () -> Unit) {
        check(coreEntity.size != IntVolumeSize.Zero)
        onSizeChanged()
    }

    @Composable
    private fun MutableSizeWatcher(coreEntity: CoreEntity, onSizeChanged: () -> Unit) {
        check(coreEntity.mutableSize != IntVolumeSize.Zero)
        onSizeChanged()
    }

    @Test
    fun coreBasePanelEntity_androidViewBasedSpatialPanelSizeNonZero_shouldBeEnabled() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialAndroidViewPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(100.dp).height(100.dp).testTag("panel"),
                    )
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isTrue()
    }

    @Test
    fun coreBasePanelEntity_contentBasedSpatialPanelSizeNonZero_shouldBeEnabled() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(SubspaceModifier.width(100.dp).height(100.dp).testTag("panel")) {}
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isTrue()
    }

    @Test
    fun coreBasePanelEntity_mainPanelSizeNonZero_shouldBeEnabled() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialMainPanel(
                        SubspaceModifier.width(100.dp).height(100.dp).testTag("mainPanel")
                    )
                }
            }
        }

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?
        assertNotNull(mainPanelSceneCoreEntity)
        assertThat(mainPanelSceneCoreEntity.isEnabled()).isTrue()
    }

    @Test
    fun coreBasePanelEntity_intentBasedSpatialPanelSizeNonZero_shouldBeEnabled() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialActivityPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        SubspaceModifier.width(100.dp).height(100.dp).testTag("panel"),
                    )
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isTrue()
    }

    @Test
    fun coreBasePanelEntity_androidViewBasedPanelSizeZeroAfterMeasure_shouldBeDisabledAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialAndroidViewPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isFalse()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun coreBasePanelEntity_contentBasedSpatialPanelSizeZeroAfterMeasure_shouldBeDisabledAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialAndroidViewPanel(
                        factory = { View(it) },
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isFalse()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun coreBasePanelEntity_mainPanelSizeZeroAfterMeasurement_shouldBeDisabledAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialMainPanel(SubspaceModifier.width(0.dp).height(0.dp).testTag("mainPanel"))
                }
            }
        }

        val mainPanelNode = composeTestRule.onSubspaceNodeWithTag("mainPanel").fetchSemanticsNode()
        val mainPanelSceneCoreEntity = mainPanelNode.semanticsEntity as PanelEntity?
        assertNotNull(mainPanelSceneCoreEntity)
        assertThat(mainPanelSceneCoreEntity.isEnabled()).isFalse()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun coreBasePanelEntity_intentBasedPanelSizeZeroAfterMeasure_shouldBeDisabledAndNotCrash() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialActivityPanel(
                        intent = Intent(composeTestRule.activity, SpatialPanelActivity::class.java),
                        SubspaceModifier.width(0.dp).height(0.dp).testTag("panel"),
                    )
                }
            }
        }

        val panelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panelSceneCoreEntity = panelNode.semanticsEntity as PanelEntity?
        assertNotNull(panelSceneCoreEntity)
        assertThat(panelSceneCoreEntity.isEnabled()).isFalse()
        expectedLogMessagesRule.expectLogMessage(
            Log.WARN,
            "CoreBasePanelEntity",
            containsString("The panel will be hidden."),
        )
    }

    @Test
    fun coreBasePanelEntity_composeBasedPanelWhenResizedToZeroAndBack_remainsDisabled() {
        var size by mutableStateOf(100.dp)

        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    DisabledPanel(
                        factory = { View(it) },
                        modifier = SubspaceModifier.testTag("panel").size(size),
                    )
                }
            }
        }

        var panelEntity =
            assertNotNull(
                composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode().semanticsEntity
                    as? PanelEntity
            )
        assertThat(panelEntity.sizeInPixels).isEqualTo(IntSize2d(100, 100))
        assertThat(panelEntity.isEnabled()).isFalse()

        size = 0.dp

        panelEntity =
            assertNotNull(
                composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode().semanticsEntity
                    as? PanelEntity
            )
        assertThat(panelEntity.sizeInPixels).isEqualTo(IntSize2d(0, 0))
        assertThat(panelEntity.isEnabled()).isFalse()

        size = 100.dp

        panelEntity =
            assertNotNull(
                composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode().semanticsEntity
                    as? PanelEntity
            )
        assertThat(panelEntity.sizeInPixels).isEqualTo(IntSize2d(100, 100))
        assertThat(panelEntity.isEnabled()).isFalse()
    }
}

@Composable
@SubspaceComposable
private fun <T : View> DisabledPanel(
    factory: (Context) -> T,
    modifier: SubspaceModifier = SubspaceModifier,
    update: (T) -> Unit = {},
    shape: SpatialShape = SpatialRoundedCornerShape(CornerSize(32.dp)),
) {
    val context = LocalContext.current
    val view = remember { factory(context) }

    val corePanelEntity =
        rememberCorePanelEntity(shape = shape) {
                PanelEntity.create(
                    session = this,
                    view = view,
                    dimensions = FloatSize2d(0.1f, 0.1f),
                    name = "ViewPanel",
                    pose = Pose.Identity,
                )
            }
            .also { it.enabled = false }

    val measurePolicy = SubspaceMeasurePolicy { _, constraints ->
        view.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val width = view.measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = view.measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val depth = constraints.minDepth.coerceAtLeast(0)
        layout(width, height, depth) {}
    }

    val compositionLocalMap = currentComposer.currentCompositionLocalMap
    ComposeNode<ComposeSubspaceNode, Applier<Any>>(
        factory = ComposeSubspaceNode.Constructor,
        update = {
            set(compositionLocalMap, SetCompositionLocalMap)
            set(corePanelEntity, SetCoreEntity)
            set(measurePolicy, SetMeasurePolicy)
            set(modifier, SetModifier)
            update(view)
        },
    )
}
