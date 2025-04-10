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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.SceneManager
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.fillMaxHeight
import androidx.xr.compose.subspace.layout.fillMaxWidth
import androidx.xr.compose.subspace.layout.testTag
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestActivitySpace
import androidx.xr.compose.testing.TestCameraViewActivityPose
import androidx.xr.compose.testing.TestHeadActivityPose
import androidx.xr.compose.testing.TestJxrPlatformAdapter
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.assertHeightIsEqualTo
import androidx.xr.compose.testing.assertHeightIsNotEqualTo
import androidx.xr.compose.testing.assertPositionInRootIsEqualTo
import androidx.xr.compose.testing.assertWidthIsEqualTo
import androidx.xr.compose.testing.assertWidthIsNotEqualTo
import androidx.xr.compose.testing.createFakeRuntime
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.testing.toDp
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.runtime.Config
import androidx.xr.runtime.HeadTrackingMode
import androidx.xr.runtime.internal.CameraViewActivityPose
import androidx.xr.runtime.internal.CameraViewActivityPose.Fov
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubspaceTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    @Test
    fun subspace_alreadyInSubspace_justRendersContentDirectly() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    Subspace {
                        SpatialPanel(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("innerPanel")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("innerPanel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_alreadyInApplicationSubspace_justRendersContentDirectly() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    ApplicationSubspace {
                        SpatialPanel(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("innerPanel")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("innerPanel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun subspace_xrEnabled_contentIsCreated() {
        composeTestRule.setContent {
            TestSetup { Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
    }

    @Test
    fun applicationSubspace_unbounded_xrEnabled_contentIsCreated() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
    }

    @Test
    fun applicationSubspace_customBounded_xrEnabled_contentIsCreated() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
    }

    @Test
    fun applicationSubspace_fovBounded_xrEnabled_contentIsCreated() {
        composeTestRule.setContent {
            TestSetup { ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
    }

    @Test
    fun subspace_nonXr_contentIsNotCreated() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
    }

    @Test
    fun applicationSubspace_unbounded_nonXr_contentIsNotCreated() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
    }

    @Test
    fun applicationSubspace_customBounded_nonXr_contentIsNotCreated() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
    }

    @Test
    fun applicationSubspace_fovBounded_nonXr_contentIsNotCreated() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
    }

    @Test
    fun subspace_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup { Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panel = node.semanticsEntity
        val subspaceBox = panel?.getParent()

        assertThat(subspaceBox?.getParent())
            .isEqualTo(composeTestRule.activity.session.scene.activitySpace)
    }

    @Test
    fun applicationSubspace_unbounded_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panel = node.semanticsEntity
        val subspaceBox = panel?.getParent()

        assertThat(subspaceBox?.getParent())
            .isEqualTo(composeTestRule.activity.session.scene.activitySpace)
    }

    @Test
    fun applicationSubspace_customBounded_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panel = node.semanticsEntity
        val subspaceBox = panel?.getParent()
        assertThat(subspaceBox?.getParent())
            .isEqualTo(composeTestRule.activity.session.scene.activitySpace)
    }

    @Test
    fun applicationSubspace_fovBounded_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup { ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        assertThat(node).isNotNull()
        val panelEntity = node.semanticsEntity
        assertThat(panelEntity).isNotNull()
        val subspaceBoxEntity = panelEntity?.getParent()
        assertThat(subspaceBoxEntity).isNotNull()
        assertThat(subspaceBoxEntity?.getParent())
            .isEqualTo(composeTestRule.activity.session.scene.activitySpace)
    }

    @Test
    fun subspace_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }

        val outerPanelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val outerPanelEntity = outerPanelNode.semanticsEntity
        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        val subspaceBoxEntity = innerPanelEntity?.getParent()
        val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
        val subspaceRootEntity = subspaceLayoutEntity?.getParent()
        val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
        val parentPanel = subspaceRootContainerEntity?.getParent()
        assertThat(parentPanel).isNotNull()
        assertThat(parentPanel).isEqualTo(outerPanelEntity)
    }

    @Test
    fun applicationSubspace_unbounded_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }

        val outerPanelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val outerPanelEntity = outerPanelNode.semanticsEntity
        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        val subspaceBoxEntity = innerPanelEntity?.getParent()
        val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
        val subspaceRootEntity = subspaceLayoutEntity?.getParent()
        val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
        val parentPanel = subspaceRootContainerEntity?.getParent()
        assertThat(parentPanel).isNotNull()
        assertThat(parentPanel).isEqualTo(outerPanelEntity)
    }

    @Test
    fun applicationSubspace_customBounded_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }

        val outerPanelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val outerPanelEntity = outerPanelNode.semanticsEntity
        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        val subspaceBoxEntity = innerPanelEntity?.getParent()
        val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
        val subspaceRootEntity = subspaceLayoutEntity?.getParent()
        val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
        val parentPanel = subspaceRootContainerEntity?.getParent()
        assertThat(parentPanel).isNotNull()
        assertThat(parentPanel).isEqualTo(outerPanelEntity)
    }

    @Test
    fun applicationSubspace_fovBounded_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }

        val outerPanelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val outerPanelEntity = outerPanelNode.semanticsEntity
        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        val subspaceBoxEntity = innerPanelEntity?.getParent()
        val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
        val subspaceRootEntity = subspaceLayoutEntity?.getParent()
        val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
        val parentPanel = subspaceRootContainerEntity?.getParent()
        assertThat(parentPanel).isNotNull()
        assertThat(parentPanel).isEqualTo(outerPanelEntity)
    }

    @Test
    fun subspace_isDisposed() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        showSubspace = false
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
    }

    @Test
    fun applicationSubspace_unbounded_isDisposed() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    ApplicationSubspace(
                        constraints = VolumeConstraints.Unbounded,
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel(SubspaceModifier.testTag("panel")) {}
                    }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        showSubspace = false
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
    }

    @Test
    fun applicationSubspace_customBounded_isDisposed() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 100,
                                minHeight = 0,
                                maxHeight = 100
                            ),
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel(SubspaceModifier.testTag("panel")) {}
                    }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        showSubspace = false
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
    }

    @Test
    fun applicationSubspace_fovBounded_isDisposed() {
        var showSubspace by mutableStateOf(true)
        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        showSubspace = false
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
    }

    @Test
    fun subspace_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter = TestJxrPlatformAdapter.create(fakeRuntime)

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        testJxrPlatformAdapter.requestHomeSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        testJxrPlatformAdapter.requestFullSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
    }

    @Test
    fun applicationSubspace_unbounded_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = fakeRuntime) {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        fakeRuntime.requestHomeSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        fakeRuntime.requestFullSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
    }

    @Test
    fun applicationSubspace_customBounded_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = fakeRuntime) {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        fakeRuntime.requestHomeSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        fakeRuntime.requestFullSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
    }

    @Test
    fun applicationSubspace_fovBounded_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter = TestJxrPlatformAdapter.create(fakeRuntime)
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        testJxrPlatformAdapter.requestHomeSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        testJxrPlatformAdapter.requestFullSpaceMode()
        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)
    }

    @Test
    fun applicationSubspace_unbounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
                        SpatialPanel {
                            ApplicationSubspace(
                                constraints = VolumeConstraints.Unbounded,
                                constraintsBehavior = ConstraintsBehavior.Specified,
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_customBounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
                        SpatialPanel {
                            ApplicationSubspace(
                                constraints =
                                    VolumeConstraints(
                                        minWidth = 0,
                                        maxWidth = 100,
                                        minHeight = 0,
                                        maxHeight = 100
                                    ),
                                constraintsBehavior = ConstraintsBehavior.Specified,
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_fovBounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup { Subspace { SpatialPanel { ApplicationSubspace {} } } }
            }
        }
    }

    @Test
    fun applicationSubspace_unbounded_asNestedInUnboundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace(
                        constraints = VolumeConstraints.Unbounded,
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel() {
                            ApplicationSubspace(constraints = VolumeConstraints.Unbounded) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_Unbounded_asNestedInFovBoundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace {
                        SpatialPanel() {
                            ApplicationSubspace(constraints = VolumeConstraints.Unbounded) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_customBounded_asNestedinCustomBoundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 50,
                                minHeight = 0,
                                maxHeight = 50
                            ),
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel() {
                            ApplicationSubspace(
                                constraints =
                                    VolumeConstraints(
                                        minWidth = 0,
                                        maxWidth = 100,
                                        minHeight = 0,
                                        maxHeight = 100
                                    ),
                                constraintsBehavior = ConstraintsBehavior.Specified,
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_fovBounded_asNestedInFovBoundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup { ApplicationSubspace { SpatialPanel() { ApplicationSubspace {} } } }
            }
        }
    }

    @Test
    fun applicationSubspace_fovBounded_asNestedInUnboundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace(constraints = VolumeConstraints.Unbounded) {
                        SpatialPanel() { ApplicationSubspace {} }
                    }
                }
            }
        }
    }

    @Test
    fun subspace_fillMaxSize_returnsCorrectWidthAndHeight() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun subspace_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() = ~5023
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() = ~5023
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(5023.toDp())
            .assertHeightIsEqualTo(5023.toDp())
    }

    @Test
    fun subspace_fillMaxSize_higherScale_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(2f, 2f, 2f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(2f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (2) / ActivitySpaceScale (2) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun subspace_zeroDistance_returnsDefaultConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose = TestHeadActivityPose(activitySpacePose = Pose.Identity)
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun subspace_zeroFov_returnsZeroConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov = CameraViewActivityPose.Fov(0.0f, 0.0f, 0.0f, 0.0f),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov = CameraViewActivityPose.Fov(0.0f, 0.0f, 0.0f, 0.0f),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(0.dp)
    }

    @Test
    fun subspace_nullHead_returnsDefaultConstraintsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun subspace_headAvailableAfter50ms_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose = null
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.headActivityPose =
                        TestHeadActivityPose(
                            activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                        )
                }
                Subspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun subspace_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun subspace_leftCameraAvailableAfter50ms_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose = null
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.leftCameraViewPose =
                        TestCameraViewActivityPose(
                            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                            fov =
                                CameraViewActivityPose.Fov(
                                    angleLeft = -1.57f,
                                    angleRight = 1.00f,
                                    angleUp = 1.57f,
                                    angleDown = -1.57f,
                                ),
                        )
                }
                Subspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun subspace_nullRightCamera_returnsDefaultConstraintsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                Subspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun subspace_rightCameraAvailableAfter50ms_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose = null
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.rightCameraViewPose =
                        TestCameraViewActivityPose(
                            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                            fov =
                                CameraViewActivityPose.Fov(
                                    angleLeft = -1.00f,
                                    angleRight = 1.57f,
                                    angleUp = 1.57f,
                                    angleDown = -1.57f,
                                ),
                        )
                }
                Subspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_unbounded_fillMaxSize_doesNotReturnCorrectWidthAndHeight() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints.Unbounded,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsNotEqualTo(VolumeConstraints.Unbounded.maxWidth.toDp())
            .assertHeightIsNotEqualTo(VolumeConstraints.Unbounded.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_customBounded_fillMaxSize_returnsCorrectWidthAndHeight() {
        val customConstraints = VolumeConstraints(0, 100, 0, 100)

        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = customConstraints,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(customConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(customConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() = ~5023
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() = ~5023
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(5023.toDp())
            .assertHeightIsEqualTo(5023.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherScale_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(2f, 2f, 2f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(2f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (2) / ActivitySpaceScale (2) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroDistance_returnsDefaultConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose = TestHeadActivityPose(activitySpacePose = Pose.Identity)
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroFov_returnsZeroConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov = CameraViewActivityPose.Fov(0.0f, 0.0f, 0.0f, 0.0f),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov = CameraViewActivityPose.Fov(0.0f, 0.0f, 0.0f, 0.0f),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(0.dp)
    }

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsSubspaceDefaultsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsCustomConstraintsAfterTimeoutIfProvided() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        )
                ) {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_headAvailableAfter50ms_returnsCorectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose = null
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.headActivityPose =
                        TestHeadActivityPose(
                            activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                        )
                }
                ApplicationSubspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_nullLeftCam_returnsCustomConstraintsAfterTimeoutIfProvided() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        )
                ) {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_leftCameraAvailableAfter50ms_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose = null
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.leftCameraViewPose =
                        TestCameraViewActivityPose(
                            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                            fov =
                                CameraViewActivityPose.Fov(
                                    angleLeft = -1.57f,
                                    angleRight = 1.00f,
                                    angleUp = 1.57f,
                                    angleDown = -1.57f,
                                ),
                        )
                }
                ApplicationSubspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_nullRightCamera_returnsDefaultConstraintsAfterTimeout() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_nullRightCam_returnsCustomConstraintsAfterTimeoutIfProvided() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace(
                    constraints =
                        VolumeConstraints(
                            minWidth = 0,
                            maxWidth = 100,
                            minHeight = 0,
                            maxHeight = 100
                        )
                ) {
                    SpatialBox(
                        SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                    ) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_rightCameraAvailableAfter50ms_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose = null
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                LaunchedEffect(Unit) {
                    delay(50)
                    testJxrPlatformAdapter.rightCameraViewPose =
                        TestCameraViewActivityPose(
                            cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                            fov =
                                CameraViewActivityPose.Fov(
                                    angleLeft = -1.00f,
                                    angleRight = 1.57f,
                                    angleUp = 1.57f,
                                    angleDown = -1.57f,
                                ),
                        )
                }
                ApplicationSubspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(20)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_constraintsChange_shouldRecomposeAndChangeConstraints() {
        val initialConstraints =
            VolumeConstraints(
                minWidth = 0,
                maxWidth = 100,
                minHeight = 0,
                maxHeight = 100,
                minDepth = 0,
                maxDepth = VolumeConstraints.INFINITY,
            )
        val updatedConstraints =
            VolumeConstraints(
                minWidth = 50,
                maxWidth = 150,
                minHeight = 50,
                maxHeight = 150,
                minDepth = 0,
                maxDepth = VolumeConstraints.INFINITY,
            )

        val constraintsState = mutableStateOf<VolumeConstraints>(initialConstraints)

        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = constraintsState.value,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialBox(
                        modifier =
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("testBox")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())

        constraintsState.value = updatedConstraints
        composeTestRule.waitForIdle()

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(150.toDp())
            .assertHeightIsEqualTo(150.toDp())
    }

    @Test
    fun applicationSubspace_behaviorChangeFromFovToSpecified_shouldRecomposeAndChangeConstraints() {
        val initialConstraintsBehavior = ConstraintsBehavior.FieldOfView
        val updatedConstraintsBehavior = ConstraintsBehavior.Specified
        val constraints =
            VolumeConstraints(
                minWidth = 0,
                maxWidth = 100,
                minHeight = 0,
                maxHeight = 100,
                minDepth = 0,
                maxDepth = VolumeConstraints.INFINITY,
            )

        val constraintsBehaviorState =
            mutableStateOf<ConstraintsBehavior>(initialConstraintsBehavior)

        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace(
                    constraints = constraints,
                    constraintsBehavior = constraintsBehaviorState.value,
                ) {
                    SpatialBox(
                        modifier =
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("testBox")
                    ) {}
                }
            }
        }

        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())

        constraintsBehaviorState.value = updatedConstraintsBehavior
        composeTestRule.waitForIdle()

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(constraints.maxWidth.toDp())
            .assertHeightIsEqualTo(constraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_constraintsChangeWithSpecified_shouldRecomposeAndChangeConstraints() {
        val initialConstraints =
            VolumeConstraints(
                minWidth = 0,
                maxWidth = 100,
                minHeight = 0,
                maxHeight = 100,
                minDepth = 0,
                maxDepth = VolumeConstraints.INFINITY,
            )
        val updatedConstraints =
            VolumeConstraints(
                minWidth = 0,
                maxWidth = 200,
                minHeight = 0,
                maxHeight = 200,
                minDepth = 0,
                maxDepth = VolumeConstraints.INFINITY,
            )

        val constraintsState = mutableStateOf<VolumeConstraints>(initialConstraints)

        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }
        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace(
                    constraints = constraintsState.value,
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialBox(
                        modifier =
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("testBox")
                    ) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(initialConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(initialConstraints.maxHeight.toDp())

        constraintsState.value = updatedConstraints
        composeTestRule.waitForIdle()

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(updatedConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(updatedConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScaleInitially_becomesNonZero_returnsCorrectConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testActivitySpace =
            TestActivitySpace(
                fakeRuntime.activitySpace,
                activitySpacePose = Pose.Identity,
                activitySpaceScale = Vector3(0f, 0f, 0f),
            )
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace = testActivitySpace
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        testActivitySpace.activitySpaceScale = Vector3(1f, 1f, 1f)

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
        // DP_PER_METER = 1
        // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() = ~2512
        // px
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(2512.toDp())
            .assertHeightIsEqualTo(2512.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToDefaultConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(0f, 0f, 0f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToCustomConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(0f, 0f, 0f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 100,
                                minHeight = 0,
                                maxHeight = 100
                            )
                    ) {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(40)
        composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun privateApplicationSubspace_mainPanelEntityHidden_whenSubspaceLeavesComposition() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    ApplicationSubspace {}
                }
            }
        }
        composeTestRule.waitForIdle()

        val mainPanelEntity = composeTestRule.activity.session.scene.mainPanelEntity
        assertThat(mainPanelEntity.isHidden()).isEqualTo(true)

        showSubspace = false
        composeTestRule.waitForIdle()
        assertThat(mainPanelEntity.isHidden()).isEqualTo(false)
    }

    @Test
    fun applicationSubspace_headTrackingDisabled_returnsCustomConstraints() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)
        val testJxrPlatformAdapter =
            TestJxrPlatformAdapter.create(fakeRuntime).apply {
                activitySpace =
                    TestActivitySpace(
                        fakeRuntime.activitySpace,
                        activitySpacePose = Pose.Identity,
                        activitySpaceScale = Vector3(1f, 1f, 1f),
                    )
                headActivityPose =
                    TestHeadActivityPose(
                        activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f))
                    )
                leftCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.57f,
                                angleRight = 1.00f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
                rightCameraViewPose =
                    TestCameraViewActivityPose(
                        cameraType = CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE,
                        fov =
                            CameraViewActivityPose.Fov(
                                angleLeft = -1.00f,
                                angleRight = 1.57f,
                                angleUp = 1.57f,
                                angleDown = -1.57f,
                            ),
                    )
            }

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                val session = LocalSession.current
                session!!.configure(Config(headTracking = HeadTrackingMode.Disabled))
                ApplicationSubspace {
                    SpatialBox(SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("box").assertExists()
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp())
    }
}
