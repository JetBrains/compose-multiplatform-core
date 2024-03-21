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
 * component is hovered or not.
 *
 * [HoverInteraction] is typically set by [androidx.compose.foundation.hoverable] and hoverable
 * components.
 *
 * @return [State] representing whether this component is being hovered or not
 */
@Composable
fun InteractionSource.noriaCollectIsHoveredAsState(@NoriaOnly context: NoriaContext): State<Boolean> =
    with(context) {
        val isHovered = remember { mutableStateOf(false) }
        LaunchedEffect(this@noriaCollectIsHoveredAsState) {
            val hoverInteractions = mutableListOf<HoverInteraction.Enter>()
            interactions.collect { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> hoverInteractions.add(interaction)
                    is HoverInteraction.Exit -> hoverInteractions.remove(interaction.enter)
                }
                isHovered.value = hoverInteractions.isNotEmpty()
            }
        }
        return isHovered
    }
