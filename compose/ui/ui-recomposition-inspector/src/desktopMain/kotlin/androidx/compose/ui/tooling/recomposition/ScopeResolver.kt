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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.IdentifiableRecomposeScope
import androidx.compose.runtime.tooling.parseSourceInformation
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.boundsInWindow

/** Name and source location of a scope, recovered from its slot-table group. */
internal class ResolvedScope(
    val name: String?,
    val location: String?,
    val hasSourceInformation: Boolean,
)

/**
 * Maps [RecomposeScope]s to what the slot table knows about them: source information (name, file,
 * line), the state objects remembered in the group, and the layout nodes it emits. Runs on the
 * composition thread while no composition is in progress.
 */
@OptIn(ComposeToolingApi::class)
internal object ScopeResolver {

    /** Finds the group of [scope] in one of [tables]; an anchor resolves in exactly one table. */
    fun group(tables: List<CompositionData>, scope: RecomposeScope): CompositionGroup? {
        val identity = (scope as? IdentifiableRecomposeScope)?.identity ?: return null
        for (data in tables) {
            val group = try {
                data.find(identity)
            } catch (_: Exception) {
                null
            }
            if (group != null) return group
        }
        return null
    }

    fun resolve(group: CompositionGroup?): ResolvedScope {
        val raw = group?.sourceInfo ?: return ResolvedScope(null, null, hasSourceInformation = false)
        val info = try {
            parseSourceInformation(raw)
        } catch (_: Exception) {
            null
        } ?: return ResolvedScope(null, null, hasSourceInformation = true)
        val line = info.locations.firstOrNull()?.lineNumber
        val file = info.sourceFile
        val location = when {
            file != null && line != null -> "$file:$line"
            file != null -> file
            else -> null
        }
        return ResolvedScope(
            name = info.functionName,
            location = location,
            hasSourceInformation = true,
        )
    }

    /** Whether [value] is stored in one of [group]'s own slots, i.e. was `remember`ed there. */
    fun remembers(group: CompositionGroup?, value: Any): Boolean {
        if (group == null) return false
        return try {
            group.data.any { it === value || (it is Pair<*, *> && it.second === value) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Union of the window bounds of the placed layout nodes [group] emits, looking at most
     * [maxNodes] nodes deep-first, or `null` if it emits none.
     */
    fun bounds(group: CompositionGroup?, maxNodes: Int = 8): Rect? {
        if (group == null) return null
        var union: Rect? = null
        var remaining = maxNodes
        fun visit(g: CompositionGroup, depth: Int) {
            if (remaining <= 0 || depth > MAX_DEPTH) return
            val node = g.node
            if (node is LayoutInfo) {
                remaining--
                val rect = nodeBounds(node) ?: return
                union = union?.let { u ->
                    Rect(
                        minOf(u.left, rect.left),
                        minOf(u.top, rect.top),
                        maxOf(u.right, rect.right),
                        maxOf(u.bottom, rect.bottom),
                    )
                } ?: rect
                // Children of a layout node are inside its bounds.
                return
            }
            for (child in g.compositionGroups) {
                if (remaining <= 0) return
                visit(child, depth + 1)
            }
        }
        try {
            visit(group, 0)
        } catch (_: Exception) {
            return union
        }
        return union
    }

    private fun nodeBounds(node: LayoutInfo): Rect? {
        if (!node.isAttached || !node.isPlaced) return null
        val rect = node.coordinates.boundsInWindow()
        return if (rect.width <= 0f && rect.height <= 0f) null else rect
    }

    private const val MAX_DEPTH = 64
}
