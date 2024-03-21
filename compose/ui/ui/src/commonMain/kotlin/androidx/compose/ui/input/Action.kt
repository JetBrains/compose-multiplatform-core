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

package androidx.compose.ui.input

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

interface Action {
    val identifier: String
    val overridable: Boolean
    operator fun invoke(actionScope: ActionScope): Boolean
}

fun Action(identifier: String, overridable: Boolean = true, block: ActionScope.() -> Boolean): Action {
    return ActionImpl(identifier, overridable, block)
}

fun SemanticsPropertyReceiver.action(
    command: Command,
    identifier: String,
    overridable: Boolean = true,
    block: ActionScope.() -> Boolean
) {
    set(command.actionSemanticsPropertyKey, Action(identifier, overridable, block))
}

interface ActionScope {
    operator fun <T> get(semanticsPropertyKey: SemanticsPropertyKey<T>): T?
}

private data class ActionImpl(
    override val identifier: String,
    override val overridable: Boolean,
    val block: ActionScope.() -> Boolean
) : Action {
    override fun invoke(actionScope: ActionScope): Boolean {
        return actionScope.block()
    }
}