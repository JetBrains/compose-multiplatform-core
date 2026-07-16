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

package androidx.compose.ui.platform.accessibility

import androidx.collection.mutableIntObjectMapOf
import androidx.collection.mutableObjectListOf
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.findClosestParentNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.sortByGeometryGroupings
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import kotlin.js.js
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.InputEvent


internal class ComposeWebSemanticsOwner(
    val semanticsOwner: SemanticsOwner,
    val webSemanticsRoot: HTMLElement,
) {

    private var job: Job? = null
    private val invalidationChannel =
        Channel<Unit>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)
    private val syncTriggerChannel =
        Channel<Long>(1, onBufferOverflow = BufferOverflow.DROP_LATEST)

    private companion object {
        const val MAX_TIME_IN_DEBOUNCE_MS = 1000L
        const val DEBOUNCE_MS = 100L
    }

    fun initialize(scope: CoroutineScope) {
        // Here we do the following:
        // - Every invalidation doesn't trigger an a11y tree sync immediately, but only after the changes have settled (debounce 100ms).
        // - We track the time spent in "debounce", so eventually it must sync the a11y tree despite no pause in invalidation events (the changes couldn't settle).
        // So the a11y tree sync will happen either when the changes have settled or when the timeSpentInDebounce exceeds 1000 ms.

        /*
              1) --x-x-x-x-------------------------------------------------
                         |--- 100ms ---| -> sync after changes settle

              2) ---x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x-x--
                    |-------- 1000ms -------| spent 1 second debouncing
                                            |-> forced sync

              3) ----------------------------x-x-x-x-x-x-x-x---------------
                 |---------- 1200ms ---------|             |--- 100 ms ---| -> sync after changes settle
                                             | No forced sync here, because the debouncing has just started
         */
        job = scope.launch {
            var timeSpentDebouncing = 0L
            var lastDebouncedTime = 0L
            var lastSyncTime = currentTimeMillis()

            launch {
                invalidationChannel.receiveAsFlow().collect {
                    val currentTime = currentTimeMillis()

                    if (lastDebouncedTime == 0L) {
                        lastDebouncedTime = currentTime
                        timeSpentDebouncing = 0L
                    } else {
                        val delta = currentTime - lastDebouncedTime
                        timeSpentDebouncing += delta
                        lastDebouncedTime = currentTime
                    }

                    if (timeSpentDebouncing >= MAX_TIME_IN_DEBOUNCE_MS) {
                        // we've been debouncing for too long, but must sync periodically, so force a sync
                        lastDebouncedTime = 0L
                        lastSyncTime = currentTime
                        syncSemanticsWithWebA11Y()
                    } else {
                        syncTriggerChannel.trySend(currentTime)
                    }
                }
            }

            @OptIn(FlowPreview::class)
            launch {
                // debounce until the Semantics changes settled for at least 100ms
                syncTriggerChannel.receiveAsFlow().debounce(DEBOUNCE_MS.milliseconds).collect {
                    val currentTime = currentTimeMillis()

                    // syncSemanticsWithWebA11Y could've been triggered from a "force sync" above,
                    // so we check the lastSyncTime here
                    if (currentTime - lastSyncTime >= DEBOUNCE_MS) {
                        lastDebouncedTime = 0L
                        lastSyncTime = currentTime
                        syncSemanticsWithWebA11Y()
                    }
                }
            }
        }
    }

    fun sendInvalidation() {
        invalidationChannel.trySend(Unit)
    }

    private val bfsDeque = ArrayDeque<SemanticsNode>()

    //Necessary due to the Breadth-First traversal of the semantics tree, to keep track of the correct parent in the DOM tree
    private val domParentQueue = ArrayDeque<HTMLElement>()

    /**
     * Maps the [WebSemanticsNode]s we have created by the [SemanticsNode.id] for which they were
     * created.
     */
    private var accessibleByNodeId = mutableIntObjectMapOf<WebSemanticsNode>()

    /**
     * An auxiliary mapping of semantics node ids to [WebSemanticsNode]s that is swapped with
     * [accessibleByNodeId] on each sync, to avoid allocating memory on each sync.
     */
    private var auxAccessibleByNodeId = mutableIntObjectMapOf<WebSemanticsNode>()

    private val pendingSyncWebSemanticsNodes = mutableObjectListOf<WebSemanticsNode>()

    /**
     * Syncs [accessibleByNodeId] with the semantics node tree.
     */
    private fun syncSemanticsWithWebA11Y() {
        fun SemanticsNode.isValid() =
            layoutNode.let { it.isPlaced && it.isAttached } and !config.let {
                @Suppress("DEPRECATION")
                SemanticsProperties.InvisibleToUser in it ||
                    SemanticsProperties.HideFromAccessibility in it
            }

        val previous = accessibleByNodeId
        val updated = auxAccessibleByNodeId
        val rootSemanticNode = semanticsOwner.rootSemanticsNode

        if (!rootSemanticNode.isValid()) {
            previous.forEach { _, node ->
                node.dispose()
            }
            previous.clear()
            return
        }

        val htmlRootElementOffset = webSemanticsRoot.getBoundingClientRect().let {
            Offset(it.left.toFloat(), it.top.toFloat())
        }

        // We use a Queue to naturally map Top-Down traversal keeping exact Parent mapping
        bfsDeque.addLast(rootSemanticNode)
        domParentQueue.addLast(webSemanticsRoot)

        while (!bfsDeque.isEmpty()) {
            val node = bfsDeque.removeFirst()
            val domParent = domParentQueue.removeFirst()

            val existingWebSemanticsNode = previous[node.id]

            val currentHtmlSemanticNode = if (existingWebSemanticsNode != null) {
                val oldSemanticsConfig = existingWebSemanticsNode.configuration
                existingWebSemanticsNode.semanticsNode = node
                existingWebSemanticsNode.pendingOldSemanticsConfiguration = oldSemanticsConfig

                existingWebSemanticsNode
            } else {
                val htmlNode = createHtmlElementForSemanticNode()
                WebSemanticsNode(htmlNode, node)
            }

            pendingSyncWebSemanticsNodes.add(currentHtmlSemanticNode)

            updated[node.id] = currentHtmlSemanticNode
            val currentWebSemanticsHtmlElement = currentHtmlSemanticNode.backingHtmlElement

            addChildToParentOrIgnore(currentWebSemanticsHtmlElement, domParent)

            var currentDomChildIndex = 0

            node.sortFlattenChildren().fastForEach { childSemanticsNode ->
                if (!childSemanticsNode.isValid()) return@fastForEach
                val childWebSemanticsNode =
                    previous[childSemanticsNode.id] ?: updated[childSemanticsNode.id]

                if (childWebSemanticsNode != null) {
                    updateHtmlNodeIfRequired(
                        currentWebSemanticsHtmlElement,
                        childWebSemanticsNode.backingHtmlElement,
                        currentDomChildIndex
                    )
                    currentDomChildIndex++
                }


                bfsDeque.addLast(childSemanticsNode)
                domParentQueue.addLast(currentWebSemanticsHtmlElement)
            }
        }

        //Remove all the nodes that are not in the new tree or are invalid
        previous.forEach { id, node ->
            if (id !in updated || !node.semanticsNode.isValid()) {
                node.dispose()
            }
        }

        auxAccessibleByNodeId = previous.also { it.clear() }
        accessibleByNodeId = updated

        pendingSyncWebSemanticsNodes.forEach { node ->
            syncNode(
                webSemanticsNode = node,
                htmlRootElementOffset = htmlRootElementOffset,
            )
            node.pendingOldSemanticsConfiguration = null
            node.appended = true
        }

        pendingSyncWebSemanticsNodes.clear()
        bfsDeque.clear()
        domParentQueue.clear()
    }


    /**
     * Returns `true` when the value has changed compared to the old configuration,
     * or this is the first sync (node just added). Avoids repeating `isAdded ||` everywhere.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> needsUpdate(isNewlyAdded: Boolean, value: T, oldValue: T): Boolean =
        isNewlyAdded || value != oldValue

    @Suppress("NOTHING_TO_INLINE")
    private inline fun needsRemoval(isNewlyAdded: Boolean, oldValueExists: Boolean): Boolean =
        !isNewlyAdded && oldValueExists

    private fun syncNode(
        webSemanticsNode: WebSemanticsNode,
        htmlRootElementOffset: Offset,
    ) {
        val isNewlyCreated = !webSemanticsNode.appended
        val config = webSemanticsNode.configuration
        val oldConfig = webSemanticsNode.pendingOldSemanticsConfiguration
        val htmlNode = webSemanticsNode.backingHtmlElement

        if (SemanticsProperties.Text in config) {
            val text = config[SemanticsProperties.Text]
            val oldText = oldConfig?.getOrNull(SemanticsProperties.Text)
            if (needsUpdate(isNewlyCreated, text, oldText)) {
                //Better than innerText since it does not cause layout reflows
                htmlNode.textContent = text.fastJoinToString("\n") { it.text }
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.Text) == true
                )
            ) {
                //Better than innerText since it does not cause layout reflows
                htmlNode.textContent = ""
            }
        }

        if (SemanticsProperties.ContentDescription in config) {
            val contentDescription = config[SemanticsProperties.ContentDescription]
            val oldContentDescription =
                oldConfig?.getOrNull(SemanticsProperties.ContentDescription)
            if (needsUpdate(isNewlyCreated, contentDescription, oldContentDescription)) {
                setAriaLabel(htmlNode, contentDescription.fastJoinToString(", "))
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.ContentDescription) == true
                )
            ) {
                removeAriaLabel(htmlNode)
            }
        }

        if (SemanticsActions.OnClick in config) {
            val listener = config[SemanticsActions.OnClick].action!!
            val oldListener = oldConfig?.getOrNull(SemanticsActions.OnClick)?.action

            if (needsUpdate(isNewlyCreated, listener, oldListener)) {
                webSemanticsNode.addOrReplaceEventListener("click") {
                    listener.invoke()
                }
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsActions.OnClick) == true
                )
            ) {
                webSemanticsNode.removeEventListener("click")
            }
        }

        if (SemanticsProperties.TestTag in config) {
            val testTag = config[SemanticsProperties.TestTag]
            val oldTestTag = oldConfig?.getOrNull(SemanticsProperties.TestTag)
            if (needsUpdate(isNewlyCreated, testTag, oldTestTag)) {
                htmlNode.id = testTag
            }
        } else {
            //Rather than removal, it is replacing it for the default id which is formatted as "a11y_${semanticsNode.id}"
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.TestTag) == true
                )
            ) {
                setElementId(htmlNode, webSemanticsNode.id)
            }
        }

        val disabled = SemanticsProperties.Disabled in config
        val oldDisabled =
            if (!isNewlyCreated) oldConfig?.contains(SemanticsProperties.Disabled) == true else false
        if (disabled) {
            setDisabled(htmlNode)
        } else {
            if (oldDisabled) {
                removeDisabled(htmlNode)
            }
        }

        if (SemanticsProperties.EditableText in config) {
            val text = config[SemanticsProperties.EditableText].text
            val oldText = oldConfig?.getOrNull(SemanticsProperties.EditableText)?.text
            if (needsUpdate(isNewlyCreated, text, oldText)) {
                htmlNode.textContent = text
            }

            val editable = config.getOrNull(SemanticsProperties.IsEditable) ?: false
            val oldEditable = oldConfig?.getOrNull(SemanticsProperties.IsEditable) ?: false
            if (needsUpdate(isNewlyCreated, editable, oldEditable)) {
                setContentEditable(htmlNode, editable)
            }

            val readOnly = !editable && !disabled
            val oldReadOnly = !oldEditable && !oldDisabled
            if (needsUpdate(isNewlyCreated, readOnly, oldReadOnly)) {
                if (readOnly) {
                    setReadOnly(htmlNode)
                } else {
                    removeReadOnly(htmlNode)
                }
            }

            if (isNewlyCreated) {
                webSemanticsNode.addOrReplaceEventListener("focus") {
                    htmlNode.click()
                }
            }
        }
        if (SemanticsProperties.MaxTextLength in config) {
            val maxTextLength = config[SemanticsProperties.MaxTextLength]
            val oldMaxTextLength = oldConfig?.getOrNull(SemanticsProperties.MaxTextLength)
            if (needsUpdate(isNewlyCreated, maxTextLength, oldMaxTextLength)) {
                setMaxTextLength(htmlNode, maxTextLength)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.MaxTextLength) == true
                )
            ) {
                removeMaxTextLength(htmlNode)
            }
        }

        if (SemanticsProperties.Selected in config) {
            val selected = config[SemanticsProperties.Selected]
            val oldSelected = oldConfig?.getOrNull(SemanticsProperties.Selected)

            if (needsUpdate(isNewlyCreated, selected, oldSelected)) {
                setSelected(htmlNode, selected)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.Selected) == true
                )
            ) {
                removeSelected(htmlNode)
            }
        }

        if (SemanticsProperties.Focused in config) {
            if (isNewlyCreated) {
                setFocusable(htmlNode)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.Focused) == true
                )
            ) {
                removeFocusable(htmlNode)
            }
        }

        if (SemanticsProperties.Error in config) {
            //TODO: Implement aria-errormessage, which accepts an HTMLElement id that refers to the error message.
            if (isNewlyCreated) {
                setErrorState(htmlNode)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.Error) == true
                )
            ) {
                removeErrorState(htmlNode)
            }
        }

        if (SemanticsProperties.IsDialog in config) {
            if (isNewlyCreated || oldConfig?.contains(SemanticsProperties.IsDialog) != true) {
                setAriaModal(htmlNode)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.IsDialog) == true
                )
            ) {
                removeAriaModal(htmlNode)
            }
        }

        if (SemanticsProperties.HideFromAccessibility !in config && !isNewlyCreated) {
            if (oldConfig?.contains(SemanticsProperties.HideFromAccessibility) == true) {
                removeHidden(htmlNode)
            }
        }

        if (SemanticsProperties.LiveRegion in config) {
            val liveRegion = config[SemanticsProperties.LiveRegion]
            val oldLiveRegion = oldConfig?.getOrNull(SemanticsProperties.LiveRegion)
            if (needsUpdate(isNewlyCreated, liveRegion, oldLiveRegion)) {
                setLiveRegion(
                    htmlNode, when (liveRegion) {
                        LiveRegionMode.Polite -> 0
                        LiveRegionMode.Assertive -> 1
                        else -> 2
                    }
                )
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.LiveRegion) == true
                )
            ) {
                removeLiveRegionAttribute(htmlNode)
            }
        }

        if (SemanticsProperties.ProgressBarRangeInfo in config) {
            val info = config[SemanticsProperties.ProgressBarRangeInfo]
            val oldInfo = oldConfig?.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            if (needsUpdate(
                    isNewlyCreated,
                    info,
                    oldInfo
                ) && info != ProgressBarRangeInfo.Indeterminate
            ) {
                setProgressBarRangeInfo(
                    htmlNode,
                    info.range.start,
                    info.range.endInclusive,
                    info.current,
                )
            }
            if (SemanticsProperties.StateDescription in config && info != ProgressBarRangeInfo.Indeterminate) {
                val stateDescription = config[SemanticsProperties.StateDescription]
                val oldStateDescription =
                    oldConfig?.getOrNull(SemanticsProperties.StateDescription)
                if (needsUpdate(isNewlyCreated, stateDescription, oldStateDescription)) {
                    setValueTextStateDescription(htmlNode, stateDescription)
                }
            }

        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.ProgressBarRangeInfo) == true
                )
            ) {
                removeRangeInfoAttributes(htmlNode)
            }
        }

        if (SemanticsProperties.ToggleableState in config) {
            val state = config[SemanticsProperties.ToggleableState]
            val oldState = oldConfig?.getOrNull(SemanticsProperties.ToggleableState)
            if (needsUpdate(isNewlyCreated, state, oldState)) {
                setCheckedState(htmlNode, state.ordinal)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.ToggleableState) == true
                )
            ) {
                removeCheckedState(htmlNode)
            }
        }

        if (SemanticsProperties.CollectionInfo in config) {
            val info = config[SemanticsProperties.CollectionInfo]
            val oldInfo = oldConfig?.getOrNull(SemanticsProperties.CollectionInfo)
            if (needsUpdate(isNewlyCreated, info, oldInfo)) {
                setCollectionInfo(htmlNode, info.rowCount, info.columnCount)
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.CollectionInfo) == true
                )
            ) {
                removeCollectionInfoAttributes(htmlNode)
            }
        }

        if (SemanticsProperties.CollectionItemInfo in config) {
            val info = config[SemanticsProperties.CollectionItemInfo]
            val oldInfo = oldConfig?.getOrNull(SemanticsProperties.CollectionItemInfo)
            if (needsUpdate(isNewlyCreated, info, oldInfo)) {
                setItemCollectionInfo(
                    htmlNode,
                    info.rowIndex,
                    info.columnIndex,
                    info.rowSpan,
                    info.columnSpan
                )
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsProperties.CollectionItemInfo) == true
                )
            ) {
                removeCollectionItemInfoAttributes(htmlNode)
            }
        }

        val verticallyScrollable = SemanticsProperties.VerticalScrollAxisRange in config
        val horizontallyScrollable = SemanticsProperties.HorizontalScrollAxisRange in config
        val oldVerticallyScrollable =
            oldConfig?.contains(SemanticsProperties.VerticalScrollAxisRange) == true
        val oldHorizontallyScrollable =
            oldConfig?.contains(SemanticsProperties.HorizontalScrollAxisRange) == true

        val scrollChanged = isNewlyCreated ||
            verticallyScrollable != oldVerticallyScrollable ||
            horizontallyScrollable != oldHorizontallyScrollable

        if (scrollChanged) {
            if (verticallyScrollable xor horizontallyScrollable) {
                setOrientation(htmlNode, verticallyScrollable)
            } else {
                removeOrientation(htmlNode)
            }
        }

        if (SemanticsActions.RequestFocus in config) {
            val listener = config[SemanticsActions.RequestFocus].action!!
            val oldListener = oldConfig?.getOrNull(SemanticsActions.RequestFocus)?.action
            if (needsUpdate(isNewlyCreated, listener, oldListener)) {
                webSemanticsNode.addOrReplaceEventListener("focus") {
                    listener.invoke()
                }
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsActions.RequestFocus) == true
                )
            ) {
                webSemanticsNode.removeEventListener("focus")
            }
        }

        if (SemanticsActions.SetProgress in config) {
            val listener = config[SemanticsActions.SetProgress].action!!
            val oldListener = oldConfig?.getOrNull(SemanticsActions.SetProgress)?.action
            if (needsUpdate(isNewlyCreated, listener, oldListener)) {
                webSemanticsNode.addOrReplaceEventListener("input") {
                    it as InputEvent
                    val value = it.data.toFloatOrNull() ?: return@addOrReplaceEventListener
                    listener.invoke(value)
                }
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsActions.SetProgress) == true
                )
            ) {
                webSemanticsNode.removeEventListener("input")
            }
        }

        if (SemanticsActions.ScrollBy in config) {
            val listener = config[SemanticsActions.ScrollBy].action!!
            val oldListener = oldConfig?.getOrNull(SemanticsActions.ScrollBy)?.action
            if (needsUpdate(isNewlyCreated, listener, oldListener)) {
                webSemanticsNode.addOrReplaceEventListener("scroll") {
                    val scrollLeft = htmlNode.scrollLeft.toFloat()
                    val scrollTop = htmlNode.scrollTop.toFloat()
                    listener.invoke(scrollLeft, scrollTop)
                }
            }
        } else {
            if (needsRemoval(
                    isNewlyCreated,
                    oldConfig?.contains(SemanticsActions.ScrollBy) == true
                )
            ) {
                webSemanticsNode.removeEventListener("scroll")
            }
        }

        val ariaRole = config.getRoleId()
        val oldAriaRole = oldConfig?.getRoleId() ?: AriaRoleId.Unknown

        if (needsUpdate(isNewlyCreated, ariaRole, oldAriaRole)) {
            setA11YAriaRole(element = htmlNode, ariaRole)
        }

        webSemanticsNode.setBounds(htmlRootElementOffset)
    }

    internal fun SemanticsNode.sortFlattenChildren(): List<SemanticsNode> {
        val sortedChildren = sortByGeometryGroupings(
            replacedChildren,
            {
                it.config.contains(SemanticsProperties.Focused)
            }
        ) as MutableList<SemanticsNode>

        //sortByGeometryGroupings uses clipped bounds for sorting, which is not accurate for nodes beyond visible bounds
        // (as their clipped bounds are empty), so we need to fix the order of those nodes. Similar to IOS fix for this
        val isRTL = layoutNode.layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        sortedChildren.sortWith(BeyondBoundsComparator(isRTL))

        // Fix the specifics of nodes sorting where a parent node may go after a child in the sorted list.
        // Swapping them if the order is not specified by other criteria as TraversalIndex.
        // In case of other sort issues, consider copy and re-implementing the `sortByGeometryGroupings`
        // method to match TalkBack application traversal order.
        repeat(sortedChildren.count() - 1) { index ->
            val first = sortedChildren[index]
            val second = sortedChildren[index + 1]
            if (!first.config.contains(SemanticsProperties.TraversalIndex) &&
                !second.config.contains(SemanticsProperties.TraversalIndex) &&
                first.layoutNode.parent != second.layoutNode.parent &&
                first.layoutNode.findClosestParentNode({ it == second.layoutNode }) != null
            ) {
                sortedChildren[index] = second
                sortedChildren[index + 1] = first
            }
        }
        return sortedChildren
    }

    /**
     * Simplified version of [SemanticsNode.sortByGeometryGroupings] based on the
     * [SemanticsNode.unclippedBoundsInWindow] because [SemanticsNode.boundsInWindow] is empty for
     * nodes beyond visible bounds.
     */
    private class BeyondBoundsComparator(private val isRTL: Boolean) : Comparator<SemanticsNode> {

        val SemanticsNode.unclippedBoundsInWindow: Rect
            get() = Rect(positionInWindow, size.toSize())
        override fun compare(a: SemanticsNode, b: SemanticsNode): Int {
            var result = a.unmergedConfig
                .getOrElse(SemanticsProperties.TraversalIndex) { 0f }
                .compareTo(b.unmergedConfig.getOrElse(SemanticsProperties.TraversalIndex) { 0f })

            if (result != 0) {
                return result
            }
            val aCenter = a.unclippedBoundsInWindow.center
            val bCenter = b.unclippedBoundsInWindow.center

            result = aCenter.y
                .compareTo(bCenter.y)

            if (result != 0) {
                return result
            }

            result = aCenter.x
                .compareTo(bCenter.x)

            if (result != 0) {
                return if (isRTL) -result else result
            }

            return result
        }
    }

    fun dispose() {
        job?.cancel()
        accessibleByNodeId.forEach { _, node ->
            node.dispose()
        }
        accessibleByNodeId.clear()
        syncTriggerChannel.close()
        invalidationChannel.close()
    }
}


