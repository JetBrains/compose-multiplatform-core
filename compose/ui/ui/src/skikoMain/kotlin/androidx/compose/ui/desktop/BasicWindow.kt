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

package noria.ui.core

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.semantics.SemanticsOwner

/**
 * The test-data view of one window.
 *
 * Wraps a live view of the [SemanticsOwner]s the window's scene owns.
 */
class UIRoot internal constructor(
    private val semanticsOwners: () -> Collection<SemanticsOwner>,
) {
    /**
     * Every test node in this [UIRoot], in registration order across every [SemanticsOwner] the
     * window's scene owns, so that `ComposeSceneLayer`-based popups are visible. Air does not use
     * those today.
     *
     * KNOWN DIVERGENCE FROM NORIA: see the note on [noria.ui.core.markTestSubtree] about overlays.
     */
    fun getAllTestNodes(): Sequence<TestNode> {
        check(TestDataMode.isEnabled) {
            "Test data was not collected: TestDataMode.isEnabled is false. Set it before the " +
                "first composition that uses Modifier.testData."
        }
        val owners = semanticsOwners().toList()
        return sequence {
            for (owner in owners) {
                yieldAll(flattenTestNodes(owner.unmergedRootSemanticsNode))
            }
        }
    }
}

data class WindowData(val windowId: LightweightWindowId, val uiRoot: UIRoot)

val LocalWindow: ProvidableCompositionLocal<Window> = staticCompositionLocalOf {
    error("LocalWindow is not provided")
}