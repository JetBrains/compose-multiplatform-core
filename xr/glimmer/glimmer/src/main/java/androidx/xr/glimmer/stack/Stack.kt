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

package androidx.xr.glimmer.stack

import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * [VerticalStack] is a lazy scrollable layout that displays its children in a form of a stack where
 * the item on top of the stack is prominently displayed. [VerticalStack] implements the item
 * traversal in a vertical direction.
 *
 * @sample androidx.xr.glimmer.samples.VerticalStackSample
 * @param modifier the modifier to apply to this layout.
 * @param state the state of the stack.
 * @param content a block that describes the content. Inside this block you can use methods like
 *   [StackScope.item] to add a single item or [StackScope.items] to add a collection of items.
 */
@Composable
public fun VerticalStack(
    modifier: Modifier = Modifier,
    state: StackState = rememberStackState(),
    content: StackScope.() -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val stackItemHolderState =
        remember(state) {
            // Re-run the DSL to parse items only when the content lambda instance changes.
            derivedStateOf(referentialEqualityPolicy()) {
                    StackItemHolder(latestContent.value).also {
                        // Set the item count on the StackState immediately when the derived state
                        // re-evaluates (i.e., when content changes), even before recomposition.
                        state.itemCount = it.itemCount
                    }
                }
                .also {
                    // This second assignment is necessary to force the derivedStateOf lambda above
                    // (which is executed lazily) to execute synchronously inside this block.
                    state.itemCount = it.value.itemCount
                }
        }

    VerticalPager(
        state = state.pagerState,
        modifier =
            modifier.layout { measurable, constraints ->
                val heightTakenByRevealArea = RevealAreaSize.roundToPx()

                // Shrink the height constraints, to account for the reveal area
                val pagerMinHeightConstraints =
                    (constraints.minHeight - heightTakenByRevealArea).coerceAtLeast(0)
                val pagerMaxHeightConstraints =
                    if (constraints.hasBoundedHeight) {
                        (constraints.maxHeight - heightTakenByRevealArea).coerceAtLeast(0)
                    } else {
                        constraints.maxHeight
                    }
                val pagerConstraints =
                    constraints.copy(
                        minHeight = pagerMinHeightConstraints,
                        maxHeight = pagerMaxHeightConstraints,
                    )

                val pagerPlaceable = measurable.measure(pagerConstraints)

                val layoutWidth = pagerPlaceable.width
                val layoutHeight = pagerPlaceable.height + heightTakenByRevealArea

                val layoutInfo = state.layoutInfoInternal
                layoutInfo.viewportSizeState.value =
                    IntSize(width = layoutWidth, height = layoutHeight)
                layout(layoutWidth, layoutHeight) { pagerPlaceable.place(x = 0, y = 0) }
            },
        key = { page -> stackItemHolderState.value.getKey(page) },
        beyondViewportPageCount = MaxNextVisibleItemCount,
    ) { page ->
        stackItemHolderState.value.withInterval(page) { localIndex, itemInterval ->
            itemInterval.item(StackItemScopeImpl(), localIndex)
        }
    }
}

/** The size of the area where the items beneath the top of the stack item are revealed. */
internal val RevealAreaSize = 18.dp

/** The maximum number of items that can be visible at the same time in addition to the top item. */
internal const val MaxNextVisibleItemCount = 2
