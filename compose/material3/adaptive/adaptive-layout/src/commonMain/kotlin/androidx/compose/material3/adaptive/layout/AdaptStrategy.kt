/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.material3.adaptive.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Stable

/**
 * Provides the information about how the associated pane should be adapted if not all panes can be
 * displayed in the [PaneAdaptedValue.Expanded] state.
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
sealed interface AdaptStrategy {
    /** Override this function to provide the resulted adapted state. */
    @Deprecated(
        "This function is deprecated in favor of directly using the info carried by the " +
            "strategy instances to make adaptation decisions."
    )
    fun adapt(): PaneAdaptedValue = PaneAdaptedValue.Hidden

    private class Simple(private val description: String) : AdaptStrategy {
        override fun toString() = "AdaptStrategy[$description]"
    }

    /**
     * Indicate the associated pane should be reflowed when certain conditions are met. With the
     * default calculation functions [calculateThreePaneScaffoldValue] we provide, when it's a
     * single pane layout, a pane with the reflow strategy will be adapted to either:
     * 1. [PaneAdaptedValue.Reflowed], when either the reflowed pane or the target pane it's
     *    supposed to be reflowed to is the current destination; or
     * 2. [PaneAdaptedValue.Hidden] otherwise.
     *
     * Note that if the current layout can have more than one horizontal partitions, the pane will
     * never be reflowed.
     *
     * @param targetPane the target pane of the reflowing, i.e., the pane that the reflowed pane
     *   will be put under.
     */
    // TODO(conradchen): Add usage samples.
    class Reflow(val targetPane: Any) : AdaptStrategy {
        override fun toString() = "AdaptStrategy[Reflow to $targetPane]"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Reflow) return false
            return targetPane == other.targetPane
        }

        override fun hashCode(): Int {
            return targetPane.hashCode()
        }
    }

    companion object {
        /**
         * The default [AdaptStrategy] that suggests the layout to hide the associated pane when it
         * has to be adapted, i.e., cannot be displayed in its [PaneAdaptedValue.Expanded] state.
         */
        val Hide: AdaptStrategy = Simple("Hide")
    }
}
