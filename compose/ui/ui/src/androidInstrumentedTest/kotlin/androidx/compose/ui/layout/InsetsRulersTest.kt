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
package androidx.compose.ui.layout

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.collection.mutableObjectListOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.InsetsRulers.CaptionBar
import androidx.compose.ui.layout.InsetsRulers.DisplayCutout
import androidx.compose.ui.layout.InsetsRulers.Ime
import androidx.compose.ui.layout.InsetsRulers.MandatorySystemGestures
import androidx.compose.ui.layout.InsetsRulers.NavigationBars
import androidx.compose.ui.layout.InsetsRulers.SafeContent
import androidx.compose.ui.layout.InsetsRulers.SafeDrawing
import androidx.compose.ui.layout.InsetsRulers.SafeGestures
import androidx.compose.ui.layout.InsetsRulers.StatusBars
import androidx.compose.ui.layout.InsetsRulers.SystemGestures
import androidx.compose.ui.layout.InsetsRulers.TappableElement
import androidx.compose.ui.layout.InsetsRulers.Waterfall
import androidx.compose.ui.layout.Placeable.PlacementScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requestRemeasure
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsAnimationCompat.BoundsCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.roundToInt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SdkSuppress(minSdkVersion = 30)
@RunWith(Parameterized::class)
class InsetsRulersTest(val useDelegatableNode: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var composeView: AndroidComposeView
    private var insetsRect: IntRect? = null
    private var sourceRect: IntRect? = null
    private var targetRect: IntRect? = null
    private var isVisible = false
    private var isAnimating = false
    private var fraction = 0f
    private var interpolatedFraction = 0f
    private var interpolator: Interpolator? = null
    private var durationMillis = 0L
    private var alpha = 0f
    private var ignoringVisibilityRect: IntRect? = null
    private var contentWidth = 0
    private var contentHeight = 0
    private val displayCutoutRects = mutableObjectListOf<IntRect?>()

    @Before
    fun setup() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
    }

    private fun setContent(content: @Composable () -> Unit) {
        rule.setContent {
            composeView = LocalView.current as AndroidComposeView
            content()
        }
    }

    private fun setSimpleRulerContent(rulerState: State<RectRulers>) {
        setContent {
            Box(
                Modifier.fillMaxSize()
                    .onPlaced {
                        contentWidth = it.size.width
                        contentHeight = it.size.height
                    }
                    .rulerToRectDelegatableNode(rulerState.value) { node, rect ->
                        insetsRect = rect

                        sourceRect = null
                        targetRect = null
                        isAnimating = false

                        isVisible = false
                        fraction = 0f
                        interpolatedFraction = 0f
                        interpolator = null
                        alpha = 1f
                        durationMillis = 0L
                        ignoringVisibilityRect = null
                        displayCutoutRects.clear()

                        val rulers = rulerState.value
                        if (rulers is BasicAnimatableInsetsRulers) {
                            sourceRect = readRulers(rulers.source)
                            targetRect = readRulers(rulers.target)
                            isAnimating =
                                if (useDelegatableNode) {
                                    rulers.isAnimating(node)
                                } else {
                                    rulers.isAnimating(this)
                                }
                            if (rulers is AnimatableInsetsRulers) {
                                if (useDelegatableNode) {
                                    isVisible = rulers.isVisible(node)
                                    fraction = rulers.fraction(node)
                                    interpolatedFraction = rulers.interpolatedFraction(node)
                                    interpolator = rulers.interpolator(node)
                                    alpha = rulers.alpha(node)
                                    durationMillis = rulers.durationMillis(node)
                                } else {
                                    isVisible = rulers.isVisible(this)
                                    fraction = rulers.fraction(this)
                                    interpolatedFraction = rulers.interpolatedFraction(this)
                                    interpolator = rulers.interpolator(this)
                                    alpha = rulers.alpha(this)
                                    durationMillis = rulers.durationMillis(this)
                                }
                                ignoringVisibilityRect = readRulers(rulers.rulersIgnoringVisibility)
                            }
                        } else if (rulers is DisplayCutoutInsetsRulers) {
                            val cutouts =
                                if (useDelegatableNode) {
                                    rulers.cutoutInsets(node)
                                } else {
                                    rulers.cutoutInsets(this)
                                }
                            cutouts.forEach { cutoutRulers ->
                                displayCutoutRects.add(readRulers(cutoutRulers))
                            }
                        }
                    }
            )
        }
    }

    private fun sendOnApplyWindowInsets(insets: WindowInsetsCompat) {
        val view = composeView.parent as View
        rule.runOnIdle { composeView.insetsListener.onApplyWindowInsets(view, insets) }
    }

    private fun startAnimation(
        animation: WindowInsetsAnimationCompat,
        type: Int,
        low: Insets,
        high: Insets,
        target: Insets
    ) {
        val view = composeView.parent as View
        rule.runOnIdle {
            val insetsListener = composeView.insetsListener
            insetsListener.onPrepare(animation)
            insetsListener.onApplyWindowInsets(view, createInsets(type to target))
            insetsListener.onStart(animation, BoundsCompat(low, high))
        }
    }

    private fun progressAnimation(
        animation: WindowInsetsAnimationCompat,
        insets: WindowInsetsCompat
    ) {
        val view = composeView.parent as View
        rule.runOnIdle {
            val insetsListener = composeView.insetsListener
            insetsListener.onProgress(insets, mutableListOf(animation))
            insetsListener.onApplyWindowInsets(view, insets)
        }
    }

    private fun endAnimation(animation: WindowInsetsAnimationCompat, insets: WindowInsetsCompat) {
        val view = composeView.parent as View
        rule.runOnIdle {
            val insetsListener = composeView.insetsListener
            insetsListener.onEnd(animation)
            insetsListener.onApplyWindowInsets(view, insets)
        }
    }

    private fun assertNotAnimating(rulers: RectRulers, updatedRulers: RectRulers = rulers) {
        val message = "$rulers / $updatedRulers"
        assertWithMessage(message).that(sourceRect).isNull()
        assertWithMessage(message).that(targetRect).isNull()
        assertWithMessage(message).that(isAnimating).isFalse()
        assertWithMessage(message).that(fraction).isEqualTo(0f)
        assertWithMessage(message).that(interpolatedFraction).isEqualTo(0f)
        assertWithMessage(message).that(interpolator).isNull()
        assertWithMessage(message).that(durationMillis).isEqualTo(0L)
    }

    @Test
    fun normalRulers() {
        val rulerState = mutableStateOf<RectRulers>(CaptionBar)
        setSimpleRulerContent(rulerState)
        val normalRulersList =
            listOf(
                TappableElement,
                StatusBars,
                Ime,
                NavigationBars,
                CaptionBar,
                MandatorySystemGestures,
                SystemGestures,
            )
        normalRulersList.forEach { visibleRulers ->
            rulerState.value = visibleRulers
            rule.waitForIdle()
            InsetsRulerTypes.forEach { (rulers, type) ->
                val insets = createInsets(type to Insets.of(1, 2, 3, 5))
                sendOnApplyWindowInsets(insets)
                rule.runOnIdle {
                    val expectedRect =
                        if (visibleRulers === rulers) {
                            IntRect(1, 2, contentWidth - 3, contentHeight - 5)
                        } else {
                            IntRect(0, 0, contentWidth, contentHeight)
                        }
                    val message = "$visibleRulers / $rulers"
                    assertWithMessage(message).that(insetsRect).isEqualTo(expectedRect)
                    if (visibleRulers === Ime) {
                        assertWithMessage(message).that(ignoringVisibilityRect).isNull()
                    } else {
                        assertWithMessage(message)
                            .that(ignoringVisibilityRect)
                            .isEqualTo(expectedRect)
                    }
                    assertNotAnimating(rulers)
                    assertWithMessage(message).that(isVisible).isEqualTo(visibleRulers === rulers)
                }
            }
        }
    }

    @Test
    fun ignoringVisibility() {
        val rulerState = mutableStateOf<RectRulers>(CaptionBar)
        setSimpleRulerContent(rulerState)
        val ignoringVisibilityRulersList =
            listOf(
                TappableElement,
                StatusBars,
                NavigationBars,
                CaptionBar,
                MandatorySystemGestures,
                SystemGestures,
            )
        ignoringVisibilityRulersList.forEach { rulers ->
            rulerState.value = rulers
            rule.waitForIdle()

            val type = InsetsRulerTypes[rulers]!!
            val expectedInsets = Insets.of(1, 2, 3, 5)
            val insets = createInsetsIgnoringVisibility(type to expectedInsets)
            sendOnApplyWindowInsets(insets)
            rule.runOnIdle {
                assertWithMessage(rulers.toString())
                    .that(insetsRect)
                    .isEqualTo(IntRect(0, 0, contentWidth, contentHeight))
                assertWithMessage(rulers.toString())
                    .that(ignoringVisibilityRect)
                    .isEqualTo(IntRect(1, 2, contentWidth - 3, contentHeight - 5))
            }
        }
    }

    @Test
    fun displayRulers() {
        val gestureRulerList =
            listOf(
                DisplayCutout,
                Waterfall,
            )
        val rulerState = mutableStateOf<RectRulers>(CaptionBar)
        setSimpleRulerContent(rulerState)

        gestureRulerList.forEach { gestureRulers ->
            rulerState.value = gestureRulers
            rule.waitForIdle()
            InsetsRulerTypes.forEach { (rulers, type) ->
                val insets = createInsets(type to Insets.of(1, 2, 3, 5))
                sendOnApplyWindowInsets(insets)
                rule.runOnIdle {
                    val expectedRect =
                        if (
                            rulers === gestureRulers ||
                                (gestureRulers === DisplayCutout && rulers === Waterfall)
                        ) {
                            IntRect(1, 2, contentWidth - 3, contentHeight - 5)
                        } else {
                            IntRect(0, 0, contentWidth, contentHeight)
                        }
                    val message = "$gestureRulers / $rulers"
                    assertWithMessage(message).that(insetsRect).isEqualTo(expectedRect)
                    assertNotAnimating(rulers)
                    // display rulers are only RectRulers and has no visibility information
                    assertWithMessage(message).that(isVisible).isFalse()
                }
            }
        }
    }

    /** Make sure that when the display cutout is set that it includes the rects for each side. */
    @Test
    fun displayCutoutRulers() {
        setSimpleRulerContent(mutableStateOf(DisplayCutout))

        val insets = createInsets(Type.displayCutout() to Insets.of(1, 2, 3, 5))
        sendOnApplyWindowInsets(insets)
        rule.runOnIdle {
            assertThat(displayCutoutRects.size).isEqualTo(4)
            assertThat(displayCutoutRects.any { it == null }).isFalse()
            assertThat(displayCutoutRects.asList())
                .containsExactly(
                    IntRect(0, 0, 1, contentHeight),
                    IntRect(0, 0, contentWidth, 2),
                    IntRect(contentWidth - 3, 0, contentWidth, contentHeight),
                    IntRect(0, contentHeight - 5, contentWidth, contentHeight)
                )
        }
    }

    @Test
    fun mergedRulers() {
        val mergedRulersMap =
            mapOf(
                SafeGestures to
                    listOf(
                        Type.systemGestures(),
                        Type.mandatorySystemGestures(),
                        Type.tappableElement(),
                        WaterfallType
                    ),
                SafeDrawing to
                    listOf(
                        Type.captionBar(),
                        Type.ime(),
                        Type.navigationBars(),
                        Type.statusBars(),
                        Type.tappableElement(),
                        Type.displayCutout(),
                        WaterfallType
                    ),
                SafeContent to
                    listOf(
                        Type.systemGestures(),
                        Type.mandatorySystemGestures(),
                        Type.tappableElement(),
                        Type.captionBar(),
                        Type.ime(),
                        Type.navigationBars(),
                        Type.statusBars(),
                        Type.displayCutout(),
                        WaterfallType
                    )
            )

        val rulerState = mutableStateOf<RectRulers>(SafeGestures)
        setSimpleRulerContent(rulerState)

        mergedRulersMap.forEach { gestureRulers, includedTypes ->
            rulerState.value = gestureRulers
            rule.waitForIdle()
            InsetsRulerTypes.forEach { (rulers, type) ->
                val insets = createInsets(type to Insets.of(1, 2, 3, 5))
                sendOnApplyWindowInsets(insets)
                rule.runOnIdle {
                    val expectedRect =
                        if (type in includedTypes) {
                            IntRect(1, 2, contentWidth - 3, contentHeight - 5)
                        } else {
                            IntRect(0, 0, contentWidth, contentHeight)
                        }
                    val message = "$gestureRulers / $rulers}"
                    assertWithMessage(message).that(insetsRect).isEqualTo(expectedRect)
                    assertNotAnimating(rulers)
                    assertWithMessage(message).that(isVisible).isFalse()
                }
            }
        }
    }

    @Test
    fun animatingRulers() {
        val rulerState = mutableStateOf<RectRulers>(Ime)
        setSimpleRulerContent(rulerState)

        val animatableRulersList =
            listOf(
                CaptionBar,
                Ime,
                NavigationBars,
                StatusBars,
                TappableElement,
            )
        animatableRulersList.forEach { animatableRulers ->
            rulerState.value = animatableRulers
            rule.waitForIdle()

            val type = InsetsRulerTypes[animatableRulers]!!
            val insets = createInsets(type to Insets.of(10, 20, 30, 50))
            // set the initial insets
            sendOnApplyWindowInsets(insets)

            val animationInterpolator = AccelerateDecelerateInterpolator()
            val animation = WindowInsetsAnimationCompat(type, animationInterpolator, 1000L)
            animation.alpha = 1f
            val targetInsets = Insets.of(0, 0, 0, 0)
            val sourceInsets = Insets.of(10, 20, 30, 50)

            startAnimation(animation, type, targetInsets, sourceInsets, targetInsets)
            rule.runOnIdle { assertAnimating(animatableRulers, sourceInsets, 0f) }

            animation.fraction = 0.25f
            animation.alpha = 0.75f
            progressAnimation(
                animation,
                createInsets(type to lerp(sourceInsets, targetInsets, 0.25f))
            )
            rule.runOnIdle { assertAnimating(animatableRulers, sourceInsets, 0.25f) }
            animation.fraction = 0.75f
            animation.alpha = 0.25f
            progressAnimation(
                animation,
                createInsets(type to lerp(sourceInsets, targetInsets, 0.75f))
            )
            rule.runOnIdle { assertAnimating(animatableRulers, sourceInsets, 0.75f) }

            animation.fraction = 1f
            animation.alpha = 0f
            endAnimation(animation, createInsets(type to Insets.of(0, 0, 0, 0)))
            rule.runOnIdle {
                val expectedRect = IntRect(0, 0, contentWidth, contentHeight)
                assertWithMessage(animatableRulers.toString())
                    .that(insetsRect)
                    .isEqualTo(expectedRect)
                assertNotAnimating(animatableRulers)
                assertWithMessage(animatableRulers.toString()).that(isVisible).isFalse()
            }
        }
    }

    private fun lerp(a: Insets, b: Insets, fraction: Float): Insets {
        val left = (a.left + fraction * (b.left - a.left)).toInt()
        val top = (a.top + fraction * (b.top - a.top)).toInt()
        val right = (a.right + fraction * (b.right - a.right)).toInt()
        val bottom = (a.bottom + fraction * (b.bottom - a.bottom)).toInt()
        return Insets.of(left, top, right, bottom)
    }

    private fun assertAnimating(
        animatableRulers: AnimatableInsetsRulers,
        source: Insets,
        expectedFraction: Float
    ) {
        val target = Insets.of(0, 0, 0, 0)
        val insets = lerp(source, target, expectedFraction)
        val expectedRect =
            IntRect(
                insets.left,
                insets.top,
                contentWidth - insets.right,
                contentHeight - insets.bottom
            )
        val rulerName = animatableRulers.toString()
        assertWithMessage(rulerName).that(insetsRect).isEqualTo(expectedRect)
        val expectedAlpha = 1f - expectedFraction

        val expectedSourceRect =
            IntRect(
                source.left,
                source.top,
                contentWidth - source.right,
                contentHeight - source.bottom
            )
        assertWithMessage(rulerName).that(sourceRect).isEqualTo(expectedSourceRect)
        val expectedTargetRect =
            IntRect(
                target.left,
                target.top,
                contentWidth - target.right,
                contentHeight - target.bottom
            )
        assertWithMessage(rulerName).that(targetRect).isEqualTo(expectedTargetRect)
        assertWithMessage(rulerName).that(isAnimating).isTrue()
        assertWithMessage(rulerName).that(fraction).isWithin(0.01f).of(expectedFraction)
        assertWithMessage(rulerName).that(interpolator).isNotNull()
        val expectedInterpolatedFraction = interpolator!!.getInterpolation(expectedFraction)
        assertWithMessage(rulerName)
            .that(interpolatedFraction)
            .isWithin(0.01f)
            .of(expectedInterpolatedFraction)
        assertWithMessage(rulerName).that(durationMillis).isEqualTo(1000L)
        assertWithMessage(rulerName).that(alpha).isWithin(0.01f).of(expectedAlpha)

        assertWithMessage(rulerName).that(isVisible).isTrue()
    }

    private fun assertAnimating(
        animatableRulers: BasicAnimatableInsetsRulers,
        animatingRulers: AnimatableInsetsRulers,
        source: Insets,
        expectedFraction: Float
    ) {
        val target = Insets.of(0, 0, 0, 0)
        val insets = lerp(source, target, expectedFraction)
        val expectedRect =
            IntRect(
                insets.left,
                insets.top,
                contentWidth - insets.right,
                contentHeight - insets.bottom
            )
        val rulerName = animatableRulers.toString()
        val modRulerName = animatingRulers.toString()
        val message = "$rulerName / $modRulerName"
        assertWithMessage(message).that(insetsRect).isEqualTo(expectedRect)

        val expectedSourceRect =
            IntRect(
                source.left,
                source.top,
                contentWidth - source.right,
                contentHeight - source.bottom
            )
        assertWithMessage(message).that(sourceRect).isEqualTo(expectedSourceRect)
        val expectedTargetRect =
            IntRect(
                target.left,
                target.top,
                contentWidth - target.right,
                contentHeight - target.bottom
            )
        assertWithMessage(message).that(targetRect).isEqualTo(expectedTargetRect)
        assertWithMessage(message).that(isAnimating).isTrue()

        // None of these should be set
        assertWithMessage(message).that(fraction).isEqualTo(0f)
        assertWithMessage(message).that(interpolator).isNull()
        assertWithMessage(message).that(interpolatedFraction).isEqualTo(0f)
        assertWithMessage(message).that(durationMillis).isEqualTo(0L)
        assertWithMessage(message).that(alpha).isEqualTo(1f)
    }

    @Test
    fun animateMergedRulers() {
        // display cutout and waterfall aren't animatable
        val mergedRulersMap =
            mapOf(
                SafeGestures to
                    listOf(
                        Type.systemGestures(),
                        Type.mandatorySystemGestures(),
                        Type.tappableElement()
                    ),
                SafeDrawing to
                    listOf(
                        Type.captionBar(),
                        Type.ime(),
                        Type.navigationBars(),
                        Type.statusBars(),
                        Type.tappableElement()
                    ),
                SafeContent to
                    listOf(
                        Type.systemGestures(),
                        Type.mandatorySystemGestures(),
                        Type.tappableElement(),
                        Type.captionBar(),
                        Type.ime(),
                        Type.navigationBars(),
                        Type.statusBars()
                    )
            )

        val rulerState = mutableStateOf<RectRulers>(SafeGestures)
        setSimpleRulerContent(rulerState)

        mergedRulersMap.forEach { mergedRulers, includedTypes ->
            rulerState.value = mergedRulers
            rule.waitForIdle()
            InsetsRulerTypes.forEach { (rulers, type) ->
                if (rulers is AnimatableInsetsRulers) {
                    // set the initial insets
                    val insets = createInsets(type to Insets.of(10, 20, 30, 50))
                    sendOnApplyWindowInsets(insets)

                    val shouldBeAnimating = type in includedTypes

                    val animationInterpolator = AccelerateDecelerateInterpolator()
                    val animation = WindowInsetsAnimationCompat(type, animationInterpolator, 1000L)
                    animation.alpha = 1f
                    val targetInsets = Insets.of(0, 0, 0, 0)
                    val sourceInsets = Insets.of(10, 20, 30, 50)

                    startAnimation(animation, type, targetInsets, sourceInsets, targetInsets)
                    rule.waitForIdle()
                    rule.runOnIdle {
                        if (shouldBeAnimating) {
                            assertAnimating(mergedRulers, rulers, sourceInsets, 0f)
                        } else {
                            assertNotAnimating(mergedRulers, rulers)
                            assertWithMessage(mergedRulers.toString()).that(isVisible).isFalse()
                        }
                    }
                    animation.fraction = 0.25f
                    animation.alpha = 0.75f
                    progressAnimation(
                        animation,
                        createInsets(type to lerp(sourceInsets, targetInsets, 0.25f))
                    )
                    rule.runOnIdle {
                        if (shouldBeAnimating) {
                            assertAnimating(mergedRulers, rulers, sourceInsets, 0.25f)
                        } else {
                            assertNotAnimating(mergedRulers, rulers)
                            assertWithMessage(mergedRulers.toString()).that(isVisible).isFalse()
                        }
                    }
                    animation.fraction = 0.75f
                    animation.alpha = 0.25f
                    progressAnimation(
                        animation,
                        createInsets(type to lerp(sourceInsets, targetInsets, 0.75f))
                    )
                    rule.runOnIdle {
                        if (shouldBeAnimating) {
                            assertAnimating(mergedRulers, rulers, sourceInsets, 0.75f)
                        } else {
                            assertNotAnimating(mergedRulers, rulers)
                            assertWithMessage(mergedRulers.toString()).that(isVisible).isFalse()
                        }
                    }

                    animation.fraction = 1f
                    animation.alpha = 0f
                    endAnimation(animation, createInsets(type to Insets.of(0, 0, 0, 0)))
                    rule.runOnIdle {
                        val expectedRect = IntRect(0, 0, contentWidth, contentHeight)
                        assertWithMessage(mergedRulers.toString())
                            .that(insetsRect)
                            .isEqualTo(expectedRect)
                        assertNotAnimating(mergedRulers, rulers)
                        assertWithMessage(mergedRulers.toString()).that(isVisible).isFalse()
                    }
                }
            }
        }
    }

    @Test
    fun rulersInCenteredDialog() {
        var boxInsetsRect: IntRect? = null
        var dialogInsetsRect: IntRect? = null
        lateinit var coordinates: LayoutCoordinates

        lateinit var composeView: AndroidComposeView
        lateinit var dialogComposeView: AndroidComposeView
        rule.setContent {
            composeView = LocalView.current as AndroidComposeView
            Box(
                Modifier.fillMaxSize()
                    .onPlaced { coordinates = it }
                    .rulerToRect(SafeDrawing) { boxInsetsRect = it }
            ) {
                Dialog(
                    onDismissRequest = {},
                    properties =
                        DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                ) {
                    dialogComposeView = LocalView.current as AndroidComposeView
                    with(LocalDensity.current) {
                        Box(
                            Modifier.size(50.toDp()).rulerToRect(SafeContent) {
                                dialogInsetsRect = it
                            }
                        )
                    }
                }
            }
        }
        rule.runOnIdle {
            val insets =
                createInsets(
                    Type.displayCutout() to Insets.of(5, 0, 0, 0),
                    Type.statusBars() to Insets.of(0, 7, 0, 0),
                    Type.navigationBars() to Insets.of(0, 0, 11, 0),
                    Type.tappableElement() to Insets.of(0, 0, 0, 13),
                )
            val view = composeView.parent as View
            composeView.insetsListener.onApplyWindowInsets(view, insets)
            val dialogView = dialogComposeView.parent as View
            dialogComposeView.insetsListener.onApplyWindowInsets(dialogView, createInsets())
        }

        rule.runOnIdle {
            val width = coordinates.size.width
            val height = coordinates.size.height
            assertThat(boxInsetsRect).isEqualTo(IntRect(5, 7, width - 11, height - 13))
            assertThat(dialogInsetsRect).isEqualTo(IntRect(0, 0, 50, 50))
        }
    }

    @Test
    fun rulersInFullScreenDialog() {
        var boxInsetsRect: IntRect? = null
        var dialogInsetsRect: IntRect? = null
        lateinit var coordinates: LayoutCoordinates

        lateinit var composeView: AndroidComposeView
        lateinit var dialogComposeView: AndroidComposeView
        rule.setContent {
            composeView = LocalView.current as AndroidComposeView
            Box(
                Modifier.fillMaxSize()
                    .onPlaced { coordinates = it }
                    .rulerToRect(SafeDrawing) { boxInsetsRect = it }
            ) {
                Dialog(
                    onDismissRequest = {},
                    properties =
                        DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                ) {
                    dialogComposeView = LocalView.current as AndroidComposeView
                    Box(Modifier.fillMaxSize().rulerToRect(SafeContent) { dialogInsetsRect = it })
                }
            }
        }
        rule.runOnIdle {
            val insets =
                createInsets(
                    Type.displayCutout() to Insets.of(5, 0, 0, 0),
                    Type.statusBars() to Insets.of(0, 7, 0, 0),
                    Type.navigationBars() to Insets.of(0, 0, 11, 0),
                    Type.tappableElement() to Insets.of(0, 0, 0, 13),
                )
            val view = composeView.parent as View
            composeView.insetsListener.onApplyWindowInsets(view, insets)
            val dialogView = dialogComposeView.parent as View
            dialogComposeView.insetsListener.onApplyWindowInsets(dialogView, insets)
        }

        rule.runOnIdle {
            val width = coordinates.size.width
            val height = coordinates.size.height
            assertThat(boxInsetsRect).isEqualTo(IntRect(5, 7, width - 11, height - 13))
            assertThat(dialogInsetsRect).isEqualTo(IntRect(5, 7, width - 11, height - 13))
        }
    }

    private fun createInsets(vararg insetValues: Pair<Int, Insets>): WindowInsetsCompat {
        val builder = WindowInsetsCompat.Builder()
        insetValues.forEach { (type, insets) ->
            if (type != Type.displayCutout() && type != WaterfallType) {
                builder.setInsets(type, insets)
                if (type != Type.ime()) {
                    builder.setInsetsIgnoringVisibility(type, insets)
                }
                builder.setVisible(
                    type,
                    insets.left != 0 || insets.top != 0 || insets.right != 0 || insets.bottom != 0
                )
            }
        }
        val map = mapOf(*insetValues)
        val displayCutoutInsets = map[Type.displayCutout()]
        if (displayCutoutInsets != null) {
            val left =
                if (displayCutoutInsets.left == 0) null
                else android.graphics.Rect(0, 0, displayCutoutInsets.left, contentHeight)
            val top =
                if (displayCutoutInsets.top == 0) null
                else android.graphics.Rect(0, 0, contentWidth, displayCutoutInsets.top)
            val right =
                if (displayCutoutInsets.right == 0) null
                else
                    android.graphics.Rect(
                        contentWidth - displayCutoutInsets.right,
                        0,
                        contentWidth,
                        contentHeight
                    )
            val bottom =
                if (displayCutoutInsets.bottom == 0) null
                else
                    android.graphics.Rect(
                        0,
                        contentHeight - displayCutoutInsets.bottom,
                        contentWidth,
                        contentHeight
                    )
            val cutout =
                DisplayCutoutCompat(
                    displayCutoutInsets,
                    left,
                    top,
                    right,
                    bottom,
                    Insets.of(0, 0, 0, 0)
                )
            builder.setDisplayCutout(cutout)
        } else {
            val waterfallInsets = map[WaterfallType]
            if (waterfallInsets != null) {
                val displayCutout =
                    DisplayCutoutCompat(waterfallInsets, null, null, null, null, waterfallInsets)
                builder.setDisplayCutout(displayCutout)
            }
        }
        val windowInsets = builder.build()
        val method =
            windowInsets::class
                .java
                .getDeclaredMethod("setOverriddenInsets", Array<Insets>::class.java)
        method.isAccessible = true
        val lastTypeMask = 1 shl 8
        val insets = arrayOfNulls<Insets>(9)
        var typeMask = 1
        while (typeMask <= lastTypeMask) {
            insets[indexOf(typeMask)] = map[typeMask] ?: Insets.of(0, 0, 0, 0)
            typeMask = typeMask shl 1
        }
        method.invoke(windowInsets, insets)
        return windowInsets
    }

    private fun createInsetsIgnoringVisibility(
        vararg insetValues: Pair<Int, Insets>
    ): WindowInsetsCompat {
        val builder = WindowInsetsCompat.Builder()
        for (type in NormalInsetsTypes) {
            val targetInsets =
                insetValues.firstOrNull { it.first == type }?.second ?: Insets.of(0, 0, 0, 0)
            builder.setInsetsIgnoringVisibility(type, targetInsets)
            builder.setVisible(type, false)
            builder.setInsets(type, Insets.of(0, 0, 0, 0))
        }
        return builder.build()
    }

    private fun indexOf(type: Int): Int =
        when (type) {
            Type.statusBars() -> 0
            Type.navigationBars() -> 1
            Type.captionBar() -> 2
            Type.ime() -> 3
            Type.systemGestures() -> 4
            Type.mandatorySystemGestures() -> 5
            Type.tappableElement() -> 6
            Type.displayCutout() -> 7
            1 shl 8 -> 8
            else -> -1
        }

    private fun PlacementScope.readRulers(rulers: RectRulers): IntRect? {
        val left = rulers.left.current(-1f).roundToInt()
        val top = rulers.top.current(-1f).roundToInt()
        val right = rulers.right.current(-1f).roundToInt()
        val bottom = rulers.bottom.current(-1f).roundToInt()
        if (left == -1 || top == -1 || right == -1 || bottom == -1) {
            return null
        }
        return IntRect(left, top, right, bottom)
    }

    private fun Modifier.rulerToRect(
        ruler: RectRulers,
        block: PlacementScope.(IntRect?) -> Unit
    ): Modifier = layout { m, c ->
        val p = m.measure(c)
        layout(p.width, p.height) {
            p.place(0, 0)
            block(readRulers(ruler))
        }
    }

    private fun Modifier.rulerToRectDelegatableNode(
        ruler: RectRulers,
        block: PlacementScope.(DelegatableNode, IntRect?) -> Unit
    ): Modifier = layoutWithDelegatableNode { m, c, d ->
        val p = m.measure(c)
        layout(p.width, p.height) {
            p.place(0, 0)
            block(d, readRulers(ruler))
        }
    }

    private fun Modifier.layoutWithDelegatableNode(
        measurePolicy: MeasureScope.(Measurable, Constraints, DelegatableNode) -> MeasureResult
    ): Modifier = this.then(MyLayoutModifierElement(measurePolicy))

    private class MyLayoutModifierElement(
        val measurePolicy: MeasureScope.(Measurable, Constraints, DelegatableNode) -> MeasureResult
    ) : ModifierNodeElement<MyLayoutModifierNode>() {
        override fun create(): MyLayoutModifierNode = MyLayoutModifierNode(measurePolicy)

        override fun hashCode(): Int = measurePolicy.hashCode()

        override fun equals(other: Any?): Boolean =
            other is MyLayoutModifierElement &&
                other.measurePolicy.hashCode() == measurePolicy.hashCode()

        override fun update(node: MyLayoutModifierNode) {
            node.measurePolicy = measurePolicy
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "layoutWithDelegatableNode"
            value = measurePolicy
        }
    }

    private class MyLayoutModifierNode(
        measurePolicy: MeasureScope.(Measurable, Constraints, DelegatableNode) -> MeasureResult
    ) : Modifier.Node(), LayoutModifierNode {
        var measurePolicy:
            MeasureScope.(Measurable, Constraints, DelegatableNode) -> MeasureResult =
            measurePolicy
            set(value) {
                if (value.hashCode() != field.hashCode()) {
                    field = value
                    requestRemeasure()
                }
            }

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            return measurePolicy.invoke(this, measurable, constraints, this@MyLayoutModifierNode)
        }
    }

    companion object {
        const val WaterfallType = -1
        val InsetsRulerTypes =
            mapOf(
                TappableElement to Type.tappableElement(),
                StatusBars to Type.statusBars(),
                Ime to Type.ime(),
                SystemGestures to Type.systemGestures(),
                MandatorySystemGestures to Type.mandatorySystemGestures(),
                DisplayCutout to Type.displayCutout(),
                NavigationBars to Type.navigationBars(),
                CaptionBar to Type.captionBar(),
                Waterfall to WaterfallType,
            )

        val NormalInsetsTypes =
            intArrayOf(
                Type.tappableElement(),
                Type.statusBars(),
                Type.systemGestures(),
                Type.mandatorySystemGestures(),
                Type.navigationBars(),
                Type.captionBar(),
            )

        @Parameters(name = "Use DelegatableNode = {0}")
        @JvmStatic
        fun parameters() = listOf(false, true)
    }
}
