/*
 * Copyright (C) 2024 The Android Open Source Project
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

package androidx.ink.geometry.internal

import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.MutableVec
import androidx.ink.nativeloader.NativeLoader
import androidx.ink.nativeloader.UsedByNative

/** Helper functions for MutableBox and ImmutableBox. */
@UsedByNative
internal object BoxNative {

    init {
        NativeLoader.load()
    }

    @UsedByNative
    external fun createCenter(
        rectXMin: Float,
        rectYMin: Float,
        rectXMax: Float,
        rectYMax: Float,
        immutableVecClass: Class<ImmutableVec>,
    ): ImmutableVec

    @UsedByNative
    external fun populateCenter(
        rectXMin: Float,
        rectYMin: Float,
        rectXMax: Float,
        rectYMax: Float,
        out: MutableVec,
    )

    @UsedByNative
    external fun containsPoint(
        rectXMin: Float,
        rectYMin: Float,
        rectXMax: Float,
        rectYMax: Float,
        pointX: Float,
        pointY: Float,
    ): Boolean

    @UsedByNative
    external fun containsBox(
        rectXMin: Float,
        rectYMin: Float,
        rectXMax: Float,
        rectYMax: Float,
        otherXMin: Float,
        otherYMin: Float,
        otherXMax: Float,
        otherYMax: Float,
    ): Boolean
}
