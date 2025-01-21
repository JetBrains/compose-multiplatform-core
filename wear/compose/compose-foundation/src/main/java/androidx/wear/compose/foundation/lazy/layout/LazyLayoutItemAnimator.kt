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

package androidx.wear.compose.foundation.lazy.layout

import androidx.collection.ObjectIntMap
import androidx.collection.mutableScatterMapOf
import androidx.collection.mutableScatterSetOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope

/**
 * Handles the item animations when it is set via "animateItem" modifiers.
 *
 * This class is responsible for:
 * - animating item appearance for the new items.
 * - detecting when item position changed, figuring our start/end offsets and starting the
 *   animations for placement animations.
 * - animating item disappearance for the removed items.
 */
internal class LazyLayoutItemAnimator<T : LazyLayoutMeasuredItem> {
    // state containing relevant info for active items.
    private val keyToItemInfoMap = mutableScatterMapOf<Any, ItemInfo>()

    // snapshot of the key to index map used for the last measuring.
    private var keyIndexMap: LazyLayoutKeyIndexMap? = null

    // keeps the index of the first visible item index.
    private var firstVisibleIndex = 0

    // stored to not allocate it every pass.
    private val movingAwayKeys = mutableScatterSetOf<Any>()
    private val disappearingItems = mutableListOf<LazyLayoutItemAnimation>()
    private var displayingNode: DrawModifierNode? = null

    /**
     * Should be called after the measuring so we can detect position changes and start animations.
     *
     * Note that this method can compose new item and add it into the [positionedItems] list.
     */
    fun onMeasured(
        positionedItems: List<T>,
        firstPassOffsetsPerItemKey: ObjectIntMap<Any>,
        keyIndexMap: LazyLayoutKeyIndexMap,
        isLookingAhead: Boolean,
        hasLookaheadOccurred: Boolean,
        layoutMinOffset: Int,
        layoutMaxOffset: Int,
        coroutineScope: CoroutineScope,
        graphicsContext: GraphicsContext
    ) {
        val previousKeyToIndexMap = this.keyIndexMap
        this.keyIndexMap = keyIndexMap

        val hasAnimations = positionedItems.fastAny { it.hasAnimations() }
        if (!hasAnimations && keyToItemInfoMap.isEmpty()) {
            // no animations specified - no work needed - clear animation info
            releaseAnimations()
            return
        }

        // Only setup animations when we have access to target value in the current pass, which
        // means lookahead pass, or regular pass when not in a lookahead scope.
        val shouldSetupAnimation = isLookingAhead || !hasLookaheadOccurred

        // first add all items we had in the previous run
        keyToItemInfoMap.forEachKey { movingAwayKeys.add(it) }

        // iterate through the items which are visible (without animated offsets)
        positionedItems.fastForEach { item ->
            // remove items we have in the current one as they are still visible.
            movingAwayKeys.remove(item.key)
            if (item.hasAnimations()) {
                val itemInfo = keyToItemInfoMap[item.key]

                // We check how much the item moved between before and after applying
                // the scroll, since we don't want to animate that movement.
                val scrollOffset =
                    item.mainAxisOffset -
                        firstPassOffsetsPerItemKey.getOrDefault(item.key, item.mainAxisOffset)

                val previousIndex = previousKeyToIndexMap?.getIndex(item.key) ?: -1
                val shouldAnimateAppearance = previousIndex == -1 && previousKeyToIndexMap != null

                // there is no state associated with this item yet
                if (itemInfo == null) {
                    val newItemInfo = ItemInfo()
                    newItemInfo.updateAnimation(
                        item,
                        coroutineScope,
                        graphicsContext,
                        layoutMinOffset,
                        layoutMaxOffset,
                    )
                    keyToItemInfoMap[item.key] = newItemInfo
                    initializeAnimation(
                        item,
                        item.getOffset(0).let { if (item.isVertical) it.y else it.x },
                        newItemInfo
                    )
                    applyScrollWithoutAnimation(newItemInfo, scrollOffset)

                    if (shouldAnimateAppearance) {
                        newItemInfo.animations.forEach { it?.animateAppearance() }
                    }
                } else {
                    if (shouldSetupAnimation) {
                        itemInfo.updateAnimation(
                            item,
                            coroutineScope,
                            graphicsContext,
                            layoutMinOffset,
                            layoutMaxOffset,
                        )
                        applyScrollWithoutAnimation(itemInfo, scrollOffset)
                        if (shouldAnimateAppearance) {
                            itemInfo.animations.forEach {
                                if (it != null) {
                                    if (it.isDisappearanceAnimationInProgress) {
                                        disappearingItems.remove(it)
                                        displayingNode?.invalidateDraw()
                                    }
                                    it.animateAppearance()
                                }
                            }
                        }
                        startPlacementAnimationsIfNeeded(item)
                    }
                }
            } else {
                removeInfoForKey(item.key)
            }
        }

        movingAwayKeys.forEach { key ->
            // found an item which was in our map previously but is not a part of the
            // positionedItems now
            val info = keyToItemInfoMap[key] ?: return@forEach

            // TODO: b/380044722 - handle cases in which the item is actually moving away (vs just
            //  being removed)
            if (keyIndexMap.getIndex(key) != -1) return@forEach

            var isProgress = false
            for (index in info.animations.indices) {
                val animation = info.animations[index]
                if (animation != null) {
                    if (animation.isDisappearanceAnimationInProgress) {
                        isProgress = true
                    } else if (animation.isDisappearanceAnimationFinished) {
                        animation.release()
                        info.animations[index] = null
                        disappearingItems.remove(animation)
                        displayingNode?.invalidateDraw()
                    } else {
                        if (animation.layer != null) {
                            animation.animateDisappearance()
                        }
                        if (animation.isDisappearanceAnimationInProgress) {
                            disappearingItems.add(animation)
                            displayingNode?.invalidateDraw()
                            isProgress = true
                        } else {
                            animation.release()
                            info.animations[index] = null
                        }
                    }
                }
            }
            if (!isProgress) {
                removeInfoForKey(key)
            }
        }

        movingAwayKeys.clear()
    }

