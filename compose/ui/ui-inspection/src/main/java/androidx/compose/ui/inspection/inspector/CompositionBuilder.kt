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

@file:OptIn(UiToolingDataApi::class)

package androidx.compose.ui.inspection.inspector

import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.collection.MutableIntObjectMap
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.CompositionInstance
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.inspection.util.AnchorMap
import androidx.compose.ui.inspection.util.NO_ANCHOR_ID
import androidx.compose.ui.inspection.util.isPrimitiveClass
import androidx.compose.ui.layout.GraphicLayerInfo
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.view
import androidx.compose.ui.node.InteroperableComposeUiNode
import androidx.compose.ui.tooling.data.ContextCache
import androidx.compose.ui.tooling.data.ParameterInformation
import androidx.compose.ui.tooling.data.SourceContext
import androidx.compose.ui.tooling.data.SourceLocation
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.mapTree
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.toSize
import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * The [InspectorNode.id] will be populated with:
 * - the layerId from a LayoutNode if this exists
 * - an id generated from an Anchor instance from the SlotTree if this exists
 * - a generated id if none of the above ids are available
 *
 * The interval -10000..-2 is reserved for the generated ids.
 */
@VisibleForTesting const val RESERVED_FOR_GENERATED_IDS = -10000L

private val unwantedCalls =
    setOf(
        "CompositionLocalProvider",
        "Content",
        "Inspectable",
        "ProvideAndroidCompositionLocals",
        "ProvideCommonCompositionLocals",
    )

