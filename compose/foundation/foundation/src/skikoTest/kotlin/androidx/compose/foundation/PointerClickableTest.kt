/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalFoundationApi::class, ExperimentalTestApi::class, InternalComposeUiApi::class)
class PointerClickableTest {

    @Test
    fun semantics_defaultAndDisabledAndRole() = runSkikoComposeUiTest {
        var enabled by mutableStateOf(true)
        var role by mutableStateOf<Role?>(Role.Button)

        setContent {
            Box(
                Modifier
                    .testTag("target")
                    .size(40.dp)
                    .onPointerClick(enabled = enabled, role = role) {}
            )
        }

        onNodeWithTag("target")
            .assertIsEnabled()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        role = null
        waitForIdle()

        onNodeWithTag("target")
            .assertIsEnabled()
            .assertHasClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))

        enabled = false
        waitForIdle()
        onNodeWithTag("target").assertIsNotEnabled().assertHasClickAction()
    }

    @Test
    fun primaryClick_providesPrimaryButtonMetadata() = runSkikoComposeUiTest {
        var event: PointerClickEvent? = null

        setContent {
            Box(
                Modifier
                    .size(40.dp)
                    .onPointerClick { clickEvent -> event = clickEvent }
            )
        }

        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 10f))
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(10f, 10f),
            button = PointerButton.Primary
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(10f, 10f),
            button = PointerButton.Primary
        )

        waitForIdle()
        val clickEvent = assertNotNull(event)
        assertNotNull(clickEvent.buttons)
        assertThat(clickEvent.buttons.isPrimaryPressed).isTrue()
    }

    @Test
    fun secondaryAndTertiaryClicks_reportCorrectButtonMask() = runSkikoComposeUiTest {
        val events = mutableListOf<PointerClickEvent>()

        setContent {
            Box(
                Modifier
                    .size(40.dp)
                    .onPointerClick { clickEvent -> events += clickEvent }
            )
        }

        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 10f))

        // Let Skiko implicitly manage the bitmask by passing the event button type
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(10f, 10f),
            button = PointerButton.Secondary
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(10f, 10f),
            button = PointerButton.Secondary
        )

        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(20f, 20f),
            button = PointerButton.Tertiary
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(20f, 20f),
            button = PointerButton.Tertiary
        )

        waitForIdle()

        assertThat(events).hasSize(2)
        assertThat(events[0].buttons?.isSecondaryPressed).isTrue()
        assertThat(events[1].buttons?.isTertiaryPressed).isTrue()
    }

    @Test
    fun keyboardModifiers_areCapturedFromPointerEvent() = runSkikoComposeUiTest {
        val events = mutableListOf<PointerClickEvent>()

        setContent {
            Box(
                Modifier
                    .size(40.dp)
                    .onPointerClick { clickEvent -> events += clickEvent }
            )
        }

        clickWithModifiers(PointerKeyboardModifiers(isShiftPressed = true))
        clickWithModifiers(PointerKeyboardModifiers(isCtrlPressed = true))
        clickWithModifiers(PointerKeyboardModifiers(isAltPressed = true))
        clickWithModifiers(PointerKeyboardModifiers(isMetaPressed = true))

        waitForIdle()

        assertThat(events).hasSize(4)
        assertThat(events[0].keyboardModifiers.isShiftPressed).isTrue()
        assertThat(events[1].keyboardModifiers.isCtrlPressed).isTrue()
        assertThat(events[2].keyboardModifiers.isAltPressed).isTrue()
        assertThat(events[3].keyboardModifiers.isMetaPressed).isTrue()
    }

    @Test
    fun positionTracking_usesLocalCoordinatesWhenNodeMovesMidGesture() = runSkikoComposeUiTest {
        var xOffset by mutableStateOf(0)
        var event: PointerClickEvent? = null

        setContent {
            Box(
                Modifier
                    .offset { IntOffset(xOffset, 0) }
                    .size(100.dp)
                    .onPointerClick { clickEvent -> event = clickEvent }
            )
        }

        scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 50f))
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(50f, 50f),
            button = PointerButton.Primary
        )

        xOffset = 20
        waitForIdle()

        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(50f, 50f),
            button = PointerButton.Primary
        )

        waitForIdle()

        val clickEvent = assertNotNull(event)
        assertThat(clickEvent.position).isEqualTo(Offset(30f, 50f))
    }

    @Test
    fun touchSlopCancellation_dragFarOutsideCancelsClick() = runSkikoComposeUiTest {
        var clicks = 0

        setContent {
            Box(Modifier.size(40.dp).onPointerClick { clicks++ })
        }

        scene.sendPointerEvent(PointerEventType.Move, Offset(10f, 10f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Move, Offset(-100f, -100f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(-100f, -100f), type = PointerType.Touch)

        waitForIdle()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun boundaryRelease_onExactEdge_isStillInside() = runSkikoComposeUiTest {
        var event: PointerClickEvent? = null

        setContent {
            Box(Modifier.size(40.dp).onPointerClick { clickEvent -> event = clickEvent })
        }

        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(1f, 20f),
            button = PointerButton.Primary
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(40f, 20f),
            button = PointerButton.Primary
        )

        waitForIdle()
        assertThat(assertNotNull(event).position).isEqualTo(Offset(40f, 20f))
    }

    @Test
    fun multiPointer_sequentialLifting_clickFiresAfterLastPointerUp() = runSkikoComposeUiTest {
        var clicks = 0

        setContent {
            Box(Modifier.size(60.dp).onPointerClick { clicks++ })
        }

        val pointerA = PointerId(1)
        val pointerB = PointerId(2)

        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(pointer(pointerA, 10f, 10f, pressed = true, type = PointerType.Touch))
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(
                pointer(pointerA, 10f, 10f, pressed = true, type = PointerType.Touch),
                pointer(pointerB, 20f, 20f, pressed = true, type = PointerType.Touch)
            )
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            pointers = listOf(
                pointer(pointerA, 10f, 10f, pressed = false, type = PointerType.Touch),
                pointer(pointerB, 20f, 20f, pressed = true, type = PointerType.Touch)
            )
        )

        waitForIdle()
        assertThat(clicks).isEqualTo(0)

        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            pointers = listOf(pointer(pointerB, 20f, 20f, pressed = false, type = PointerType.Touch))
        )

        waitForIdle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun multiPointer_clickResolvesWithOriginalPointerPosition() = runSkikoComposeUiTest {
        var clickEvent: PointerClickEvent? = null

        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onPointerClick { clickEvent = it }
            )
        }

        val p1 = PointerId(1)
        val p2 = PointerId(2)

        // Press P1
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(pointer(p1, 10f, 10f, pressed = true, type = PointerType.Touch))
        )
        // Press P2 elsewhere
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(
                pointer(p1, 10f, 10f, pressed = true, type = PointerType.Touch),
                pointer(p2, 50f, 50f, pressed = true, type = PointerType.Touch)
            )
        )
        // Release P2
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            pointers = listOf(
                pointer(p1, 10f, 10f, pressed = true, type = PointerType.Touch),
                pointer(p2, 50f, 50f, pressed = false, type = PointerType.Touch)
            )
        )
        // Release P1
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            pointers = listOf(
                pointer(p1, 10f, 10f, pressed = false, type = PointerType.Touch)
            )
        )

        waitForIdle()

        // Ensure the click event corresponds to the original down event (P1)
        assertNotNull(clickEvent)
        assertThat(clickEvent.position).isEqualTo(Offset(10f, 10f))
    }

    @Test
    fun parentScrolling_cancelsClickAndEmitsPressCancel() = runSkikoComposeUiTest {
        val interactionSource = MutableInteractionSource()
        val interactions = mutableListOf<Interaction>()
        var clicks = 0

        this.mainClock.autoAdvance = false

        setContent {
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interactions += it }
            }
            Box(Modifier.size(120.dp).verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .size(80.dp)
                        .onPointerClick(interactionSource = interactionSource) { clicks++ }
                )
            }
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f), type = PointerType.Touch)
        this.mainClock.advanceTimeBy(100L) // Advance past tap delay
        waitForIdle()

        scene.sendPointerEvent(PointerEventType.Move, Offset(20f, 100f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 100f), type = PointerType.Touch)

        waitForIdle()

        val pressInteractions = interactions.filterIsInstance<PressInteraction>()
        assertThat(pressInteractions).hasSize(2)
        val press = assertIs<PressInteraction.Press>(pressInteractions[0])
        val cancel = assertIs<PressInteraction.Cancel>(pressInteractions[1])
        assertEquals(press, cancel.press)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun midGestureDisable_preventsClick() = runSkikoComposeUiTest {
        var enabled by mutableStateOf(true)
        var clicks = 0

        setContent {
            Box(Modifier.size(40.dp).onPointerClick(enabled = enabled) { clicks++ })
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), type = PointerType.Touch)

        enabled = false
        waitForIdle() // Force recomposition down to the Node layer

        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), type = PointerType.Touch)

        waitForIdle()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun semanticTrigger_invokesCallbackWithNullButtons() = runSkikoComposeUiTest {
        var event: PointerClickEvent? = null

        setContent {
            Box(
                Modifier
                    .size(40.dp)
                    .testTag("target")
                    .onPointerClick { clickEvent -> event = clickEvent }
            )
        }

        onNodeWithTag("target").performSemanticsAction(SemanticsActions.OnClick)

        waitForIdle()
        val clickEvent = assertNotNull(event)
        assertNull(clickEvent.buttons)
    }

    @Test
    fun requestIndication_controlsRippleEmission() = runSkikoComposeUiTest {
        val interactions = mutableListOf<Interaction>()
        val interactionSource = MutableInteractionSource()

        setContent {
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interactions += it }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .onPointerClick(
                        interactionSource = interactionSource,
                        // Custom logic: Only ripple on Right Click (Secondary)
                        triggerPressIndication = { it.buttons.isSecondaryPressed }
                    ) {}
            )
        }

        // Left Click -> Should NOT ripple
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), button = PointerButton.Primary)

        waitForIdle()
        assertThat(interactions.filterIsInstance<PressInteraction>()).isEmpty()

        // Right Click -> SHOULD ripple
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), button = PointerButton.Secondary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), button = PointerButton.Secondary)

        waitForIdle()
        assertThat(interactions.filterIsInstance<PressInteraction>()).hasSize(2)
    }

    @Test
    fun rippleLifecycle_successAndCancel_emitExpectedInteractions() = runSkikoComposeUiTest {
        val sourceSuccess = MutableInteractionSource()
        val sourceCancel = MutableInteractionSource()
        val successInteractions = mutableListOf<Interaction>()
        val cancelInteractions = mutableListOf<Interaction>()

        setContent {
            LaunchedEffect(sourceSuccess) {
                sourceSuccess.interactions.collect { successInteractions += it }
            }
            LaunchedEffect(sourceCancel) {
                sourceCancel.interactions.collect { cancelInteractions += it }
            }
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .onPointerClick(interactionSource = sourceSuccess) {}
                )
                Box(
                    Modifier
                        .offset { IntOffset(50, 0) }
                        .size(40.dp)
                        .onPointerClick(interactionSource = sourceCancel) {}
                )
            }
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), type = PointerType.Touch)

        scene.sendPointerEvent(PointerEventType.Press, Offset(60f, 10f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Move, Offset(150f, 10f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(150f, 10f), type = PointerType.Touch)

        waitForIdle()

        val successPresses = successInteractions.filterIsInstance<PressInteraction>()
        assertThat(successPresses).hasSize(2)
        val successPress = assertIs<PressInteraction.Press>(successPresses[0])
        val successRelease = assertIs<PressInteraction.Release>(successPresses[1])
        assertEquals(successPress, successRelease.press)

        val cancelPresses = cancelInteractions.filterIsInstance<PressInteraction>()
        assertThat(cancelPresses).hasSize(2)
        val cancelPress = assertIs<PressInteraction.Press>(cancelPresses[0])
        val cancelEvent = assertIs<PressInteraction.Cancel>(cancelPresses[1])
        assertEquals(cancelPress, cancelEvent.press)
    }

    @Test
    fun lightningClick_inScrollableContainerStillEmitsPressThenRelease() = runSkikoComposeUiTest {
        val interactionSource = MutableInteractionSource()
        val interactions = mutableListOf<Interaction>()

        this.mainClock.autoAdvance = false

        setContent {
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interactions += it }
            }
            Box(Modifier.size(120.dp).verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .size(80.dp)
                        .onPointerClick(interactionSource = interactionSource) {}
                )
            }
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f), type = PointerType.Touch)
        scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 20f), type = PointerType.Touch)

        waitForIdle()

        val pressInteractions = interactions.filterIsInstance<PressInteraction>()
        assertThat(pressInteractions).hasSize(2)
        val press = assertIs<PressInteraction.Press>(pressInteractions[0])
        val release = assertIs<PressInteraction.Release>(pressInteractions[1])
        assertEquals(press, release.press)
    }

    @Test
    fun nodeReuse_updatesRequestIndicationAndOnClickLambda() = runSkikoComposeUiTest {
        var onlyRippleOnShift by mutableStateOf(false)
        var clickCount1 = 0
        var clickCount2 = 0
        val interactionSource = MutableInteractionSource()
        val interactions = mutableListOf<Interaction>()

        setContent {
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interactions += it }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .onPointerClick(
                        interactionSource = interactionSource,
                        triggerPressIndication = { if (onlyRippleOnShift) it.keyboardModifiers.isShiftPressed else true }
                    ) {
                        if (onlyRippleOnShift) clickCount2++ else clickCount1++
                    }
            )
        }

        // 1. Initial state: ripple on anything.
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), button = PointerButton.Primary)
        waitForIdle()

        assertThat(clickCount1).isEqualTo(1)
        assertThat(interactions.filterIsInstance<PressInteraction>()).hasSize(2)
        interactions.clear()

        // 2. Recompose: swap the behavior logic
        onlyRippleOnShift = true
        waitForIdle()

        // 3. Click WITHOUT shift -> Callback fires, NO ripple
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), button = PointerButton.Primary)
        waitForIdle()

        assertThat(clickCount2).isEqualTo(1)
        assertThat(interactions.filterIsInstance<PressInteraction>()).isEmpty()

        // 4. Click WITH shift -> Callback fires, YES ripple
        clickWithModifiers(PointerKeyboardModifiers(isShiftPressed = true))
        waitForIdle()

        assertThat(clickCount2).isEqualTo(2)
        assertThat(interactions.filterIsInstance<PressInteraction>()).hasSize(2)
    }

    private fun pointer(
        id: PointerId,
        x: Float,
        y: Float,
        pressed: Boolean,
        type: PointerType
    ): ComposeScenePointer = ComposeScenePointer(
        id = id,
        position = Offset(x, y),
        pressed = pressed,
        type = type
    )

    private fun androidx.compose.ui.test.SkikoComposeUiTest.clickWithModifiers(
        modifiers: PointerKeyboardModifiers
    ) {
        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(10f, 10f),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(10f, 10f),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary
        )
    }
}