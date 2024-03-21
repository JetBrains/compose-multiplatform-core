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

interface KeyboardShortcutMatcher {
    fun processInput(keyCombination: KeyCombination): List<Command>
    fun reset()
}

internal class DefaultKeyboardShortcutMatcher(private val keymap: Keymap = platformDefaultKeymap) : KeyboardShortcutMatcher {
    private val currentKeyCombinationSequence = mutableListOf<KeyCombination>()

    override fun processInput(keyCombination: KeyCombination): List<Command> {
        currentKeyCombinationSequence.add(keyCombination)

        return findLongestMatchingKeyboardShortcut().also {
            if (it.isNotEmpty()) {
                currentKeyCombinationSequence.clear()
            }
        }
    }

    override fun reset() {
        currentKeyCombinationSequence.clear()
    }

    private fun findLongestMatchingKeyboardShortcut(): List<Command> {
        var subsequence: List<KeyCombination> = currentKeyCombinationSequence
        while (subsequence.isNotEmpty()) {
            val match = keymap[subsequence]
            if (match.isNotEmpty()) {
                return match
            }
            subsequence = subsequence.drop(1)
        }
        return emptyList()
    }
}