private fun WebSemanticsNode.setBounds(htmlRootElementOffset: Offset) {
    val density = this.semanticsNode.layoutNode.density

    // Fetch the attached coordinates directly for this node and its strict semantics parent,
    // explicitly bypassing "isImportantForBounds" ancestral skipping logic.
    //If we use boundsInParent, in some cases the bounds will not be really from the parent,
    // but from the parent of the parent.
    val layoutCoordinates = this.semanticsNode.findCoordinatorToGetBounds()
        ?.takeIf { it.isAttached }?.coordinates
    val parentLayoutCoordinates = this.semanticsNode.parent?.findCoordinatorToGetBounds()
        ?.takeIf { it.isAttached }?.coordinates

    // Calculate strict local boundaries between the two nodes
    val localBounds = if (layoutCoordinates != null && parentLayoutCoordinates != null) {
        parentLayoutCoordinates.localBoundingBoxOf(layoutCoordinates, clipBounds = false)
    } else if (layoutCoordinates != null) {
        // Fallback for the root node
        this.semanticsNode.boundsInRoot
    } else {
        // Unattached or invalid state
        Rect.Zero
    }

    val newPosition =
        localBounds.topLeft / density.density + if (this.semanticsNode.isRoot) htmlRootElementOffset else Offset.Zero

    val newSize = localBounds.size.div(density.density)

    if (newPosition != this.topLeft) {
        this.topLeft = newPosition
        setPosition(this.backingHtmlElement, newPosition.x, newPosition.y)
    }

    if (newSize != this.size) {
        this.size = newSize
        setSize(this.backingHtmlElement, newSize.width, newSize.height)
    }
}

