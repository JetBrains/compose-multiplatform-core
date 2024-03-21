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

package noria.foundation

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import noria.NoriaContext
import noria.ClosureContext
import noria.ui.withModifier

@JvmInline
value class ScrollTarget internal constructor(internal val bringIntoViewRequester: BringIntoViewRequester)

@Composable
fun NoriaContext.ScrollTarget(
    modifier: Modifier = Modifier.Companion,
    content: @Composable NoriaContext.(ScrollTarget) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    withModifier(modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        content(ScrollTarget(bringIntoViewRequester))
    }
}

fun ClosureContext.latestScrollAuthor(scrollTarget: ScrollTarget): Any {
    // TODO
    return Any()
}