/** Builder of [InspectorNode] trees from [root] compositions. */
@OptIn(UiToolingDataApi::class)
internal class CompositionBuilder(
    private val info: SharedBuilderData,
) : SharedBuilderData by info {
    private var ownerView: View? = null
    private var subCompositionsFound = 0
    private val subCompositions = mutableMapOf<Any?, MutableList<SubCompositionResult>>()
    private var capturingSubCompositions =
        mutableMapOf<MutableInspectorNode, MutableList<SubCompositionResult>>()

    /**
     * Build a list of [InspectorNode] trees from a single [root] composition, and insert the
     * already built child compositions in the proper location in the tree.
     */
    fun convert(
        composition: CompositionInstance,
        root: CompositionData,
        childCompositions: List<SubCompositionResult>
    ): SubCompositionResult {
        reset(root, childCompositions)
        val node = root.mapTree(::convert, contextCache) ?: newNode()
        updateSubCompositionsAtEnd(node)
        val result = SubCompositionResult(composition, ownerView, node.children.toList())
        release(node)
        reset(null, emptyList())
        return result
    }

    private fun reset(root: CompositionData?, childCompositions: List<SubCompositionResult>) {
        ownerView = root?.let { findOwnerView(it) }
        subCompositionsFound = 0
        subCompositions.clear()
        childCompositions
            .filter { it.group != null && it.nodes.isNotEmpty() }
            .forEach { result ->
                subCompositions.getOrPut(result.group) { mutableListOf() }.add(result)
            }
        capturingSubCompositions.clear()
    }

    /**
     * We do NOT know which [View] a composition belongs to. Perform an initial tree walk to find
     * the composition owner.
     */
    private fun findOwnerView(root: CompositionData): View? {
        root.compositionGroups.forEach { group ->
            (group.node as? LayoutInfo)?.view?.let {
                return it
            }
            findOwnerView(group)?.let {
                return it
            }
        }
        return null
    }

    /** This function is called recursively from [CompositionData.mapTree] in Post-Order (LRN). */
    private fun convert(
        group: CompositionGroup,
        context: SourceContext,
        children: List<MutableInspectorNode>
    ): MutableInspectorNode {
        val parent = parse(group, context, children)
        addToParent(parent, children)
        addSubCompositions(group, parent)
        return parent
    }

    /**
     * Adds the nodes in [input] to the children of [parentNode]. Nodes without a reference to a
     * wanted Composable are skipped. A single skipped render id will be added to [parentNode].
     */
    private fun addToParent(
        parentNode: MutableInspectorNode,
        input: List<MutableInspectorNode>,
    ) {
        // If we're adding an unwanted node from the `input` to the parent node and it has a
        // View ID, then assign it to the parent view so that we don't lose the context that we
        // found a View as a descendant of the parent node. Most likely, there were one or more
        // unwanted intermediate nodes between the node that actually owns the Android View
        // and the desired node that the View should be associated with in the inspector. If
        // there's more than one input node with a View ID, we skip this step since it's
        // unclear how these views would be related.
        input
            .singleOrNull { it.hostingAndroidView }
            ?.takeIf { node -> node.isUnwanted }
            ?.let { nodeHostingAndroidView -> parentNode.viewId = nodeHostingAndroidView.viewId }

        var id: Long? = null
        input.forEach { node ->
            if (node.isUnwanted) {
                parentNode.children.addAll(node.children)
                if (node.hasLayerId) {
                    // If multiple siblings with a render ids are dropped:
                    // Ignore them all. And delegate the drawing to a parent in the inspector.
                    id = if (id == null) node.id else UNDEFINED_ID
                }
            } else {
                node.id = if (node.hasAssignedId) node.id else --generatedId
                val withSemantics = node.packageHash !in systemPackages
                val resultNode = node.build(withSemantics)
                parentNode.children.add(resultNode)
                if (withSemantics) {
                    node.mergedSemantics.clear()
                    node.unmergedSemantics.clear()
                }
            }
            if (node.bounds != null && parentNode.box == node.box) {
                parentNode.bounds = node.bounds
            }
            parentNode.mergedSemantics.addAll(node.mergedSemantics)
            parentNode.unmergedSemantics.addAll(node.unmergedSemantics)
            release(node)
        }
        val nodeId = id
        parentNode.id = if (!parentNode.hasLayerId && nodeId != null) nodeId else parentNode.id
    }

    private fun addSubCompositions(group: CompositionGroup, parent: MutableInspectorNode) {
        // Note: Minimize the number of calls to identity by only looking for a fixed number of
        // sub-composition parents. Calling identity may create an anchor for the group.
        if (subCompositions.size > subCompositionsFound) {
            subCompositions[group.identity]?.let { subCompositions ->
                subCompositionsFound++

                subCompositions.forEach { subComposition ->
                    if (subComposition.ownerView == ownerView || subComposition.ownerView == null) {
                        // Steal all the nodes from the sub-compositions and add them to the parent.
                        parent.children.addAll(subComposition.nodes)
                        subComposition.nodes = emptyList()
                    } else {
                        // If the sub-composition belongs to a different view prepare to copy the
                        // parent node to the sub-composition. See: checkCapturingSubCompositions.
                        capturingSubCompositions
                            .getOrPut(parent) { mutableListOf() }
                            .add(subComposition)
                    }
                }
            }
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun parse(
        group: CompositionGroup,
        context: SourceContext,
        children: List<MutableInspectorNode>
    ): MutableInspectorNode {
        val node = newNode()
        node.name = context.name ?: ""
        node.key = group.key as? Int ?: 0
        node.inlined = context.isInline
        node.box = context.bounds.emptyCheck()

        // If this node is associated with an android View, set the node's viewId to point to
        // the hosted view. We use the parent's uniqueDrawingId since the interopView returned here
        // will be the view itself, but we want to use the `AndroidViewHolder` that hosts the view
        // instead of the view directly.
        (group.node as? InteroperableComposeUiNode?)?.getInteropView()?.let { interopView ->
            (interopView.parent as? View)?.uniqueDrawingId?.let { viewId -> node.viewId = viewId }
        }

        checkCapturingSubCompositions(node, children)

        val layoutInfo = group.node as? LayoutInfo
        if (layoutInfo != null) {
            return parseLayoutInfo(layoutInfo, context, node)
        }
        // Keep an empty node if we are capturing nodes into sub-compositions.
        // Mark it unwanted after copying the node to the sub-compositions.
        if (
            (node.box == emptyBox && !capturingSubCompositions.contains(node)) ||
                unwantedName(node.name)
        ) {
            return node.markUnwanted()
        }
        parseCallLocation(context.location, node)
        if (isHiddenSystemNode(node)) {
            return node.markUnwanted()
        }
        node.anchorId = anchorMap[group.identity]
        node.id = syntheticId(node.anchorId)
        if (includeAllParameters) {
            addParameters(context, node)
        }
        return node
    }

    /**
     * Check if any of the previously found sub compositions have parent nodes in this parent
     * composition that should be copied to the sub-compositions.
     *
     * An example is a dialog:
     * <pre>
     *     <code>
     *         AlertDialog(
     *             onDismissRequest = {},
     *             confirmButton = { Button({}) { Text("Confirm Button") } }
     *         )
     *     </code>
     * </pre>
     *
     * The Button & Text will be in a sub-composition and the AlertDialog will be in a parent
     * composition with an empty size. We would like to show the Button inside the AlertDialog in
     * the sub-composition. Otherwise it will look like a random Button in the component tree.
     */
    private fun checkCapturingSubCompositions(
        node: MutableInspectorNode,
        children: List<MutableInspectorNode>
    ) {
        if (capturingSubCompositions.isEmpty()) {
            return
        }

        var nodeSubCompositions: MutableList<SubCompositionResult>? = null
        val subCompositionChildren = children.intersect(capturingSubCompositions.keys)
        subCompositionChildren.forEach { child ->
            val subCompositions = capturingSubCompositions.remove(child)!!
            if (!child.isUnwanted) {
                copyNodeToAllSubCompositions(child, subCompositions)
                if (child.box == emptyBox) {
                    child.markUnwanted()
                }
            }
            nodeSubCompositions = addAll(nodeSubCompositions, subCompositions)
        }
        if (node.box == emptyBox) {
            // Prepare to copy the current
            nodeSubCompositions?.let { capturingSubCompositions[node] = it }
        }
    }

    private fun addAll(
        first: MutableList<SubCompositionResult>?,
        second: MutableList<SubCompositionResult>?
    ): MutableList<SubCompositionResult>? =
        when {
            first == null -> second
            second == null -> first
            first === second -> first
            else -> {
                first.addAll(second)
                first
            }
        }

    private fun copyNodeToAllSubCompositions(
        node: MutableInspectorNode,
        subCompositions: List<SubCompositionResult>
    ) {
        subCompositions.forEach { subComposition ->
            val copy = newNode(node)
            copy.box = subComposition.ownerViewBox ?: emptyBox
            copy.children.addAll(subComposition.nodes)
            subComposition.nodes = listOf(copy.build())
        }
    }

    private fun updateSubCompositionsAtEnd(node: MutableInspectorNode?) {
        val subCompositions = node?.let { capturingSubCompositions.remove(node) } ?: return
        if (!node.isUnwanted) {
            copyNodeToAllSubCompositions(node, subCompositions)
            if (node.box == emptyBox) {
                node.markUnwanted()
            }
        }
    }

    private fun parseLayoutInfo(
        layoutInfo: LayoutInfo,
        context: SourceContext,
        node: MutableInspectorNode
    ): MutableInspectorNode {
        val box = context.bounds
        val size = box.size.toSize()
        val coordinates = layoutInfo.coordinates
        var bounds: QuadBounds? = null
        if (layoutInfo.isAttached && coordinates.isAttached) {
            val topLeft = toIntOffset(coordinates.localToWindow(Offset.Zero))
            val topRight = toIntOffset(coordinates.localToWindow(Offset(size.width, 0f)))
            val bottomRight =
                toIntOffset(coordinates.localToWindow(Offset(size.width, size.height)))
            val bottomLeft = toIntOffset(coordinates.localToWindow(Offset(0f, size.height)))

            if (
                topLeft.x != box.left ||
                    topLeft.y != box.top ||
                    topRight.x != box.right ||
                    topRight.y != box.top ||
                    bottomRight.x != box.right ||
                    bottomRight.y != box.bottom ||
                    bottomLeft.x != box.left ||
                    bottomLeft.y != box.bottom
            ) {
                bounds =
                    QuadBounds(
                        topLeft.x,
                        topLeft.y,
                        topRight.x,
                        topRight.y,
                        bottomRight.x,
                        bottomRight.y,
                        bottomLeft.x,
                        bottomLeft.y,
                    )
            }
        }

        node.box = box.emptyCheck()
        node.bounds = bounds
        node.layoutNodes.add(layoutInfo)
        val modifierInfo = layoutInfo.getModifierInfo()

        val unmergedSemantics = unmergedSemanticsMap[layoutInfo.semanticsId]
        if (unmergedSemantics != null) {
            node.unmergedSemantics.addAll(unmergedSemantics)
        }

        val mergedSemantics = semanticsMap[layoutInfo.semanticsId]
        if (mergedSemantics != null) {
            node.mergedSemantics.addAll(mergedSemantics)
        }

        val layerInfo =
            modifierInfo.map { it.extra }.filterIsInstance<GraphicLayerInfo>().firstOrNull()
        node.id = layerInfo?.layerId ?: UNDEFINED_ID
        return node
    }

    private fun parseCallLocation(location: SourceLocation?, node: MutableInspectorNode) {
        val fileName = location?.sourceFile ?: return
        node.fileName = fileName
        node.packageHash = location.packageHash
        node.lineNumber = location.lineNumber
        node.offset = location.offset
    }

    private fun addParameters(context: SourceContext, node: MutableInspectorNode) {
        context.parameters.forEach {
            val castedValue = castValue(it)
            node.parameters.add(RawParameter(it.name, castedValue))
        }
    }

    private fun castValue(parameter: ParameterInformation): Any? {
        val value = parameter.value ?: return null
        if (parameter.inlineClass == null || !value.javaClass.isPrimitiveClass()) return value
        return inlineClassConverter.castParameterValue(parameter.inlineClass, value)
    }

    private fun isHiddenSystemNode(node: MutableInspectorNode): Boolean =
        hideSystemNodes && node.packageHash in systemPackages

    private fun unwantedName(name: String): Boolean =
        name.isEmpty() ||
            name.startsWith("remember") ||
            name.startsWith('<') && name.endsWith('>') ||
            name in unwantedCalls

    /**
     * Generate a synthetic id for the node. If we have an anchor id, use an encoded anchor id such
     * that the id will stay the same after an update.
     */
    private fun syntheticId(anchorId: Int): Long {
        if (anchorId == NO_ANCHOR_ID) {
            return UNDEFINED_ID
        }
        // The anchorId is an Int
        return anchorId.toLong() - Int.MAX_VALUE.toLong() + RESERVED_FOR_GENERATED_IDS
    }

    private fun IntRect.emptyCheck(): IntRect =
        if (left >= right && top >= bottom) emptyBox else this

    private fun toIntOffset(offset: Offset): IntOffset =
        IntOffset(offset.x.roundToInt(), offset.y.roundToInt())

    private fun newNode(): MutableInspectorNode =
        if (cache.isNotEmpty()) cache.pop() else MutableInspectorNode()

    private fun newNode(copyFrom: MutableInspectorNode): MutableInspectorNode =
        newNode().shallowCopy(copyFrom)

    private fun release(node: MutableInspectorNode) {
        node.reset()
        cache.add(node)
    }
}

@OptIn(UiToolingDataApi::class)
internal interface SharedBuilderData {
    val cache: ArrayDeque<MutableInspectorNode>
    val contextCache: ContextCache
    val anchorMap: AnchorMap

    /** Map from semantics id to a list of merged semantics information */
    val semanticsMap: MutableIntObjectMap<List<RawParameter>>

    /** Map of semantics id to a list of unmerged semantics information */
    val unmergedSemanticsMap: MutableIntObjectMap<List<RawParameter>>

    val inlineClassConverter: InlineClassConverter
    val parameterFactory: ParameterFactory
    val hideSystemNodes: Boolean
    val includeAllParameters: Boolean
    var generatedId: Long
}

/** Sub-Compositions of the composition being parsed. */
internal class SubCompositionResult(
    /** The sub-composition instance */
    composition: CompositionInstance,

    /** The ownerView of this composition */
    val ownerView: View?,

    /** The parsed sub-composition, that may be replaced later */
    var nodes: List<InspectorNode>
) {
    /**
     * The identity of the parent [CompositionGroup] where this composition belongs in a parent
     * composition.
     */
    val group = composition.findContextGroup()?.identity

    /** The size of the owner view */
    val ownerViewBox = ownerView?.let { IntRect(0, 0, it.width, it.height) }
}
