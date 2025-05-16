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

package androidx.navigationevent

import android.os.Build
import android.window.BackEvent
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo

public actual class NavigationEvent(
    /**
     * Absolute X location of the touch point of this event in the coordinate space of the screen
     * that received this navigation event.
     */
    public val touchX: Float,
    /**
     * Absolute Y location of the touch point of this event in the coordinate space of the screen
     * that received this navigation event.
     */
    public val touchY: Float,
    /** Value between 0 and 1 on how far along the back gesture is. */
    public val progress: Float,
    /** Indicates which edge the swipe starts from. */
    public val swipeEdge: @SwipeEdge Int,
    /** Frame time of the navigation event. */
    public val frameTimeMillis: Long = 0
) {

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    internal constructor(
        backEvent: BackEvent
    ) : this(backEvent.touchX, backEvent.touchY, backEvent.progress, backEvent.swipeEdge)

    /**  */
    @Target(AnnotationTarget.TYPE)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(EDGE_LEFT, EDGE_RIGHT, EDGE_NONE)
    public annotation class SwipeEdge

    public companion object {
        /** Indicates that the edge swipe starts from the left edge of the screen */
        public const val EDGE_LEFT: Int = 0

        /** Indicates that the edge swipe starts from the right edge of the screen */
        public const val EDGE_RIGHT: Int = 1

        /**
         * Indicates that the back event was not triggered by an edge swipe back gesture. This
         * applies to cases like using the back button in 3-button navigation or pressing a hardware
         * back button.
         */
        public const val EDGE_NONE: Int = 2
    }
}
