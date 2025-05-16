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
import androidx.xr.runtime.Config.HeadTrackingMode
import androidx.xr.runtime.internal.CameraViewActivityPose
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
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

    private fun doBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> Unit) {
        runBlocking(context, block)
    }

    @Test
    fun subspace_alreadyInSubspace_justRendersContentDirectly() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_alreadyInApplicationSubspace_justRendersContentDirectly() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun subspace_xrEnabled_contentIsCreated() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup { Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("panel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
        }

    @Test
    fun applicationSubspace_unbounded_xrEnabled_contentIsCreated() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("panel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
        }

    @Test
    fun applicationSubspace_customBounded_xrEnabled_contentIsCreated() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("panel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
        }

    @Test
    fun applicationSubspace_fovBounded_xrEnabled_contentIsCreated() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("panel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
        }

    @Test
    fun subspace_nonXr_contentIsNotCreated() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup(isXrEnabled = false) {
                    Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        }

    @Test
    fun applicationSubspace_unbounded_nonXr_contentIsNotCreated() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        }

    @Test
    fun applicationSubspace_customBounded_nonXr_contentIsNotCreated() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        }

    @Test
    fun applicationSubspace_fovBounded_nonXr_contentIsNotCreated() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup(isXrEnabled = false) {
                    ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
        }

    @Test
    fun subspace_contentIsParentedToActivitySpace() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup { Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
            val panel = node.semanticsEntity
            val subspaceBox = panel?.getParent()
            val session = composeTestRule.activity.session
            assertNotNull(session)
            assertThat(subspaceBox?.getParent()).isEqualTo(session.scene.activitySpace)
        }

    @Test
    fun applicationSubspace_unbounded_contentIsParentedToActivitySpace() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
            val panel = node.semanticsEntity
            val subspaceBox = panel?.getParent()
            val session = composeTestRule.activity.session
            assertNotNull(session)
            assertThat(subspaceBox?.getParent()).isEqualTo(session.scene.activitySpace)
        }

    @Test
    fun applicationSubspace_customBounded_contentIsParentedToActivitySpace() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
            val panel = node.semanticsEntity
            val subspaceBox = panel?.getParent()
            val session = composeTestRule.activity.session
            assertNotNull(session)
            assertThat(subspaceBox?.getParent()).isEqualTo(session.scene.activitySpace)
        }

    @Test
    fun applicationSubspace_fovBounded_contentIsParentedToActivitySpace() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val node = composeTestRule.onSubspaceNodeWithTag("panel").fetchSemanticsNode()
            assertNotNull(node)
            val panelEntity = node.semanticsEntity
            assertNotNull(panelEntity)
            val subspaceBoxEntity = panelEntity.getParent()
            assertNotNull(subspaceBoxEntity)
            val session = composeTestRule.activity.session
            assertNotNull(session)
            assertThat(subspaceBoxEntity.getParent()).isEqualTo(session.scene.activitySpace)
        }

    @Test
    fun subspace_nestedSubspace_contentIsParentedToContainingPanel() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
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
            val subspaceBoxEntity = innerPanelEntity?.getParent()
            val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
            val subspaceRootEntity = subspaceLayoutEntity?.getParent()
            val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
            val parentPanel = subspaceRootContainerEntity?.getParent()
            assertNotNull(parentPanel)
            assertThat(parentPanel).isEqualTo(outerPanelEntity)
        }

    @Test
    fun applicationSubspace_unbounded_nestedSubspace_contentIsParentedToContainingPanel() =
        doBlocking(testDispatcher.scheduler) {
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
            val subspaceBoxEntity = innerPanelEntity?.getParent()
            val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
            val subspaceRootEntity = subspaceLayoutEntity?.getParent()
            val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
            val parentPanel = subspaceRootContainerEntity?.getParent()
            assertNotNull(parentPanel)
            assertThat(parentPanel).isEqualTo(outerPanelEntity)
        }

    @Test
    fun applicationSubspace_customBounded_nestedSubspace_contentIsParentedToContainingPanel() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

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
            assertNotNull(parentPanel)
            assertThat(parentPanel).isEqualTo(outerPanelEntity)
        }

    @Test
    fun applicationSubspace_fovBounded_nestedSubspace_contentIsParentedToContainingPanel() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace {
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
            val subspaceBoxEntity = innerPanelEntity?.getParent()
            val subspaceLayoutEntity = subspaceBoxEntity?.getParent()
            val subspaceRootEntity = subspaceLayoutEntity?.getParent()
            val subspaceRootContainerEntity = subspaceRootEntity?.getParent()
            val parentPanel = subspaceRootContainerEntity?.getParent()
            assertNotNull(parentPanel)
            assertThat(parentPanel).isEqualTo(outerPanelEntity)
        }

    @Test
    fun subspace_isDisposed() =
        doBlocking(testDispatcher.scheduler) {
            var showSubspace by mutableStateOf(true)

            composeTestRule.setContent {
                TestSetup {
                    if (showSubspace) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            showSubspace = false
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        }

    @Test
    fun applicationSubspace_unbounded_isDisposed() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            showSubspace = false
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        }

    @Test
    fun applicationSubspace_customBounded_isDisposed() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            showSubspace = false
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        }

    @Test
    fun applicationSubspace_fovBounded_isDisposed() =
        doBlocking(testDispatcher.scheduler) {
            var showSubspace by mutableStateOf(true)

            composeTestRule.setContent {
                TestSetup {
                    if (showSubspace) {
                        ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            showSubspace = false
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)
        }

    @Test
    fun subspace_onlyOneSceneExists_afterSpaceModeChanges() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter = TestJxrPlatformAdapter.create(fakeRuntime)

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    Subspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            testJxrPlatformAdapter.requestHomeSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)

            testJxrPlatformAdapter.requestFullSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        }

    @Test
    fun applicationSubspace_unbounded_onlyOneSceneExists_afterSpaceModeChanges() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            fakeRuntime.requestHomeSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)

            fakeRuntime.requestFullSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)
        }

    @Test
    fun applicationSubspace_customBounded_onlyOneSceneExists_afterSpaceModeChanges() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            fakeRuntime.requestHomeSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)

            fakeRuntime.requestFullSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()
        }

    @Test
    fun applicationSubspace_fovBounded_onlyOneSceneExists_afterSpaceModeChanges() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter = TestJxrPlatformAdapter.create(fakeRuntime)

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
                    ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            testJxrPlatformAdapter.requestHomeSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
            assertThat(SceneManager.getSceneCount()).isEqualTo(0)

            testJxrPlatformAdapter.requestFullSpaceMode()
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onSubspaceNodeWithTag("panel").assertExists()
            assertThat(SceneManager.getSceneCount()).isEqualTo(1)

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()
        }

    @Test
    fun applicationSubspace_unbounded_asNestedInSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
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
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_customBounded_asNestedInSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
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
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_fovBounded_asNestedInSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
            assertFailsWith<IllegalStateException>(
                message = "ApplicationSubspace cannot be nested within another Subspace."
            ) {
                composeTestRule.setContent {
                    TestSetup { Subspace { SpatialPanel { ApplicationSubspace {} } } }
                }
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_unbounded_asNestedInUnboundedApplicationSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
            assertFailsWith<IllegalStateException>(
                message = "ApplicationSubspace cannot be nested within another Subspace."
            ) {
                composeTestRule.setContent {
                    TestSetup {
                        ApplicationSubspace(
                            constraints = VolumeConstraints(),
                            constraintsBehavior = ConstraintsBehavior.Specified,
                        ) {
                            SpatialPanel() {
                                ApplicationSubspace(constraints = VolumeConstraints()) {}
                            }
                        }
                    }
                }
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_Unbounded_asNestedInFovBoundedApplicationSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
            assertFailsWith<IllegalStateException>(
                message = "ApplicationSubspace cannot be nested within another Subspace."
            ) {
                composeTestRule.setContent {
                    TestSetup {
                        ApplicationSubspace {
                            SpatialPanel {
                                ApplicationSubspace(constraints = VolumeConstraints()) {}
                            }
                        }
                    }
                }
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_customBounded_asNestedinCustomBoundedApplicationSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
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
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_fovBounded_asNestedInFovBoundedApplicationSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
            assertFailsWith<IllegalStateException>(
                message = "ApplicationSubspace cannot be nested within another Subspace."
            ) {
                composeTestRule.setContent {
                    TestSetup { ApplicationSubspace { SpatialPanel { ApplicationSubspace {} } } }
                }
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun applicationSubspace_fovBounded_asNestedInUnboundedApplicationSubspace_throwsError() =
        doBlocking(testDispatcher.scheduler) {
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
                testDispatcher.scheduler.advanceUntilIdle()
                composeTestRule.waitForIdle()
            }
        }

    @Test
    fun subspace_fillMaxSize_returnsCorrectWidthAndHeight() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun subspace_fillMaxSize_higherDensity_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun subspace_fillMaxSize_higherScale_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun subspace_zeroDistance_returnsDefaultConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun subspace_zeroFov_returnsZeroConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(0.dp)
                .assertHeightIsEqualTo(0.dp)
        }

    @Test
    fun subspace_nullHead_returnsDefaultConstraintsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun subspace_headAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun subspace_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun subspace_leftCameraAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun subspace_nullRightCamera_returnsDefaultConstraintsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun subspace_rightCameraAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_unbounded_fillMaxSize_doesNotReturnCorrectWidthAndHeight() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsNotEqualTo(VolumeConstraints().maxWidth.toDp())
                .assertHeightIsNotEqualTo(VolumeConstraints().maxHeight.toDp())
        }

    @Test
    fun applicationSubspace_customBounded_fillMaxSize_returnsCorrectWidthAndHeight() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(customConstraints.maxWidth.toDp())
                .assertHeightIsEqualTo(customConstraints.maxHeight.toDp())
        }

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherDensity_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_fovBounded_fillMaxSize_higherScale_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_fovBounded_zeroDistance_returnsDefaultConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_fovBounded_zeroFov_returnsZeroConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertWidthIsEqualTo(0.dp)
                .assertHeightIsEqualTo(0.dp)
        }

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsSubspaceDefaultsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_nullHead_returnsCustomConstraintsAfterTimeoutIfProvided() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { headActivityPose = null }

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

    @Test
    fun applicationSubspace_fovBounded_headAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_nullLeftCamera_returnsDefaultConstraintsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_nullLeftCam_returnsCustomConstraintsAfterTimeoutIfProvided() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { leftCameraViewPose = null }

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

    @Test
    fun applicationSubspace_fovBounded_leftCameraAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_nullRightCamera_returnsDefaultConstraintsAfterTimeout() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

            composeTestRule.setContent {
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_nullRightCam_returnsCustomConstraintsAfterTimeoutIfProvided() =
        doBlocking(testDispatcher.scheduler) {
            val fakeRuntime = createFakeRuntime(composeTestRule.activity)
            val testJxrPlatformAdapter =
                TestJxrPlatformAdapter.create(fakeRuntime).apply { rightCameraViewPose = null }

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

    @Test
    fun applicationSubspace_fovBounded_rightCameraAvailableAfter50ms_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_constraintsChange_shouldRecomposeAndChangeConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(100.toDp())
                .assertHeightIsEqualTo(100.toDp())

            constraintsState.value = updatedConstraints
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(150.toDp())
                .assertHeightIsEqualTo(150.toDp())
        }

    @Test
    fun applicationSubspace_behaviorChangeFromFovToSpecified_shouldRecomposeAndChangeConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun applicationSubspace_constraintsChangeWithSpecified_shouldRecomposeAndChangeConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(initialConstraints.maxWidth.toDp())
                .assertHeightIsEqualTo(initialConstraints.maxHeight.toDp())

            constraintsState.value = updatedConstraints
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("testBox")
                .assertWidthIsEqualTo(updatedConstraints.maxWidth.toDp())
                .assertHeightIsEqualTo(updatedConstraints.maxHeight.toDp())
        }

    @Test
    fun applicationSubspace_fovBounded_zeroScaleInitially_becomesNonZero_returnsCorrectConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                    TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToDefaultConstraints() =
        doBlocking(testDispatcher.scheduler) {
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
                    TestSetup(runtime = testJxrPlatformAdapter) {
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

    @Test
    fun applicationSubspace_fovBounded_zeroScalePersists_fallsBackToCustomConstraints() =
        doBlocking(testDispatcher.scheduler) {
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

    @Test
    fun privateApplicationSubspace_mainPanelEntityHidden_whenSubspaceLeavesComposition() =
        doBlocking(testDispatcher.scheduler) {
            var showSubspace by mutableStateOf(true)

            composeTestRule.setContent {
                TestSetup {
                    if (showSubspace) {
                        ApplicationSubspace {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            val session = composeTestRule.activity.session
            assertNotNull(session)
            val mainPanelEntity = session.scene.mainPanelEntity
            assertThat(mainPanelEntity.isHidden()).isEqualTo(true)

            showSubspace = false
            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertThat(mainPanelEntity.isHidden()).isEqualTo(false)
        }

    @Test
    fun applicationSubspace_headTrackingDisabled_returnsFallbackFovConstraints() =
        doBlocking(testDispatcher.scheduler) {
            composeTestRule.setContent {
                TestSetup {
                    val session = LocalSession.current
                    assertNotNull(session)
                    session.configure(Config(headTracking = HeadTrackingMode.DISABLED))
                    ApplicationSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                        ) {}
                    }
                }
            }
            testDispatcher.scheduler.advanceUntilIdle()
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

    @Test
    fun applicationSubspace_returnsEarly_afterCalculatedFovConstraintsValueIsDecided() =
        doBlocking(testDispatcher.scheduler) {
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
                    TestSetup(runtime = testJxrPlatformAdapter) {
                        ApplicationSubspace {
                            SpatialBox(
                                SubspaceModifier.fillMaxWidth().fillMaxHeight().testTag("box")
                            ) {}
                        }
                    }
                }
            }

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertExists()
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())

            densityState.value = Density(2f)

            testDispatcher.scheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule
                .onSubspaceNodeWithTag("box")
                .assertExists()
                .assertWidthIsEqualTo(2512.toDp())
                .assertHeightIsEqualTo(2512.toDp())
        }
}
