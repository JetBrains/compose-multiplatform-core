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

package androidx.compose.ui.input.indirect

import android.os.SystemClock
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeUiFlags.isOptimizedFocusEventDispatchEnabled
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.InputDeviceCompat.SOURCE_TOUCH_NAVIGATION
import androidx.test.core.view.MotionEventBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/*
 * Verifies SOURCE_TOUCH_NAVIGATION [MotionEvent]s passing through the system properly trigger
 * Indirect Events (and their navigation movements) in Compose.
 *
 * Important Note: While we set every other value in the MotionEvent for these tests, we manually
 * set the IndirectPointerEventPrimaryAxis in Compose vs. deriving it from the [MotionEvent],
 * because setting the [MotionEvent] fields required for deriving the IndirectPointerEventPrimaryAxis
 * (InputDevice and InputDevice.MotionRange) do not have setters, and we can't mock() or spy() on
 * MotionEvent to make the tests work (see details below):
 *   - spy() - While spy "wraps" the original MotionEvent, it actually makes a copy of the
 * mNativePtr (so both the original MotionEvent and the spy have a non-zero, matching number).
 * During garbage collection, both the spy object and the original MotionEvent have their
 * `finalize()` methods triggered and you aren't allowed to override that method in spy. If the
 * mNativePtr is a non-zero value, the MotionEvent pointer associated with that id is deleted in
 * android_view_MotionEvent_nativeDispose(). Since they both have a non-zero value (the same value)
 * and both are triggered separately, one will delete the backing MotionEvent and the other will
 * cause a crash since it no longer exists.
 *   - mock() - While you can mock most everything you would need for [MotionEvent], you can not
 * mock native fields/functions that are required for the [android.view.GestureDetector] to work (it
 * does a native copy and crashes if it isn't a real [MotionEvent]). ([AndroidComposeView] uses
 * [android.view.GestureDetector] to detect indirect pointer event gestures.)
 */
@RunWith(AndroidJUnit4::class)
class IndirectPointerEventNavigationSystemTests {
    @get:Rule val rule = createComposeRule(StandardTestDispatcher())

    // Used to dispatch motion events
    private lateinit var rootView: AndroidComposeView
    private var receivedEvent: IndirectPointerEvent? = null
    private var indirectPointerCancelForTopContainer = false

    // Simple UI tests
    val testTagRootSimple = "testTagRootSimple"
    val testTagBox1 = "testTagBox1"
    val testTagBox2 = "testTagBox2"
    val testTagBox3 = "testTagBox3"
    var indirectPointerCancelForBox1 = false
    var indirectPointerCancelForBox2 = false
    var indirectPointerCancelForBox3 = false

    // Complex UI tests
    val testTagParent1 = "testTagParent1"
    val testTagParent1Child1 = "testTagParent1Child1"
    val testTagParent1Child2 = "testTagParent1Child2"
    var onIndirectPointerCancelForParent1 = false
    var onIndirectPointerCancelForParent1Child1 = false
    var onIndirectPointerCancelForParent1Child2 = false

    val testTagParent2 = "testTagParent2"
    val testTagParent2Child1 = "testTagParent2Child1"
    val testTagParent2Child2 = "testTagParent2Child2"
    var onIndirectPointerCancelForParent2 = false
    var onIndirectPointerCancelForParent2Child1 = false
    var onIndirectPointerCancelForParent2Child2 = false

    val testTagParent3 = "testTagParent3"
    val testTagParent3Child1 = "testTagParent3Child1"
    val testTagParent3Child2 = "testTagParent3Child2"
    var onIndirectPointerCancelForParent3 = false
    var onIndirectPointerCancelForParent3Child1 = false
    var onIndirectPointerCancelForParent3Child2 = false

    // Other general setup and focus/fling enabling behavior variables
    val contentBoxSize = 100.dp
    val boxPadding = 10.dp

    lateinit var focusManager: FocusManager

    private val timeBetweenEvents = 20L
    private val flingTriggeringDistanceBetweenEvents = 50
    private val nonFlingTriggeringDistanceBetweenEvents = 5

