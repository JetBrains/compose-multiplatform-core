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

package androidx.wear.compose.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.GestureInclusion
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.RevealActionType.Companion.PrimaryAction
import androidx.wear.compose.material3.RevealActionType.Companion.SecondaryAction
import androidx.wear.compose.material3.RevealActionType.Companion.UndoAction
import androidx.wear.compose.material3.RevealDirection.Companion.Bidirectional
import androidx.wear.compose.material3.RevealDirection.Companion.RightToLeft
import androidx.wear.compose.material3.RevealValue.Companion.Covered
import androidx.wear.compose.material3.RevealValue.Companion.LeftRevealed
import androidx.wear.compose.material3.RevealValue.Companion.LeftRevealing
import androidx.wear.compose.material3.RevealValue.Companion.RightRevealed
import androidx.wear.compose.material3.RevealValue.Companion.RightRevealing
import androidx.wear.compose.material3.SwipeToRevealDefaults.SingleActionAnchorWidth
import androidx.wear.compose.material3.SwipeToRevealDefaults.bidirectionalGestureInclusion
import androidx.wear.compose.material3.SwipeToRevealDefaults.gestureInclusion
import androidx.wear.compose.materialcore.CustomTouchSlopProvider
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

class SwipeToRevealTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun onStateChangeToRevealed_performsHaptics() {
        val results = mutableMapOf<HapticFeedbackType, Int>()
        val haptics = hapticFeedback(collectResultsFromHapticFeedback(results))
        val revealValueFlow = MutableStateFlow(RightRevealing)

        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val revealValue by revealValueFlow.collectAsStateWithLifecycle()

