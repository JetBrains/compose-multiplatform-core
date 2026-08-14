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

package androidx.compose.ui.integrations

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.navigationevent.UIKitNavigationEventInput
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.FrameChoreographer
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneMediator
import androidx.compose.ui.scene.FontScale
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.uikit.EndEdgePanGestureBehavior
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIContentSizeCategory
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryUnspecified
import platform.UIKit.UIWindowScene

class ComposeSceneMediatorTest {
    @Test
    fun testDisposedMediatorShouldNotCrash() {
        runBlocking {
            val context = Dispatchers.Main + Job()
            val frameChoreographer = FrameChoreographer.choreographerForScene(UIWindowScene())
            val mediator = makeMediator(
                coroutineContext = context,
                frameChoreographer = frameChoreographer,
            )
            mediator.setContent(
                parentCompositionContext = frameChoreographer.frameRecomposer.compositionContext,
            ) {}
            context.cancel()

            mediator.composeSceneDensity = Density(2f)
            mediator.layoutDirection = LayoutDirection.Rtl
            mediator.interactionBounds = IntRect.Zero
            mediator.isFocusEnabled = true
            mediator.prepareAndGetSizeTransitionAnimation { onFrame -> onFrame(1.0f) }
        }
    }

    @Test
    fun testComposeSceneDensityChangesWhenFontScaleChanges() {
        var preferredContentSizeCategory: UIContentSizeCategory = UIContentSizeCategoryLarge
        val context = Dispatchers.Main + Job()
        val fontScale = FontScale(preferredContentSizeCategory = { preferredContentSizeCategory })
        val mediator = makeMediator(
            coroutineContext = context,
            fontScale = fontScale,
        )

        assertEquals(1f, mediator.composeSceneDensity.fontScale)
        preferredContentSizeCategory = UIContentSizeCategoryAccessibilityLarge
        fontScale.onTraitCollectionDidChange()

        assertEquals(1.5f, mediator.composeSceneDensity.fontScale)
        context.cancel()
    }

    @Test
    fun testComposeSceneDensityPreservesDensityWhenFontScaleChanges() {
        var preferredContentSizeCategory: UIContentSizeCategory = UIContentSizeCategoryLarge
        val context = Dispatchers.Main + Job()
        val fontScale = FontScale(preferredContentSizeCategory = { preferredContentSizeCategory })
        val mediator = makeMediator(
            coroutineContext = context,
            fontScale = fontScale,
        )

        assertEquals(Density(1f, 1f), mediator.composeSceneDensity)

        mediator.composeSceneDensity = Density(2f)

        preferredContentSizeCategory = UIContentSizeCategoryAccessibilityLarge
        fontScale.onTraitCollectionDidChange()

        assertEquals(Density(2f, 1.5f), mediator.composeSceneDensity)
        context.cancel()
    }

    private fun makeMediator(
        coroutineContext: CoroutineContext,
        frameChoreographer: FrameChoreographer = FrameChoreographer.choreographerForScene(UIWindowScene()),
        fontScale: FontScale = FontScale({ UIContentSizeCategoryUnspecified }),
    ): ComposeSceneMediator = ComposeSceneMediator(
        frameChoreographer = frameChoreographer,
        onFocusBehavior = OnFocusBehavior.DoNothing,
        isClearFocusOnMouseDownEnabled = false,
        focusedViewsList = null,
        windowContext = PlatformWindowContext(),
        fontScale = fontScale,
        architectureComponentsOwner = DefaultArchitectureComponentsOwner(),
        coroutineContext = coroutineContext,
        navigationEventInput = UIKitNavigationEventInput(
            density = Density(1f),
            initialLayoutDirection = LayoutDirection.Ltr,
            getTopLeftOffsetInWindow = { IntOffset.Zero },
            endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Disabled,
        ),
        interfaceOrientationState = mutableStateOf(InterfaceOrientation.Portrait),
        composeSceneFactory = { platformContext ->
            registerSkikoComposeImplementation()
            PlatformLayersComposeScene(
                frameRecomposer = frameChoreographer.frameRecomposer,
                density = Density(1f),
                composeSceneContext = object : ComposeSceneContext {
                    override val platformContext = platformContext
                },
                invalidateLayout = {},
                invalidateDraw = {},
            )
        },
    )
}
