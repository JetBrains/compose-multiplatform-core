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
import androidx.xr.compose.subspace.layout.depth
import androidx.xr.compose.subspace.layout.fillMaxHeight
import androidx.xr.compose.subspace.layout.fillMaxSize
import androidx.xr.compose.subspace.layout.fillMaxWidth
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.size
import androidx.xr.compose.subspace.layout.sizeIn
import androidx.xr.compose.subspace.layout.testTag
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.TestActivitySpace
import androidx.xr.compose.testing.TestJxrPlatformAdapter
import androidx.xr.compose.testing.TestSetup
import androidx.xr.compose.testing.assertDepthIsAtLeast
import androidx.xr.compose.testing.assertDepthIsEqualTo
import androidx.xr.compose.testing.assertDepthIsNotEqualTo
import androidx.xr.compose.testing.assertHeightIsAtLeast
import androidx.xr.compose.testing.assertHeightIsEqualTo
import androidx.xr.compose.testing.assertHeightIsNotEqualTo
import androidx.xr.compose.testing.assertPositionInRootIsEqualTo
import androidx.xr.compose.testing.assertWidthIsAtLeast
import androidx.xr.compose.testing.assertWidthIsEqualTo
import androidx.xr.compose.testing.assertWidthIsNotEqualTo
import androidx.xr.compose.testing.createFakeRuntime
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.testing.toDp
import androidx.xr.compose.unit.Meter
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.GroupEntity
import androidx.xr.scenecore.scene
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SubspaceTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    private object DefaultTestRecommendedBoxSize {
        const val WIDTH_METERS: Float = 1.73f
        const val HEIGHT_METERS: Float = 1.61f
        const val DEPTH_METERS: Float = 0.5f
    }

    /**
     * Creates a TestJxrPlatformAdapter with a recommended content box of the given size.
     *
     * Don't call this inside composeTestRule in a test. If it recomposes, a new Session will be
     * created when a previous one already exists for the activity.
     */
    private fun createAdapterWithRecommendedBox(
        widthMeters: Float = DefaultTestRecommendedBoxSize.WIDTH_METERS,
        heightMeters: Float = DefaultTestRecommendedBoxSize.HEIGHT_METERS,
        depthMeters: Float = DefaultTestRecommendedBoxSize.DEPTH_METERS,
    ): TestJxrPlatformAdapter {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)

        return TestJxrPlatformAdapter.create(fakeRuntime).apply {
            activitySpace =
                TestActivitySpace(
                    fakeRuntime.activitySpace,
                    recommendedContentBoxInFullSpace =
                        BoundingBox(
                            min = Vector3(-widthMeters / 2, -heightMeters / 2, -depthMeters / 2),
                            max = Vector3(widthMeters / 2, heightMeters / 2, depthMeters / 2),
                        ),
                )
        }
    }

    @Test
    fun subspace_alreadyInSubspace_justRendersContentDirectly() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    Subspace {
                        SpatialPanel(
                            SubspaceModifier.width(100.dp).height(100.dp).testTag("innerPanel")
                        ) {}
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onSubspaceNodeWithTag("innerPanel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun applicationSubspace_alreadyInApplicationSubspace_justRendersContentDirectly() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    ApplicationSubspace {
                        SpatialPanel(
                            SubspaceModifier.width(100.dp).height(100.dp).testTag("innerPanel")
                        ) {}
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onSubspaceNodeWithTag("innerPanel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
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
    fun applicationSubspace_recommendedBoxed_xrEnabled_contentIsCreated() {
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
    fun applicationSubspace_recommendedBoxed_nonXr_contentIsNotCreated() {
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
    fun applicationSubspace_recommendedBoxed_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup { ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} } }
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
    fun subspace_nestedSubspace_contentIsEnabledWhenContentSizeMatchesParentSize() {
        composeTestRule.setContent {
            TestSetup {
                Subspace {
                    SpatialPanel(SubspaceModifier.size(100.dp).testTag("panel")) {
                        Subspace {
                            SpatialPanel(SubspaceModifier.testTag("innerPanel")) {
                                Box(Modifier.size(100.dp))
                            }
                        }
                    }
                }
            }
        }

        val innerPanelNode =
            composeTestRule.onSubspaceNodeWithTag("innerPanel").fetchSemanticsNode()
        val innerPanelEntity = innerPanelNode.semanticsEntity
        assertThat(innerPanelEntity?.isEnabled(true)).isTrue()
    }

    @Test
    fun applicationSubspace_recommendedBoxed_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }
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
    fun applicationSubspace_recommendedBoxed_isDisposed() {
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
    fun applicationSubspace_recommendedBoxed_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = fakeRuntime) {
                ApplicationSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
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
    fun applicationSubspace_recommendedBoxed_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup { Subspace { SpatialPanel { ApplicationSubspace {} } } }
            }
        }
    }

    @Test
    fun applicationSubspace_unbounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
                        SpatialPanel { ApplicationSubspace(allowUnboundedSubspace = true) {} }
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
                                modifier =
                                    SubspaceModifier.sizeIn(minWidth = 0.dp, maxWidth = 100.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun applicationSubspace_recommendedBoxed_asNestedInUnboundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "ApplicationSubspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    ApplicationSubspace(allowUnboundedSubspace = true) {
                        SpatialPanel() { ApplicationSubspace {} }
                    }
                }
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
                    ApplicationSubspace(allowUnboundedSubspace = true) {
                        SpatialPanel() { ApplicationSubspace(allowUnboundedSubspace = true) {} }
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
                        modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
                    ) {
                        SpatialPanel {
                            ApplicationSubspace(
                                modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun subspace_fillMaxSize_returnsRecommendedContentBoxSizeConstraints() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            density = LocalDensity.current
            TestSetup(runtime = runtime) {
                Subspace { SpatialBox(SubspaceModifier.fillMaxSize(1.0f).testTag("box")) {} }
            }
        }

        assertNotNull(density)
        val expectedWidthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
        val expectedHeightPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
        val expectedDepthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.DEPTH_METERS).roundToPx(this) }
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .assertDepthIsEqualTo(expectedDepthPx.toDp())
    }

    @Test
    fun subspace_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                density = LocalDensity.current
                TestSetup(runtime = runtime) {
                    Subspace { SpatialBox(SubspaceModifier.fillMaxSize(1.0f).testTag("box")) {} }
                }
            }
        }

        assertNotNull(density)
        assertThat(density.density).isEqualTo(2f)
        val expectedWidthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
        val expectedHeightPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
        val expectedDepthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.DEPTH_METERS).roundToPx(this) }
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .assertDepthIsEqualTo(expectedDepthPx.toDp())
    }

    @Test
    fun applicationSubspace_fillMaxSize_returnsRecommendedContentBoxSizeConstraints() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            density = LocalDensity.current
            TestSetup(runtime = runtime) {
                ApplicationSubspace {
                    SpatialBox(SubspaceModifier.fillMaxSize(1.0f).testTag("box")) {}
                }
            }
        }

        assertNotNull(density)
        val expectedWidthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
        val expectedHeightPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
        val expectedDepthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.DEPTH_METERS).roundToPx(this) }
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
            .assertDepthIsEqualTo(expectedDepthPx.toDp())
    }

    @Test
    fun applicationSubspace_unbounded_fillMaxSize_doesNotReturnCorrectWidthAndHeight() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(allowUnboundedSubspace = true) {
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
            .assertDepthIsNotEqualTo(VolumeConstraints().maxDepth.toDp())
    }

    @Test
    fun applicationSubspace_customBounded_fillMaxSize_returnsCorrectWidthAndHeight() {
        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(
                    modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
                ) {
                    SpatialBox(SubspaceModifier.fillMaxSize(1.0f).testTag("box")) {}
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun applicationSubspace_allowUnboundedSubspaceIsTrue_isUnbounded() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            density = LocalDensity.current
            TestSetup(runtime = runtime) {
                // This large width is explicitly bigger than the recommended box width.
                val widthLargerThanRecommendedBox =
                    with(LocalDensity.current) {
                        Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS + 1000000.0f)
                            .roundToPx(this)
                            .toDp()
                    }
                val heightLargerThanRecommendedBox =
                    with(LocalDensity.current) {
                        Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS + 100000.0f)
                            .roundToPx(this)
                            .toDp()
                    }
                val depthLargerThanRecommendedBox =
                    with(LocalDensity.current) {
                        Meter(DefaultTestRecommendedBoxSize.DEPTH_METERS + 100000.0f)
                            .roundToPx(this)
                            .toDp()
                    }

                ApplicationSubspace(allowUnboundedSubspace = true) {
                    SpatialPanel(
                        SubspaceModifier.width(widthLargerThanRecommendedBox)
                            .height(heightLargerThanRecommendedBox)
                            .depth(depthLargerThanRecommendedBox)
                            .testTag("panel")
                    ) {}
                }
            }
        }

        val recommendedWidthPx =
            with(density!!) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
        val recommendedHeightPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
        val recommendedDepthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.DEPTH_METERS).roundToPx(this) }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertWidthIsAtLeast(recommendedWidthPx.toDp())
            .assertHeightIsAtLeast(recommendedHeightPx.toDp())
            .assertDepthIsAtLeast(recommendedDepthPx.toDp())
    }

    @Test
    fun applicationSubspace_userProvidedModifierBiggerThanDefault_isRespected() {
        val largeSize = 500000000.dp
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            TestSetup(runtime = runtime) {
                // The user provides a modifier bigger than the recommended box.
                ApplicationSubspace(modifier = SubspaceModifier.size(largeSize)) {
                    SpatialPanel(SubspaceModifier.fillMaxSize().testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertWidthIsEqualTo(largeSize)
        composeTestRule.onSubspaceNodeWithTag("panel").assertHeightIsEqualTo(largeSize)
        composeTestRule.onSubspaceNodeWithTag("panel").assertDepthIsEqualTo(largeSize)
    }

    @Test
    fun applicationSubspace_userProvidedModifierSmallerThanDefault_isRespected() {
        val smallSize = 2.dp
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            TestSetup(runtime = runtime) {
                // The user provides a modifier smaller than the recommended box.
                ApplicationSubspace(modifier = SubspaceModifier.size(smallSize)) {
                    SpatialPanel(SubspaceModifier.fillMaxSize().testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertWidthIsEqualTo(smallSize)
        composeTestRule.onSubspaceNodeWithTag("panel").assertHeightIsEqualTo(smallSize)
        composeTestRule.onSubspaceNodeWithTag("panel").assertDepthIsEqualTo(smallSize)
    }

    @Test
    fun applicationSubspace_constraintsChange_shouldRecomposeAndChangeConstraints() {
        val initialConstraints =
            SubspaceModifier.sizeIn(
                minWidth = 0.dp,
                maxWidth = 100.dp,
                minHeight = 0.dp,
                maxHeight = 100.dp,
                minDepth = 0.dp,
                maxDepth = VolumeConstraints.INFINITY.dp,
            )
        val updatedConstraints =
            SubspaceModifier.sizeIn(
                minWidth = 50.dp,
                maxWidth = 150.dp,
                minHeight = 50.dp,
                maxHeight = 150.dp,
                minDepth = 0.dp,
                maxDepth = VolumeConstraints.INFINITY.dp,
            )
        val constraintsState = mutableStateOf(initialConstraints)

        composeTestRule.setContent {
            TestSetup {
                ApplicationSubspace(modifier = constraintsState.value) {
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
                testNode = GroupEntity.create(LocalSession.current!!, "TestRoot")
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

    @Test
    fun gravityAlignedSubspace_alreadyInGravityAlignedSubspace_throwsError() {
        val runtime = createAdapterWithRecommendedBox()

        assertFailsWith<IllegalStateException>(
            message = "Gravity Aligned Subspace cannot be nested within another Subspace."
        ) {
            var density: Density? = null
            composeTestRule.setContent {
                density = LocalDensity.current
                TestSetup(runtime = runtime) {
                    GravityAlignedSubspace {
                        GravityAlignedSubspace {
                            SpatialPanel(
                                SubspaceModifier.fillMaxWidth()
                                    .fillMaxHeight()
                                    .testTag("innerPanel")
                            ) {}
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            assertNotNull(density)
            val expectedWidthPx =
                with(density) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
            val expectedHeightPx =
                with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
            composeTestRule
                .onSubspaceNodeWithTag("innerPanel")
                .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
                .assertWidthIsEqualTo(expectedWidthPx.toDp())
                .assertHeightIsEqualTo(expectedHeightPx.toDp())
        }
    }

    @Test
    fun gravityAlignedSubspace_recommendedBoxed_xrEnabled_contentIsCreated() {
        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertPositionInRootIsEqualTo(0.dp, 0.dp, 0.dp)
    }

    @Test
    fun gravityAlignedSubspace_recommendedBoxed_nonXr_contentIsNotCreated() {
        composeTestRule.setContent {
            TestSetup(isXrEnabled = false) {
                GravityAlignedSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertDoesNotExist()
    }

    @Test
    fun gravityAlignedSubspace_recommendedBoxed_contentIsParentedToActivitySpace() {
        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
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
    fun gravityAlignedSubspace_recommendedBoxed_nestedSubspace_contentIsParentedToContainingPanel() {
        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace {
                    SpatialPanel(SubspaceModifier.testTag("panel")) {
                        Subspace { SpatialPanel(SubspaceModifier.testTag("innerPanel")) {} }
                    }
                }
            }
        }
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
    fun gravityAlignedSubspace_recommendedBoxed_isDisposed() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    GravityAlignedSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
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
    fun gravityAlignedSubspace_recommendedBoxed_onlyOneSceneExists_afterSpaceModeChanges() {
        val fakeRuntime = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = fakeRuntime) {
                GravityAlignedSubspace { SpatialPanel(SubspaceModifier.testTag("panel")) {} }
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
    fun gravityAlignedSubspace_unbounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "Gravity Aligned Subspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
                        SpatialPanel { GravityAlignedSubspace(allowUnboundedSubspace = true) {} }
                    }
                }
            }
        }
    }

    @Test
    fun gravityAlignedSubspace_customBounded_asNestedInSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "Gravity Aligned Subspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    Subspace {
                        SpatialPanel {
                            GravityAlignedSubspace(
                                modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun gravityAlignedSubspace_unbounded_asNestedInUnboundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "Gravity Aligned Subspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    GravityAlignedSubspace(allowUnboundedSubspace = true) {
                        SpatialPanel() { GravityAlignedSubspace(allowUnboundedSubspace = true) {} }
                    }
                }
            }
        }
    }

    @Test
    fun gravityAlignedSubspace_customBounded_asNestedinCustomBoundedApplicationSubspace_throwsError() {
        assertFailsWith<IllegalStateException>(
            message = "Gravity Aligned Subspace cannot be nested within another Subspace."
        ) {
            composeTestRule.setContent {
                TestSetup {
                    GravityAlignedSubspace(
                        modifier = SubspaceModifier.sizeIn(0.dp, 50.dp, 0.dp, 50.dp)
                    ) {
                        SpatialPanel {
                            GravityAlignedSubspace(
                                modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Test
    fun gravityAlignedSubspace_fillMaxSize_higherDensity_returnsCorrectConstraints() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                density = LocalDensity.current
                TestSetup(runtime = runtime) {
                    GravityAlignedSubspace {
                        SpatialBox(
                            SubspaceModifier.fillMaxWidth(1.0f).fillMaxHeight(1.0f).testTag("box")
                        ) {}
                    }
                }
            }
        }

        assertNotNull(density)
        assertThat(density.density).isEqualTo(2f)
        val expectedWidthPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this) }
        val expectedHeightPx =
            with(density) { Meter(DefaultTestRecommendedBoxSize.HEIGHT_METERS).roundToPx(this) }
        composeTestRule
            .onSubspaceNodeWithTag("box")
            .assertWidthIsEqualTo(expectedWidthPx.toDp())
            .assertHeightIsEqualTo(expectedHeightPx.toDp())
    }

    @Test
    fun gravityAlignedSubspace_unbounded_fillMaxSize_doesNotReturnCorrectWidthAndHeight() {
        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace(allowUnboundedSubspace = true) {
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
    fun gravityAlignedSubspace_customBounded_fillMaxSize_returnsCorrectWidthAndHeight() {
        SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)

        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace(
                    modifier = SubspaceModifier.sizeIn(0.dp, 100.dp, 0.dp, 100.dp)
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
            .assertWidthIsEqualTo(100.toDp())
            .assertHeightIsEqualTo(100.toDp())
    }

    @Test
    fun gravityAlignedSubspace_allowUnboundedSubspaceIsTrue_isUnbounded() {
        var density: Density? = null
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            density = LocalDensity.current
            TestSetup(runtime = runtime) {
                // This large width is explicitly bigger than the recommended box width.
                val widthLargerThanRecommendedBox =
                    with(LocalDensity.current) {
                        Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS + 1000000.0f)
                            .roundToPx(this)
                            .toDp()
                    }

                GravityAlignedSubspace(allowUnboundedSubspace = true) {
                    SpatialPanel(
                        SubspaceModifier.size(widthLargerThanRecommendedBox).testTag("panel")
                    ) {}
                }
            }
        }

        val recommendedWidthDp =
            with(density!!) {
                Meter(DefaultTestRecommendedBoxSize.WIDTH_METERS).roundToPx(this).toDp()
            }

        composeTestRule.onSubspaceNodeWithTag("panel").assertWidthIsAtLeast(recommendedWidthDp)
    }

    @Test
    fun gravityAlignedSubspace_userProvidedModifierBiggerThanDefault_isRespected() {
        val largeWidth = 500000000.dp
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            TestSetup(runtime = runtime) {
                // The user provides a modifier bigger than the recommended box.
                GravityAlignedSubspace(modifier = SubspaceModifier.size(largeWidth)) {
                    SpatialPanel(SubspaceModifier.fillMaxSize().testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertWidthIsEqualTo(largeWidth)
    }

    @Test
    fun gravityAlignedSubspace_userProvidedModifierSmallerThanDefault_isRespected() {
        val smallWidth = 2.dp
        val runtime = createAdapterWithRecommendedBox()
        composeTestRule.setContent {
            TestSetup(runtime = runtime) {
                // The user provides a modifier smaller than the recommended box.
                GravityAlignedSubspace(modifier = SubspaceModifier.size(smallWidth)) {
                    SpatialPanel(SubspaceModifier.fillMaxSize().testTag("panel")) {}
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertWidthIsEqualTo(smallWidth)
    }

    @Test
    fun gravityAlignedSubspace_constraintsChange_shouldRecomposeAndChangeConstraints() {
        val initialConstraints =
            SubspaceModifier.sizeIn(
                minWidth = 0.dp,
                maxWidth = 100.dp,
                minHeight = 0.dp,
                maxHeight = 100.dp,
                minDepth = 0.dp,
                maxDepth = VolumeConstraints.INFINITY.dp,
            )
        val updatedConstraints =
            SubspaceModifier.sizeIn(
                minWidth = 50.dp,
                maxWidth = 150.dp,
                minHeight = 50.dp,
                maxHeight = 150.dp,
                minDepth = 0.dp,
                maxDepth = VolumeConstraints.INFINITY.dp,
            )
        val constraintsState = mutableStateOf<SubspaceModifier>(initialConstraints)

        composeTestRule.setContent {
            TestSetup {
                GravityAlignedSubspace(modifier = constraintsState.value) {
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
    fun privateGravityAlignedSubspace_mainPanelEntityDisabled_whenSubspaceLeavesComposition() {
        var showSubspace by mutableStateOf(true)

        composeTestRule.setContent {
            TestSetup {
                if (showSubspace) {
                    GravityAlignedSubspace {}
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
    fun gravityAlignedSubspace_retainsState_whenSwitchingModes() {
        val testJxrPlatformAdapter = createFakeRuntime(composeTestRule.activity)

        composeTestRule.setContent {
            TestSetup(runtime = testJxrPlatformAdapter) {
                GravityAlignedSubspace {
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
    fun gravityAlignedSubspace_retainsState_whenSwitchingModesStartingFromHomeSpace() {
        val runtime = createFakeRuntime(composeTestRule.activity)
        runtime.requestHomeSpaceMode()

        composeTestRule.setContent {
            CompositionLocalProvider {
                TestSetup(runtime = runtime) {
                    GravityAlignedSubspace {
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
    fun gravityAlignedSubspace_multipleApplicationSubspaces_haveTheSameRootContainer() {
        composeTestRule.setContent {
            TestSetup {
                assertThat(LocalSession.current!!.scene.keyEntity).isNull()
                GravityAlignedSubspace { SpatialBox(modifier = SubspaceModifier.testTag("Box")) {} }
                GravityAlignedSubspace {
                    SpatialBox(modifier = SubspaceModifier.testTag("Box2")) {}
                }
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
