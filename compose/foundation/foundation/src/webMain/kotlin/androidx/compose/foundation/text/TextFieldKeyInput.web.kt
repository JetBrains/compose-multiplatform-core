/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import kotlin.js.js
import org.w3c.dom.events.KeyboardEvent

internal actual val KeyEvent.isTypedEvent: Boolean
    get() = type == KeyEventType.KeyDown && domEventOrNull?.isTypedEvent == true

private val KeyboardEvent.isTypedEvent: Boolean
    get() = isTypedEvent(this)

private fun isTypedEvent(evt: KeyboardEvent): Boolean =
    js("!evt.metaKey && !evt.ctrlKey && evt.key.charAt(0) === evt.key")