private fun setPosition(
    element: HTMLElement, left: Float, top: Float
) {
    // language=javascript
    js("element.style.transform = 'matrix(1, 0, 0, 1, ' + left + ', ' + top + ')'")
}

private fun setSize(
    element: HTMLElement, width: Float, height: Float
) {
    // language=javascript
    js(
        """
       element.style.width = "" + width + "px";
       element.style.height = "" + height + "px";
    """
    )
}

internal object AriaRoleId {
    const val Unknown = -1

    // Mapped from [androidx.compose.ui.semantics.Role] values:
    const val Button = 0
    const val Checkbox = 1
    const val Switch = 2
    const val RadioButton = 3
    const val Tab = 4
    const val Image = 5
    const val DropdownList = 6
    const val ValuePicker = Unknown // TODO: Any web alternative?
    const val Carousel = Unknown // TODO: Any web alternative?

    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Other ARIA roles not specified explicitly by [androidx.compose.ui.semantics.Role]:
    const val Heading = 7
    const val TextBox = 8
    const val List = 9
    const val Grid = 10

    const val Dialog = 11
    const val ProgressBar = 12
    const val Slider = 13
}

internal fun SemanticsConfiguration.getRoleId(): Int {
    // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
    // Unfortunately, Role value is private, so we map it here:
    fun Role.toIntId(): Int = when (this) {
        Role.Button -> AriaRoleId.Button
        Role.Checkbox -> AriaRoleId.Checkbox
        Role.Switch -> AriaRoleId.Switch
        Role.RadioButton -> AriaRoleId.RadioButton
        Role.Tab -> AriaRoleId.Tab
        Role.Image -> AriaRoleId.Image
        Role.DropdownList -> AriaRoleId.DropdownList
        Role.ValuePicker -> AriaRoleId.Unknown // TODO: Any web alternative?
        Role.Carousel -> AriaRoleId.Unknown // TODO: Any web alternative?
        else -> AriaRoleId.Unknown
    }

    var roleId = -1

    if (SemanticsProperties.Role in this) {
        roleId = this[SemanticsProperties.Role].toIntId()
    }

    if (SemanticsActions.OnClick in this && roleId == AriaRoleId.Unknown) {
        // TODO: Not everything with OnClick is a button!!!
        roleId = Role.Button.toIntId()
    }

    if (SemanticsProperties.Heading in this) {
        roleId = AriaRoleId.Heading
    }

    if (SemanticsProperties.EditableText in this) {
        roleId = AriaRoleId.TextBox
    }

    if (SemanticsProperties.CollectionInfo in this) {
        val info = this[SemanticsProperties.CollectionInfo]
        roleId = if (info.columnCount > 1 && info.rowCount > 1) {
            AriaRoleId.Grid
        } else {
            AriaRoleId.List
        }
    }

    if (SemanticsProperties.ProgressBarRangeInfo in this) {
        val info = this[SemanticsProperties.ProgressBarRangeInfo]
        roleId =
            if (info.steps > 0 || SemanticsProperties.Disabled in this || SemanticsActions.SetProgress in this) {
                AriaRoleId.Slider
            } else {
                AriaRoleId.ProgressBar
            }
    }
    if (SemanticsProperties.IsDialog in this) {
        roleId = AriaRoleId.Dialog
    }

    return roleId
}

