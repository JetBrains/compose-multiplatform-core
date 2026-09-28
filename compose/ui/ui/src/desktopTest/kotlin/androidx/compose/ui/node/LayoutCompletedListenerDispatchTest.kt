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

package androidx.compose.ui.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the layout delegate calls its layout-completed listeners when a listener lays out again or
 * registers another listener from inside its call, and which layouts call the listeners that wait
 * for a full layout pass.
 */
class LayoutCompletedListenerDispatchTest {

    /**
     * The first of two listeners lays out a node again, which dispatches the listeners from
     * inside its own call, and then registers a third listener. The dispatch must not fail, and
     * must call each listener exactly once, in registration order, the third one included.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a listener that lays out again and registers another runs each listener once in order`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val node = arrayOfNulls<LayoutNode>(1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                Box(Modifier.size(10.dp).onPlaced { node[0] = (it as NodeCoordinator).layoutNode })
            }
            scene.render(0)
            val laidOut = node[0]!!
            val owner = laidOut.owner!!
            val calls = mutableListOf<String>()
            val third =
                object : Owner.OnLayoutCompletedListener {
                    override fun onLayoutComplete() {
                        calls += "third"
                    }
                }
            owner.registerOnLayoutCompletedListener(
                object : Owner.OnLayoutCompletedListener {
                    override fun onLayoutComplete() {
                        calls += "first"
                        // Only once, so a dispatch that calls it again cannot recurse for good.
                        if (calls.count { it == "first" } > 1) return
                        owner.measureAndLayout(laidOut, Constraints.fixed(10, 10))
                        owner.registerOnLayoutCompletedListener(third)
                    }
                }
            )
            owner.registerOnLayoutCompletedListener(
                object : Owner.OnLayoutCompletedListener {
                    override fun onLayoutComplete() {
                        calls += "second"
                    }
                }
            )

            owner.measureAndLayout(laidOut, Constraints.fixed(10, 10))

            assertEquals(listOf("first", "second", "third"), calls)
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }

    /**
     * A listener registered for a full layout pass waits for one: a remeasure of a single node,
     * which calls the other listeners, must not call it, and the owner's next layout must, even
     * with no node left to measure. A full pass calls these listeners before the others, so one
     * that another listener registers during that pass waits for the next full pass.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a full-layout listener waits for a full pass and runs before the other listeners`() {
        val scheduling = SchedulingDispatcherFixture().apply { install() }
        val node = arrayOfNulls<LayoutNode>(1)
        val scene = ImageComposeScene(width = 100, height = 100)
        try {
            scene.setContent {
                Box(Modifier.size(10.dp).onPlaced { node[0] = (it as NodeCoordinator).layoutNode })
            }
            scene.render(0)
            val laidOut = node[0]!!
            val owner = laidOut.owner!!
            val calls = mutableListOf<String>()
            fun listener(name: String, then: () -> Unit = {}) =
                object : Owner.OnLayoutCompletedListener {
                    override fun onLayoutComplete() {
                        calls += name
                        then()
                    }
                }
            // Registered after the other listener, and called before it.
            owner.registerOnLayoutCompletedListener(
                listener("registering") {
                    owner.registerOnFullLayoutCompletedListener(listener("next full"))
                }
            )
            owner.registerOnFullLayoutCompletedListener(listener("full"))
            owner.measureAndLayout()
            assertEquals(
                listOf("full", "registering"),
                calls,
                "the owner's next layout must call the full-layout listener, and first, although " +
                    "no node was left to measure",
            )

            owner.registerOnLayoutCompletedListener(listener("any"))
            owner.measureAndLayout(laidOut, Constraints.fixed(10, 10))
            assertEquals(
                listOf("full", "registering", "any"),
                calls,
                "a remeasure of a single node must not call a full-layout listener",
            )

            owner.measureAndLayout()
            assertEquals(
                listOf("full", "registering", "any", "next full"),
                calls,
                "a full-layout listener registered by another listener waits for the next full " +
                    "pass",
            )
        } finally {
            scene.close()
            scheduling.uninstall()
        }
    }
}
