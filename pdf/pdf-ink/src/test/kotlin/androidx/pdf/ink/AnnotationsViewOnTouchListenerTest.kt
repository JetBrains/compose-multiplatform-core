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

package androidx.pdf.ink

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnnotationsViewOnTouchListenerTest {
    private lateinit var listener: AnnotationsViewOnTouchListener
    private lateinit var view: View

    // Consistent event timing for sequences
    private var gestureDownTime = 0L
    private var currentEventTimeInGesture = 0L

    private lateinit var inkViewEventTracker: TestTouchEventDispatcher
    private lateinit var pdfViewEventTracker: TestTouchEventDispatcher

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        view = View(context)
        gestureDownTime = System.currentTimeMillis()
        currentEventTimeInGesture = gestureDownTime

        inkViewEventTracker = TestTouchEventDispatcher()
        pdfViewEventTracker = TestTouchEventDispatcher()

        listener =
            AnnotationsViewOnTouchListener(
                inkViewDispatcher = inkViewEventTracker,
                pdfViewDispatcher = pdfViewEventTracker,
            )
    }

    private fun resetDispatchTrackers() {
        inkViewEventTracker.reset()
        pdfViewEventTracker.reset()
    }

    private fun createMotionEvent(
        action: Int,
        numPointers: Int = 1,
        pointerIndexForAction: Int = 0, // For ACTION_POINTER_DOWN/UP
    ): MotionEvent {
        currentEventTimeInGesture += 10

        val pointerProperties =
            Array(numPointers) { index -> MotionEvent.PointerProperties().apply { id = index } }
        val pointerCoords =
            Array(numPointers) { index ->
                MotionEvent.PointerCoords().apply {
                    x = index * 10f
                    y = index * 10f
                }
            }

        // Determine the final action for the MotionEvent.
        // For POINTER_DOWN/UP, the action is combined with the pointerIndexForAction.
        // Otherwise, the original action is used.
        val effectiveAction =
            when (action) {
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_UP ->
                    action or (pointerIndexForAction shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                else -> action
            }

        return MotionEvent.obtain(
            /* downTime = */ gestureDownTime,
            /* eventTime = */ currentEventTimeInGesture,
            /* action = */ effectiveAction,
            /* pointerCount = */ numPointers,
            /* pointerProperties = */ pointerProperties,
            /* pointerCoords = */ pointerCoords,
            /* metaState = */ 0,
            /* buttonState = */ 0,
            /* xPrecision = */ 1.0f,
            /* yPrecision = */ 1.0f,
            /* deviceId = */ 0,
            /* edgeFlags = */ 0,
            /* source = */ android.view.InputDevice.SOURCE_TOUCHSCREEN,
            /* flags = */ 0,
        )
    }

    @Test
    fun onTouch_actionDown_dispatchesToBothViewDispatchers() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))

        assertThat(inkViewEventTracker.wasCalled).isTrue()
        assertThat(inkViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_DOWN)
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_DOWN)
    }

    @Test
    fun onTouch_singleTouchMove_dispatchesOnlyToInkViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        resetDispatchTrackers()

        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_MOVE))

        assertThat(inkViewEventTracker.wasCalled).isTrue()
        assertThat(inkViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_MOVE)
        assertThat(pdfViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.lastReceivedAction).isNull()
    }

    @Test
    fun onTouch_actionPointerDown_sendsCancelToInkAndPointerDownToPdfViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        resetDispatchTrackers()

        val pointerDownEvent =
            createMotionEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                numPointers = 2,
                pointerIndexForAction = 1,
            )
        listener.onTouch(view, pointerDownEvent)

        assertThat(inkViewEventTracker.wasCalled).isTrue()
        assertThat(inkViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_CANCEL)
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction)
            .isEqualTo(MotionEvent.ACTION_POINTER_DOWN)
    }

    @Test
    fun onTouch_multiTouchMove_dispatchesOnlyToPdfViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        listener.onTouch(
            view,
            createMotionEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                numPointers = 2,
                pointerIndexForAction = 1,
            ),
        ) // Setup multi-touch
        resetDispatchTrackers()

        val multiMoveEvent = createMotionEvent(MotionEvent.ACTION_MOVE)
        listener.onTouch(view, multiMoveEvent)

        assertThat(inkViewEventTracker.wasCalled).isFalse()
        assertThat(inkViewEventTracker.lastReceivedAction).isNull()
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_MOVE)
    }

    @Test
    fun onTouch_singleTouchUp_dispatchesOnlyToInkViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        resetDispatchTrackers()

        val upEvent = createMotionEvent(MotionEvent.ACTION_UP)
        listener.onTouch(view, upEvent)

        assertThat(inkViewEventTracker.wasCalled).isTrue()
        assertThat(inkViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_UP)
        assertThat(pdfViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.lastReceivedAction).isNull()
    }

    @Test
    fun onTouch_multiTouchPointerUp_dispatchesOnlyToPdfViewDispatcher() {
        // Simulates one of two fingers lifting
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        listener.onTouch(
            view,
            createMotionEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                numPointers = 2,
                pointerIndexForAction = 1,
            ),
        )
        resetDispatchTrackers()

        // Second pointer (index 1) lifts, but pointer 0 is still down.
        val pointerUpEvent =
            createMotionEvent(
                MotionEvent.ACTION_POINTER_UP,
                numPointers = 2,
                pointerIndexForAction = 1,
            )
        listener.onTouch(view, pointerUpEvent)

        assertThat(inkViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_POINTER_UP)
    }

    @Test
    fun onTouch_multiTouchLastUp_dispatchesOnlyToPdfViewDispatcher() {
        // Simulates the last of multiple fingers lifting
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        listener.onTouch(
            view,
            createMotionEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                numPointers = 2,
                pointerIndexForAction = 1,
            ),
        )
        listener.onTouch(
            view,
            createMotionEvent(
                MotionEvent.ACTION_POINTER_UP,
                numPointers = 2,
                pointerIndexForAction = 1,
            ),
        ) // First multi-touch pointer up
        resetDispatchTrackers()

        // Last pointer (index 0) lifts
        val lastUpEvent = createMotionEvent(MotionEvent.ACTION_UP)
        listener.onTouch(view, lastUpEvent)

        assertThat(inkViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_UP)
    }

    @Test
    fun onTouch_actionCancelDuringSingleTouch_dispatchesToInkViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        resetDispatchTrackers()

        val cancelEvent = createMotionEvent(MotionEvent.ACTION_CANCEL)
        listener.onTouch(view, cancelEvent)

        assertThat(inkViewEventTracker.wasCalled).isTrue()
        assertThat(inkViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_CANCEL)
        assertThat(pdfViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.lastReceivedAction).isNull()
    }

    @Test
    fun onTouch_actionCancelDuringMultiTouch_dispatchesToPdfViewDispatcher() {
        listener.onTouch(view, createMotionEvent(MotionEvent.ACTION_DOWN))
        listener.onTouch(
            view,
            createMotionEvent(
                MotionEvent.ACTION_POINTER_DOWN,
                numPointers = 2,
                pointerIndexForAction = 1,
            ),
        )
        resetDispatchTrackers()

        val cancelEvent =
            createMotionEvent(MotionEvent.ACTION_CANCEL, numPointers = 2) // numPointers for CANCEL
        listener.onTouch(view, cancelEvent)

        assertThat(inkViewEventTracker.wasCalled).isFalse()
        assertThat(pdfViewEventTracker.wasCalled).isTrue()
        assertThat(pdfViewEventTracker.lastReceivedAction).isEqualTo(MotionEvent.ACTION_CANCEL)
    }

    companion object {
        // Helper class to track dispatched events
        private class TestTouchEventDispatcher : TouchEventDispatcher {
            var wasCalled = false
            var lastReceivedAction: Int? = null

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                wasCalled = true
                lastReceivedAction = event.actionMasked
                return true
            }

            fun reset() {
                wasCalled = false
                lastReceivedAction = null
            }
        }
    }
}
