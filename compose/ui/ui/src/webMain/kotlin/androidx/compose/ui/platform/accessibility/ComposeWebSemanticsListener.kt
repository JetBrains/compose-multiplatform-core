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

package androidx.compose.ui.platform.accessibility

import androidx.collection.mutableObjectListOf
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.semantics.SemanticsOwner
import kotlinx.coroutines.CoroutineScope
import org.w3c.dom.HTMLElement

internal class ComposeWebSemanticsListener(
    val webSemanticsRoot: HTMLElement,
) : PlatformContext.SemanticsOwnerListener {

    private val semanticOwners = mutableObjectListOf<ComposeWebSemanticsOwner>()
    override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
        val owner = ComposeWebSemanticsOwner(semanticsOwner, webSemanticsRoot)
        semanticOwners.add(owner)
        owner.initialize(coroutineScope)
    }

    override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
        semanticOwners.removeIf {
            (it.semanticsOwner == semanticsOwner).also { isNode ->
                if (isNode) it.dispose()
            }
        }
    }

    override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        semanticOwners.forEach { webSemanticsOwner ->
            if(webSemanticsOwner.semanticsOwner.rootSemanticsNode.id == semanticsOwner.rootSemanticsNode.id) {
                webSemanticsOwner.sendInvalidation()
            }
        }
    }

    override fun onLayoutChange(
        semanticsOwner: SemanticsOwner, semanticsNodeId: Int
    ) {
        semanticOwners.forEach { webSemanticsOwner ->
            if(webSemanticsOwner.semanticsOwner.rootSemanticsNode.id == semanticsOwner.rootSemanticsNode.id) {
                webSemanticsOwner.sendInvalidation()
            }
        }
    }
}