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

package noria.ui.core

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull

//region TestTree via DockApi Flow

class TestNode(val data: Map<TestDataKey<*>, *>)

class TestTree(val testDataCollection: Map<Long, List<TestNode>>) {
    fun <T : Any> testDataValues(
        key: TestDataKey<T>,
        windowId: Long,
        path: List<String> = emptyList(),
        allByPath: Boolean = false
    ): List<T>? =
        testDataCollection[windowId]
            ?.let {
                if (allByPath) it.filterAllTestNodesByPath(path) else it.filterTestNodesByPath(
                    path
                )
            }
            ?.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it.data[key] as T?
            }
}

fun List<TestNode>.filterTestNodesByPath(path: List<String>): List<TestNode> {
    var currentNodes = this
    for (id in path) {
        currentNodes = currentNodes
            .dropWhile { it.data[EnterTestDataSubtreeKey] != id }
            .takeWhile { it.data[ExitTestDataSubtreeKey] != id }
    }
    return currentNodes
}

fun List<TestNode>.filterAllTestNodesByPath(path: List<String>): List<TestNode> {
    return when {
        path.isNotEmpty() -> buildList {
            var currentDepth = -1
            var isTerminalPathPart = false

            this@filterAllTestNodesByPath.forEach { node ->
                val enterKey = node.data[EnterTestDataSubtreeKey] as? String
                val exitKey = node.data[ExitTestDataSubtreeKey] as? String
                when {
                    enterKey != null && currentDepth + 1 < path.size && enterKey == path[currentDepth + 1] -> {
                        currentDepth++
                        isTerminalPathPart = currentDepth == path.lastIndex
                    }

                    exitKey != null && exitKey == path.getOrNull(currentDepth) -> {
                        isTerminalPathPart = false
                        currentDepth--
                    }

                    isTerminalPathPart -> {
                        add(node)
                    }
                }
            }
        }

        else -> this
    }
}

//endregion


internal val EnterTestDataSubtreeKey = TestDataKey<String>("test-data/enter-subtree")
internal val ExitTestDataSubtreeKey = TestDataKey<String>("test-data/exit-subtree")

/** Diagnostic dump of the flattened stream, indented by subtree depth. */
fun UIRoot.dumpToBuffer(buffer: StringBuilder) {
    var depth = 0
    getAllTestNodes().forEach { node ->
        val enter = node.data[EnterTestDataSubtreeKey]
        when {
            enter != null -> {
                buffer.append("  ".repeat(depth.coerceAtLeast(0))).append("Container Node: $enter\n")
                depth += 1
            }
            node.data.containsKey(ExitTestDataSubtreeKey) -> depth -= 1
            else -> {
                val ids = node.data.keys.joinToString(", ") { it.id }
                buffer.append("  ".repeat(depth.coerceAtLeast(0))).append("Leaf Node: $ids\n")
            }
        }
    }
}

fun UIRoot.dumpToString(): String = StringBuilder().also { dumpToBuffer(it) }.toString()

/**
 * Noria's flat, depth-first pre-order stream, reproduced exactly.
 *
 * For each node: the `Enter` marker of every subtree entry and every payload, in outermost-first
 * modifier order; then the node's entire subtree; then the `Exit` markers in reverse. That is what
 * makes `.markTestSubtree(x).testData(k, v)` put the payload inside `x` and the reverse chain put
 * it outside — a distinction Air depends on.
 */
internal fun flattenTestNodes(node: SemanticsNode): Sequence<TestNode> = sequence {
    val entries = node.config.getOrNull(TestDataEntries).orEmpty()
    for (entry in entries) {
        when (entry) {
            is TestDataEntry.Subtree -> yield(TestNode(mapOf(EnterTestDataSubtreeKey to entry.id)))
            is TestDataEntry.Payload<*> -> yield(TestNode(mapOf(entry.key to entry.value)))
        }
    }
    for (child in node.children) {
        yieldAll(flattenTestNodes(child))
    }
    for (entry in entries.asReversed()) {
        if (entry is TestDataEntry.Subtree) {
            yield(TestNode(mapOf(ExitTestDataSubtreeKey to entry.id)))
        }
    }
}

fun <T : Any> UIRoot.findDataByKey(key: TestDataKey<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return getAllTestNodes().firstNotNullOfOrNull { it.data[key] } as T?
}

fun Sequence<TestNode>.filterByPath(path: List<String>): Sequence<TestNode> {
    var currentNodes = this
    for (id in path) {
        currentNodes = currentNodes
            .dropWhile { it.data[EnterTestDataSubtreeKey] != id }
            .takeWhile { it.data[ExitTestDataSubtreeKey] != id }
    }
    return currentNodes
}

fun <T : Any> UIRoot.findByPathOrNull(path: List<String> = emptyList(), key: TestDataKey<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return getAllTestNodes().filterByPath(path).firstNotNullOfOrNull { it.data[key] as T? }
}

fun <T : Any> UIRoot.findByPath(path: List<String> = emptyList(), key: TestDataKey<T>): T =
    findByPathOrNull(path, key)!!
