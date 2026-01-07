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

package androidx.wear.compose.material3.lazy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Creates and remembers a [PaddingValues] instance that dynamically calculates top and bottom
 * padding based on the screen height and the [ResponsiveItemType] of the first and last items in
 * the list.
 *
 * The returned [PaddingValues] object is stable (memoized) and optimized to avoid unnecessary
 * recompositions. It defers the reading of [firstItemTypeProvider] and [lastItemTypeProvider] until
 * the layout measurement phase. This ensures that changes to the list content (which update the
 * providers) do not force the parent composable or the [PaddingValues] object itself to be
 * recreated.
 *
 * The final padding is calculated as the maximum of the responsive padding (derived from item types
 * and screen height) and the provided [minimumContentPadding].
 *
 * @param firstItemTypeProvider A provider returning the [ResponsiveItemType] of the first item.
 *   This is read lazily during measurement.
 * @param lastItemTypeProvider A provider returning the [ResponsiveItemType] of the last item. This
 *   is read lazily during measurement.
 * @param minimumContentPadding Additional padding provided by the developer (e.g. via
 *   ScreenScaffold). The returned padding will be at least this large.
 */
@Composable
internal fun rememberResponsiveContentPadding(
    firstItemTypeProvider: () -> ResponsiveItemType?,
    lastItemTypeProvider: () -> ResponsiveItemType?,
    minimumContentPadding: PaddingValues = PaddingValues(),
): PaddingValues {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val currentFirstItemTypeProvider by rememberUpdatedState(firstItemTypeProvider)
    val currentLastItemTypeProvider by rememberUpdatedState(lastItemTypeProvider)
    return remember(screenHeight, minimumContentPadding) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                minimumContentPadding.calculateLeftPadding(layoutDirection)

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                minimumContentPadding.calculateRightPadding(layoutDirection)

            override fun calculateTopPadding(): Dp {
                val itemType = currentFirstItemTypeProvider() ?: ResponsiveItemType.Default
                val responsiveDp = getTopPadding(itemType, screenHeight.dp)

                return max(responsiveDp, minimumContentPadding.calculateTopPadding())
            }

            override fun calculateBottomPadding(): Dp {
                val itemType = currentLastItemTypeProvider() ?: ResponsiveItemType.Default
                val responsiveDp = getBottomPadding(itemType, screenHeight.dp)
                return max(responsiveDp, minimumContentPadding.calculateBottomPadding())
            }
        }
    }
}

private fun getTopPadding(itemType: ResponsiveItemType, screenHeight: Dp): Dp {
    val fraction =
        when (itemType) {
            // 23% Group
            ResponsiveItemType.Button,
            ResponsiveItemType.ButtonGroup,
            ResponsiveItemType.Card -> 0.23f
            // 13% Group
            ResponsiveItemType.CompactButton,
            ResponsiveItemType.ListHeader,
            ResponsiveItemType.Text,
            ResponsiveItemType.IconButton,
            ResponsiveItemType.TextButton -> 0.13f
            else -> 0f
        }
    return screenHeight * fraction
}

private fun getBottomPadding(itemType: ResponsiveItemType, screenHeight: Dp): Dp {
    val fraction =
        when (itemType) {
            // 23% Group
            ResponsiveItemType.Button,
            ResponsiveItemType.ButtonGroup,
            ResponsiveItemType.Card -> 0.23f
            // Asymmetric 23% Bottom Group
            ResponsiveItemType.ListHeader,
            ResponsiveItemType.Text -> 0.23f
            // 13% Group
            ResponsiveItemType.CompactButton,
            ResponsiveItemType.IconButton,
            ResponsiveItemType.TextButton -> 0.13f
            else -> 0f
        }
    return screenHeight * fraction
}
