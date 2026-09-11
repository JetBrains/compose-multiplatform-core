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

import androidx.compose.ui.semantics.SemanticsNode

/**
 * Whether this non-zero-sized semantics node is fully clipped in the Compose root.
 *
 * Zero-sized semantics retain their existing exposure. A hidden ancestor is handled separately by
 * the accessibility traversal and still hides zero-sized descendants.
 */
internal fun SemanticsNode.isFullyClippedForA11Y(): Boolean =
    size.width != 0 && size.height != 0 && boundsInRoot.isEmpty
