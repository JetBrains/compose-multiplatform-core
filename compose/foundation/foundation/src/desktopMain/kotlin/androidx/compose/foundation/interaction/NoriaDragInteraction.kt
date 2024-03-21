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
 * component is dragged or not.
 *
 * [DragInteraction] is typically set by interactions
 * such as [androidx.compose.foundation.gestures.draggable] and
 * [androidx.compose.foundation.gestures.scrollable], and higher level components such as
 * [androidx.compose.foundation.lazy.LazyRow], available through
 * [androidx.compose.foundation.lazy.LazyListState.interactionSource].
 *
 * @return [State] representing whether this component is being dragged or not
 */
@Composable
fun InteractionSource.noriaCollectIsDraggedAsState(@NoriaOnly context: NoriaContext): State<Boolean> = with(context) {
    val isDragged = remember { mutableStateOf(false) }
    LaunchedEffect(this@noriaCollectIsDraggedAsState) {
        val dragInteractions = mutableListOf<DragInteraction.Start>()
        interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragInteractions.add(interaction)
                is DragInteraction.Stop -> dragInteractions.remove(interaction.start)
                is DragInteraction.Cancel -> dragInteractions.remove(interaction.start)
            }
            isDragged.value = dragInteractions.isNotEmpty()
        }
    }
    return isDragged
}
