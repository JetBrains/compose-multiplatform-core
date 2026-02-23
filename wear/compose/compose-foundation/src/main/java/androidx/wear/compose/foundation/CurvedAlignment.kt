/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.wear.compose.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

public interface CurvedAlignment {
    /**
     * How to lay down components when they are thinner than the container. This is analogue of
     * [Alignment.Vertical] in a [Row].
     */
    @kotlin.jvm.JvmInline
    public value class Radial internal constructor(internal val ratio: Float) {
        public companion object {
            /** Put the child closest to the center of the container, within the available space */
            public val Inner: Radial = Radial(1f)

            /** Put the child in the middle point of the available space. */
            public val Center: Radial = Radial(0.5f)

            /**
             * Put the child farthest from the center of the container, within the available space
             */
            public val Outer: Radial = Radial(0f)

            /** Align the child in a custom position, 0 means Outer, 1 means Inner */
            public fun Custom(ratio: Float): Radial {
                return Radial(ratio)
            }
        }
    }

    /**
     * How to lay down components when they have a smaller sweep than their container. This is
     * analogue of [Alignment.Horizontal] in a [Column].
     */
    @kotlin.jvm.JvmInline
    public value class Angular internal constructor(internal val ratio: Float) {
        public companion object {
            /**
             * Put the child at the angular start of the layout of the container, within the
             * available space
             */
            public val Start: Angular = Angular(0f)

            /** Put the child in the middle point of the available space. */
            public val Center: Angular = Angular(0.5f)

            /**
             * Put the child at the angular end of the layout of the container, within the available
             * space
             */
            public val End: Angular = Angular(1f)

            /** Align the child in a custom position, 0 means Start, 1 means End */
            public fun Custom(ratio: Float): Angular {
                return Angular(ratio)
            }
        }
    }
}
