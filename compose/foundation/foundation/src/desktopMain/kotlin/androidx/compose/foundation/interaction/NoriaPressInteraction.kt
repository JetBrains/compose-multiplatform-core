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
 * component is pressed or not.
 *
 * [PressInteraction] is typically set by [androidx.compose.foundation.clickable] and clickable
 * higher level components, such as buttons.
 *
 * @return [State] representing whether this component is being pressed or not
 */
@Composable
fun InteractionSource.noriaCollectIsPressedAsState(@NoriaOnly context: NoriaContext): State<Boolean> = with(context) {
    val isPressed = remember { mutableStateOf(false) }
  LaunchedEffect(this@noriaCollectIsPressedAsState) {
        val pressInteractions = mutableListOf<PressInteraction.Press>()
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressInteractions.add(interaction)
                is PressInteraction.Release -> pressInteractions.remove(interaction.press)
                is PressInteraction.Cancel -> pressInteractions.remove(interaction.press)
            }
            isPressed.value = pressInteractions.isNotEmpty()
        }
    }
    return isPressed
}
