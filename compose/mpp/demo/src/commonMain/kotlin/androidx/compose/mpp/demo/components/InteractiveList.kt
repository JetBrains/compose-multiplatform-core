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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.Action
import androidx.compose.ui.input.ActionScope
import androidx.compose.ui.input.DefaultCommands
import androidx.compose.ui.input.action
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@ExperimentalFoundationApi
@Composable
fun InteractiveList(
    state: InteractiveListState,
    nestedContent: @Composable (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable InteractiveListItemScope<Int>.() -> Unit,
) {
    val nestedContentFocusRequester = remember { FocusRequester() }
    Column(
        modifier
            .padding(4.dp)
            .semantics {
                interactiveList(state)
            }
            .focusGroup(),
    ) {
        nestedContent(nestedContentFocusRequester)

        repeat(state.itemCount) { index ->
            key(index) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .let {
                            if (index in state.selectedIndices) {
                                it.background(MaterialTheme.colors.onSurface.copy(alpha = TextFieldDefaults.BackgroundOpacity))
                            } else {
                                it
                            }
                        }
                        .border(
                            if (index == state.cursorIndex) {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colors.primary.copy(alpha = ContentAlpha.high)
                                )
                            } else {
                                BorderStroke(0.dp, Color.Unspecified)
                            },
                            MaterialTheme.shapes.small,
                        )
                        .pointerInput(nestedContentFocusRequester, state) {
                            detectTapGestures(onPress = { nestedContentFocusRequester.requestFocus() }) {
                                state.placeCursor(index)
                            }
                        }
                        .padding(8.dp),
                    propagateMinConstraints = true,
                ) {
                    Row {
                        val interactiveListItemScope =
                            object : InteractiveListItemScope<Int>, RowScope by this {
                                override val item: Int get() = index
                            }
                        interactiveListItemScope.itemContent()
                    }
                }
            }
        }
    }
}

interface InteractiveListItemScope<T> : RowScope {
    val item: T
}

@Composable
fun rememberInteractiveListState(itemCount: Int): InteractiveListState {
    return remember { InteractiveListState(itemCount) }.also { it.itemCount = itemCount }
}

val InteractiveListSemanticsPropertyKey = SemanticsPropertyKey<InteractiveList>("InteractiveListExtensionModel")

fun SemanticsPropertyReceiver.interactiveList(interactiveListState: InteractiveListState) {
    selectableGroup()
    collectionInfo = CollectionInfo(interactiveListState.itemCount, 1)
    action(DefaultCommands.MoveUp, "MoveListCursorUp", overridable = false) {
        interactiveListState.moveCursorUp()
        true
    }
    action(DefaultCommands.MoveDown, "MoveListCursorDown", overridable = false) {
        interactiveListState.moveCursorDown()
        true
    }
}
val ActionScope.interactiveList get() = get(InteractiveListSemanticsPropertyKey)!!

interface InteractiveList {
    val itemCount: Int
    val cursorIndex: Int?
    val selectedIndices: Set<Int>
    fun moveCursorUp(fullCircleEnabled: Boolean = false)
    fun moveCursorDown(fullCircleEnabled: Boolean = false)
    fun placeCursor(index: Int)
}

@Stable
class InteractiveListState(itemCount: Int) : InteractiveList {
    override var itemCount: Int by mutableStateOf(itemCount)
    //    private set

    override var cursorIndex by mutableStateOf<Int?>(null)
        private set
    override var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
        private set

    override fun moveCursorDown(fullCircleEnabled: Boolean) {
        cursorIndex = when {
            cursorIndex != null && fullCircleEnabled -> (cursorIndex!! + 1) % itemCount
            cursorIndex != null && !fullCircleEnabled -> (cursorIndex!! + 1).coerceAtMost(itemCount - 1)
            else -> 0
        }
    }

    override fun moveCursorUp(fullCircleEnabled: Boolean) {
        cursorIndex = when {
            cursorIndex != null && fullCircleEnabled -> (cursorIndex!! - 1) % itemCount
            cursorIndex != null && !fullCircleEnabled -> (cursorIndex!! - 1).coerceAtLeast(0)
            else -> itemCount - 1
        }
    }

    override fun placeCursor(index: Int) {
        cursorIndex = index
    }
}

val someExtensionAction = Action("DoSomethingFancyWithTheListCursor") {
    val itemIndex = doSomethingFancy()
    interactiveList.placeCursor(itemIndex)
    true
}

private fun doSomethingFancy() = 4
