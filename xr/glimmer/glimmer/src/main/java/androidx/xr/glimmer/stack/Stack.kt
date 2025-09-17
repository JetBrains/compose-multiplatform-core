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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * [VerticalStack] is a lazy scrollable layout that displays its children in a form of a stack where
 * the item on top of the stack is prominently displayed. [VerticalStack] implements the item
 * traversal in a vertical direction.
 *
 * @sample androidx.xr.glimmer.samples.VerticalStackSample
 * @param modifier the modifier to apply to this layout.
 * @param content a block that describes the content. Inside this block you can use methods like
 *   [StackScope.item] to add a single item or [StackScope.items] to add a collection of items.
 */
@Composable
public fun VerticalStack(modifier: Modifier = Modifier, content: StackScope.() -> Unit) {
    val latestContent = rememberUpdatedState(content)
    val stackItemHolderState = remember {
        // Re-run the DSL to parse items only when the content lambda instance changes.
        derivedStateOf(referentialEqualityPolicy()) { StackItemHolder(latestContent.value) }
    }
    val pagerState = rememberPagerState(pageCount = { stackItemHolderState.value.itemCount })

    VerticalPager(
        state = pagerState,
        modifier = modifier,
        key = { page -> stackItemHolderState.value.getKey(page) },
    ) { page ->
        stackItemHolderState.value.withInterval(page) { localIndex, itemInterval ->
            itemInterval.item(StackItemScopeImpl(), localIndex)
        }
    }
}
