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

package androidx.compose.foundation.lazy.layout

import androidx.collection.mutableIntIntMapOf
import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableIntSetOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState.PrefetchHandle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.traceValue
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

/** Implements the logic for [LazyLayoutCacheWindow] prefetching and item preservation. */
@OptIn(ExperimentalFoundationApi::class)
internal abstract class CacheWindowLogic(
    private val cacheWindow: LazyLayoutCacheWindow,
) {

    /** Handles for prefetched items in the current forward window. */
    private val prefetchWindowHandles = mutableIntObjectMapOf<List<PrefetchHandle>>()

    private val indicesToRemove = mutableIntSetOf()
    /**
     * Cache for items sizes in the current window. Holds sizes for both visible and non-visible
     * items
     */
    private val windowCache = mutableIntIntMapOf()
    private var previousPassDelta = 0f
    private var hasUpdatedVisibleItemsOnce = false

    /**
     * Indices for the start and end of the cache window. The items between
     * [prefetchWindowStartLine] and [prefetchWindowEndLine] can be:
     * 1) Visible.
     * 2) Cached.
     * 3) Scheduled for prefetching.
     * 4) Not scheduled yet.
     */
    internal var prefetchWindowStartLine = Int.MAX_VALUE
        private set

    internal var prefetchWindowEndLine = Int.MIN_VALUE
        private set

    /**
     * Keeps track of the "extra" space used. Extra space starts by being the amount of space
     * occupied by the first and last visible items outside of the viewport, that is, how much
     * they're "peeking" out of view. These values will be updated as we fill the cache window.
     */
    private var prefetchWindowStartExtraSpace = 0
    private var prefetchWindowEndExtraSpace = 0

    /** Signals that we should run the window refilling loop from start. */
    private var shouldRefillWindow = false

    /** Keep the latest item count where it can be used more easily. */
    private var itemsCount = 0

    fun CacheWindowScope.onScroll(delta: Float) {
        traceWindowInfo()
        fillCacheWindowBackward(delta)
        fillCacheWindowForward(delta)
        previousPassDelta = delta
        traceWindowInfo()
    }

    private fun traceWindowInfo() {
        traceValue("prefetchWindowStartExtraSpace", prefetchWindowStartExtraSpace.toLong())
        traceValue("prefetchWindowEndExtraSpace", prefetchWindowEndExtraSpace.toLong())
        traceValue("prefetchWindowStartIndex", prefetchWindowStartLine.toLong())
        traceValue("prefetchWindowEndIndex", prefetchWindowEndLine.toLong())
    }

    fun CacheWindowScope.onVisibleItemsUpdated() {
        if (!hasUpdatedVisibleItemsOnce) {
            val prefetchForwardWindow =
                with(cacheWindow) { density?.calculateAheadWindow(mainAxisViewportSize) ?: 0 }
            // we won't fill the window if we don't have a prefetch window
            if (prefetchForwardWindow != 0) shouldRefillWindow = true
            hasUpdatedVisibleItemsOnce = true
        }

        itemsCount = totalItemsCount
        // If visible items changed, update cached information. Any items that were visible
        // and became out of bounds will either count for the cache window or be cancelled/removed
        // by [cancelOutOfBounds]. If any items changed sizes we re-trigger the window filling
        // update.
        if (hasVisibleItems) {
            forEachVisibleItem { index, mainAxisSize -> cacheVisibleItemsInfo(index, mainAxisSize) }
            if (shouldRefillWindow) {
                fillCacheWindowForward(0.0f)
                shouldRefillWindow = false
            }
        } else {
            // if no visible items, it means the dataset is empty and we should reset the window.
            // Next time visible items update we we re-start the window strategy.
            resetStrategy()
        }
    }

    fun hasValidBounds() =
        prefetchWindowStartLine != Int.MAX_VALUE && prefetchWindowEndLine != Int.MIN_VALUE

    private fun CacheWindowScope.fillCacheWindowBackward(delta: Float) {
        if (hasVisibleItems) {
            val viewport = mainAxisViewportSize

            val keepAroundWindow =
                with(cacheWindow) { density?.calculateBehindWindow(viewport) ?: 0 }

            // save latest item count
            itemsCount = totalItemsCount

            onKeepAround(
                visibleWindowStart = firstVisibleLineIndex,
                visibleWindowEnd = lastVisibleLineIndex,
                keepAroundWindow = keepAroundWindow,
                scrollDelta = delta,
                itemsCount = totalItemsCount,
                mainAxisExtraSpaceStart = mainAxisExtraSpaceStart,
                mainAxisExtraSpaceEnd = mainAxisExtraSpaceEnd,
            )
        }
    }

    private fun CacheWindowScope.fillCacheWindowForward(delta: Float) {
        if (hasVisibleItems) {
            val viewport = mainAxisViewportSize

            val prefetchForwardWindow =
                with(cacheWindow) { density?.calculateAheadWindow(viewport) ?: 0 }

            onPrefetchForward(
                visibleWindowStart = firstVisibleLineIndex,
                visibleWindowEnd = lastVisibleLineIndex,
                prefetchForwardWindow = prefetchForwardWindow,
                scrollDelta = delta,
                mainAxisExtraSpaceStart = mainAxisExtraSpaceStart,
                mainAxisExtraSpaceEnd = mainAxisExtraSpaceEnd
            )
        }
    }

    fun resetStrategy() {
        prefetchWindowStartLine = Int.MAX_VALUE
        prefetchWindowEndLine = Int.MIN_VALUE
        prefetchWindowStartExtraSpace = 0
        prefetchWindowEndExtraSpace = 0

        windowCache.clear()
        prefetchWindowHandles.removeIf { _, value ->
            value.fastForEach { it.cancel() }
            true
        }
    }

    /**
     * Prefetch Forward Logic: Fill in the forward window with prefetched items from the previous
     * measure pass. If the item is not prefetched yet, schedule a prefetching for it. Once a
     * prefetch returns, we check if the window is filled and if not we schedule the next
     * prefetching.
     */
    private fun CacheWindowScope.onPrefetchForward(
        visibleWindowStart: Int,
        visibleWindowEnd: Int,
        prefetchForwardWindow: Int,
        mainAxisExtraSpaceEnd: Int,
        mainAxisExtraSpaceStart: Int,
        scrollDelta: Float
    ) {
        val changedScrollDirection = scrollDelta.sign != previousPassDelta.sign

        if (scrollDelta <= 0.0f) { // scrolling forward, starting on last visible
            if (changedScrollDirection || shouldRefillWindow) {
                prefetchWindowEndExtraSpace = (prefetchForwardWindow - mainAxisExtraSpaceEnd)
                prefetchWindowEndLine = visibleWindowEnd
            } else {
                prefetchWindowEndExtraSpace += scrollDelta.absoluteValue.roundToInt()
            }

            while (prefetchWindowEndExtraSpace > 0 && prefetchWindowEndLine < itemsCount) {
                // If we get the same delta in the next frame, would we cover the extra space needed
                // to actually need this item? If so, mark it as urgent
                val isUrgent: Boolean =
                    if (prefetchWindowEndLine + 1 == visibleWindowEnd + 1) {
                        scrollDelta.absoluteValue >= mainAxisExtraSpaceEnd
                    } else {
                        false
                    }

                // no more items available to fill prefetch window if this is null, break
                val itemSize =
                    getItemSizeOrPrefetch(index = prefetchWindowEndLine + 1, isUrgent = isUrgent)

                if (itemSize == InvalidItemSize) break

                prefetchWindowEndLine++
                prefetchWindowEndExtraSpace -= itemSize
            }
        } else { // scrolling backwards, starting on first visible

            if (changedScrollDirection || shouldRefillWindow) {
                prefetchWindowStartExtraSpace = (prefetchForwardWindow - mainAxisExtraSpaceStart)
                prefetchWindowStartLine = visibleWindowStart
            } else {
                prefetchWindowStartExtraSpace += scrollDelta.absoluteValue.roundToInt()
            }

            while (prefetchWindowStartExtraSpace > 0 && prefetchWindowStartLine > 0) {
                // If we get the same delta in the next frame, would we cover the extra space needed
                // to actually need this item? If so, mark it as urgent
                val isUrgent: Boolean =
                    if (prefetchWindowStartLine - 1 == visibleWindowStart - 1) {
                        scrollDelta.absoluteValue >= mainAxisExtraSpaceStart
                    } else {
                        false
                    }
                // no more items available to fill prefetch window if this is null, break
                val itemSize =
                    getItemSizeOrPrefetch(index = prefetchWindowStartLine - 1, isUrgent = isUrgent)
                if (itemSize == InvalidItemSize) break
                prefetchWindowStartLine--
                prefetchWindowStartExtraSpace -= itemSize
            }
        }
    }

    /**
     * Keep Around Logic: Keep around items were visible in the previous measure pass. This means
     * that they will be present in [windowCache] along their size information. We loop through
     * items starting in the last visible one and update [prefetchWindowStartExtraSpace] or
     * [prefetchWindowEndExtraSpace] and also [prefetchWindowStartLine] or [prefetchWindowEndLine].
     * We never schedule a prefetch call for keep around items.
     */
    private fun onKeepAround(
        visibleWindowStart: Int,
        visibleWindowEnd: Int,
        mainAxisExtraSpaceEnd: Int,
        mainAxisExtraSpaceStart: Int,
        keepAroundWindow: Int,
        scrollDelta: Float,
        itemsCount: Int
    ) {

        if (scrollDelta <= 0.0f) { // scrolling forward, keep around from firstVisible
            prefetchWindowStartExtraSpace = (keepAroundWindow - mainAxisExtraSpaceStart)
            prefetchWindowStartLine = visibleWindowStart
            while (prefetchWindowStartExtraSpace > 0 && prefetchWindowStartLine > 0) {
                val item =
                    if (windowCache.containsKey(prefetchWindowStartLine - 1)) {
                        windowCache[prefetchWindowStartLine - 1]
                    } else {
                        break
                    }

                prefetchWindowStartLine--
                prefetchWindowStartExtraSpace -= item
            }
            removeOutOfBoundsItems(0, prefetchWindowStartLine - 1)
        } else { // scrolling backwards, keep around from last visible
            prefetchWindowEndExtraSpace = (keepAroundWindow - mainAxisExtraSpaceEnd)
            prefetchWindowEndLine = visibleWindowEnd
            while (prefetchWindowEndExtraSpace > 0 && prefetchWindowEndLine < itemsCount) {
                val item =
                    if (windowCache.containsKey(prefetchWindowEndLine + 1)) {
                        windowCache[prefetchWindowEndLine + 1]
                    } else {
                        break
                    }
                prefetchWindowEndLine++
                prefetchWindowEndExtraSpace -= item
            }
            removeOutOfBoundsItems(prefetchWindowEndLine + 1, itemsCount - 1)
        }
    }

    private fun CacheWindowScope.getItemSizeOrPrefetch(index: Int, isUrgent: Boolean): Int {
        return if (windowCache.containsKey(index)) {
            windowCache[index]
        } else if (prefetchWindowHandles.containsKey(index)) {
            // item is scheduled but didn't finish yet
            if (isUrgent) prefetchWindowHandles[index]?.fastForEach { it.markAsUrgent() }
            InvalidItemSize
        } else {
            // item is not scheduled
            prefetchWindowHandles[index] =
                schedulePrefetch(index) { prefetchedIndex, size ->
                    onItemPrefetched(prefetchedIndex, size)
                }
            if (isUrgent) prefetchWindowHandles[index]?.fastForEach { it.markAsUrgent() }
            InvalidItemSize
        }
    }

    /** Grows the window with measured items and prefetched items. */
    private fun cachePrefetchedItem(index: Int, size: Int) {
        windowCache[index] = size
        if (index > prefetchWindowEndLine) {
            prefetchWindowEndLine = index
            prefetchWindowEndExtraSpace -= size
        } else if (index < prefetchWindowStartLine) {
            prefetchWindowStartLine = index
            prefetchWindowStartExtraSpace -= size
        }
    }

    /**
     * When caching visible items we need to check if the existing item changed sizes. If so, we
     * will set [shouldRefillWindow] which will trigger a complete window filling and cancel any out
     * of bounds requests.
     */
    private fun cacheVisibleItemsInfo(index: Int, size: Int) {
        if (windowCache.containsKey(index) && windowCache[index] != size) {
            shouldRefillWindow = true
        }

        windowCache[index] = size
        // We're caching a visible item, remove its handle since we won't need it anymore.
        prefetchWindowStartLine = minOf(prefetchWindowStartLine, index)
        prefetchWindowEndLine = maxOf(prefetchWindowEndLine, index)
        prefetchWindowHandles.remove(index)?.fastForEach { it.cancel() }
    }

    /** Takes care of removing caches and canceling handles for items that we won't use anymore. */
    private fun removeOutOfBoundsItems(startLine: Int, endLine: Int) {
        indicesToRemove.clear()
        prefetchWindowHandles.forEachKey { if (it in startLine..endLine) indicesToRemove.add(it) }

        windowCache.forEachKey { if (it in startLine..endLine) indicesToRemove.add(it) }

        indicesToRemove.forEach {
            prefetchWindowHandles.remove(it)?.fastForEach { it.cancel() }
            windowCache.remove(it)
        }
    }

    /**
     * Item prefetching finished, we can cache its information and schedule the next prefetching if
     * needed.
     */
    private fun CacheWindowScope.onItemPrefetched(index: Int, itemSize: Int) {
        cachePrefetchedItem(index, itemSize)
        scheduleNextItemIfNeeded()
        traceWindowInfo()
    }

    private fun CacheWindowScope.scheduleNextItemIfNeeded() {
        var nextPrefetchableIndex: Int = -1
        // if was scrolling forward
        if (previousPassDelta.sign <= 0) {
            if (prefetchWindowEndExtraSpace > 0) nextPrefetchableIndex = prefetchWindowEndLine + 1
        } else if (previousPassDelta.sign > 0) {
            if (prefetchWindowStartExtraSpace > 0)
                nextPrefetchableIndex = prefetchWindowStartLine - 1
        }

        if (nextPrefetchableIndex in 0..itemsCount) {
            prefetchWindowHandles[nextPrefetchableIndex] =
                schedulePrefetch(nextPrefetchableIndex) { index, mainAxisSize ->
                    onItemPrefetched(index, mainAxisSize)
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Bridge between LazyLayout and its implementation. */
internal interface CacheWindowScope {
    val totalItemsCount: Int
    val visibleLineCount: Int
    val hasVisibleItems: Boolean
    val mainAxisExtraSpaceStart: Int
    val mainAxisExtraSpaceEnd: Int
    val firstVisibleLineIndex: Int
    val lastVisibleLineIndex: Int
    val mainAxisViewportSize: Int
    val density: Density?

    fun schedulePrefetch(lineIndex: Int, onItemPrefetched: (Int, Int) -> Unit): List<PrefetchHandle>

    fun getVisibleItemSize(indexInVisibleLines: Int): Int

    fun getVisibleItemLine(indexInVisibleLines: Int): Int
}

internal inline fun CacheWindowScope.forEachVisibleItem(
    action: (itemIndex: Int, mainAxisSize: Int) -> Unit
) {
    repeat(visibleLineCount) { action(getVisibleItemLine(it), getVisibleItemSize(it)) }
}

private const val InvalidItemSize = -1
