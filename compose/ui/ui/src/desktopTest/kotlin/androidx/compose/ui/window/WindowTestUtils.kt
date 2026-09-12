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

package androidx.compose.ui.window

import androidx.compose.ui.isLinux
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.Insets
import java.awt.Point
import kotlin.math.absoluteValue


/**
 * Converts AWT [Insets] to a [DpSize] object with the sums on each axis.
 */
internal fun Insets.toSize(): DpSize {
    // The AWT coordinates are scaled, so they're Dp
    return DpSize(
        width = (left + right).dp,
        height = (top + bottom).dp
    )
}

/**
 * Returns the size of the screen, as a [DpSize] object.
 */
internal fun GraphicsConfiguration.screenSize(): DpSize {
    return bounds.let {
        // The AWT coordinates are scaled, so they're Dp
        DpSize(it.width.dp, it.height.dp)
    }
}


private const val LinuxCoordinateTolerance = 10

private val CoordinateTolerance = if (isLinux) LinuxCoordinateTolerance else 0

internal fun assertCoordinatesApproximatelyEqual(
    expected: Point,
    actual: Point,
) {
    if (((expected.x - actual.x).absoluteValue > CoordinateTolerance) ||
        ((expected.y - actual.y).absoluteValue > CoordinateTolerance)
    ) {
        throw AssertionError(
            "Expected <$expected> with absolute tolerance" +
                " <$CoordinateTolerance>, actual <$actual>."
        )
    }
}

internal fun assertCoordinatesApproximatelyEqual(
    expected: DpOffset,
    actual: DpOffset,
) {
    assertCoordinatesApproximatelyEqual(
        expected = expected.roundToPoint(),
        actual = actual.roundToPoint(),
    )
}

internal fun assertSizesApproximatelyEqual(
    expected: Dimension,
    actual: Dimension,
) {
    if (((expected.width - actual.width).absoluteValue > CoordinateTolerance) ||
        ((expected.height - actual.height).absoluteValue > CoordinateTolerance)
    ) {
        throw AssertionError(
            "Expected <$expected> with absolute tolerance" +
                " <$CoordinateTolerance>, actual <$actual>."
        )
    }
}

internal fun assertSizesApproximatelyEqual(
    expected: DpSize,
    actual: DpSize,
) {
    assertSizesApproximatelyEqual(
        expected = expected.roundToDimension(),
        actual = actual.roundToDimension(),
    )
}

internal fun assertCoordinatesNotApproximatelyEqual(
    expected: Point,
    actual: Point,
) {
    if (((expected.x - actual.x).absoluteValue <= CoordinateTolerance) &&
        ((expected.y - actual.y).absoluteValue <= CoordinateTolerance)
    ) {
        throw AssertionError(
            "Expected <$expected> to not equal actual <$actual> with absolute" +
                " tolerance <$CoordinateTolerance>"
        )
    }
}

internal fun assertSizesNotApproximatelyEqual(
    expected: Dimension,
    actual: Dimension,
) {
    if (((expected.width - actual.width).absoluteValue <= CoordinateTolerance) &&
        ((expected.height - actual.height).absoluteValue <= CoordinateTolerance)
    ) {
        throw AssertionError(
            "Expected <$expected> to not equal actual <$actual> with absolute" +
                " tolerance <$CoordinateTolerance>"
        )
    }
}