    private fun applyScrollWithoutAnimation(
        itemInfo: LazyLayoutItemAnimator<T>.ItemInfo,
        scrollYOffset: Int
    ) {
        itemInfo.animations.forEach { animation ->
            if (
                animation != null && animation.rawOffset != LazyLayoutItemAnimation.NotInitialized
            ) {
                animation.rawOffset += IntOffset(0, scrollYOffset)
            }
        }
    }

    private fun removeInfoForKey(key: Any) {
        keyToItemInfoMap.remove(key)?.animations?.forEach { it?.release() }
    }

    /**
     * Should be called when the animations are not needed for the next positions change, for
     * example when we snap to a new position.
     */
    fun reset() {
        releaseAnimations()
        keyIndexMap = null
        firstVisibleIndex = -1
    }

    fun releaseAnimations() {
        if (keyToItemInfoMap.isNotEmpty()) {
            keyToItemInfoMap.forEachValue {
                it.animations.forEach { animation -> animation?.release() }
            }
            keyToItemInfoMap.clear()
        }
    }

    private fun initializeAnimation(
        item: T,
        mainAxisOffset: Int,
        itemInfo: ItemInfo = keyToItemInfoMap[item.key]!!
    ) {
        val firstPlaceableOffset = item.getOffset(0)

        val targetFirstPlaceableOffset =
            if (item.isVertical) {
                firstPlaceableOffset.copy(y = mainAxisOffset)
            } else {
                firstPlaceableOffset.copy(x = mainAxisOffset)
            }

        // initialize offsets
        itemInfo.animations.forEachIndexed { placeableIndex, animation ->
            if (animation != null) {
                val diffToFirstPlaceableOffset =
                    item.getOffset(placeableIndex) - firstPlaceableOffset
                animation.rawOffset = targetFirstPlaceableOffset + diffToFirstPlaceableOffset
            }
        }
    }

    private fun startPlacementAnimationsIfNeeded(item: T, isMovingAway: Boolean = false) {
        val itemInfo = keyToItemInfoMap[item.key]!!
        itemInfo.animations.forEachIndexed { placeableIndex, animation ->
            if (animation != null) {
                val newTarget = item.getOffset(placeableIndex)
                val currentTarget = animation.rawOffset
                if (
                    currentTarget != LazyLayoutItemAnimation.NotInitialized &&
                        currentTarget != newTarget
                ) {
                    animation.animatePlacementDelta(newTarget - currentTarget, isMovingAway)
                }
                animation.rawOffset = newTarget
            }
        }
    }

