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

package androidx.compose.ui.platform.accessibility

import androidx.collection.MutableScatterMap
import androidx.collection.mutableScatterMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

internal data class WebSemanticsNode(
    var backingHtmlElement: HTMLElement,
    var semanticsNode: SemanticsNode,
    val listeners: MutableScatterMap<String, (Event) -> Unit> = mutableScatterMapOf<String, (Event) -> Unit>()
) {
    var topLeft: Offset = Offset.Zero
    var size: Size = Size.Zero
    var appended: Boolean = false
    var pendingOldSemanticsConfiguration: SemanticsConfiguration? = null


    val configuration: SemanticsConfiguration
        get() = semanticsNode.config

    val id: Int
        get() = semanticsNode.id

    fun addOrReplaceEventListener(type: String, listener: (Event) -> Unit) {
        listeners[type]?.let { backingHtmlElement.removeEventListener(type, it) }
        backingHtmlElement.addEventListener(type, listener)
        listeners[type] = listener
    }

    fun removeEventListener(type: String) {
        listeners.remove(type)?.let { backingHtmlElement.removeEventListener(type, it) }
    }

    fun clearAllEventListeners() {
        listeners.forEach { type, listener ->
            backingHtmlElement.removeEventListener(
                type,
                listener
            )
        }
        listeners.clear()
    }

    fun dispose() {
        backingHtmlElement.remove()
        clearAllEventListeners()
    }
}