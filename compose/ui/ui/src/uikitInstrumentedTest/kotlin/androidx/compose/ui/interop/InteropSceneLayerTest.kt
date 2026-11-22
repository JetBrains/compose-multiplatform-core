/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.interop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.IosInteropContainer
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.sceneInteropLayer
import androidx.compose.ui.viewinterop.sceneInteropLayerRoot
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.CoreGraphics.CGPathContainsPoint
import platform.CoreGraphics.CGPathIsEmpty
import platform.CoreGraphics.CGPointMake
import platform.QuartzCore.CAShapeLayer
import platform.UIKit.UIView

@OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
class InteropSceneLayerTest {
    @Test
    fun fullyOccludedOverlayIsMaskedOut() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(modifier = Modifier.fillMaxSize().sceneInteropLayerRoot()) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
                ) {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black)
                            .sceneInteropLayer(occludesLowerLayers = true, zIndex = 1f)
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        val maskLayer = interopWrappingView.layer.mask as? CAShapeLayer
        val maskPath = maskLayer?.path

        assertNotNull(maskLayer, "A fully covered overlay interop must retain a mask")
        assertTrue(
            maskPath?.let { path ->
                !CGPathContainsPoint(path, null, CGPointMake(1.0, 1.0), false)
            } == true,
            "A fully covered overlay interop mask must not contain any visible point",
        )
    }

    @Test
    fun removedOrReusedOverlayClearsItsMask() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(modifier = Modifier.fillMaxSize().sceneInteropLayerRoot()) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
                ) {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black)
                            .sceneInteropLayer(occludesLowerLayers = true, zIndex = 1f)
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        assertNotNull(
            interopWrappingView.layer.mask,
            "A fully covered overlay interop should have a mask before it is removed",
        )

        val holder = checkNotNull(checkNotNull(interopContainer).holderOfView(interopView))

        // Reusing a pooled holder must also release its mask layer before the holder is placed
        // into another composition.
        holder.onReuse()
        waitForIdle()
        assertNull(
            interopWrappingView.layer.mask,
            "A reused overlay interop must clear its retained mask layer",
        )

        // Removing the interop from the hierarchy must keep the mask layer released. This
        // prevents a released holder from retaining a CAShapeLayer and its CGPath.
        checkNotNull(interopContainer).unplace(holder)
        waitForIdle()

        assertNull(
            interopWrappingView.layer.mask,
            "A removed overlay interop must clear its retained mask layer",
        )
    }

    @Test
    fun overlappingOccludingLayersDoNotReopenCoveredInterop() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(modifier = Modifier.fillMaxSize().sceneInteropLayerRoot()) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
                ) {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black)
                            .sceneInteropLayer(occludesLowerLayers = true, zIndex = 1f)
                )
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(Color.Black)
                            .sceneInteropLayer(occludesLowerLayers = true, zIndex = 2f)
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        val maskLayer = interopWrappingView.layer.mask as? CAShapeLayer

        assertNotNull(maskLayer, "Overlapping occluding layers must retain an interop mask")
        assertTrue(
            maskLayer.path?.let { path ->
                !CGPathContainsPoint(path, null, CGPointMake(1.0, 1.0), false)
            } == true,
            "Overlapping occluding layers must not reopen covered interop regions",
        )
    }

    @Test
    fun contentModifierOccludesOverlayInteropDuringTransition() = runUIKitInstrumentedTest {
        animationSpeed = UIKitInstrumentedTest.IncreasedAnimationSpeed
        val interopView = UIView()
        val targetState = mutableStateOf("controls")
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            val transition = updateTransition(targetState = targetState.value)
            transition.AnimatedContent(
                modifier = Modifier.sceneInteropLayerRoot(),
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(tween(1_000)) { it },
                        initialContentExit = slideOutHorizontally(tween(1_000)) { -it / 3 },
                    )
                },
                contentModifier = { state ->
                    Modifier.sceneInteropLayer(
                        occludesLowerLayers = state == "details",
                        zIndex = if (state == "details") 1f else 0f,
                    )
                },
            ) { state ->
                if (state == "controls") {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        targetState.value = "details"
        waitUntil("Occluding animated child did not mask lower-scene interop") {
            checkNotNull(interopContainer).groupForInteropView(interopView)?.layer?.mask != null
        }

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        val maskLayer = interopWrappingView.layer.mask as? CAShapeLayer
        assertNotNull(maskLayer, "AnimatedContent content modifier should install an interop mask")
        assertTrue(
            maskLayer.path?.let { !CGPathIsEmpty(it) } == true,
            "AnimatedContent content modifier should retain visible interop regions during transition",
        )
    }

    @Test
    fun popTransitionDoesNotExposeUnmaskedInterop() = runUIKitInstrumentedTest {
        animationSpeed = UIKitInstrumentedTest.IncreasedAnimationSpeed
        val interopView = UIView()
        val targetState = mutableStateOf("details")
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            val transition = updateTransition(targetState = targetState.value)
            transition.AnimatedContent(
                modifier = Modifier.sceneInteropLayerRoot(),
                transitionSpec = {
                    if (transition.targetState == "controls") {
                        ContentTransform(
                            targetContentEnter = slideInHorizontally(tween(1_000)) { -it / 3 },
                            initialContentExit = slideOutHorizontally(tween(1_000)) { it },
                            targetContentZIndex = -1f,
                        )
                    } else {
                        ContentTransform(
                            targetContentEnter = slideInHorizontally(tween(1_000)) { it },
                            initialContentExit = slideOutHorizontally(tween(1_000)) { -it / 3 },
                        )
                    }
                },
                contentModifier = { state ->
                    Modifier.sceneInteropLayer(
                        occludesLowerLayers = state == "details",
                        zIndex = if (state == "details") 1f else 0f,
                    )
                },
            ) { state ->
                if (state == "controls") {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        targetState.value = "controls"
        var becameVisible = false
        repeat(60) {
            delay(5)
            val wrappingView = checkNotNull(interopContainer).groupForInteropView(interopView)
            if (wrappingView != null && interopView.window != null && !wrappingView.isHidden()) {
                becameVisible = true
                assertNotNull(
                    wrappingView.layer.mask,
                    "A visible popped-in overlay interop must already have its scene mask",
                )
            }
        }

        assertTrue(
            becameVisible,
            "The popped-in overlay interop should become visible during the transition",
        )
    }

    @Test
    fun unoccludedOverlayDoesNotUseAMask() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .sceneInteropLayerRoot()
                        .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
            ) {
                UIKitView(
                    factory = { interopView },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(placedAsOverlay = true),
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        assertNull(
            interopWrappingView.layer.mask,
            "An unclipped and unoccluded overlay interop should not be masked",
        )
    }

    @Test
    fun partiallyOccludedOverlayKeepsVisibleRegions() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(modifier = Modifier.fillMaxSize().sceneInteropLayerRoot()) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
                ) {
                    UIKitView(
                        factory = { interopView },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(placedAsOverlay = true),
                    )
                }
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black)
                            .sceneInteropLayer(occludesLowerLayers = true, zIndex = 1f)
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        val maskLayer = interopWrappingView.layer.mask as? CAShapeLayer

        assertNotNull(maskLayer, "A partially covered overlay interop must retain a mask")
        assertTrue(
            maskLayer.path?.let { !CGPathIsEmpty(it) } == true,
            "A partially covered overlay interop mask must retain visible regions",
        )
    }

    @Test
    fun clippedOverlayKeepsMaskVisibleRegions() = runUIKitInstrumentedTest {
        val interopView = UIView()
        var interopContainer: IosInteropContainer? = null

        setContent {
            interopContainer = LocalInteropContainer.current as IosInteropContainer

            Box(
                modifier =
                    Modifier.size(100.dp)
                        .sceneInteropLayerRoot()
                        .clip(RectangleShape)
                        .sceneInteropLayer(occludesLowerLayers = false, zIndex = 0f)
            ) {
                UIKitView(
                    factory = { interopView },
                    modifier = Modifier.requiredSize(200.dp),
                    properties = UIKitInteropProperties(placedAsOverlay = true),
                )
            }
        }

        waitUntil("Overlay interop view was not attached") { interopView.window != null }
        waitForIdle()

        val interopWrappingView =
            checkNotNull(checkNotNull(interopContainer).groupForInteropView(interopView))
        val maskLayer = interopWrappingView.layer.mask as? CAShapeLayer

        assertNotNull(maskLayer, "Clipped overlay interop must retain a mask")
        assertTrue(
            maskLayer.path?.let { !CGPathIsEmpty(it) } == true,
            "Clipped overlay interop mask must retain the Compose clip region",
        )
    }
}