    private fun resetCancelEvents() {
        indirectPointerCancelForTopContainer = false
        indirectPointerCancelForBox1 = false
        indirectPointerCancelForBox2 = false
        indirectPointerCancelForBox3 = false

        indirectPointerCancelForTopContainer = false
        onIndirectPointerCancelForParent1 = false
        onIndirectPointerCancelForParent1Child1 = false
        onIndirectPointerCancelForParent1Child2 = false
        onIndirectPointerCancelForParent2 = false
        onIndirectPointerCancelForParent2Child1 = false
        onIndirectPointerCancelForParent2Child2 = false
        onIndirectPointerCancelForParent3 = false
        onIndirectPointerCancelForParent3Child1 = false
        onIndirectPointerCancelForParent3Child2 = false
    }

    @Before
    fun setup() {
        receivedEvent = null
        resetCancelEvents()
    }

    // ----- Primary Directional Motion Axis X tests -----
    @Test
    fun swipeViaNavigationMotionEvent_swipeRightWithPrimaryAxisX_movesFocusableBoxToNext() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var previousTime = eventTime
        var indirectX = 100f
        var previousIndirectX = indirectX
        val indirectY = 100f
        val previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(false)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeLeftWithPrimaryAxisX_movesFocusableBoxToPrevious() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeRightAndSmallDownWithPrimaryAxisX_movesFocusableBoxToNext() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()

            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeLeftAndSmallUpWithPrimaryAxisX_movesFocusableBoxToPrevious() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // A movement along the primary axis that is over the threshold for a swipe should not trigger
    // a navigation if the non-primary axis motion is larger (the larger motion always wins).
    @Test
    fun swipeViaNavigationMotionEvent_swipeRightAndDoubleSwipeDownWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += (flingTriggeringDistanceBetweenEvents * 2)

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += (flingTriggeringDistanceBetweenEvents * 2)

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += (flingTriggeringDistanceBetweenEvents * 2)

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // A movement along the primary axis that is over the threshold for a swipe should not trigger
    // a navigation if the non-primary axis motion is larger (the larger motion always wins).
    @Test
    fun swipeViaNavigationMotionEvent_swipeLeftAndDoubleSwipeUpWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (6 * flingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (6 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= (2 * flingTriggeringDistanceBetweenEvents)

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= (2 * flingTriggeringDistanceBetweenEvents)

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= (2 * flingTriggeringDistanceBetweenEvents)

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveRightWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveLeftWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * nonFlingTriggeringDistanceBetweenEvents)
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeDownWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeUpWithPrimaryAxisX_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // ----- Primary Directional Motion Axis Y tests -----
    @Test
    fun swipeViaNavigationMotionEvent_swipeDownWithPrimaryAxisY_movesFocusableBoxToNext() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var previousTime = eventTime
        val indirectX = 100f
        val previousIndirectX = indirectX
        var indirectY = 100f
        var previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectY = indirectY

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectY = indirectY

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(false)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()

            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeUpWithPrimaryAxisY_movesFocusableBoxToPrevious() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeDownAndSmallRightWithPrimaryAxisY_movesFocusableBoxToNext() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)

            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeUpAndSmallLeftWithPrimaryAxisY_movesFocusableBoxToPrevious() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            // The MotionEvent combo above creates a swipe which triggers a focus move (which
            // triggers focus change and some indirect nodes losing focus and, thus, getting an
            // indirect cancel event).
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // A movement along the primary axis that is over the threshold for a swipe should not trigger
    // a navigation if the non-primary axis motion is larger (the larger motion always wins).
    @Test
    fun swipeViaNavigationMotionEvent_swipeDownAndDoubleSwipeRightWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += (flingTriggeringDistanceBetweenEvents * 2)
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += (flingTriggeringDistanceBetweenEvents * 2)
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += (flingTriggeringDistanceBetweenEvents * 2)
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // A movement along the primary axis that is over the threshold for a swipe should not trigger
    // a navigation if the non-primary axis motion is larger (the larger motion always wins).
    @Test
    fun swipeViaNavigationMotionEvent_swipeUpAndDoubleSwipeLeftWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (6 * flingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (6 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= (2 * flingTriggeringDistanceBetweenEvents)
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= (2 * flingTriggeringDistanceBetweenEvents)
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= (2 * flingTriggeringDistanceBetweenEvents)
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveDownWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveUpWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f + (3 * nonFlingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeRightWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeLeftWithPrimaryAxisY_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // ----- Primary Directional Motion Axis X AND Y tests -----
    // There is no behavior for Primary Directional Motion Axis X AND Y because it is translated
    // by the system into key up, down, left, and right.
    @Test
    fun swipeViaNavigationMotionEvent_swipeDownAndRightWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var previousTime = eventTime
        var indirectX = 100f
        var previousIndirectX = indirectX
        var indirectY = 100f
        var previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX
        previousIndirectY = indirectY

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX
        previousIndirectY = indirectY

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(false)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeUpAndLeftWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveDownAndRightWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += nonFlingTriggeringDistanceBetweenEvents
        indirectY += nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Checks a minimal move does NOT qualify as a swipe/fling and does not trigger a behavior.
    @Test
    fun nonSwipeViaNavigationMotionEvent_minimalMoveUpAndLeftWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * nonFlingTriggeringDistanceBetweenEvents)
        var indirectY = 100f + (3 * nonFlingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= nonFlingTriggeringDistanceBetweenEvents
        indirectY -= nonFlingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeRightWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeLeftWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f + (3 * flingTriggeringDistanceBetweenEvents)
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeDownWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_swipeUpWithPrimaryAxisNone_noBehavior() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        val indirectX = 100f
        var indirectY = 100f + (3 * flingTriggeringDistanceBetweenEvents)

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectY -= flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun swipeViaNavigationMotionEvent_cancelEvent_callsOnCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var indirectX = 100f
        val indirectY = 100f

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents

        val cancelEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_CANCEL)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(cancelEvent) }
        rule.runOnIdle {
            assertThat(indirectPointerCancelForTopContainer).isTrue()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isTrue()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // ----- Tests for indirect pointer cancellations -----
    @Test
    fun noNavigationMotionEvent_clearsFocus_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // If nothing was focused, then indirect pointer callbacks will NOT get a cancel event.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        resetCancelEvents()

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        // Manually clear focus
        rule.runOnIdle { focusManager.clearFocus(true) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)

            // Because a ui element with indirect pointer was focused, indirect pointer callbacks
            // WILL receive cancel events.
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForTopContainer).isTrue()
            } else {
                assertThat(indirectPointerCancelForTopContainer).isFalse()
            }
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_clearsFocusWithDeeperUiTree_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackUIsWithTwoBoxChildren(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagParent1 = testTagParent1,
                testTagParent1Child1 = testTagParent1Child1,
                testTagParent1Child2 = testTagParent1Child2,
                testTagParent2 = testTagParent2,
                testTagParent2Child1 = testTagParent2Child1,
                testTagParent2Child2 = testTagParent2Child2,
                testTagParent3 = testTagParent3,
                testTagParent3Child1 = testTagParent3Child1,
                testTagParent3Child2 = testTagParent3Child2,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForParent1 = { onIndirectPointerCancelForParent1 = true },
                onIndirectPointerCancelForParent1Child1 = {
                    onIndirectPointerCancelForParent1Child1 = true
                },
                onIndirectPointerCancelForParent1Child2 = {
                    onIndirectPointerCancelForParent1Child2 = true
                },
                onIndirectPointerCancelForParent2 = { onIndirectPointerCancelForParent2 = true },
                onIndirectPointerCancelForParent2Child1 = {
                    onIndirectPointerCancelForParent2Child1 = true
                },
                onIndirectPointerCancelForParent2Child2 = {
                    onIndirectPointerCancelForParent2Child2 = true
                },
                onIndirectPointerCancelForParent3 = { onIndirectPointerCancelForParent3 = true },
                onIndirectPointerCancelForParent3Child1 = {
                    onIndirectPointerCancelForParent3Child1 = true
                },
                onIndirectPointerCancelForParent3Child2 = {
                    onIndirectPointerCancelForParent3Child2 = true
                },
            )
        }

        // --- Test assertions and actions ---
        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // If nothing was focused, then indirect pointer callbacks will NOT get a cancel event.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagParent2Child1).requestFocus()

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // If nothing was focused, then indirect pointer callbacks will NOT get a cancel event.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }

        resetCancelEvents()

        // Manually clear focus
        rule.runOnIdle { focusManager.clearFocus(true) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForTopContainer).isTrue()
            } else {
                assertThat(indirectPointerCancelForTopContainer).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent2).isTrue()
                assertThat(onIndirectPointerCancelForParent2Child1).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent2).isFalse()
                assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_moveFocusNextProgrammatically_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_moveFocusToFocusableParentProgrammatically_triggersIndirectCancelInChild() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxesWithFocusableParent(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagRoot = testTagRootSimple,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        // Manually move focus
        rule.onNodeWithTag(testTagRootSimple).requestFocus()

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_moveFocusProgrammaticallyWrapAround_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox1).requestFocus()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Previous) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            // For situations where the focus wraps around (start of the list going to end of the
            // list or vice versa), an indirect cancel will be sent to existing focused indirect
            // nodes.
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForTopContainer).isTrue()
                assertThat(indirectPointerCancelForBox1).isTrue()
            } else {
                assertThat(indirectPointerCancelForTopContainer).isFalse()
                assertThat(indirectPointerCancelForBox1).isFalse()
            }
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_moveFocusProgrammaticallyWithDeeperUiTree_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackUIsWithTwoBoxChildren(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagParent1 = testTagParent1,
                testTagParent1Child1 = testTagParent1Child1,
                testTagParent1Child2 = testTagParent1Child2,
                testTagParent2 = testTagParent2,
                testTagParent2Child1 = testTagParent2Child1,
                testTagParent2Child2 = testTagParent2Child2,
                testTagParent3 = testTagParent3,
                testTagParent3Child1 = testTagParent3Child1,
                testTagParent3Child2 = testTagParent3Child2,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForParent1 = { onIndirectPointerCancelForParent1 = true },
                onIndirectPointerCancelForParent1Child1 = {
                    onIndirectPointerCancelForParent1Child1 = true
                },
                onIndirectPointerCancelForParent1Child2 = {
                    onIndirectPointerCancelForParent1Child2 = true
                },
                onIndirectPointerCancelForParent2 = { onIndirectPointerCancelForParent2 = true },
                onIndirectPointerCancelForParent2Child1 = {
                    onIndirectPointerCancelForParent2Child1 = true
                },
                onIndirectPointerCancelForParent2Child2 = {
                    onIndirectPointerCancelForParent2Child2 = true
                },
                onIndirectPointerCancelForParent3 = { onIndirectPointerCancelForParent3 = true },
                onIndirectPointerCancelForParent3Child1 = {
                    onIndirectPointerCancelForParent3Child1 = true
                },
                onIndirectPointerCancelForParent3Child2 = {
                    onIndirectPointerCancelForParent3Child2 = true
                },
            )
        }

        // --- Test assertions and actions ---
        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagParent2Child1).requestFocus()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent2Child1).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }

        resetCancelEvents()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent2).isTrue()
                assertThat(onIndirectPointerCancelForParent2Child2).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent2).isFalse()
                assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }

        resetCancelEvents()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Previous) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent3).isTrue()
                assertThat(onIndirectPointerCancelForParent3Child1).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent3).isFalse()
                assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }
    }

    @Test
    fun noNavigationMotionEvent_moveFocusProgrammaticallyWrapAroundWithDeeperUiTree_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackUIsWithTwoBoxChildren(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagParent1 = testTagParent1,
                testTagParent1Child1 = testTagParent1Child1,
                testTagParent1Child2 = testTagParent1Child2,
                testTagParent2 = testTagParent2,
                testTagParent2Child1 = testTagParent2Child1,
                testTagParent2Child2 = testTagParent2Child2,
                testTagParent3 = testTagParent3,
                testTagParent3Child1 = testTagParent3Child1,
                testTagParent3Child2 = testTagParent3Child2,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForParent1 = { onIndirectPointerCancelForParent1 = true },
                onIndirectPointerCancelForParent1Child1 = {
                    onIndirectPointerCancelForParent1Child1 = true
                },
                onIndirectPointerCancelForParent1Child2 = {
                    onIndirectPointerCancelForParent1Child2 = true
                },
                onIndirectPointerCancelForParent2 = { onIndirectPointerCancelForParent2 = true },
                onIndirectPointerCancelForParent2Child1 = {
                    onIndirectPointerCancelForParent2Child1 = true
                },
                onIndirectPointerCancelForParent2Child2 = {
                    onIndirectPointerCancelForParent2Child2 = true
                },
                onIndirectPointerCancelForParent3 = { onIndirectPointerCancelForParent3 = true },
                onIndirectPointerCancelForParent3Child1 = {
                    onIndirectPointerCancelForParent3Child1 = true
                },
                onIndirectPointerCancelForParent3Child2 = {
                    onIndirectPointerCancelForParent3Child2 = true
                },
            )
        }

        // --- Test assertions and actions ---
        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagParent1Child1).requestFocus()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Previous) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            // For situations where the focus wraps around (start of the list going to end of the
            // list or vice versa), an indirect cancel will be sent to existing focused indirect
            // nodes.
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForTopContainer).isTrue()
                assertThat(onIndirectPointerCancelForParent1).isTrue()
                assertThat(onIndirectPointerCancelForParent1Child1).isTrue()
            } else {
                assertThat(indirectPointerCancelForTopContainer).isFalse()
                assertThat(onIndirectPointerCancelForParent1).isFalse()
                assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }

        resetCancelEvents()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForTopContainer).isTrue()
            } else {
                assertThat(indirectPointerCancelForTopContainer).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent3).isTrue()
                assertThat(onIndirectPointerCancelForParent3Child2).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent3).isFalse()
                assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
        }

        resetCancelEvents()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(onIndirectPointerCancelForParent1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(onIndirectPointerCancelForParent1Child1).isTrue()
            } else {
                assertThat(onIndirectPointerCancelForParent1Child1).isFalse()
            }
            assertThat(onIndirectPointerCancelForParent1Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent2).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent2Child2).isFalse()
            assertThat(onIndirectPointerCancelForParent3).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child1).isFalse()
            assertThat(onIndirectPointerCancelForParent3Child2).isFalse()
        }
    }

    @Test
    fun downViaNavigationMotionEvent_moveFocusProgrammatically_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        val eventTime = downTime
        val previousTime = eventTime
        val indirectX = 100f
        val previousIndirectX = indirectX
        val indirectY = 100f
        val previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        receivedEvent = null

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    @Test
    fun downAndMovesViaNavigationMotionEvent_moveFocusProgrammatically_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var previousTime = eventTime
        var indirectX = 100f
        var previousIndirectX = indirectX
        val indirectY = 100f
        val previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        receivedEvent = null

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Next) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }
            assertThat(indirectPointerCancelForBox3).isFalse()
        }
    }

    // Indirect cancel will trigger after an up (considered the end of the event stream). We test
    // that after a full swipe (down, move, move, up).
    @Test
    fun swipeViaNavigationMotionEvent_moveFocusProgrammatically_triggersIndirectCancel() {
        rule.setContent {
            rootView = LocalView.current as AndroidComposeView
            focusManager = LocalFocusManager.current

            ThreeStackedBoxes(
                boxSize = contentBoxSize,
                boxPadding = boxPadding,
                testTagBox1 = testTagBox1,
                testTagBox2 = testTagBox2,
                testTagBox3 = testTagBox3,
                onIndirectPointerEventMainPassForAllBoxes = {
                    receivedEvent = it
                    // We don't consume the event, so it can pass on for system navigation behavior.
                },
                onIndirectPointerCancelForTopContainer = {
                    indirectPointerCancelForTopContainer = true
                },
                onIndirectPointerCancelForBox1 = { indirectPointerCancelForBox1 = true },
                onIndirectPointerCancelForBox2 = { indirectPointerCancelForBox2 = true },
                onIndirectPointerCancelForBox3 = { indirectPointerCancelForBox3 = true },
            )
        }

        // --- Test assertions and actions ---

        // Clear focus to start
        rule.runOnIdle { focusManager.clearFocus(true) }

        // Request initial focus for center box
        rule.onNodeWithTag(testTagBox2).requestFocus()

        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        var previousTime = eventTime
        var indirectX = 100f
        var previousIndirectX = indirectX
        val indirectY = 100f
        val previousIndirectY = indirectY

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_DOWN)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle {
            rootView.primaryDirectionalMotionAxisOverride =
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            rootView.dispatchGenericMotionEvent(downEvent)
        }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Press)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            // For a first event in a stream, previous is going to equal current (since there is
            // no previous).
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(false)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent1 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent1) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val moveEvent2 =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_MOVE)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(moveEvent2) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Move)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(true)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        previousTime = eventTime
        previousIndirectX = indirectX

        eventTime += timeBetweenEvents
        indirectX += flingTriggeringDistanceBetweenEvents

        val upEvent =
            MotionEventBuilder.newBuilder()
                .setDownTime(downTime)
                .setEventTime(eventTime)
                .setAction(ACTION_UP)
                .setSource(SOURCE_TOUCH_NAVIGATION)
                .setPointer(indirectX, indirectY)
                .build()

        rule.runOnIdle { rootView.dispatchGenericMotionEvent(upEvent) }
        rule.runOnIdle {
            assertThat(receivedEvent?.type).isEqualTo(IndirectPointerEventType.Release)
            val firstChange = checkNotNull(receivedEvent?.changes?.first())
            assertThat(firstChange.position.x).isEqualTo(indirectX)
            assertThat(firstChange.position.y).isEqualTo(indirectY)
            assertThat(firstChange.isConsumed).isEqualTo(false)
            assertThat(firstChange.pressed).isEqualTo(false)
            assertThat(firstChange.previousPosition.x).isEqualTo(previousIndirectX)
            assertThat(firstChange.previousPosition.y).isEqualTo(previousIndirectY)
            assertThat(firstChange.previousUptimeMillis).isEqualTo(previousTime)
            assertThat(firstChange.previousPressed).isEqualTo(true)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox2).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox2).isFalse()
            }

            assertThat(indirectPointerCancelForBox3).isFalse()
        }

        receivedEvent = null

        resetCancelEvents()

        // Manually move focus
        rule.runOnIdle { focusManager.moveFocus(FocusDirection.Previous) }

        rule.runOnIdle {
            assertThat(receivedEvent).isEqualTo(null)
            // Any focus move (which triggers a focus change) will cause an indirect cancellation
            // event for any losing focus.
            assertThat(indirectPointerCancelForTopContainer).isFalse()
            assertThat(indirectPointerCancelForBox1).isFalse()
            assertThat(indirectPointerCancelForBox2).isFalse()
            @OptIn(ExperimentalComposeUiApi::class)
            if (isOptimizedFocusEventDispatchEnabled) {
                assertThat(indirectPointerCancelForBox3).isTrue()
            } else {
                assertThat(indirectPointerCancelForBox3).isFalse()
            }
        }
    }

    @Composable
    private fun ThreeStackedBoxes(
        boxSize: Dp,
        boxPadding: Dp,
        testTagBox1: String,
        testTagBox2: String,
        testTagBox3: String,
        onIndirectPointerEventMainPassForAllBoxes: (IndirectPointerEvent) -> Unit,
        onIndirectPointerCancelForTopContainer: () -> Unit,
        onIndirectPointerCancelForBox1: () -> Unit,
        onIndirectPointerCancelForBox2: () -> Unit,
        onIndirectPointerCancelForBox3: () -> Unit,
    ) {
        Column(
            modifier =
                @Suppress("DEPRECATION")
                Modifier.fillMaxSize()
                    .onIndirectPointerInput(
                        onEvent = {
                            indirectPointerEvent: IndirectPointerEvent,
                            pointerEventPass: PointerEventPass ->
                            if (pointerEventPass == PointerEventPass.Main) {
                                onIndirectPointerEventMainPassForAllBoxes(indirectPointerEvent)
                            }
                        },
                        onCancel = { onIndirectPointerCancelForTopContainer() },
                    )
        ) {
            // Box 1
            Box(
                modifier =
                    Modifier.testTag(testTagBox1)
                        .size(boxSize)
                        .background(Color.Red)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox1() },
                        )
                        .focusable()
            )
            // Box 2
            Box(
                modifier =
                    Modifier.testTag(testTagBox2)
                        .size(boxSize)
                        .background(Color.Green)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox2() },
                        )
                        .focusable()
            )
            // Box 3
            Box(
                modifier =
                    Modifier.testTag(testTagBox3)
                        .size(boxSize)
                        .background(Color.Blue)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox3() },
                        )
                        .focusable()
            )
        }
    }

    @Composable
    private fun ThreeStackedBoxesWithFocusableParent(
        boxSize: Dp,
        boxPadding: Dp,
        testTagRoot: String,
        testTagBox1: String,
        testTagBox2: String,
        testTagBox3: String,
        onIndirectPointerEventMainPassForAllBoxes: (IndirectPointerEvent) -> Unit,
        onIndirectPointerCancelForTopContainer: () -> Unit,
        onIndirectPointerCancelForBox1: () -> Unit,
        onIndirectPointerCancelForBox2: () -> Unit,
        onIndirectPointerCancelForBox3: () -> Unit,
    ) {
        Column(
            modifier =
                @Suppress("DEPRECATION")
                Modifier.testTag(testTagRoot)
                    .fillMaxSize()
                    .onIndirectPointerInput(
                        onEvent = {
                            indirectPointerEvent: IndirectPointerEvent,
                            pointerEventPass: PointerEventPass ->
                            if (pointerEventPass == PointerEventPass.Main) {
                                onIndirectPointerEventMainPassForAllBoxes(indirectPointerEvent)
                            }
                        },
                        onCancel = { onIndirectPointerCancelForTopContainer() },
                    )
                    .focusable()
        ) {
            // Box 1
            Box(
                modifier =
                    Modifier.testTag(testTagBox1)
                        .size(boxSize)
                        .background(Color.Red)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox1() },
                        )
                        .focusable()
            )
            // Box 2
            Box(
                modifier =
                    Modifier.testTag(testTagBox2)
                        .size(boxSize)
                        .background(Color.Green)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox2() },
                        )
                        .focusable()
            )
            // Box 3
            Box(
                modifier =
                    Modifier.testTag(testTagBox3)
                        .size(boxSize)
                        .background(Color.Blue)
                        .padding(boxPadding)
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = { onIndirectPointerCancelForBox3() },
                        )
                        .focusable()
            )
        }
    }

    @Composable
    private fun ThreeStackUIsWithTwoBoxChildren(
        boxSize: Dp,
        boxPadding: Dp,
        testTagParent1: String,
        testTagParent1Child1: String,
        testTagParent1Child2: String,
        testTagParent2: String,
        testTagParent2Child1: String,
        testTagParent2Child2: String,
        testTagParent3: String,
        testTagParent3Child1: String,
        testTagParent3Child2: String,
        onIndirectPointerEventMainPassForAllBoxes: (IndirectPointerEvent) -> Unit,
        onIndirectPointerCancelForTopContainer: () -> Unit,
        onIndirectPointerCancelForParent1: () -> Unit,
        onIndirectPointerCancelForParent1Child1: () -> Unit,
        onIndirectPointerCancelForParent1Child2: () -> Unit,
        onIndirectPointerCancelForParent2: () -> Unit,
        onIndirectPointerCancelForParent2Child1: () -> Unit,
        onIndirectPointerCancelForParent2Child2: () -> Unit,
        onIndirectPointerCancelForParent3: () -> Unit,
        onIndirectPointerCancelForParent3Child1: () -> Unit,
        onIndirectPointerCancelForParent3Child2: () -> Unit,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .onIndirectPointerInput(
                        onEvent = {
                            indirectPointerEvent: IndirectPointerEvent,
                            pointerEventPass: PointerEventPass ->
                            if (pointerEventPass == PointerEventPass.Main) {
                                onIndirectPointerEventMainPassForAllBoxes(indirectPointerEvent)
                            }
                        },
                        onCancel = { onIndirectPointerCancelForTopContainer() },
                    )
        ) {
            // Parent UI Element 1
            Column(
                modifier =
                    Modifier.testTag(testTagParent1)
                        .fillMaxWidth()
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = onIndirectPointerCancelForParent1,
                        )
            ) {
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent1Child1)
                            .size(boxSize)
                            .background(Color.Red)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent1Child1,
                            )
                            .focusable()
                )
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent1Child2)
                            .size(boxSize)
                            .background(Color.Magenta)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent1Child2,
                            )
                            .focusable()
                )
            }

            // Parent UI Element 2
            Column(
                modifier =
                    Modifier.testTag(testTagParent2)
                        .fillMaxWidth()
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = onIndirectPointerCancelForParent2,
                        )
            ) {
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent2Child1)
                            .size(boxSize)
                            .background(Color.Green)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent2Child1,
                            )
                            .focusable()
                )
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent2Child2)
                            .size(boxSize)
                            .background(Color.Yellow)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent2Child2,
                            )
                            .focusable()
                )
            }

            // Parent UI Element 3
            Column(
                modifier =
                    Modifier.testTag(testTagParent3)
                        .fillMaxWidth()
                        .onIndirectPointerInput(
                            onEvent = { _, _ -> },
                            onCancel = onIndirectPointerCancelForParent3,
                        )
            ) {
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent3Child1)
                            .size(boxSize)
                            .background(Color.Blue)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent3Child1,
                            )
                            .focusable()
                )
                Box(
                    modifier =
                        @Suppress("DEPRECATION")
                        Modifier.testTag(testTagParent3Child2)
                            .size(boxSize)
                            .background(Color.Cyan)
                            .padding(boxPadding)
                            .onIndirectPointerInput(
                                onEvent = { _, _ -> },
                                onCancel = onIndirectPointerCancelForParent3Child2,
                            )
                            .focusable()
                )
            }
        }
    }
}