// To avoid passing a Kotlin string to JS, we pass an int instead and map it to String on the JS side.
// See https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles
internal fun setA11YAriaRole(element: HTMLElement, ariaRoleId: Int) {
    // language=javascript
    js(
        """
        var roleValue = "";
        switch (ariaRoleId) {
            case 0: // Role.Button
                roleValue = "button";
                break;
            case 1: // Role.Checkbox
                roleValue = "checkbox";
                break;
            case 2: // Role.Switch
                roleValue = "switch";
                break;
            case 3: // Role.RadioButton
                roleValue = "radio";
                break;
            case 4: // Role.Tab
                roleValue = "tab";
                break;
            case 5: // Role.Image
                roleValue = "img";
                break;
            case 6: // Role.DropdownList
                roleValue = "combobox";
                break;
            case 7: // heading https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/heading_role
                roleValue = "heading";
                break;
            case 8: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/textbox_role
                roleValue = "textbox";
                break;
            case 9: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/list_role
                roleValue = "list";
                break;
            case 10: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/grid_role
                roleValue = "grid";
                break;
            case 11: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/dialog_role
                roleValue = "dialog";
                break;
            case 12: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/progressbar_role/
                roleValue = "progressbar";
                break;
            case 13: // https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/slider_role
                roleValue = "slider";
                break;
            default:
                break;
        }
        if (roleValue.length > 0) { 
            element.setAttribute("role", roleValue);
        } else {
            element.removeAttribute("role");
        }
    """
    )
}

