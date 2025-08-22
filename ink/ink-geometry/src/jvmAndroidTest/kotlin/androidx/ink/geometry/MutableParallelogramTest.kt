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

package androidx.ink.geometry

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MutableParallelogramTest {

    @Test
    fun defaultConstructor_constructsCorrectMutableParallelogram() {
        val parallelogram = MutableParallelogram()

        assertThat(parallelogram.center).isEqualTo(MutableVec(0f, 0f))
        assertThat(parallelogram.width).isZero()
        assertThat(parallelogram.height).isZero()
        assertThat(parallelogram.rotation).isZero()
        assertThat(parallelogram.skew).isZero()
    }

    @Test
    fun setCenter_changesCenter() {
        val parallelogram = MutableParallelogram()
        val newCenter = MutableVec(5f, -2f)
        parallelogram.center = newCenter
        assertThat(parallelogram.center).isEqualTo(newCenter)
    }

    @Test
    @Suppress("Range") // Intentionally testing values out of intended range.
    fun setWidth_toNegativeValue_forcesNormalizationOfParallelogram() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsAndRotation(
                    MutableVec(10f, 0f),
                    6f,
                    4f,
                    Angle.QUARTER_TURN_RADIANS,
                )
        assertThat(parallelogram.width).isEqualTo(6f)
        assertThat(parallelogram.height).isEqualTo(4f)

        parallelogram.width = -6f
        assertThat(parallelogram.width).isEqualTo(6f)
        assertThat(parallelogram.height).isEqualTo(-4f)
        assertThat(parallelogram.rotation).isWithin(1e-6f).of(1.5f * Angle.HALF_TURN_RADIANS)
    }

    @Test
    fun setRotation_toOutOfRangeNormalRange_forcesNormalizationOfAngle() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsAndRotation(
                    MutableVec(10f, 0f),
                    6f,
                    4f,
                    Angle.QUARTER_TURN_RADIANS,
                )
        parallelogram.rotation = 3f * Angle.HALF_TURN_RADIANS
        assertThat(parallelogram.rotation).isWithin(1e-6f).of(Angle.HALF_TURN_RADIANS)
    }

    @Test
    fun populateFromCenterAndDimensions_constructsCorrectMutableParallelogram() {
        val parallelogram =
            MutableParallelogram().populateFromCenterAndDimensions(MutableVec(10f, 0f), 6f, 4f)

        assertThat(parallelogram.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogram.width).isEqualTo(6f)
        assertThat(parallelogram.height).isEqualTo(4f)
        assertThat(parallelogram.rotation).isZero()
        assertThat(parallelogram.skew).isZero()
    }

    @Suppress("Range")
    @Test
    fun populateFromCenterAndDimensions_forNegativeWidth_constructsCorrectMutableParallelogram() {
        val parallelogramWithNegativeWidth =
            MutableParallelogram().populateFromCenterAndDimensions(MutableVec(10f, 0f), -6f, 4f)

        assertThat(parallelogramWithNegativeWidth.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogramWithNegativeWidth.width).isEqualTo(6f)
        assertThat(parallelogramWithNegativeWidth.height).isEqualTo(-4f)
        assertThat(parallelogramWithNegativeWidth.rotation).isEqualTo(Math.PI.toFloat())
        assertThat(parallelogramWithNegativeWidth.skew).isZero()
    }

    @Test
    fun populateFromCenterDimensionsAndRotation_constructsCorrectMutableParallelogram() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsAndRotation(
                    MutableVec(10f, 0f),
                    6f,
                    4f,
                    Angle.FULL_TURN_RADIANS,
                )

        assertThat(parallelogram.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogram.width).isEqualTo(6f)
        assertThat(parallelogram.height).isEqualTo(4f)
        assertThat(parallelogram.rotation).isZero()
        assertThat(parallelogram.skew).isZero()
    }

    @Test
    @Suppress("Range") // Intentionally testing values out of intended range.
    fun populateFromCenterDimensionsAndRotation_forNegativeWidth_constructsCorrectMutableParallelogram() {
        val parallelogramWithNegativeWidth =
            MutableParallelogram()
                .populateFromCenterDimensionsAndRotation(
                    MutableVec(10f, 0f),
                    -6f,
                    4f,
                    Angle.FULL_TURN_RADIANS,
                )

        assertThat(parallelogramWithNegativeWidth.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogramWithNegativeWidth.width).isEqualTo(6f)
        assertThat(parallelogramWithNegativeWidth.height).isEqualTo(-4f)
        assertThat(parallelogramWithNegativeWidth.rotation).isWithin(1e-6f).of(Math.PI.toFloat())
        assertThat(parallelogramWithNegativeWidth.skew).isZero()
    }

    @Test
    fun populateFromCenterDimensionsRotationAndSkew_constructsCorrectMutableParallelogram() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, 0f),
                    6f,
                    4f,
                    Angle.HALF_TURN_RADIANS,
                    1f,
                )

        assertThat(parallelogram.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogram.width).isEqualTo(6f)
        assertThat(parallelogram.height).isEqualTo(4f)
        assertThat(parallelogram.rotation).isWithin(1e-6f).of(Math.PI.toFloat())
        assertThat(parallelogram.skew).isEqualTo(1f)
    }

    @Test
    fun toImmutable_returnsImmutableEquivalent() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, 0f),
                    6f,
                    4f,
                    Angle.HALF_TURN_RADIANS,
                    1f,
                )
        assertThat(Parallelogram.areEquivalent(parallelogram, parallelogram.toImmutable())).isTrue()
    }

    @Test
    @Suppress("Range") // Intentionally testing values out of intended range.
    fun populateFromCenterDimensionsRotationAndSkew_forNegativeWidth_constructsCorrectMutableParallelogram() {
        val parallelogramWithNegativeWidth =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, 0f),
                    -6f,
                    4f,
                    Angle.FULL_TURN_RADIANS,
                    1f,
                )

        assertThat(parallelogramWithNegativeWidth.center).isEqualTo(MutableVec(10f, 0f))
        assertThat(parallelogramWithNegativeWidth.width).isEqualTo(6f)
        assertThat(parallelogramWithNegativeWidth.height).isEqualTo(-4f)
        assertThat(parallelogramWithNegativeWidth.rotation).isWithin(1e-6f).of(Math.PI.toFloat())
        assertThat(parallelogramWithNegativeWidth.skew).isEqualTo(1)
    }

    @Test
    fun populateFromSegmentAndPadding_returnsCorrectParallelogramWithNoRotation() {
        val parallelogram =
            MutableParallelogram()
                .populateFromSegmentAndPadding(
                    segment = ImmutableSegment(MutableVec(5f, 0f), MutableVec(-5f, 0f)),
                    padding = 2f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    center = MutableVec(0f, 0f),
                    width = 14f,
                    height = 4f,
                    rotation = Angle.ZERO,
                    skew = 0f,
                )
        assertThat(parallelogram.isAlmostEqual(other, tolerance)).isTrue()
    }

    @Test
    fun populateFromSegmentAndPadding_returnsCorrectParallelogramWithRotation() {
        val parallelogram =
            MutableParallelogram()
                .populateFromSegmentAndPadding(
                    segment = MutableSegment(MutableVec(6f, 6f), MutableVec(0f, 0f)),
                    padding = 2f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    center = MutableVec(3f, 3f),
                    width = 12.485281f,
                    height = 4f,
                    rotation = Angle.HALF_TURN_RADIANS / 4.0f,
                    skew = 0f,
                )
        assertThat(parallelogram.isAlmostEqual(other, tolerance)).isTrue()
    }

    @Test
    fun populateFrom_copiesValuesFromInputParallelogram() {
        val source =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, 10f),
                    12f,
                    2f,
                    Angle.HALF_TURN_RADIANS,
                    2f,
                )
        val destination = MutableParallelogram()
        destination.populateFrom(source)
        assertThat(destination).isEqualTo(source)
    }

    @Test
    fun equals_whenSameInstance_returnsTrueAndSameHashCode() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, 10f),
                    12f,
                    2f,
                    Angle.HALF_TURN_RADIANS,
                    0f,
                )
        assertThat(parallelogram).isEqualTo(parallelogram)
        assertThat(parallelogram.hashCode()).isEqualTo(parallelogram.hashCode())
    }

    @Test
    fun equals_whenSameValues_returnsTrueAndSameHashCode() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )

        assertThat(parallelogram).isEqualTo(other)
        assertThat(parallelogram.hashCode()).isEqualTo(other.hashCode())
    }

    @Test
    fun equals_whenDifferentTypes_returnsFalse() {
        // An axis-aligned rectangle with center at (0,0) and width and height equal to 2
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(0f, 0f),
                    2f,
                    2f,
                    Angle.ZERO,
                    0f,
                )
        val other = MutableBox().populateFromTwoPoints(ImmutableVec(-1f, -1f), ImmutableVec(1f, 1f))

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun equals_whenDifferentCenter_returnsFalse() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(10f, -10.5f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun equals_whenDifferentWidth_returnsFalse() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    11f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun equals_whenDifferentHeight_returnsFalse() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun equals_whenDifferentRotation_returnsFalse() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.QUARTER_TURN_RADIANS,
                    -3f,
                )

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun equals_whenDifferentShearFactor_returnsFalse() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    -3f,
                )
        val other =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(-10f, 10f),
                    12f,
                    -7.5f,
                    Angle.HALF_TURN_RADIANS,
                    0f,
                )

        assertThat(parallelogram).isNotEqualTo(other)
    }

    @Test
    fun getters_returnCorrectValues() {
        val parallelogram =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(3f, -5f),
                    8f,
                    -1f,
                    Angle.HALF_TURN_RADIANS,
                    0f,
                )

        assertThat(parallelogram.center).isEqualTo(MutableVec(3f, -5f))
        assertThat(parallelogram.width).isEqualTo(8f)
        assertThat(parallelogram.height).isEqualTo(-1f)
        assertThat(parallelogram.rotation).isEqualTo(Angle.HALF_TURN_RADIANS)
        assertThat(parallelogram.skew).isEqualTo(0f)
    }

    @Test
    fun signedArea_returnsCorrectValue() {
        val parallelogram =
            MutableParallelogram().populateFromCenterAndDimensions(MutableVec(0f, 10f), 6f, 4f)
        val degenerateParallelogram =
            MutableParallelogram().populateFromCenterAndDimensions(MutableVec(0f, 10f), 0f, 4f)
        val negativeAreaParallelogram =
            MutableParallelogram().populateFromCenterAndDimensions(MutableVec(0f, 10f), 2f, -3f)

        assertThat(parallelogram.computeSignedArea()).isEqualTo(24f)
        assertThat(degenerateParallelogram.computeSignedArea()).isZero()
        assertThat(negativeAreaParallelogram.computeSignedArea()).isEqualTo(-6f)
    }

    @Test
    fun toString_returnsCorrectValue() {
        val parallelogramString =
            MutableParallelogram()
                .populateFromCenterDimensionsRotationAndSkew(
                    MutableVec(3f, -5f),
                    8f,
                    -1f,
                    Angle.HALF_TURN_RADIANS,
                    0.25f,
                )
                .toString()

        assertThat(parallelogramString).contains("MutableParallelogram")
        assertThat(parallelogramString).contains("center")
        assertThat(parallelogramString).contains("width")
        assertThat(parallelogramString).contains("height")
        assertThat(parallelogramString).contains("rotation")
        assertThat(parallelogramString).contains("skew")
    }

    private val tolerance = 1e-4f
}
