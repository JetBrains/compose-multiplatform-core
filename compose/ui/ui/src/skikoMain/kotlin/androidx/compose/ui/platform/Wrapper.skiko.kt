/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.compose.ui.platform

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.internal.bindFrameDomain
import androidx.compose.ui.node.RootNodeOwner

/**
 * Composes the given composable into [RootNodeOwner]
 *
 * @param parent The parent composition reference to coordinate scheduling of composition updates
 *        If null then default root composition will be used.
 * @param getCompositionLocalContext getter for retrieving the top-level composition local context.
 * Can be backed by `mutableStateOf` to dynamically change top-level locals.
 * @param frameDomain The frame domain the composition belongs to, or null when it is in none.
 * Bound before the content is composed, because the initial composition is part of what has to run
 * in the domain: reads it performs outside one record dependencies against the substrate only, and
 * nothing would ever invalidate them.
 * @param content A `@Composable` function declaring the UI contents
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeApi::class)
internal fun RootNodeOwner.setContent(
    parent: CompositionContext,
    getCompositionLocalContext: () -> CompositionLocalContext? = { null },
    frameDomain: SnapshotHolder? = null,
    content: @Composable () -> Unit
): Composition {
    val composition = Composition(DefaultUiApplier(owner.root), parent)
    if (frameDomain != null) composition.bindFrameDomain(frameDomain)
    composition.setContent {
        getCompositionLocalContext().provide {
            ProvideCommonCompositionLocals(
                owner = owner,
                uriHandler = remember { createPlatformUriHandler() },
                content = content
            )
        }
    }
    return composition
}

@Composable
private fun CompositionLocalContext?.provide(content: @Composable () -> Unit) {
    if (this != null) {
        CompositionLocalProvider(this, content = content)
    } else {
        content()
    }
}
