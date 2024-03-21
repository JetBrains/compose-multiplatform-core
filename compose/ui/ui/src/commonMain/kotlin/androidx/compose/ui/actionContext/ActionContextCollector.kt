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

package androidx.compose.ui.actionContext

import androidx.compose.ui.Modifier
import androidx.compose.ui.actionContext.ActionContext.Data
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutNode
import androidx.compose.ui.semantics.SemanticsNode

private const val ActionContextCollectorNotInitialized = """
   ActionContextCollector is not initialized. Here are some possible fixes:

   1. Remember the ActionContextCollector: val actionContextCollector = remember { ActionContextCollector() }
   2. Did you forget to add a Modifier.actionContextCollector() ?
"""

fun Modifier.actionContextCollector(actionContextCollector: ActionContextCollector): Modifier =
    this.then(CollectActionContextElement(actionContextCollector))

private class CollectActionContextElement(
    private val actionContextCollector: ActionContextCollector
) : ModifierNodeElement<CollectActionContextModifierNode>() {
    override fun create(): CollectActionContextModifierNode {
        return CollectActionContextModifierNode(actionContextCollector)
    }

    override fun update(node: CollectActionContextModifierNode) {
        node.update(actionContextCollector)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CollectActionContextElement) return false
        return actionContextCollector == other.actionContextCollector
    }

    override fun hashCode(): Int {
        return actionContextCollector.hashCode()
    }
}

private class CollectActionContextModifierNode(
    private var actionContextCollector: ActionContextCollector
) : Modifier.Node() {
    fun update(newCollector: ActionContextCollector) {
        val layoutNodeProvider = actionContextCollector.layoutNodeProvider
        actionContextCollector.layoutNodeProvider = null
        newCollector.layoutNodeProvider = layoutNodeProvider
        actionContextCollector = newCollector
    }

    override fun onAttach() {
        actionContextCollector.layoutNodeProvider = {
            requireLayoutNode()
        }
    }

    override fun onDetach() {
        actionContextCollector.layoutNodeProvider = null
    }
}

/**
 * The [ActionContextCollector] is used together with [actionContextCollector]
 * to collect [ActionContext] of the component where the modifier is attached.
 */
class ActionContextCollector {
    internal var layoutNodeProvider: (() -> LayoutNode)? = null

    /**
     * Collects [ActionContext] from the component where [actionContextCollector] modifier is attached.
     *
     * @param includeFocusedDescendants if true, then focusData of focused descendants will be collected as well.
     * Otherwise, only focusData down to the component will be collected.
     */
    fun collectActionContext(includeFocusedDescendants: Boolean = true): ActionContext {
        // TODO: write proper algorithm of merging ActionContext + deal with unmergedTrees
        checkNotNull(layoutNodeProvider) {
            ActionContextCollectorNotInitialized
        }
        val currentLayoutNodeProvider = layoutNodeProvider!!
        val layoutNode = currentLayoutNodeProvider()
        val modifierSemanticsNode = SemanticsNode(layoutNode, mergingEnabled = true)
        val semanticsNodeStack = mutableListOf<SemanticsNode>()
        for (semanticsNode in generateSequence(modifierSemanticsNode) { it.parent }) {
            semanticsNodeStack.add(semanticsNode)
        }
        val props = mutableMapOf<FocusDataKey<*, *>, List<Data<Any?>>>()
        for (semanticsNode in semanticsNodeStack.asReversed()) {
            for ((_, semanticsValue) in semanticsNode.config) {
                if (semanticsValue !is FocusDataSemanticsValue<*, *>) {
                    continue
                }
                // TODO: pass this data properly
                props[semanticsValue.key] = listOf(Data(semanticsValue.value, 0, 0))
            }
        }
        return ActionContext(props)
    }
}