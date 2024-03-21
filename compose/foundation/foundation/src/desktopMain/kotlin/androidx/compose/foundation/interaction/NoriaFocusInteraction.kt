/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.interaction

import noria.NoriaContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.*

/**
 * Subscribes to this [MutableInteractionSource] and returns a [State] representing whether this
 * component is focused or not.
 *
 * [FocusInteraction] is typically set by [androidx.compose.foundation.focusable] and focusable
 * components, such as [androidx.compose.foundation.text.BasicTextField].
 *
 * @return [State] representing whether this component is being focused or not
 */
@Composable
fun InteractionSource.noriaCollectIsFocusedAsState(@NoriaOnly context: NoriaContext): State<Boolean> = with(context) {
    val isFocused = remember { mutableStateOf(false) }
    LaunchedEffect(this@noriaCollectIsFocusedAsState) {
        val focusInteractions = mutableListOf<FocusInteraction.Focus>()
        interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> focusInteractions.add(interaction)
                is FocusInteraction.Unfocus -> focusInteractions.remove(interaction.focus)
            }
            isFocused.value = focusInteractions.isNotEmpty()
        }
    }
    return isFocused
}
