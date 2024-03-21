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

package androidx.compose.ui.input.key

import androidx.compose.ui.input.Command
import androidx.compose.ui.input.DefaultCommands

actual val platformDefaultKeymap: Keymap = object : Keymap {
    private val mappings = hashMapOf(
        Key.DirectionUp triggers DefaultCommands.MoveUp,
//        Key.P with KeyboardModifiers(isCtrlPressed = true) triggers DefaultCommands.MoveUp,
        Key.DirectionDown triggers DefaultCommands.MoveDown,
//        Key.N with KeyboardModifiers(isCtrlPressed = true) triggers DefaultCommands.MoveDown,
        Key.Enter triggers DefaultCommands.Activate,
        Key.Enter with KeyboardModifiers(isMetaPressed = true) triggers DefaultCommands.Confirm,
    )

    override fun get(shortcut: KeyboardShortcut): List<Command> {
        return mappings[shortcut] ?: emptyList()
    }
}

private infix fun Key.triggers(
    command: Command
): Pair<KeyboardShortcut, List<Command>> {
    return KeyCombination(this) triggers command
}

private infix fun Key.triggers(
    commands: List<Command>
): Pair<KeyboardShortcut, List<Command>> {
    return KeyCombination(this) triggers commands
}

private infix fun KeyCombination.triggers(
    command: Command
): Pair<KeyboardShortcut, List<Command>> {
    return listOf(this) triggers listOf(command)
}

private infix fun KeyCombination.triggers(
    commands: List<Command>
): Pair<KeyboardShortcut, List<Command>> {
    return listOf(this) triggers commands
}

private infix fun KeyboardShortcut.triggers(
    commands: List<Command>
): Pair<KeyboardShortcut, List<Command>> {
    return this to commands
}

private infix fun Key.with(keyboardModifiers: KeyboardModifiers): KeyCombination {
    return KeyCombination(this, keyboardModifiers)
}
