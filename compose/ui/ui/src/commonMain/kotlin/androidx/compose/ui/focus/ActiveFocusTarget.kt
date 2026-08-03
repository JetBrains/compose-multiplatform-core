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

package androidx.compose.ui.focus

import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireOwner

/**
 * The node that currently holds focus in the same owner as this node, or `null` if nothing is
 * focused (or if this node is not attached).
 *
 * Focus state is otherwise only observable per-node, through [FocusEventModifierNode]. Consumers
 * that need to walk the chain of modifier nodes leading to the focused leaf — rather than react to
 * their own focus changes — need the leaf itself.
 */
fun DelegatableNode.activeFocusTargetNode(): DelegatableNode? =
    if (node.isAttached) requireOwner().focusOwner.activeFocusTargetNode else null
