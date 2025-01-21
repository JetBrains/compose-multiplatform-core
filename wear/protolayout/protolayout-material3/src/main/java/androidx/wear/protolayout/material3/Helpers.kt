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

package androidx.wear.protolayout.material3

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.DP
import androidx.annotation.Dimension.Companion.SP
import androidx.annotation.FloatRange
import androidx.annotation.RestrictTo
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.DpProp
import androidx.wear.protolayout.DimensionBuilders.WrappedDimensionProp
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.ElementMetadata
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ModifiersBuilders.SEMANTICS_ROLE_BUTTON
import androidx.wear.protolayout.ModifiersBuilders.Semantics
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.materialcore.fontscaling.FontScaleConverterFactory
import java.nio.charset.StandardCharsets

/**
 * The breakpoint value defining the screen width on and after which, some properties should be
 * changed, depending on the use case.
 */
internal const val SCREEN_WIDTH_BREAKPOINT_DP = 225

/** Minimum tap target for any clickable element. */
internal val MINIMUM_TAP_TARGET_SIZE: DpProp = dp(48f)

/** Returns byte array representation of tag from String. */
internal fun String.toTagBytes(): ByteArray = toByteArray(StandardCharsets.UTF_8)

/** Returns String representation of tag from Metadata. */
internal fun ElementMetadata.toTagName(): String = String(tagData, StandardCharsets.UTF_8)

internal fun <T> Iterable<T>.addBetween(newItem: T): Sequence<T> = sequence {
    var isFirst = true
    for (element in this@addBetween) {
        if (!isFirst) {
            yield(newItem)
        } else {
            isFirst = false
        }
        yield(element)
    }
}

@Dimension(unit = SP)
internal fun Float.dpToSp(fontScale: Float): Float =
    (if (SDK_INT >= UPSIDE_DOWN_CAKE) FontScaleConverterFactory.forScale(fontScale) else null)
        ?.convertDpToSp(this) ?: dpToSpLinear(fontScale)

@Dimension(unit = SP)
private fun Float.dpToSpLinear(fontScale: Float): Float {
    return this / fontScale
}

internal fun StringProp.buttonRoleSemantics() =
    Semantics.Builder().setContentDescription(this).setRole(SEMANTICS_ROLE_BUTTON).build()

internal fun Int.toDp() = dp(this.toFloat())

internal fun String.toElementMetadata() = ElementMetadata.Builder().setTagData(toTagBytes()).build()

/** Builds a horizontal Spacer, with width set to expand and height set to the given value. */
internal fun horizontalSpacer(@Dimension(unit = DP) heightDp: Int): Spacer {
    return Spacer.Builder().setWidth(expand()).setHeight(dp(heightDp.toFloat())).build()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun String.prop(): StringProp = StringProp.Builder(this).build()

/**
 * Returns [wrap] but with minimum dimension of [MINIMUM_TAP_TARGET_SIZE] for accessibility
 * requirements of tap targets.
 */
internal fun wrapWithMinTapTargetDimension(): WrappedDimensionProp =
    WrappedDimensionProp.Builder().setMinimumSize(MINIMUM_TAP_TARGET_SIZE).build()

/** Returns the [Modifiers] object containing this padding and nothing else. */
internal fun Padding.toModifiers(): Modifiers = Modifiers.Builder().setPadding(this).build()

/** Returns the [Background] object containing this color and nothing else. */
internal fun ColorProp.toBackground(): Background = Background.Builder().setColor(this).build()

/** Returns the [Background] object containing this corner and nothing else. */
internal fun Corner.toBackground(): Background = Background.Builder().setCorner(this).build()

/**
 * Changes the opacity/transparency of the given color.
 *
 * Note that this only looks at the static value of the [ColorProp], any dynamic value will be
 * ignored.
 */
public fun ColorProp.withOpacity(@FloatRange(from = 0.0, to = 1.0) ratio: Float): ColorProp {
    // From androidx.core.graphics.ColorUtils
    require(!(ratio < 0 || ratio > 1)) { "setOpacityForColor ratio must be between 0 and 1." }
    val fullyOpaque = 255
    val alphaMask = 0x00ffffff
    val alpha = (ratio * fullyOpaque).toInt()
    val alphaPosition = 24
    return argb((this.argb and alphaMask) or (alpha shl alphaPosition))
}