                SwipeToRevealWithDefaults(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState = rememberRevealState(initialValue = revealValue),
                )
            }
        }

        rule.runOnIdle { assertThat(results).isEmpty() }

        revealValueFlow.value = RightRevealed

        rule.runOnIdle {
            assertThat(results).hasSize(1)
            assertThat(results).containsKey(HapticFeedbackType.GestureThresholdActivate)
            assertThat(results[HapticFeedbackType.GestureThresholdActivate]).isEqualTo(1)
        }
    }

    @Test
    fun onStateChangeToLeftRevealed_performsHaptics() {
        val results = mutableMapOf<HapticFeedbackType, Int>()
        val haptics = hapticFeedback(collectResultsFromHapticFeedback(results))
        val revealValueFlow = MutableStateFlow(LeftRevealing)

        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val revealValue by revealValueFlow.collectAsStateWithLifecycle()

                SwipeToRevealWithDefaults(
                    modifier = Modifier.testTag(TEST_TAG),
                    revealState = rememberRevealState(initialValue = revealValue),
                    revealDirection = Bidirectional
                )
            }
        }

        rule.runOnIdle { assertThat(results).isEmpty() }

        revealValueFlow.value = LeftRevealed

        rule.runOnIdle {
            assertThat(results).hasSize(1)
            assertThat(results).containsKey(HapticFeedbackType.GestureThresholdActivate)
            assertThat(results[HapticFeedbackType.GestureThresholdActivate]).isEqualTo(1)
        }
    }

    @Test
    fun onStart_defaultState_keepsContentToRight() {
        rule.setContent {
            SwipeToRevealWithDefaults { DefaultContent(modifier = Modifier.testTag(TEST_TAG)) }
        }

        rule.onNodeWithTag(TEST_TAG).assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun onCovered_doesNotDrawActions() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(TEST_TAG))
                }
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun onRightRevealing_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(TEST_TAG))
                },
                revealState = rememberRevealState(initialValue = RightRevealing),
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun onRightRevealing_twoActions_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                secondaryAction = {
                    DefaultSecondaryActionButton(modifier = Modifier.testTag(SECONDARY_ACTION_TAG))
                },
                revealState = rememberRevealState(initialValue = RightRevealing),
            )
        }

        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
        rule.onNodeWithTag(SECONDARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onLeftRevealing_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(TEST_TAG))
                },
                revealDirection = Bidirectional,
                revealState = rememberRevealState(initialValue = LeftRevealing),
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun onLeftRevealing_twoActions_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                secondaryAction = {
                    DefaultSecondaryActionButton(modifier = Modifier.testTag(SECONDARY_ACTION_TAG))
                },
                revealDirection = Bidirectional,
                revealState = rememberRevealState(initialValue = LeftRevealing),
            )
        }

        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
        rule.onNodeWithTag(SECONDARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onSwipe_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            // Move the pointer by quarter of the screen width, don't move up the pointer
            moveBy(delta = Offset(x = -(centerX / 4), y = 0f))
        }

        rule.waitForIdle()
        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onSwipe_twoActions_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                secondaryAction = {
                    DefaultSecondaryActionButton(modifier = Modifier.testTag(SECONDARY_ACTION_TAG))
                },
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            // Move the pointer by quarter of the screen width, don't move up the pointer
            moveBy(delta = Offset(x = -(centerX / 4), y = 0f))
        }

        rule.waitForIdle()
        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
        rule.onNodeWithTag(SECONDARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onSwipeRight_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                revealDirection = Bidirectional,
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            // Move the pointer by quarter of the screen width, don't move up the pointer
            moveBy(delta = Offset(x = centerX / 4, y = 0f))
        }

        rule.waitForIdle()
        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onSwipeRight_twoActions_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                primaryAction = {
                    DefaultPrimaryActionButton(modifier = Modifier.testTag(PRIMARY_ACTION_TAG))
                },
                secondaryAction = {
                    DefaultSecondaryActionButton(modifier = Modifier.testTag(SECONDARY_ACTION_TAG))
                },
                revealDirection = Bidirectional,
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            // Move the pointer by quarter of the screen width, don't move up the pointer
            moveBy(delta = Offset(x = centerX / 4, y = 0f))
        }

        rule.waitForIdle()
        rule.onNodeWithTag(PRIMARY_ACTION_TAG).assertExists()
        rule.onNodeWithTag(SECONDARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onFullSwipe_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                undoPrimaryAction = {
                    DefaultUndoActionButton(modifier = Modifier.testTag(UNDO_PRIMARY_ACTION_TAG))
                },
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput { swipeLeft() }

        rule.waitForIdle()
        rule.onNodeWithTag(UNDO_PRIMARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onFullSwipeRight_drawsAction() {
        rule.setContent {
            SwipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG),
                undoPrimaryAction = {
                    DefaultUndoActionButton(modifier = Modifier.testTag(UNDO_PRIMARY_ACTION_TAG))
                },
                revealDirection = Bidirectional,
                enableTouchSlop = false
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput { swipeRight() }

        rule.waitForIdle()
        rule.onNodeWithTag(UNDO_PRIMARY_ACTION_TAG).assertExists()
    }

    @Test
    fun onFullSwipeRight_noSwipe() {
        verifyGesture(expectedRevealValue = Covered) { swipeRight() }
    }

    @Test
    fun onFullSwipeRight_bidirectionalGestureInclusion_noSwipe() {
        verifyGesture(
            expectedRevealValue = Covered,
            bidirectionalGestureInclusion = true,
        ) {
            swipeRight()
        }
    }

    @Test
    fun onFullSwipeLeft_stateToSwiped() {
        verifyGesture(expectedRevealValue = RightRevealed) { swipeLeft() }
    }

    @Test
    fun onFullSwipeLeft_bidirectionalGestureInclusion_stateToSwiped() {
        verifyGesture(
            expectedRevealValue = RightRevealed,
            bidirectionalGestureInclusion = true,
        ) {
            swipeLeft()
        }
    }

    @Test
    fun onFullSwipeRight_bidirectionalNonBidirectionalGestureInclusion_noSwipe() {
        verifyGesture(
            expectedRevealValue = Covered,
            revealDirection = Bidirectional,
            bidirectionalGestureInclusion = false,
        ) {
            swipeRight()
        }
    }

    @Test
    fun onFullSwipeRight_bidirectional_stateToSwiped() {
        verifyGesture(expectedRevealValue = LeftRevealed, revealDirection = Bidirectional) {
            swipeRight()
        }
    }

    @Test
    fun onPartialSwipeRight_bidirectionalNonBidirectionalGestureInclusion_stateToSwiped() {
        verifyGesture(
            expectedRevealValue = LeftRevealed,
            revealDirection = Bidirectional,
            bidirectionalGestureInclusion = false,
        ) {
            swipeRight(startX = width / 2f)
        }
    }

    @Test
    fun onPartialSwipeRight_bidirectional_stateToSwiped() {
        verifyGesture(expectedRevealValue = LeftRevealed, revealDirection = Bidirectional) {
            swipeRight(startX = width / 2f)
        }
    }

    @Test
    fun onFullSwipeLeft_bidirectionalNonBidirectionalGestureInclusion_stateToSwiped() {
        verifyGesture(
            expectedRevealValue = RightRevealed,
            revealDirection = Bidirectional,
            bidirectionalGestureInclusion = false,
        ) {
            swipeLeft()
        }
    }

    @Test
    fun onFullSwipeLeft_bidirectional_stateToSwiped() {
        verifyGesture(
            expectedRevealValue = RightRevealed,
            revealDirection = Bidirectional,
        ) {
            swipeLeft()
        }
    }

    @Test
    fun onAboveVelocityThresholdSmallDistanceSwipe_stateToRevealing() {
        verifyGesture(expectedRevealValue = RightRevealing, enableTouchSlop = false) {
            swipeLeft(endX = right - 65, durationMillis = 30L)
        }
    }

    @Test
    fun onBelowVelocityThresholdSmallDistanceSwipe_noSwipe() {
        verifyGesture(expectedRevealValue = Covered, enableTouchSlop = false) {
            swipeLeft(endX = right - 65, durationMillis = 1000L)
        }
    }

    @Test
    fun onAboveVelocityThresholdLongDistanceSwipe_stateToRevealing() {
        verifyGesture(expectedRevealValue = RightRevealing, enableTouchSlop = false) {
            swipeLeft(endX = right - 150, durationMillis = 30L)
        }
    }

    @Test
    fun onBelowVelocityThresholdLongDistanceSwipe_stateToRevealing() {
        verifyGesture(expectedRevealValue = RightRevealing, enableTouchSlop = false) {
            swipeLeft(endX = right - 150, durationMillis = 1000L)
        }
    }

    @Test
    fun onPartialSwipe_lastStateRevealing_resetsLastState() {
        verifyStateMultipleSwipeToReveal(
            actions = { revealStateOne, revealStateTwo, density ->
                // swipe the first S2R to Revealing state
                rule.onNodeWithTag(SWIPE_TO_REVEAL_TAG).performTouchInput {
                    swipeLeftToRevealing(density)
                }

                // swipe the second S2R to Revealing state
                rule.onNodeWithTag(SWIPE_TO_REVEAL_SECOND_TAG).performTouchInput {
                    swipeLeftToRevealing(density)
                }
            },
            assertions = { revealStateOne, revealStateTwo ->
                assertEquals(Covered, revealStateOne.currentValue)
                assertEquals(RightRevealing, revealStateTwo.currentValue)
            }
        )
    }

    @Test
    fun onPartialSwipe_whenLastStateRevealed_doesNotReset() {
        verifyStateMultipleSwipeToReveal(
            actions = { revealStateOne, revealStateTwo, density ->
                // swipe the first S2R to Revealed (full screen swipe)
                rule.onNodeWithTag(SWIPE_TO_REVEAL_TAG).performTouchInput { swipeLeft() }

                // swipe the second S2R to Revealing state
                rule.onNodeWithTag(SWIPE_TO_REVEAL_SECOND_TAG).performTouchInput {
                    swipeLeftToRevealing(density)
                }
            },
            assertions = { revealStateOne, revealStateTwo ->
                // assert that state does not reset
                assertEquals(RightRevealed, revealStateOne.currentValue)
                assertEquals(RightRevealing, revealStateTwo.currentValue)
            }
        )
    }

    @Test
    fun onMultiSnap_differentComponents_lastOneGetsReset() {
        verifyStateMultipleSwipeToReveal(
            actionsSuspended = { revealStateOne, revealStateTwo, density ->
                // First change
                revealStateOne.snapTo(RightRevealing)
                // Second change, in a different component
                revealStateTwo.snapTo(RightRevealing)
            },
            assertions = { revealStateOne, revealStateTwo ->
                assertEquals(Covered, revealStateOne.currentValue)
            }
        )
    }

    @Test
    fun onMultiSnap_sameComponents_doesNotReset() {
        val lastValue = RightRevealed
        verifyStateMultipleSwipeToReveal(
            actionsSuspended = { revealStateOne, revealStateTwo, density ->
                revealStateOne.snapTo(RightRevealing) // First change
                revealStateOne.snapTo(lastValue) // Second change, same component
            },
            assertions = { revealStateOne, revealStateTwo ->
                assertEquals(lastValue, revealStateOne.currentValue)
            }
        )
    }

    @Test
    fun onSecondaryActionClick_setsLastClickAction() =
        verifyLastClickAction(
            expectedClickType = SecondaryAction,
            initialRevealValue = RightRevealing,
            nodeTagToPerformClick = SECONDARY_ACTION_TAG,
        )

    @Test
    fun onPrimaryActionClick_setsLastClickAction() =
        verifyLastClickAction(
            expectedClickType = PrimaryAction,
            initialRevealValue = RightRevealing,
        )

    @Test
    fun onUndoActionClick_setsLastClickAction() =
        verifyLastClickAction(
            expectedClickType = UndoAction,
            initialRevealValue = RightRevealed,
            nodeTagToPerformClick = UNDO_PRIMARY_ACTION_TAG,
        )

    @Test
    fun onFullSwipeRight_wrappedInSwipeToDismissBox_navigationSwipe() {
        verifyGesture(
            expectedRevealValue = Covered,
            wrappedInSwipeToDismissBox = true,
            expectedSwipeToDismissBoxDismissed = true,
        ) {
            swipeRight()
        }
    }

    @Test
    fun onFullSwipeRight_wrappedInSTDBBidirectionalGI_noSwipe() {
        verifyGesture(
            expectedRevealValue = Covered,
            bidirectionalGestureInclusion = true,
            wrappedInSwipeToDismissBox = true,
        ) {
            swipeRight()
        }
    }

    @Test
    fun onFullSwipeRight_wrappedInSwipeToDismissBoxAndRightRevealing_stateToCovered() {
        verifyGesture(
            initialRevealValue = RightRevealing,
            expectedRevealValue = Covered,
            wrappedInSwipeToDismissBox = true,
        ) {
            swipeRight()
        }
    }

    @Test
    fun onSwipeRightFromOutsideEdge_wrappedInSwipeToDismissBoxAndRightRevealing_stateToCovered() {
        verifyGesture(
            initialRevealValue = RightRevealing,
            expectedRevealValue = Covered,
            wrappedInSwipeToDismissBox = true,
            enableTouchSlop = false,
        ) {
            swipeRight(startX = width / 2f)
        }
    }

    @Test
    fun onRightSwipe_dispatchEventsToParent() {
        var onPreScrollDispatch = 0f
        rule.setContent {
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        onPreScrollDispatch = available.x
                        return available
                    }
                }
            }
            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                SwipeToRevealWithDefaults(modifier = Modifier.testTag(TEST_TAG))
            }
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput { swipeRight() }

        assert(onPreScrollDispatch > 0)
    }

    @Test
    fun onLeftSwipe_dispatchEventsToParent() {
        var onPreScrollDispatch = 0f
        rule.setContent {
            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        onPreScrollDispatch = available.x
                        return available
                    }
                }
            }
            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                SwipeToRevealWithDefaults(modifier = Modifier.testTag(TEST_TAG))
            }
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput { swipeLeft() }

        assert(onPreScrollDispatch < 0) // Swiping left means the dispatch will be negative
    }

    private fun verifyLastClickAction(
        expectedClickType: RevealActionType,
        initialRevealValue: RevealValue,
        nodeTagToPerformClick: String = PRIMARY_ACTION_TAG
    ) {
        lateinit var revealState: RevealState
        rule.setContent {
            revealState = rememberRevealState(initialRevealValue)
            val coroutineScope = rememberCoroutineScope()
            SwipeToRevealWithDefaults(
                primaryAction = {
                    DefaultPrimaryActionButton(
                        modifier = Modifier.testTag(PRIMARY_ACTION_TAG),
                        onClick = {
                            coroutineScope.launch {
                                revealState.snapTo(Covered)
                                revealState.lastActionType = PrimaryAction
                            }
                        }
                    )
                },
                revealState = revealState,
                secondaryAction = {
                    DefaultSecondaryActionButton(
                        modifier = Modifier.testTag(SECONDARY_ACTION_TAG),
                        onClick = {
                            coroutineScope.launch {
                                revealState.snapTo(Covered)
                                revealState.lastActionType = SecondaryAction
                            }
                        }
                    )
                },
                undoPrimaryAction = {
                    DefaultUndoActionButton(
                        modifier = Modifier.testTag(UNDO_PRIMARY_ACTION_TAG),
                        onClick = {
                            coroutineScope.launch {
                                revealState.animateTo(Covered)
                                revealState.lastActionType = UndoAction
                            }
                        }
                    )
                }
            )
        }
        rule.onNodeWithTag(nodeTagToPerformClick).performClick()
        rule.runOnIdle { assertEquals(expectedClickType, revealState.lastActionType) }
    }

    private fun verifyGesture(
        initialRevealValue: RevealValue = Covered,
        expectedRevealValue: RevealValue,
        expectedFullSwipeTriggered: Boolean =
            (expectedRevealValue == RightRevealed || expectedRevealValue == LeftRevealed),
        revealDirection: RevealDirection = RightToLeft,
        bidirectionalGestureInclusion: Boolean = revealDirection == Bidirectional,
        enableTouchSlop: Boolean = true,
        wrappedInSwipeToDismissBox: Boolean = false,
        expectedSwipeToDismissBoxDismissed: Boolean = false,
        gesture: TouchInjectionScope.() -> Unit,
    ) {
        var onFullSwipeTriggerCounter = 0
        var onSwipeToDismissBoxDismissed = false
        lateinit var revealState: RevealState

        rule.setContent {
            revealState =
                rememberRevealState(
                    initialValue = initialRevealValue,
                )

            val content =
                @Composable {
                    SwipeToRevealWithDefaults(
                        modifier = Modifier.testTag(TEST_TAG),
                        onSwipePrimaryAction = { onFullSwipeTriggerCounter++ },
                        revealState = revealState,
                        revealDirection = revealDirection,
                        gestureInclusion =
                            if (bidirectionalGestureInclusion) {
                                SwipeToRevealDefaults.bidirectionalGestureInclusion
                            } else {
                                gestureInclusion(revealState)
                            },
                        enableTouchSlop = enableTouchSlop,
                    )
                }

            if (!wrappedInSwipeToDismissBox) {
                content()
            } else {
                BasicSwipeToDismissBox(
                    onDismissed = { onSwipeToDismissBoxDismissed = true },
                    state = rememberSwipeToDismissBoxState(),
                ) { isBackground ->
                    if (isBackground) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Red),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) { content() }
                    }
                }
            }
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput(block = gesture)

        rule.runOnIdle { assertEquals(expectedRevealValue, revealState.currentValue) }

        assertEquals(if (expectedFullSwipeTriggered) 1 else 0, onFullSwipeTriggerCounter)
        assertEquals(expectedSwipeToDismissBoxDismissed, onSwipeToDismissBoxDismissed)
    }

    private fun verifyStateMultipleSwipeToReveal(
        actions:
            ((revealStateOne: RevealState, revealStateTwo: RevealState, density: Float) -> Unit)? =
            null,
        actionsSuspended:
            (suspend (
                revealStateOne: RevealState, revealStateTwo: RevealState, density: Float
            ) -> Unit)? =
            null,
        assertions: (revealStateOne: RevealState, revealStateTwo: RevealState) -> Unit,
    ) {
        lateinit var revealStateOne: RevealState
        lateinit var revealStateTwo: RevealState
        var density = 0f
        rule.setContent {
            with(LocalDensity.current) { density = this.density }
            revealStateOne = rememberRevealState()
            revealStateTwo = rememberRevealState()
            CustomTouchSlopProvider(newTouchSlop = 0f) {
                Column {
                    SwipeToRevealWithDefaults(
                        modifier = Modifier.testTag(SWIPE_TO_REVEAL_TAG),
                        revealState = revealStateOne
                    )
                    SwipeToRevealWithDefaults(
                        modifier = Modifier.testTag(SWIPE_TO_REVEAL_SECOND_TAG),
                        revealState = revealStateTwo
                    )
                }
            }

            val coroutineScope = rememberCoroutineScope()
            coroutineScope.launch {
                actionsSuspended?.invoke(revealStateOne, revealStateTwo, density)
            }
        }

        actions?.invoke(revealStateOne, revealStateTwo, density)

        rule.runOnIdle { assertions(revealStateOne, revealStateTwo) }
    }

    @Composable
    fun SwipeToRevealWithDefaults(
        modifier: Modifier = Modifier,
        onSwipePrimaryAction: () -> Unit = {},
        primaryAction: @Composable SwipeToRevealScope.() -> Unit =
            @Composable { DefaultPrimaryActionButton(onClick = onSwipePrimaryAction) },
        secondaryAction: (@Composable SwipeToRevealScope.() -> Unit)? = null,
        undoPrimaryAction: (@Composable SwipeToRevealScope.() -> Unit)? = null,
        undoSecondaryAction: (@Composable SwipeToRevealScope.() -> Unit)? = null,
        revealState: RevealState = rememberRevealState(),
        revealDirection: RevealDirection = RightToLeft,
        hasPartiallyRevealedState: Boolean = true,
        gestureInclusion: GestureInclusion =
            if (revealDirection == Bidirectional) {
                bidirectionalGestureInclusion
            } else {
                gestureInclusion(revealState)
            },
        enableTouchSlop: Boolean = true,
        content: @Composable () -> Unit = @Composable { DefaultContent() }
    ) {
        val swipeToRevealContent =
            @Composable {
                SwipeToReveal(
                    primaryAction = primaryAction,
                    onSwipePrimaryAction = onSwipePrimaryAction,
                    modifier = modifier,
                    secondaryAction = secondaryAction,
                    undoPrimaryAction = undoPrimaryAction,
                    undoSecondaryAction = undoSecondaryAction,
                    revealState = revealState,
                    revealDirection = revealDirection,
                    hasPartiallyRevealedState = hasPartiallyRevealedState,
                    gestureInclusion = gestureInclusion,
                    content = content
                )
            }
        if (enableTouchSlop) {
            swipeToRevealContent()
        } else {
            CustomTouchSlopProvider(newTouchSlop = 0f, content = swipeToRevealContent)
        }
    }

    @Composable
    private fun SwipeToRevealScope.DefaultPrimaryActionButton(
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
    ) {
        PrimaryActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
            text = { Text("Delete") },
            modifier = modifier,
        )
    }

    @Composable
    private fun SwipeToRevealScope.DefaultSecondaryActionButton(
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
    ) {
        SecondaryActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Outlined.MoreVert, contentDescription = "More") },
            modifier = modifier,
        )
    }

    @Composable
    private fun SwipeToRevealScope.DefaultUndoActionButton(
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {},
    ) {
        UndoActionButton(
            onClick = onClick,
            text = { Text("Undo Delete") },
            modifier = modifier,
        )
    }

    @Composable
    private fun DefaultContent(modifier: Modifier = Modifier) {
        Button({}, modifier.fillMaxWidth()) { Text("Swipe me!") }
    }

    private fun TouchInjectionScope.swipeLeftToRevealing(density: Float) {
        val singleActionAnchorWidthPx = SingleActionAnchorWidth.value * density
        swipeLeft(
            startX = right,
            endX = right - (singleActionAnchorWidthPx * 0.75f),
        )
    }

    companion object {
        private const val SWIPE_TO_REVEAL_TAG = TEST_TAG
        private const val SWIPE_TO_REVEAL_SECOND_TAG = "SWIPE_TO_REVEAL_SECOND_TAG"
        private const val PRIMARY_ACTION_TAG = "PRIMARY_ACTION_TAG"
        private const val SECONDARY_ACTION_TAG = "SECONDARY_ACTION_TAG"
        private const val UNDO_PRIMARY_ACTION_TAG = "UNDO_PRIMARY_ACTION_TAG"
    }
}
