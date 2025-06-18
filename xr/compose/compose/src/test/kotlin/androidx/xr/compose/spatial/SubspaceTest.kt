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

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import androidx.xr.runtime.Config.HeadTrackingMode
import androidx.xr.runtime.internal.CameraViewActivityPose
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.ContentlessEntity
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SubspaceTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        PerceptionStackRetrySettings.FovPollingDispatcherOverride = testDispatcher
    }

    @After
    fun tearDown() {
        PerceptionStackRetrySettings.FovPollingDispatcherOverride = null
    }

    @Test
    fun subspace_alreadyInSubspace_justRendersContentDirectly() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        Subspace {
                            SpatialPanel(
                                SubspaceModifier.fillMaxWidth()
                                    .fillMaxHeight()
                                    .testTag("innerPanel")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("innerPanel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_alreadyInApplicationSubspace_justRendersContentDirectly() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        ApplicationSubspace {
                            SpatialPanel(
                                SubspaceModifier.fillMaxWidth()
                                    .fillMaxHeight()
                                    .testTag("innerPanel")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("innerPanel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
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
                    constraints = VolumeConstraints(),
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
                            maxHeight = 100,
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
                    constraints = VolumeConstraints(),
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
                            maxHeight = 100,
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
        val subspaceBox = panel?.parent
        val session = assertNotNull(composeTestRule.activity.session)
        val subspaceRootEntity = assertNotNull(subspaceBox?.parent)
        val subspaceRootContainerEntity = assertNotNull(subspaceRootEntity.parent)
        assertThat(subspaceRootContainerEntity).isEqualTo(session.scene.activitySpace)
    }

    @Test
    fun applicationSubspace_unbounded_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints(),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panel = node.semanticsEntity
        val subspaceBox = panel?.parent
        val session = assertNotNull(composeTestRule.activity.session)
        val subspaceRootEntity = assertNotNull(subspaceBox?.parent)
        val subspaceRootContainerEntity = assertNotNull(subspaceRootEntity.parent)
        assertThat(subspaceRootContainerEntity).isEqualTo(session.scene.activitySpace)
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
                            maxHeight = 100,
                        ),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val panel = node.semanticsEntity
        val subspaceBox = panel?.parent
        val session = assertNotNull(composeTestRule.activity.session)
        val subspaceRootEntity = assertNotNull(subspaceBox?.parent)
        val subspaceRootContainerEntity = assertNotNull(subspaceRootEntity.parent)
        assertThat(subspaceRootContainerEntity).isEqualTo(session.scene.activitySpace)
    }

    @Test
    fun applicationSubspace_fovBounded_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup { ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
        }

        val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        assertNotNull(node)
        val panelEntity = node.semanticsEntity
        assertNotNull(panelEntity)
        val subspaceBoxEntity = panelEntity.parent
        assertNotNull(subspaceBoxEntity)
        val session = assertNotNull(composeTestRule.activity.session)
        val subspaceRootEntity = assertNotNull(subspaceBoxEntity.parent)
        val subspaceRootContainerEntity = assertNotNull(subspaceRootEntity.parent)
        assertThat(subspaceRootContainerEntity).isEqualTo(session.scene.activitySpace)
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
        val subspaceBoxEntity = innerPanelEntity?.parent
        val subspaceLayoutEntity = subspaceBoxEntity?.parent
        val subspaceRootEntity = subspaceLayoutEntity?.parent
        val subspaceRootContainerEntity = subspaceRootEntity?.parent
        val parentPanel = subspaceRootContainerEntity?.parent
        assertNotNull(parentPanel)
        assertThat(parentPanel).isEqualTo(outerPanelEntity)
    }

    @Test
    fun applicationSubspace_unbounded_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints(),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.waitForIdle()

        val outerPanelNode = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
        val outerPanelEntity = outerPanelNode.semanticsEntity
        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        val subspaceBoxEntity = innerPanelEntity?.parent
        val subspaceLayoutEntity = subspaceBoxEntity?.parent
        val subspaceRootEntity = subspaceLayoutEntity?.parent
        val subspaceRootContainerEntity = subspaceRootEntity?.parent
        val parentPanel = subspaceRootContainerEntity?.parent
        assertNotNull(parentPanel)
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
                            maxHeight = 100,
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
        val subspaceBoxEntity = innerPanelEntity?.parent
        val subspaceLayoutEntity = subspaceBoxEntity?.parent
        val subspaceRootEntity = subspaceLayoutEntity?.parent
        val subspaceRootContainerEntity = subspaceRootEntity?.parent
        val parentPanel = subspaceRootContainerEntity?.parent
        assertNotNull(parentPanel)
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
        val subspaceBoxEntity = innerPanelEntity?.parent
        val subspaceLayoutEntity = subspaceBoxEntity?.parent
        val subspaceRootEntity = subspaceLayoutEntity?.parent
        val subspaceRootContainerEntity = subspaceRootEntity?.parent
        val parentPanel = subspaceRootContainerEntity?.parent
        assertNotNull(parentPanel)
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
                        constraints = VolumeConstraints(),
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
                                maxHeight = 100,
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

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)

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
                    constraints = VolumeConstraints(),
                    constraintsBehavior = ConstraintsBehavior.Specified,
                ) {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)

        fakeRuntime.requestHomeSpaceMode()

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)

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
                            maxHeight = 100,
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

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)

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

        composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
        assertThat(SceneManager.getSceneCount()).isEqualTo(1)

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
                                constraints = VolumeConstraints(),
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
                                        maxHeight = 100,
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
                        constraints = VolumeConstraints(),
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel() { ApplicationSubspace(constraints = VolumeConstraints()) {} }
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
                        SpatialPanel { ApplicationSubspace(constraints = VolumeConstraints()) {} }
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
                                maxHeight = 50,
                            ),
                        constraintsBehavior = ConstraintsBehavior.Specified,
                    ) {
                        SpatialPanel {
                            ApplicationSubspace(
                                constraints =
                                    VolumeConstraints(
                                        minWidth = 0,
                                        maxWidth = 100,
                                        minHeight = 0,
                                        maxHeight = 100,
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
                TestSetup { ApplicationSubspace { SpatialPanel { ApplicationSubspace {} } } }
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
                    ApplicationSubspace(constraints = VolumeConstraints()) {
                        SpatialPanel { ApplicationSubspace {} }
                    }
                }
            }
        }
    }

    @Test
    fun subspace_fillMaxSize_returnsCorrectWidthAndHeight() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun subspace_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        Subspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth(1.0f)
                                    .fillMaxHeight(1.0f)
                                    .testTag("box")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() =
            // ~5023 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() =
            // ~5023 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(5023.toDp())
                .assertHeightIsEqualTo(5023.toDp())
        }
    }

    @Test
    fun subspace_fillMaxSize_higherScale_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        Subspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth(1.0f)
                                    .fillMaxHeight(1.0f)
                                    .testTag("box")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Distance in Meters = ActivitySpace unit (2) / ActivitySpaceScale (2) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun subspace_zeroDistance_returnsDefaultConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun subspace_zeroFov_returnsZeroConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(0.dp)
                .assertHeightIsEqualTo(0.dp)
        }
    }

    @Test
    fun subspace_nullHead_returnsDefaultConstraintsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun subspace_headAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply {
                    activitySpace =
                        TestActivitySpace(
                            fakeRuntime.activitySpace,
                            activitySpacePose = Pose.Identity,
                            activitySpaceScale = Vector3(1f, 1f, 1f),
                        )
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

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testJxrPlatformAdapter.headActivityPose =
                TestHeadActivityPose(activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f)))

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun subspace_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun subspace_leftCameraAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

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

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun subspace_nullRightCamera_returnsDefaultConstraintsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun subspace_rightCameraAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    rightCameraViewPose = null
                }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    Subspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

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

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_unbounded_fillMaxSize_doesNotReturnCorrectWidthAndHeight() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    constraints = VolumeConstraints(),
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
            .assertWidthIsNotEqualTo(VolumeConstraints().maxWidth.toDp())
            .assertHeightIsNotEqualTo(VolumeConstraints().maxHeight.toDp())
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
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth(1.0f)
                                    .fillMaxHeight(1.0f)
                                    .testTag("box")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() =
            // ~5023 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (2)).roundToInt() =
            // ~5023 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(5023.toDp())
                .assertHeightIsEqualTo(5023.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherScale_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth(1.0f)
                                    .fillMaxHeight(1.0f)
                                    .testTag("box")
                            ) {}
                        }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (2) / ActivitySpaceScale (2) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_zeroDistance_returnsDefaultConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun applicationSubspace_fovBounded_zeroFov_returnsZeroConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(0.dp)
                .assertHeightIsEqualTo(0.dp)
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsSubspaceDefaultsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsCustomConstraintsAfterTimeoutIfProvided() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 100,
                                minHeight = 0,
                                maxHeight = 100,
                            )
                    ) {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(100.toDp())
                .assertHeightIsEqualTo(100.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_headAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply {
                    activitySpace =
                        TestActivitySpace(
                            fakeRuntime.activitySpace,
                            activitySpacePose = Pose.Identity,
                            activitySpaceScale = Vector3(1f, 1f, 1f),
                        )
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

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testJxrPlatformAdapter.headActivityPose =
                TestHeadActivityPose(activitySpacePose = Pose(translation = Vector3(1f, 0f, 0f)))

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullLeftCam_returnsCustomConstraintsAfterTimeoutIfProvided() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 100,
                                minHeight = 0,
                                maxHeight = 100,
                            )
                    ) {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(100.toDp())
                .assertHeightIsEqualTo(100.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_leftCameraAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

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

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullRightCamera_returnsDefaultConstraintsAfterTimeout() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun applicationSubspace_fovBounded_nullRightCam_returnsCustomConstraintsAfterTimeoutIfProvided() {
        runBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace(
                        constraints =
                            VolumeConstraints(
                                minWidth = 0,
                                maxWidth = 100,
                                minHeight = 0,
                                maxHeight = 100,
                            )
                    ) {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS - 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testDispatcher.scheduler.advanceTimeBy(2)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(100.toDp())
                .assertHeightIsEqualTo(100.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_rightCameraAvailableAfter50ms_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                    rightCameraViewPose = null
                }

            composeTestRule.setContent {
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

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

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
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

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(150.toDp())
            .assertHeightIsEqualTo(150.toDp())
    }

    @Test
    fun applicationSubspace_behaviorChangeFromFovToSpecified_shouldRecomposeAndChangeConstraints() {
        runBlocking(testDispatcher.scheduler) {
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
                TestSetup(
                    runtime = testJxrPlatformAdapter,
                    onSessionCreated = { session ->
                        session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                    },
                ) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())

            constraintsBehaviorState.value = updatedConstraintsBehavior
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(constraints.maxWidth.toDp())
                .assertHeightIsEqualTo(constraints.maxHeight.toDp())
        }
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

        composeTestRule
            .onSubspaceNodeWithTag("testBox")
            .assertWidthIsEqualTo(updatedConstraints.maxWidth.toDp())
            .assertHeightIsEqualTo(updatedConstraints.maxHeight.toDp())
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScaleInitially_becomesNonZero_returnsCorrectConstraints() {
        runBlocking(testDispatcher.scheduler) {
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

            composeTestRule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                            ) {}
                        }
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(50)
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertDoesNotExist()

            testActivitySpace.activitySpaceScale = Vector3(1f, 1f, 1f)
            testActivitySpace.triggerOnSpaceUpdatedListener()

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            // Distance in Meters = ActivitySpace unit (1) / ActivitySpaceScale (1) = 1
            // DP_PER_METER = 1
            // width: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            // height: (1 meter * (abs(tan(1.57)) + abs(tan(-1.57)) * Density (1)).roundToInt() =
            // ~2512 px
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToDefaultConstraints() {
        runBlocking(testDispatcher.scheduler) {
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

            composeTestRule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                            ) {}
                        }
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS + 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxWidth.toDp()
                )
                .assertHeightIsEqualTo(
                    SubspaceDefaults.fallbackFieldOfViewConstraints.maxHeight.toDp()
                )
        }
    }

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToCustomConstraints() {
        runBlocking(testDispatcher.scheduler) {
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

            composeTestRule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace(
                            constraints =
                                VolumeConstraints(
                                    minWidth = 0,
                                    maxWidth = 100,
                                    minHeight = 0,
                                    maxHeight = 100,
                                )
                        ) {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                            ) {}
                        }
                    }
                }
            }

            testDispatcher.scheduler.advanceTimeBy(
                PerceptionStackRetrySettings.MAX_WAIT_TIME_MILLIS + 1
            )
            testDispatcher.scheduler.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("box").assertExists()
            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(100.toDp())
                .assertHeightIsEqualTo(100.toDp())
        }
    }

    @Test
    fun privateApplicationSubspace_mainPanelEntityDisabled_whenSubspaceLeavesComposition() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    ApplicationSubspace {}
                }
            }
        }

        val session = composeTestRule.activity.session
        assertNotNull(session)
        val mainPanelEntity = session.scene.mainPanelEntity
        assertThat(mainPanelEntity.isEnabled()).isEqualTo(false)

        showSubspace = false
        composeTestRule.waitForIdle()

        assertThat(mainPanelEntity.isEnabled()).isEqualTo(true)
    }

    @Test
    fun applicationSubspace_headTrackingDisabled_returnsFallbackFovConstraints() {
        composeTestRule.setContent {
            TestSetup {
                val session = LocalSession.current
                assertNotNull(session)
                session.configure(Config(headTracking = HeadTrackingMode.DISABLED))
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

    @Test
    fun applicationSubspace_returnsEarly_afterCalculatedFovConstraintsValueIsDecided() {
        runBlocking(testDispatcher.scheduler) {
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

            val densityState = mutableStateOf(Density(1f))

            composeTestRule.setContent {
                CompositionLocalProvider(LocalDensity provides densityState.value) {
                    TestSetup(
                        runtime = testJxrPlatformAdapter,
                        onSessionCreated = { session ->
                            session.configure(Config(headTracking = HeadTrackingMode.LAST_KNOWN))
                        },
                    ) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                            ) {}
                        }
                    }
                }
            }

            testDispatcher.scheduler.advanceUntilIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertExists()
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())

            densityState.value = Density(2f)

            testDispatcher.scheduler.advanceUntilIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertExists()
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
    }

    @Test
    fun applicationSubspace_retainsState_whenSwitchingModes() {
        val testJxrPlatformAdapter = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                ApplicationSubspace {
                    SpatialPanel {
                        var state by remember { mutableStateOf(0) }
                        Button(onClick = { state++ }) { Text("Increment") }
                        Text("$state", modifier = Modifier.testTag("state"))
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("state").assertTextContains("0")

        composeTestRule.onNodeWithText("Increment").performClick().performClick().performClick()

        composeTestRule.onNodeWithTag("state").assertTextContains("3")

        testJxrPlatformAdapter.requestHomeSpaceMode()

        composeTestRule.onNodeWithTag("state").assertTextContains("3")

        testJxrPlatformAdapter.requestFullSpaceMode()
        composeTestRule.onNodeWithText("Increment").performClick().performClick()

        composeTestRule.onNodeWithTag("state").assertTextContains("5")
    }

    @Test
    fun applicationSubspace_retainsState_whenSwitchingModesStartingFromHomeSpace() {
        val runtime = createFakeRuntime(composeTestRule.activity)
        runtime.requestHomeSpaceMode()

        composeTestRule.setContent {
            CompositionLocalProvider {
                TestSetup(runtime = runtime) {
                    ApplicationSubspace {
                        SpatialPanel {
                            var state by remember { mutableStateOf(0) }
                            Button(onClick = { state++ }) { Text("Increment") }
                            Text("$state", modifier = Modifier.testTag("state"))
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("state").assertTextContains("0")

        runtime.requestFullSpaceMode()

        composeTestRule.onNodeWithTag("state").assertTextContains("0")

        composeTestRule.onNodeWithText("Increment").performClick().performClick().performClick()

        composeTestRule.onNodeWithTag("state").assertTextContains("3")

        runtime.requestHomeSpaceMode()

        composeTestRule.onNodeWithTag("state").assertTextContains("3")

        runtime.requestFullSpaceMode()
        composeTestRule.onNodeWithText("Increment").performClick().performClick()

        composeTestRule.onNodeWithTag("state").assertTextContains("5")

        runtime.requestHomeSpaceMode()

        composeTestRule.onNodeWithTag("state").assertTextContains("5")
    }

    @Test
    fun applicationSubspace_usesProvidedRootContainer() {
        var testNode: Entity? = null

        composeTestRule.setContent {
            TestSetup {
                testNode = ContentlessEntity.create(LocalSession.current!!, "TestRoot")
                CompositionLocalProvider(LocalSubspaceRootNode provides testNode) {
                    assertThat(LocalSession.current!!.scene.keyEntity).isNull()
                    ApplicationSubspace {
                        SpatialBox(modifier = SubspaceModifier.testTag("Box")) {}
                    }
                }
            }
        }

        val boxNode = composeTestRule.onSubspaceNodeWithTag("Box").fetchSemanticsNode()
        val boxEntity = assertNotNull(boxNode.semanticsEntity)
        val layoutRootEntity = assertNotNull(boxEntity.parent)
        val subspaceRootEntity = assertNotNull(layoutRootEntity.parent)
        val subspaceRootContainer = assertNotNull(subspaceRootEntity.parent)

        assertThat(testNode).isEqualTo(subspaceRootContainer)
    }

    @Test
    fun applicationSubspace_multipleApplicationSubspaces_haveTheSameRootContainer() {
        composeTestRule.setContent {
            TestSetup {
                assertThat(LocalSession.current!!.scene.keyEntity).isNull()
                ApplicationSubspace { SpatialBox(modifier = SubspaceModifier.testTag("Box")) {} }
                ApplicationSubspace { SpatialBox(modifier = SubspaceModifier.testTag("Box2")) {} }
            }
        }

        val boxNode = composeTestRule.onSubspaceNodeWithTag("Box").fetchSemanticsNode()
        val boxEntity = assertNotNull(boxNode.semanticsEntity)
        val layoutRootEntity = assertNotNull(boxEntity.parent)
        val subspaceRootEntity = assertNotNull(layoutRootEntity.parent)
        val subspaceRootContainer = assertNotNull(subspaceRootEntity.parent)
        val boxNode2 = composeTestRule.onSubspaceNodeWithTag("Box2").fetchSemanticsNode()
        val boxEntity2 = assertNotNull(boxNode2.semanticsEntity)
        val layoutRootEntity2 = assertNotNull(boxEntity2.parent)
        val subspaceRootEntity2 = assertNotNull(layoutRootEntity2.parent)
        val subspaceRootContainer2 = assertNotNull(subspaceRootEntity2.parent)

        assertThat(subspaceRootContainer).isEqualTo(subspaceRootContainer2)
    }
}
