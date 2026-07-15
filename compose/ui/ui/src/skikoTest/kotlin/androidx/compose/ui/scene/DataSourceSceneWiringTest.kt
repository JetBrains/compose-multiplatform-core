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

package androidx.compose.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.IntSize
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Surface

/**
 * End-to-end coverage for the scene wiring that drains a scene's [DataSourceContext] at every
 * scene-entry boundary and folds a still-pending advance into the "do I need a frame?" decision.
 *
 * Until this file existed, that wiring -
 * `SnapshotInvalidationTracker.sendAndPerformSnapshotChanges` draining `context` unconditionally,
 * and `BaseComposeScene.updateInvalidations` folding in `context.hasPendingAdvance` - had zero
 * automated coverage, and every prior I2 test ran with frame isolation off.
 * [aStoreOnlyChangeRequestsAFrame] is the direct regression guard for the originally reported bug:
 * a change to a custom data source with no accompanying snapshot write used to request no frame at
 * all, leaving the UI stale.
 *
 * [RecordingSource] is modelled on `DataSourcePhaseTrackingTest.HookOnlySource`: it records reads
 * ONLY through the recorder handed to it by [DataSource.observe], never through the static
 * [DataSource.recordDependency]. A source built on the static recorder would already work without
 * any of this project's fixes (it goes through the thread-local recorder chain regardless of where
 * the read happens), which would make a test built on it vacuous.
 */
class DataSourceSceneWiringTest {
    private val dispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun setUp() {
        // install(), not installControlled(): scene construction here doesn't run inside a
        // blocking invokeAndWait, but the delivery path this file exercises is fully
        // synchronous (verified in Task 6), so there is nothing installControlled()'s deferred
        // scheduling would buy us - only a scheduler with nothing to advance it.
        dispatcher.install()
    }

    @AfterTest
    fun tearDown() {
        dispatcher.uninstall()
    }

    @Test
    fun aBufferedForeignWriteIsPublishedByEnteringAnySceneEntryPoint() {
        val source = RecordingSource()
        val scene =
            ImageComposeScene(
                width = 10,
                height = 10,
                dataSourceContext = DataSourceContext(source),
            )
        try {
            scene.render()
            // Buffered: not published, and not accompanied by a snapshot write anywhere.
            source.write("k", 42)
            assertNull(source.publishedValue("k"), "the write must still be buffered")

            // No explicit context.advanceGlobalSnapshot() call here: entering a scene entry
            // point (render, in this case) must drain the context on its own -
            // SnapshotInvalidationTracker.sendAndPerformSnapshotChanges (called from
            // BaseComposeScene.postponeInvalidation around every entry point) does that
            // unconditionally, independent of whether anything reads the source.
            scene.render()

            assertEquals(42, source.publishedValue("k"))
        } finally {
            scene.close()
        }
    }

    @Test
    fun aStoreOnlyChangeRequestsAFrame() {
        var invalidateCount = 0
        val context = DataSourceContext()
        val size = IntSize(10, 10)
        val scene =
            CanvasLayersComposeScene(
                size = size,
                dataSourceContext = context,
                invalidate = { invalidateCount++ },
            )
        try {
            scene.setContent {}
            // Settle the scene: SnapshotInvalidationTracker starts with needMeasureAndLayout
            // and needDraw both true, so hasInvalidations() alone would keep requesting a
            // frame regardless of hasPendingAdvance until the first render clears them. A
            // canvas is needed only for this - the render itself is not what this test is
            // about.
            val surface = Surface.makeRasterN32Premul(size.width, size.height)
            scene.render(surface.canvas.asComposeCanvas(), nanoTime = 0L)
            val before = invalidateCount

            // No snapshot state is written anywhere - this is exactly a foreign source
            // signalling that it holds unpublished data with no accompanying mutableStateOf
            // write. BaseComposeScene.updateInvalidations must still fold this into its
            // "do I need to render?" decision (frameSnapshotHolder.context.hasPendingAdvance)
            // and call the scene's invalidate callback.
            context.scheduleAdvance()

            assertTrue(
                invalidateCount > before,
                "a store-only change must still request a frame; the UI would otherwise stay " +
                    "stale until something unrelated happens to render",
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun aFlagOnForeignSourceChangeIsObservedOnTheNextRender() {
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = true
        try {
            val source = RecordingSource()
            var composed: Int? = -1
            val scene =
                ImageComposeScene(
                    width = 10,
                    height = 10,
                    dataSourceContext = DataSourceContext(source),
                ) {
                    composed = source.read("k")
                    Box(Modifier.fillMaxSize())
                }
            try {
                scene.render()
                assertNull(composed)

                // Buffered, no explicit advance: the drain that BaseComposeScene.render()
                // performs on entry (via sendAndPerformSnapshotChanges) must both publish
                // this and deliver the invalidation to the composition that read it, so the
                // very next render sees the new value.
                source.write("k", 42)
                scene.render(nanoTime = 16_000_000L)

                assertEquals(42, composed)
            } finally {
                scene.close()
            }
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
        }
    }

    @Test
    fun flagOffWithNoForeignSourcesBehavesAsBefore() {
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
        var invalidateCount = 0
        val context = DataSourceContext()
        val scene =
            CanvasLayersComposeScene(
                size = IntSize(100, 100),
                dataSourceContext = context,
                invalidate = { invalidateCount++ },
            )
        try {
            // Same shape as CanvasLayersComposeSceneTest.sceneSizeChangeTriggersInvalidation:
            // with no foreign sources, folding context.hasPendingAdvance into
            // updateInvalidations must not add any extra renders over the pre-existing
            // (flag-off) behavior.
            scene.setContent { Box(Modifier.fillMaxSize()) }
            assertEquals(1, invalidateCount)
            assertFalse(context.hasPendingAdvance)

            scene.size = IntSize(120, 120)
            assertEquals(2, invalidateCount)
            assertFalse(context.hasPendingAdvance)
        } finally {
            scene.close()
        }
    }
}

/**
 * A hook-only [DataSource]: records reads ONLY through the recorder installed by [observe], never
 * through the static [DataSource.recordDependency]. See the class KDoc above for why that shape -
 * not a static-recorder source - is the one these tests need.
 *
 * Writes are buffered until the context drains this source (via [advanceGlobalSnapshot]), so a
 * write is invisible to [read]/[publishedValue] until something drains it - modelling an external
 * store rather than a snapshot-backed one.
 */
private class RecordingSource : DataSource {
    private var published: Map<String, Int> = emptyMap()
    private var buffered = mutableMapOf<String, Int>()
    private var changedKeys = mutableSetOf<Any>()

    /** The recorder installed by [observe], or null when no hook is installed. */
    private var hook: ((Any) -> Boolean)? = null

    /**
     * A tracked read: records [key] through the installed hook, or nothing when none is installed.
     */
    fun read(key: String): Int? {
        hook?.invoke(key)
        return published[key]
    }

    /**
     * What [aBufferedForeignWriteIsPublishedByEnteringAnySceneEntryPoint] asserts on, bypassing the
     * hook: this test is about publication timing, not dependency tracking, so it must not itself
     * establish a dependency.
     */
    fun publishedValue(key: String): Int? = published[key]

    /** Buffers a write. Invisible to [read]/[publishedValue] until something drains this source. */
    fun write(key: String, value: Int) {
        buffered[key] = value
        changedKeys.add(key)
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
        buffered = mutableMapOf()
        val changed: Set<Any> = changedKeys
        changedKeys = mutableSetOf()
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
