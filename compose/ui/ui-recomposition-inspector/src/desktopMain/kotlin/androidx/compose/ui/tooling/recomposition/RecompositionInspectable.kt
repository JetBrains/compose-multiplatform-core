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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import java.util.Collections

/** Slot tables of every composition under a [RecompositionInspectable]. */
internal object RecompositionInspectionTables {
    val tables: MutableSet<CompositionData> = Collections.synchronizedSet(LinkedHashSet())

    /** Copy safe to iterate while compositions register. */
    fun snapshot(): List<CompositionData> = synchronized(tables) { tables.toList() }
}

/**
 * Wrap each window's content root with it so the inspector can name scopes. It registers the
 * composition's slot table, provides [LocalInspectionTables] for subcompositions and calls
 * `collectParameterInformation()`, which keeps source information and gives every restartable
 * function a recompose scope. That costs slot-table memory, so use it in development builds only.
 */
@Composable
fun RecompositionInspectable(content: @Composable () -> Unit) {
    val tables = RecompositionInspectionTables.tables
    val compositionData = currentComposer.compositionData
    currentComposer.collectParameterInformation()
    // Subcompositions are removed by the runtime on dispose; the root table is removed here.
    tables.add(compositionData)
    DisposableEffect(compositionData) {
        onDispose { tables.remove(compositionData) }
    }
    CompositionLocalProvider(LocalInspectionTables provides tables) {
        content()
    }
}
