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

package androidx.wear.compose.material3.demos

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.GestureInclusion
import androidx.wear.compose.foundation.RevealActionType
import androidx.wear.compose.foundation.RevealValue
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SplitSwitchButton
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwipeToRevealBothDirectionsNonAnchoring() {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState =
                    rememberRevealState(
                        anchors =
                            SwipeToRevealDefaults.bidirectionalAnchors(
                                useAnchoredActions = false,
                            )
                    ),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        text = { Text("Undo Delete") },
                    )
                }
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary action accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Button has only one action", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun SwipeToRevealBothDirections() {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState =
                    rememberRevealState(
                        anchors =
                            SwipeToRevealDefaults.bidirectionalAnchors(
                                // Use the double action anchor width when revealing two actions
                                anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                            )
                    ),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    secondaryAction(
                        onClick = { /* This block is called when the secondary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        text = { Text("Undo Delete") },
                    )
                    undoSecondaryAction(
                        onClick = { /* This block is called when the undo secondary action is executed. */
                        },
                        text = { Text("Undo Secondary") },
                    )
                }
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary and secondary actions
                            // accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                    CustomAccessibilityAction("More") {
                                        /* Add the secondary click handler here */
                                        true
                                    }
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Button has two actions", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun SwipeToRevealTwoActionsWithUndo() {
    val context = LocalContext.current
    val showToasts = remember { mutableStateOf(true) }

    ScalingLazyDemo {
        item { ListHeader { Text("Two Undo Actions") } }
        item {
            SwipeToReveal(
                revealState =
                    rememberRevealState(
                        anchors =
                            SwipeToRevealDefaults.anchors(
                                // Use the double action anchor width when revealing two actions
                                anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth
                            )
                    ),
                actionButtonHeight = SwipeToRevealDefaults.LargeActionButtonHeight,
                actions = {
                    primaryAction(
                        onClick = {
                            if (showToasts.value) {
                                Toast.makeText(
                                        context,
                                        "Primary action executed.",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    secondaryAction(
                        onClick = {
                            if (showToasts.value) {
                                Toast.makeText(
                                        context,
                                        "Secondary action executed.",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        },
                        icon = { Icon(Icons.Filled.Lock, contentDescription = "Lock") }
                    )
                    undoPrimaryAction(
                        onClick = {
                            if (showToasts.value) {
                                Toast.makeText(
                                        context,
                                        "Undo primary action executed.",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        },
                        text = { Text("Undo Delete") },
                    )
                    undoSecondaryAction(
                        onClick = {
                            if (showToasts.value) {
                                Toast.makeText(
                                        context,
                                        "Undo secondary action executed.",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        },
                        text = { Text("Undo Lock") },
                    )
                },
            ) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary and secondary actions
                            // accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                    CustomAccessibilityAction("Lock") {
                                        /* Add the secondary click handler here */
                                        true
                                    }
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Card has two actions", modifier = Modifier.fillMaxSize())
                }
            }
        }
        item {
            SplitSwitchButton(
                showToasts.value,
                onCheckedChange = { showToasts.value = it },
                onContainerClick = { showToasts.value = !showToasts.value },
                toggleContentDescription = "Show toasts"
            ) {
                Text("Show toasts")
            }
        }
    }
}

@Composable
fun SwipeToRevealInScalingLazyColumn() {
    val namesList = remember { mutableStateListOf("Alice", "Bob", "Charlie", "Dave", "Eve") }
    val coroutineScope = rememberCoroutineScope()
    ScalingLazyDemo(contentPadding = PaddingValues(0.dp)) {
        items(namesList.size, key = { namesList[it] }) {
            val revealState =
                rememberRevealState(
                    anchors =
                        SwipeToRevealDefaults.bidirectionalAnchors(
                            anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth
                        )
                )
            val name = remember { namesList[it] }
            SwipeToReveal(
                revealState = revealState,
                actions = {
                    primaryAction(
                        onClick = {
                            coroutineScope.launch {
                                delay(2000)
                                // After a delay, remove the item from the list if the last action
                                // performed by the user is still the primary action, so the user
                                // didn't press "Undo".
                                if (revealState.lastActionType == RevealActionType.PrimaryAction) {
                                    namesList.remove(name)
                                }
                            }
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    secondaryAction(
                        onClick = {
                            // Add a duplicate item to the list, if it doesn't exist already
                            val nextName = "$name+"
                            if (!namesList.contains(nextName)) {
                                namesList.add(namesList.indexOf(name) + 1, nextName)
                                coroutineScope.launch { revealState.animateTo(RevealValue.Covered) }
                            }
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Duplicate") }
                    )
                    undoPrimaryAction(onClick = {}, text = { Text("Undo Delete") })
                }
            ) {
                Button(
                    {},
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics {
                        // Use custom actions to make the primary and secondary actions accessible
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Delete") {
                                    /* Add the primary action click handler here */
                                    true
                                },
                                CustomAccessibilityAction("Duplicate") {
                                    /* Add the secondary click handler here */
                                    true
                                }
                            )
                    }
                ) {
                    Text(name)
                }
            }
        }
    }
}

@Composable
fun SwipeToRevealSingleButtonWithAnchoring() {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState = rememberRevealState(anchors = SwipeToRevealDefaults.anchors()),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        text = { Text("Undo Delete") },
                    )
                }
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary action accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Button has only one action", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun SwipeToRevealWithLongLabels() {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState =
                    rememberRevealState(
                        anchors =
                            SwipeToRevealDefaults.anchors(
                                // Use the double action anchor width when revealing two actions
                                anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                            )
                    ),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = {
                            Text("Delete action with an extremely long label that should truncate.")
                        }
                    )
                    secondaryAction(
                        onClick = { /* This block is called when the secondary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Lock, contentDescription = "Lock") }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        text = {
                            Text(
                                "Undo Delete action with an extremely long label that should truncate."
                            )
                        },
                    )
                    undoSecondaryAction(
                        onClick = { /* This block is called when the undo secondary action is executed. */
                        },
                        text = {
                            Text(
                                "Undo Lock action with an extremely long label that should truncate."
                            )
                        },
                    )
                }
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary and secondary actions
                            // accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                    CustomAccessibilityAction("Lock") {
                                        /* Add the secondary click handler here */
                                        true
                                    }
                                )
                        },
                    onClick = {}
                ) {
                    Text(
                        "This Button has actions with extremely long labels that should truncate.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeToRevealWithCustomIcons() {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState =
                    rememberRevealState(
                        anchors =
                            SwipeToRevealDefaults.anchors(
                                // Use the double action anchor width when revealing two actions
                                anchorWidth = SwipeToRevealDefaults.DoubleActionAnchorWidth,
                            )
                    ),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = {
                            // Although this practice is not recommended, this demo deliberately
                            // passes Text in the icon slot so that this edge case can be
                            // visualised.
                            Text(
                                "🗑",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = { Text("Delete") }
                    )
                    secondaryAction(
                        onClick = { /* This block is called when the secondary action is executed. */
                        },
                        icon = {
                            // Although this practice is not recommended, this demo deliberately
                            // passes Text in the icon slot so that this edge case can be
                            // visualised.
                            Text(
                                "U",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        icon = {
                            // Although this practice is not recommended, this demo deliberately
                            // passes Text in the icon slot so that this edge case can be
                            // visualised.
                            Text(
                                "<",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = { Text("Undo Delete") },
                    )
                    undoSecondaryAction(
                        onClick = { /* This block is called when the undo secondary action is executed. */
                        },
                        icon = {
                            // Although this practice is not recommended, this demo deliberately
                            // passes Text in the icon slot so that this edge case can be
                            // visualised.
                            Text(
                                text = "🔙",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        text = { Text("Undo Update") },
                    )
                }
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary and secondary actions
                            // accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                    CustomAccessibilityAction("Update") {
                                        /* Add the secondary click handler here */
                                        true
                                    }
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Button has two actions.", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Usage of [edgeSwipeToDismiss] modifier with [SwipeToReveal] is discouraged. Instead, the
 * [GestureInclusion] parameter should be used.
 *
 * This demo is to check compatibility with code that is still using [edgeSwipeToDismiss] after
 * [GestureInclusion] was introduced.
 */
@Composable
fun SwipeToRevealWithEdgeSwipeToDismiss(swipeToDismissBoxState: SwipeToDismissBoxState) {
    ScalingLazyDemo {
        item {
            SwipeToReveal(
                revealState = rememberRevealState(anchors = SwipeToRevealDefaults.anchors()),
                actions = {
                    primaryAction(
                        onClick = { /* This block is called when the primary action is executed. */
                        },
                        icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete") },
                        text = { Text("Delete") }
                    )
                    undoPrimaryAction(
                        onClick = { /* This block is called when the undo primary action is executed. */
                        },
                        text = { Text("Undo Delete") },
                    )
                },
                modifier = Modifier.edgeSwipeToDismiss(swipeToDismissBoxState),
            ) {
                Button(
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            // Use custom actions to make the primary action accessible
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Delete") {
                                        /* Add the primary action click handler here */
                                        true
                                    },
                                )
                        },
                    onClick = {}
                ) {
                    Text("This Button has only one action", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
