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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull

internal class RootScrollObserver : NestedScrollConnection {
    private var consumedDistance = 0f
    private var totalScrollEvents = 0

    // Reset when all pointers are up.
    fun reset() {
        consumedDistance = 0f
        totalScrollEvents = 0
    }

    fun consumedAnyScroll() = totalScrollEvents > 0 && consumedDistance > 0f
    fun hadAnyScroll() = totalScrollEvents > 0

    // Descendants call dispatchPreScroll BEFORE trying to scroll themselves.
    // We don't want to steal anything — return Zero.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return Offset.Zero
    }

    // Descendants call dispatchPostScroll AFTER they've scrolled.
    // `consumed` is the total already consumed by the chain below us.
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        // Only care about drag-driven scrolls, not fling/programmatic.
        if (source == NestedScrollSource.UserInput) {
            totalScrollEvents++
            consumedDistance += consumed.getDistanceSquared()
        }
        return Offset.Zero
    }
}

@Composable
internal fun WithNestedScrollObserver(
    rootScrollObserver: RootScrollObserver,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = Modifier.nestedScroll(rootScrollObserver),
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.fastMap { it.measure(constraints) }
        val w = placeables.fastMaxOfOrNull { it.width } ?: 0
        val h = placeables.fastMaxOfOrNull { it.height } ?: 0
        layout(w, h) {
            placeables.fastForEach { it.place(0, 0) }
        }
    }
}