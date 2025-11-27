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

package androidx.pdf.ink.history

import androidx.pdf.annotation.models.AnnotationEditOperation
import androidx.pdf.annotation.models.EditOperation.Companion.invert
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AnnotationEditsHistoryImpl(internal val maxSize: Int) : AnnotationEditsHistory {
    private val undoStack: LinkedList<AnnotationEditOperation> = LinkedList()
    private val redoStack: LinkedList<AnnotationEditOperation> = LinkedList()
    private val lock = ReentrantLock()

    // Private mutable state flows
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    // Publicly exposed immutable state flows
    override val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    override val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        require(maxSize > 0) { "maxSize must be a positive integer." }
    }

    override fun addEntry(editOperation: AnnotationEditOperation) {
        lock.withLock {
            undoStack.push(editOperation)
            if (undoStack.size > maxSize) {
                // Remove the oldest entry from the bottom of the stack.
                // LinkedList.removeLast() performs this operation efficiently.
                undoStack.removeLast()
            }
            redoStack.clear()

            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    override fun undo(): AnnotationEditOperation? {
        return lock.withLock {
            if (undoStack.isEmpty()) return null
            val editOperation = undoStack.pop()
            redoStack.push(editOperation)

            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()

            // Invert the operation before returning
            editOperation.copy(op = editOperation.op.invert())
        }
    }

    override fun redo(): AnnotationEditOperation? {
        return lock.withLock {
            if (redoStack.isEmpty()) return null
            val editOperation = redoStack.pop()
            undoStack.push(editOperation)

            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()

            // No inversion is required
            editOperation
        }
    }

    override fun clear() {
        lock.withLock {
            undoStack.clear()
            redoStack.clear()

            _canUndo.value = false
            _canRedo.value = false
        }
    }
}
