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

package androidx.compose.ui.test

import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.invalidateDependants
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestResult

/**
 * The runner's scene under frame isolation, chosen per test: content reading a foreign
 * [DataSource] through the runner's [DataSourceContext] catches up with a write made by the test
 * body before an idle wait returns.
 *
 * Under isolation a write from outside the content is not delivered to the composition when it is
 * made but at the next frame's pin swap, and until then no recomposition, layout or effect work is
 * pending. The idle waits below are therefore only correct if the runner also counts the scene's
 * undelivered frame-domain work as "not idle"; without that they return at once and the assertions
 * see the old value.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class FrameIsolationTest {

    @Test
    fun aPublishedWriteReachesTheContentBeforeWaitForIdleReturns(): TestResult {
        val source = MapSource()
        return runSkikoComposeUiTest(
            dataSourceContext = DataSourceContext(source),
            frameIsolation = true,
        ) {
            var composed: Int? = -1
            setContent { composed = source.read("k") }
            assertNull(composed)

            source.publish("k", 42)
            waitForIdle()

            assertEquals(42, composed)
        }
    }

    @Test
    fun aBufferedWriteReachesTheContentBeforeAwaitIdleReturns(): TestResult {
        val source = MapSource()
        val context = DataSourceContext(source)
        return runSkikoComposeUiTest(dataSourceContext = context, frameIsolation = true) {
            var composed: Int? = -1
            setContent { composed = source.read("k") }
            assertNull(composed)

            // The source holds the value unpublished and only signals that the context has
            // something to advance; the next frame drains it and then delivers it.
            source.buffer("k", 7)
            context.scheduleAdvance()
            awaitIdle()

            assertEquals(7, composed)
        }
    }

    /**
     * The scene's standing pin is taken when the scene is created, before the test body runs, and
     * the first composition reads through it. A state the body creates before `setContent`, the
     * most common test shape, postdates it, so the runner has to rotate the pin before composing.
     */
    @Test
    fun aStateCreatedBeforeSetContentIsReadByTheFirstComposition() =
        runSkikoComposeUiTest(frameIsolation = true) {
            val state = mutableStateOf(1)
            var composed = 0
            setContent { composed = state.value }
            assertEquals(1, composed)

            state.value = 2
            waitForIdle()
            assertEquals(2, composed)
        }

    @Test
    fun frameIsolationOnGivesTheSceneAFrameUnit() =
        runSkikoComposeUiTest(frameIsolation = true) {
            assertNotNull(runOnUiThread { scene.currentFrameSnapshot })
        }

    @Test
    fun frameIsolationOffLeavesTheSceneWithoutAFrameUnit() =
        runSkikoComposeUiTest(frameIsolation = false) {
            assertNull(runOnUiThread { scene.currentFrameSnapshot })
        }
}

/**
 * An in-memory [DataSource] outside snapshot state. Reads are recorded only through the recorder
 * [observe] installs, so the content is invalidated by this source only when the runner's scene
 * actually observes through the context the source is a member of.
 */
private class MapSource : DataSource {
    private var published: Map<String, Int> = emptyMap()
    private var buffered: Map<String, Int> = emptyMap()
    private var hook: ((Any) -> Boolean)? = null

    fun read(key: String): Int? {
        hook?.invoke(key)
        return published[key]
    }

    /** Makes the value visible and invalidates its readers straight away. */
    fun publish(key: String, value: Int) {
        published = published + (key to value)
        invalidateDependants(setOf(key))
    }

    /** Holds the value until the context advances this source. */
    fun buffer(key: String, value: Int) {
        buffered = buffered + (key to value)
    }

    override fun <T> observe(
        recordDependency: (Any) -> Boolean,
        recordChange: ((Any) -> Unit)?,
        block: () -> T,
    ): T {
        val previous = hook
        hook = recordDependency
        try {
            return block()
        } finally {
            hook = previous
        }
    }

    override fun <T> withTransaction(block: () -> T): T = block()

    override fun advanceGlobalSnapshot(): Set<Any> {
        if (buffered.isEmpty()) return emptySet()
        published = published + buffered
        val changed: Set<Any> = buffered.keys
        buffered = emptyMap()
        return changed
    }

    override fun takeSnapshot(): DataSource.Snapshot =
        object : DataSource.Snapshot {
            override fun makeCurrent(): Any? = null

            override fun restoreCurrent(previous: Any?) {}

            override fun beginTransaction(): Any? = null

            override fun endTransaction(frame: Any?, cause: Throwable?) {}

            override fun dispose() {}
        }
}
