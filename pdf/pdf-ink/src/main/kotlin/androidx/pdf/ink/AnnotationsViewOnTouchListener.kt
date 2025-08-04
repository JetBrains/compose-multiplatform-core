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

import android.view.MotionEvent
import android.view.View

/** Routes touch events to either ink view or pdf view based on single or multi-touch gestures. */
internal class AnnotationsViewOnTouchListener(
    private val inkViewDispatcher: TouchEventDispatcher,
    private val pdfViewDispatcher: TouchEventDispatcher,
) : View.OnTouchListener {

    private var multiTouchTracking = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouchTracking = false

                // The ACTION_DOWN event is the first event of any touch event stream and needs to
                // be intercepted by all underlying views so they can react to it.
                inkViewDispatcher.dispatchTouchEvent(event)
                pdfViewDispatcher.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouchTracking = true

                val cancelEvent =
                    MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                inkViewDispatcher.dispatchTouchEvent(cancelEvent)
                pdfViewDispatcher.dispatchTouchEvent(event)

                cancelEvent.recycle()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (multiTouchTracking) {
                    pdfViewDispatcher.dispatchTouchEvent(event)
                } else {
                    inkViewDispatcher.dispatchTouchEvent(event)
                }

                multiTouchTracking = false // Reset for the next gesture
            }
            else -> {
                if (multiTouchTracking) {
                    pdfViewDispatcher.dispatchTouchEvent(event)
                } else {
                    inkViewDispatcher.dispatchTouchEvent(event)
                }
            }
        }
        // Consume the event as it's been explicitly dispatched.
        return true
    }
}

/** Dispatches touch events to a target view. */
internal interface TouchEventDispatcher {
    fun dispatchTouchEvent(event: MotionEvent): Boolean
}
