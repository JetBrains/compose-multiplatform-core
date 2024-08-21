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

package androidx.compose.ui.window

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.VisibleForTesting

/**
 * Compat around the BackEvent class
 */
class BackEventCompat @VisibleForTesting constructor(
    /**
     * Absolute X location of the touch point of this event in the coordinate space of the view that
     *      * received this back event.
     */
    val touchX: Float,
    /**
     * Absolute Y location of the touch point of this event in the coordinate space of the view that
     * received this back event.
     */
    val touchY: Float,
    /**
     * Value between 0 and 1 on how far along the back gesture is.
     */
    @FloatRange(from = 0.0, to = 1.0)
    val progress: Float,
    /**
     * Indicates which edge the swipe starts from.
     */
    @IntRange(from = 0L, to = 1L)
    val swipeEdge: Int
)