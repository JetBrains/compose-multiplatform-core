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

@file:OptIn(ExperimentalComposeUiApi::class)

package androidx.compose.ui.draganddrop

import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Returns the transfer action currently selected for this drag-and-drop [DragAndDropEvent], or
 * `null` if none is selected.
 *
 * Function form of the [DragAndDropEvent.action] property, provided so callers can use a uniform
 * `event.action()` accessor alongside [containsFormat]/[acceptsFormat]/[clipboardEntry].
 */
fun DragAndDropEvent.action(): DragAndDropTransferAction? = this.action
