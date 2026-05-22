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

package androidx.compose.foundation.gestures

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny

/** Base class for 1-D ([ScrollableNode]) and 2-D ([Scrollable2DNode]) scrollable nodes. */
internal abstract class AbstractScrollableNode(
    canDrag: (PointerType) -> Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    orientation: Orientation?,
) :
    DragGestureNode(
        canDrag = canDrag,
        enabled = enabled,
        interactionSource = interactionSource,
        orientation = orientation,
    ) {
    protected val nestedScrollDispatcher = NestedScrollDispatcher()

    protected abstract val scrollLogic: ScrollLogic

    private var createdMouseWheelScrollingLogic: Boolean = false
    private var createdTrackpadScrollingLogic: Boolean = false

    private var mouseWheelScrollingLogic: NonTouchScrollingLogic? = null
    private var trackpadScrollingLogic: NonTouchScrollingLogic? = null

    /** Creates a new scrolling logic for mouse-wheel events, or `null` if not supported. */
    protected abstract fun createMouseWheelScrollingLogic(): NonTouchScrollingLogic?

    /** Creates a new scrolling logic for trackpad events, or `null` if not supported. */
    protected abstract fun createTrackpadScrollingLogic(): NonTouchScrollingLogic?

    override fun onAttach() {
        super.onAttach()
        mouseWheelScrollingLogic?.updateDensity(requireDensity())
        trackpadScrollingLogic?.updateDensity(requireDensity())
    }

    override fun onDensityChange() {
        super.onDensityChange()
        mouseWheelScrollingLogic?.updateDensity(requireDensity())
        trackpadScrollingLogic?.updateDensity(requireDensity())
    }

    private fun initializeMouseWheelScrollingLogic() {
        if (!createdMouseWheelScrollingLogic) {
            mouseWheelScrollingLogic = createMouseWheelScrollingLogic()
            createdMouseWheelScrollingLogic = true
        }

        mouseWheelScrollingLogic?.startReceivingEvents(coroutineScope)
    }

    private fun initializeTrackpadScrollingLogic() {
        if (!createdTrackpadScrollingLogic) {
            trackpadScrollingLogic = createTrackpadScrollingLogic()
            createdTrackpadScrollingLogic = true
        }

        trackpadScrollingLogic?.startReceivingEvents(coroutineScope)
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pointerEvent.changes.fastAny { canDrag.invoke(it.type) }) {
            super.onPointerEvent(pointerEvent, pass, bounds)
            return
        }

        if (enabled) {
            initializePointerInputGestureCoordination()
            if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Scroll) {
                initializeMouseWheelScrollingLogic()
            }
            mouseWheelScrollingLogic?.onPointerEvent(pointerEvent, pass, bounds)

            if (
                pass == PointerEventPass.Initial &&
                    (pointerEvent.type == PointerEventType.PanStart ||
                        pointerEvent.type == PointerEventType.PanMove ||
                        pointerEvent.type == PointerEventType.PanEnd)
            ) {
                initializeTrackpadScrollingLogic()
            }
            trackpadScrollingLogic?.onPointerEvent(pointerEvent, pass, bounds)
        }
    }
}
