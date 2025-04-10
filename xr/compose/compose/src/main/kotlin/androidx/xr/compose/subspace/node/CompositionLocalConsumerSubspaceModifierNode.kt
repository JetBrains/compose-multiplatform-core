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

package androidx.xr.compose.subspace.node

import androidx.annotation.RestrictTo
import androidx.compose.runtime.CompositionLocal
import androidx.xr.compose.subspace.layout.SubspaceModifier

/** Interface for nodes that can consume composition local values. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface CompositionLocalConsumerSubspaceModifierNode

/**
 * Returns the current value of the given composition local.
 *
 * @param local The composition local to get the value of.
 * @return The current value of the given composition local.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun <T : Any?> CompositionLocalConsumerSubspaceModifierNode.currentValueOf(
    local: CompositionLocal<T>
): T {
    // TODO(b/405402746): Checking if the node is attached at this point sometimes results in a
    // runtime exception. We should investigate to see if the node lifecycle is behaving properly.
    check(this is SubspaceModifier.Node) {
        "Expected CompositionLocalConsumerSubspaceModifierNode to be a SubspaceModifier.Node"
    }
    val compositionLocalMap =
        checkNotNull(layoutNode?.compositionLocalMap) {
            "Expected layoutNode and compositionLocalMap to be set before requesting composition locals."
        }
    return compositionLocalMap[local]
}