private fun removeAllChildrenOf(element: HTMLElement) {
    // language=javascript
    js("element.replaceChildren()")
}

private fun addChildToParentOrIgnore(
    currentWebSemanticsHtmlNode: HTMLElement,
    domParent: HTMLElement
) {
    // language=javascript
    js(
        """
        if (currentWebSemanticsHtmlNode.parentElement !== domParent) {
                domParent.appendChild(currentWebSemanticsHtmlNode)
            }
    """
    )
}

private fun updateHtmlNodeIfRequired(
    currentWebSemanticsHtmlNode: HTMLElement,
    childElement: HTMLElement,
    currentDomChildIndex: Int
) {
    // language=javascript
    js(
        """
        let existingNodeAtIndex = currentWebSemanticsHtmlNode.children.item(currentDomChildIndex);
        // If the element sitting at DOM[index] isn't our expected child, move it there.
        if (existingNodeAtIndex !== childElement) {
            currentWebSemanticsHtmlNode.insertBefore(childElement, existingNodeAtIndex)
        }
    """
    )
}

private fun createHtmlElementForSemanticNode(): HTMLElement = js(
//language=javascript
    """
        {
            let htmlNode = document.createElement("div")
            htmlNode.style.position = "fixed"
            htmlNode.style.top = "0px"
            htmlNode.style.left = "0px"
            htmlNode.style.whiteSpace = "pre"
            htmlNode.style.transformOrigin = "0px 0px 0px"
            return htmlNode;
        }
    """
)


