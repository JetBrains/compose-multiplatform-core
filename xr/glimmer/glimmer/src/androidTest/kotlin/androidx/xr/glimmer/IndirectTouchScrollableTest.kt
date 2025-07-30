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

package androidx.xr.glimmer

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalIndirectTouchTypeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.indirect.IndirectTouchEvent
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performIndirectTouchEvent
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalIndirectTouchTypeApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class IndirectTouchScrollableTest {

    @get:Rule val rule = createComposeRule()

    private val scrollableBoxTag = "scrollableBox"

    private lateinit var scope: CoroutineScope

    private val focusRequester = FocusRequester()

    private fun ComposeContentTestRule.setContentAndGetScope(content: @Composable () -> Unit) {
        setContent {
            val actualScope = rememberCoroutineScope()
            SideEffect { scope = actualScope }
            content()
        }
    }

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun indirectTouchScrollable_horizontal_consumesEventsAccordingly() {
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent {
            Modifier.indirectTouchScrollable(
                state = controller,
                orientation = Orientation.Horizontal,
            )
        }

        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()

        rule.runOnIdle { assertThat(total).isGreaterThan(0) }

        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeBackward()
        rule.runOnIdle { assertThat(total).isWithin(0.01f).of(0.0f) }
    }

    @Test
    fun indirectTouchScrollable_horizontalScroll_reverse() {
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent {
            Modifier.indirectTouchScrollable(
                reverseDirection = true,
                state = controller,
                orientation = Orientation.Horizontal,
            )
        }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()

        rule.runOnIdle { assertThat(total).isLessThan(0) }

        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeBackward()
        rule.runOnIdle { assertThat(total).isWithin(0.01f).of(0.0f) }
    }

    @Test
    fun indirectTouchScrollable_vertical_consumesEventsAccordingly() {
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent {
            Modifier.indirectTouchScrollable(state = controller, orientation = Orientation.Vertical)
        }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()

        rule.runOnIdle { assertThat(total).isGreaterThan(0) }

        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeBackward()
        rule.runOnIdle { assertThat(total).isLessThan(0.01f) }
    }

    @Test
    fun indirectTouchScrollable_verticalScroll_reverse() {
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent {
            Modifier.indirectTouchScrollable(
                reverseDirection = true,
                state = controller,
                orientation = Orientation.Vertical,
            )
        }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()

        rule.runOnIdle { assertThat(total).isLessThan(0) }

        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeBackward()
        rule.runOnIdle { assertThat(total).isLessThan(0.01f) }
    }

    @Test
    fun indirectTouchScrollable_disabledWontCallLambda() {
        val enabled = mutableStateOf(true)
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent {
            Modifier.indirectTouchScrollable(
                state = controller,
                orientation = Orientation.Horizontal,
                enabled = enabled.value,
            )
        }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()
        val prevTotal =
            rule.runOnIdle {
                assertThat(total).isGreaterThan(0f)
                enabled.value = false
                total
            }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()
        rule.runOnIdle { assertThat(total).isEqualTo(prevTotal) }
    }

    @Test
    fun indirectTouchScrollable_notFocusedWontCallLambda() {
        var total = 0f
        val controller =
            ScrollableState(
                consumeScrollDelta = {
                    total += it
                    it
                }
            )
        setScrollableContent(false) {
            Modifier.indirectTouchScrollable(
                state = controller,
                orientation = Orientation.Horizontal,
            )
        }
        rule.onNodeWithTag(scrollableBoxTag).sendIndirectSwipeForward()
        rule.runOnIdle { assertThat(total).isEqualTo(0f) }
    }

    private fun setScrollableContent(
        enableInitialFocus: Boolean = true,
        scrollableModifierFactory: @Composable () -> Modifier,
    ) {

        rule.setContentAndGetScope {
            Box {
                val scrollable = scrollableModifierFactory()
                val initialFocus =
                    if (enableInitialFocus) {
                        Modifier.focusRequester(focusRequester).focusTarget()
                    } else {
                        Modifier
                    }
                Box(
                    modifier =
                        Modifier.testTag(scrollableBoxTag)
                            .size(100.dp)
                            .then(scrollable)
                            .then(initialFocus)
                )
            }
        }

        if (enableInitialFocus)
            rule.runOnIdle { assertThat(focusRequester.requestFocus()).isTrue() }
    }
}

/** Synthetically range the x movements from 1000 to 0 */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
private fun SemanticsNodeInteraction.sendIndirectSwipeEvent(
    from: Float,
    to: Float,
    touchpadWidth: Float = TouchPadEnd - TouchPadStart,
    stepCount: Int = 10,
    delayTimeMills: Long = 200L,
) {
    require(stepCount > 0) { "Step count should be at least 1" }
    val stepSize = touchpadWidth / stepCount
    val sign = if (from > to) -1 else 1

    var currentTime = SystemClock.uptimeMillis()
    var currentValue = from

    val down =
        MotionEvent.obtain(
            currentTime, // downTime,
            currentTime, // eventTime,
            MotionEvent.ACTION_DOWN,
            currentValue,
            Offset.Zero.y,
            0,
        )
    performIndirectTouchEvent(IndirectTouchEvent(down))
    currentTime += delayTimeMills
    currentValue += sign * stepSize

    repeat(stepCount) {
        val move =
            MotionEvent.obtain(
                currentTime,
                currentTime,
                MotionEvent.ACTION_MOVE,
                currentValue,
                Offset.Zero.y,
                0,
            )
        if (it != stepCount - 1) {
            currentTime += delayTimeMills
            currentValue += sign * stepSize
        }
        performIndirectTouchEvent(IndirectTouchEvent(move))
    }

    val up =
        MotionEvent.obtain(
            currentTime,
            currentTime,
            MotionEvent.ACTION_UP,
            currentValue,
            Offset.Zero.y,
            0,
        )
    performIndirectTouchEvent(IndirectTouchEvent(up))
}

/** Swiping towards the start of the touchpad */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
private fun SemanticsNodeInteraction.sendIndirectSwipeBackward() {
    sendIndirectSwipeEvent(TouchPadEnd, TouchPadStart)
}

/** Swiping towards the end of the touchpad. */
@OptIn(ExperimentalIndirectTouchTypeApi::class)
private fun SemanticsNodeInteraction.sendIndirectSwipeForward() {
    sendIndirectSwipeEvent(TouchPadStart, TouchPadEnd)
}

private const val TouchPadEnd = 1000f
private const val TouchPadStart = 0f
