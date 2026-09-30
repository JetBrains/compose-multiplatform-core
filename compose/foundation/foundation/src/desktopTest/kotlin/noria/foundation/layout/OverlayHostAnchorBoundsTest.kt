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

package noria.foundation.layout

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [OverlayScope.anchorBounds] comes from the anchor's `onGloballyPositioned`, which the owner
 * dispatches after a layout pass, and not at all for a node the pass left unplaced or
 * measure-pending. The overlay's content is composed from the host's measure, a shallower node
 * that the pass reaches first. So the host must not compose the content before the anchor has
 * been positioned once.
 */
@OptIn(ExperimentalComposeUiApi::class)
class OverlayHostAnchorBoundsTest {
    /**
     * Composes an anchor with an overlay whose content records [OverlayScope.anchorBounds], under
     * a layout that measures the anchor every pass but places it at (10, 10) only while
     * [placeAnchor] is true. An unplaced anchor is never positioned, which is how an anchor looks
     * to a host measure that runs before the anchor's first positioned dispatch.
     */
    private fun ImageComposeScene.setAnchorContent(
        placeAnchor: () -> Boolean,
        onContentComposed: (IntRect) -> Unit,
    ) {
        setContent {
            OverlayHost(MainOverlayHostKey, modifier = Modifier.fillMaxSize()) {
                Layout(
                    content = {
                        Spacer(
                            Modifier.size(20.dp).overlay(MainOverlayHostKey) {
                                onContentComposed(anchorBounds)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    val placeable = measurables.single().measure(Constraints())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (placeAnchor()) placeable.place(10, 10)
                    }
                }
            }
        }
    }

    @Test
    fun `the content of an overlay composes once its anchor has been positioned`() {
        var placeAnchor by mutableStateOf(false)
        val composedBounds = mutableListOf<IntRect>()
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setAnchorContent({ placeAnchor }) { composedBounds += it }

            // Frame 1 places the host and registers the overlay; the host composes the slot in
            // frame 2 and measures it, while the anchor has still never been placed.
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(
                emptyList(),
                composedBounds,
                "the content must not compose before the anchor has been positioned",
            )

            // Placing the anchor positions it at the end of that pass, which re-runs the host's
            // measure in the frame's trailing pass.
            Snapshot.withMutableSnapshot { placeAnchor = true }
            scene.render(32_000_000)
            assertEquals(listOf(IntRect(10, 10, 30, 30)), composedBounds)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the content of an overlay with a placed anchor composes the frame after it is added`() {
        val composedBounds = mutableListOf<IntRect>()
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setAnchorContent({ true }) { composedBounds += it }

            scene.render(0)
            scene.render(16_000_000)
            assertEquals(listOf(IntRect(10, 10, 30, 30)), composedBounds)
        } finally {
            scene.close()
        }
    }
}