private fun setContentEditable(element: HTMLElement, isEditable: Boolean): Unit =
    // language=javascript
    js("element.setAttribute('contenteditable', isEditable ? 'true' : 'false')")

private fun setOrientation(element: HTMLElement, isVertical: Boolean): Unit =
    // language=javascript
    js("element.setAttribute('aria-orientation', isVertical ? 'vertical' : 'horizontal')")

private fun removeOrientation(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-orientation')")

private fun setReadOnly(element: HTMLElement): Unit =
    // language=javascript
    js("element.setAttribute('aria-readonly', 'true')")

private fun removeReadOnly(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-readonly')")

private fun setDisabled(element: HTMLElement): Unit =
    // language=javascript
    js("element.setAttribute('aria-disabled', 'true')")

private fun removeDisabled(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-disabled')")

private fun setSelected(element: HTMLElement, isSelected: Boolean): Unit =
    // language=javascript
    js("element.setAttribute('aria-selected', isSelected ? 'true' : 'false')")

private fun removeSelected(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-selected')")

private fun setCheckedState(element: HTMLElement, type: Int): Unit =
    // language=javascript
    js(
        """
    {
        switch (type) {
            case 0: // ToggleableState.On
                element.setAttribute('aria-checked', 'true');
                break;
            case 1: // ToggleableState.Off
                element.setAttribute('aria-checked', 'false');
                break;
            case 2: // ToggleableState.Indeterminate
                element.setAttribute('aria-checked', 'mixed');
                break;
        }
     }   
    """
    )

private fun removeCheckedState(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-checked')")

private fun setProgressBarRangeInfo(
    element: HTMLElement,
    start: Float,
    endIncl: Float,
    current: Float
) {
    // language=javascript
    js(
        """
        element.setAttribute('aria-valuemin', start);
        element.setAttribute('aria-valuemax', endIncl);
        element.setAttribute('aria-valuenow', current);
    """
    )
}

private fun setValueTextStateDescription(element: HTMLElement, description: String): Unit =
    // language=javascript
    js("element.setAttribute('aria-valuetext', description)")

private fun removeRangeInfoAttributes(element: HTMLElement) {
    // language=javascript
    js(
        """
    element.removeAttribute('aria-valuemin');
    element.removeAttribute('aria-valuemax');
    element.removeAttribute('aria-valuenow');
    element.removeAttribute('aria-valuetext');
    """
    )
}

private fun removeCollectionItemInfoAttributes(element: HTMLElement) {
    // language=javascript
    js(
        """
    element.removeAttribute('aria-rowindex');
    element.removeAttribute('aria-colindex');
    element.removeAttribute('aria-rowspan');
    element.removeAttribute('aria-colspan');
    """
    )
}

private fun removeCollectionInfoAttributes(element: HTMLElement) {
    // language=javascript
    js(
        """
    element.removeAttribute('aria-rowcount');
    element.removeAttribute('aria-colcount');
    """
    )
}

private fun setLiveRegion(
    element: HTMLElement,
    liveRegionMode: Int
) {
    // language=javascript
    js(
        """
    switch (liveRegionMode) {
        case 0: // LiveRegionMode.None
            element.setAttribute('aria-live', 'off');
            break;
        case 1: // LiveRegionMode.Polite
            element.setAttribute('aria-live', 'polite');
            break;
        case 2: // LiveRegionMode.Assertive
            element.setAttribute('aria-live', 'assertive');
            break;
    }
    """
    )
}

private fun removeLiveRegionAttribute(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-live')")

private fun setMaxTextLength(element: HTMLElement, maxLength: Int): Unit =
    // language=javascript
    js("element.setAttribute('maxlength', maxLength)")

private fun removeMaxTextLength(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('maxlength')")

private fun setFocusable(element: HTMLElement): Unit =
    // language=javascript
    js("element.setAttribute('tabindex', '0')")

private fun removeFocusable(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('tabindex')")

private fun setErrorState(element: HTMLElement): Unit =
    // language=javascript
    js("element.setAttribute('aria-invalid', 'true')")

private fun removeErrorState(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-invalid')")

private fun setElementId(element: HTMLElement, id: Int): Unit =
    // language=javascript
    js("element.id = 'a11y_' + id")

private fun setCollectionInfo(element: HTMLElement, rowCount: Int, columnCount: Int) {
    // language=javascript
    js(
        """
        const isGrid = rowCount > 1 && columnCount > 1;
        if (isGrid || rowCount > 1) {
            element.setAttribute("aria-rowcount", rowCount);
        } else {
            element.removeAttribute("aria-rowcount");
        }
        if (isGrid || columnCount > 1) {
            element.setAttribute("aria-colcount", columnCount);
        } else {
            element.removeAttribute("aria-colcount");
        }
    """
    )
}

private fun setItemCollectionInfo(
    element: HTMLElement,
    rowIndex: Int,
    columnIndex: Int,
    rowSpan: Int,
    columnSpan: Int
) {
    // language=javascript
    js(
        """
        element.setAttribute("aria-rowindex", rowIndex);
        element.setAttribute("aria-colindex", columnIndex);
        if (rowSpan > 1) {
            element.setAttribute("aria-rowspan", rowSpan);
        } else {
            element.removeAttribute("aria-rowspan");
        }
        if (columnSpan > 1) {
            element.setAttribute("aria-colspan", columnSpan);
        } else {
            element.removeAttribute("aria-colspan");
        }
    """
    )
}

private fun setAriaLabel(element: HTMLElement, label: String): Unit =
    // language=javascript
    js("element.setAttribute('aria-label', label)")

private fun removeAriaLabel(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-label')")

private fun removeHidden(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-hidden')")


private fun setAriaModal(element: HTMLElement): Unit =
    // language=javascript
    js("element.setAttribute('aria-modal', 'true')")

private fun removeAriaModal(element: HTMLElement): Unit =
    // language=javascript
    js("element.removeAttribute('aria-modal')")