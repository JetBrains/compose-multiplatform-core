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

import androidx.compose.runtime.Composable
import noria.NoriaContext
import noria.ui.focus.internal.FocusNode

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


fun FocusNode.dumpToBuffer(buffer: StringBuilder) {
    // TODO
}

@Deprecated("Use Modifier.testData instead")
@Composable
fun <T : Any> NoriaContext.testData(key: TestDataKey<T>, value: T) {
    // TODO
}

private val EnterTestDataSubtreeKey = TestDataKey<String>("test-data/enter-subtree")
private val ExitTestDataSubtreeKey = TestDataKey<String>("test-data/exit-subtree")

@Composable
fun NoriaContext.markTestSubtree(id: String, builder: @Composable NoriaContext.() -> Unit) {
    testData(EnterTestDataSubtreeKey, id)
    builder()
    testData(ExitTestDataSubtreeKey, id)
}
