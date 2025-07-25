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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.w3c.dom.HTMLElement

actual typealias InteropView = Any

internal actual class InteropViewGroup(val htmlElement: HTMLElement)

@Composable
internal fun <T : HTMLElement> InternalWebElementView(
    factory: () -> T,
    modifier: Modifier,
    update: (T) -> Unit,
    onRelease: (T) -> Unit,
    onReset: ((T) -> Unit)?,
) {
    val interopContainer = LocalInteropContainer.current

    InteropView(
        factory = { compositeKeyHash ->
            WebInteropViewHolder(
                factory,
                interopContainer,
                compositeKeyHash
            )
        },
        modifier,
        onReset,
        onRelease,
        update = {
            update(it)
        }
    )
}
