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

import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.MutableBox
import androidx.ink.geometry.MutableVec
import androidx.ink.nativeloader.NativeLoader
import androidx.ink.nativeloader.UsedByNative

/** Native helper functions for Parallelogram. */
@UsedByNative
internal object ParallelogramNative {

    init {
        NativeLoader.load()
    }

    @UsedByNative
    external fun createBoundingBox(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        immutableBoxClass: Class<ImmutableBox>,
        immutableVecClass: Class<ImmutableVec>,
    ): ImmutableBox

    @UsedByNative
    external fun populateBoundingBox(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        outBox: MutableBox,
    )

    @UsedByNative
    external fun createSemiAxes(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        immutableVecClass: Class<ImmutableVec>,
    ): Array<ImmutableVec>

    @UsedByNative
    external fun populateSemiAxes(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        outAxis1: MutableVec,
        outAxis2: MutableVec,
    )

    @UsedByNative
    external fun createCorners(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        immutableVecClass: Class<ImmutableVec>,
    ): Array<ImmutableVec>

    @UsedByNative
    external fun populateCorners(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        outCorner1: MutableVec,
        outCorner2: MutableVec,
        outCorner3: MutableVec,
        outCorner4: MutableVec,
    )

    @UsedByNative
    external fun contains(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float,
        shearFactor: Float,
        pointX: Float,
        pointY: Float,
    ): Boolean
}