    fun getAnimation(key: Any, placeableIndex: Int): LazyLayoutItemAnimation? =
        keyToItemInfoMap[key]?.animations?.getOrNull(placeableIndex)

    val modifier: Modifier = DisplayingDisappearingItemsElement(this)

    private val LazyLayoutMeasuredItem.mainAxisOffset
        get() = getOffset(0).let { if (isVertical) it.y else it.x }

    private val LazyLayoutMeasuredItem.crossAxisOffset
        get() = getOffset(0).let { if (!isVertical) it.y else it.x }

    private inner class ItemInfo {
        /**
         * This array will have the same amount of elements as there are placeables on the item. If
         * the element is not null this means there are specs associated with the given placeable.
         */
        var animations = EmptyArray
            private set

        var constraints: Constraints? = null
        var crossAxisOffset: Int = 0

        private val isRunningPlacement
            get() = animations.any { it?.isRunningMovingAwayAnimation == true }

        var layoutMinOffset = 0
            private set

        var layoutMaxOffset = 0
            private set

        fun updateAnimation(
            positionedItem: T,
            coroutineScope: CoroutineScope,
            graphicsContext: GraphicsContext,
            layoutMinOffset: Int,
            layoutMaxOffset: Int,
            crossAxisOffset: Int = positionedItem.crossAxisOffset
        ) {
            if (!isRunningPlacement) {
                this.layoutMinOffset = layoutMinOffset
                this.layoutMaxOffset = layoutMaxOffset
            }
            for (i in positionedItem.placeablesCount until animations.size) {
                animations[i]?.release()
            }
            if (animations.size != positionedItem.placeablesCount) {
                animations = animations.copyOf(positionedItem.placeablesCount)
            }
            constraints = positionedItem.constraints
            this.crossAxisOffset = crossAxisOffset
            repeat(positionedItem.placeablesCount) { index ->
                val specs = positionedItem.getParentData(index).specs
                if (specs == null) {
                    animations[index]?.release()
                    animations[index] = null
                } else {
                    val animation =
                        animations[index]
                            ?: LazyLayoutItemAnimation(
                                    coroutineScope = coroutineScope,
                                    graphicsContext = graphicsContext,
                                    // until b/329417380 is fixed we have to trigger any
                                    // invalidation in
                                    // order for the layer properties change to be applied:
                                    onLayerPropertyChanged = { displayingNode?.invalidateDraw() },
                                    containerHeight = layoutMaxOffset - layoutMinOffset
                                )
                                .also {
                                    animations[index] = it
                                    it.transformedHeight = positionedItem.mainAxisSizeWithSpacings
                                }
                    animation.fadeInSpec = specs.fadeInSpec
                    animation.placementSpec = specs.placementSpec
                    animation.fadeOutSpec = specs.fadeOutSpec
                }
            }
        }
    }

    private data class DisplayingDisappearingItemsElement(
        private val animator: LazyLayoutItemAnimator<*>
    ) : ModifierNodeElement<DisplayingDisappearingItemsNode>() {
        override fun create() = DisplayingDisappearingItemsNode(animator)

        override fun update(node: DisplayingDisappearingItemsNode) {
            node.setAnimator(animator)
        }

        override fun InspectorInfo.inspectableProperties() {
            name = "DisplayingDisappearingItemsElement"
        }
    }

    private data class DisplayingDisappearingItemsNode(
        private var animator: LazyLayoutItemAnimator<*>
    ) : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            animator.disappearingItems.fastForEach {
                val layer = it.layer ?: return@fastForEach
                val x = it.finalOffset.x.toFloat()
                val y = it.finalOffset.y.toFloat()
                translate(x - layer.topLeft.x.toFloat(), y - layer.topLeft.y.toFloat()) {
                    drawLayer(layer)
                }
            }
            drawContent()
        }

        override fun onAttach() {
            animator.displayingNode = this
        }

        override fun onDetach() {
            animator.reset()
        }

        fun setAnimator(animator: LazyLayoutItemAnimator<*>) {
            if (this.animator != animator) {
                if (node.isAttached) {
                    this.animator.reset()
                    animator.displayingNode = this
                    this.animator = animator
                }
            }
        }
    }
}

private val EmptyArray = emptyArray<LazyLayoutItemAnimation?>()
