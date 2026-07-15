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
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene must bind a read view at every ingress, because the phase observers it drives
 * ([androidx.compose.runtime.snapshots.SnapshotStateObserver.observeReads] for measure, layout, draw
 * and semantics) route through [DataSourceContext.observe] on every pass - and a transaction's
 * publication dispatches its invalidations after that transaction has been restored off the thread.
 *
 * A source that cannot read without a bound view therefore fails on the FIRST phase of the first
 * render unless the ingress entered a read scope. That is the crash this file guards: it is the
 * reduced form of Fleet's `OutOfDbContext` on Air's first frame.
 */
class DataSourceReadScopeSceneTest {
    private val dispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun setUp() {
        dispatcher.install()
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = true
    }

    @AfterTest
    fun tearDown() {
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
        dispatcher.uninstall()
    }

    /**
     * Models a source whose reads need thread-bound state, the way a database context does. A
     * transaction ([withTransaction]/[DataSource.Snapshot.beginTransaction]) grants it, and so does a read
     * scope ([DataSource.Snapshot.makeCurrent]) - the latter being the only one available at a
     * publication's invalidation dispatch, where the transaction is already gone.
     */
    private class ViewRequiringSource(
        /**
         * When false, only [DataSource.Snapshot.makeCurrent] binds a view - opening a transaction
         * does not. This is the shape a real integration takes once the read scope exists (Fleet's
         * RhizomeDB source binds its `DbContext` in `makeCurrent`), and it is what makes a
         * transaction-without-a-read-scope observable rather than accidentally working.
         */
        private val bindsViewInTransaction: Boolean = true
    ) : DataSource {
        private var published = mapOf<String, Int>()
        private var boundView: Map<String, Int>? = null
        private var pinnedView: Map<String, Int>? = null

        private val hasView: Boolean
            get() = pinnedView != null || boundView != null

        /**
         * Enforcement is opt-in because scene CONSTRUCTION legitimately observes with no unit in
         * existence: `activateFrameDomain()` must run after the constructor returns (activation
         * snapshots the pin, so scene-owned state has to predate it), yet `RootNodeOwner`'s
         * `LayoutNode.attach` already drives `calculateSemanticsConfiguration` ->
         * `observeReads`. Nothing can bind a view in that window, so a view-requiring source
         * must tolerate its absence until activation. Turned on afterwards, where the design
         * does guarantee a view.
         */
        var strict = false

        var observeCount = 0
            private set

        fun write(key: String, value: Int) {
            published = published + (key to value)
        }

        /**
         * Checks for a view like a real database read does - the Air crash surfaced in
         * `Entity.exists()`, a plain read, not in [observe].
         */
        fun read(key: String): Int? {
            check(!strict || hasView) {
                "No bound view: read() was reached outside both a transaction and a read scope"
            }
            return (pinnedView ?: boundView ?: published)[key]
        }

        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T {
            check(!strict || hasView) {
                "No bound view: observe() was reached outside both a transaction and a read scope"
            }
            observeCount++
            return block()
        }

        override fun <T> withTransaction(block: () -> T): T {
            if (!bindsViewInTransaction) return block()
            val previous = pinnedView
            pinnedView = published
            try {
                return block()
            } finally {
                pinnedView = previous
            }
        }

        override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

        override fun takeSnapshot(): DataSource.Snapshot =
            object : DataSource.Snapshot {
                private val base = published
                private var depth = 0
                private var outerView: Map<String, Int>? = null

                override fun makeCurrent(): Any? {
                    val previous = boundView
                    boundView = base
                    return previous
                }

                @Suppress("UNCHECKED_CAST")
                override fun restoreCurrent(previous: Any?) {
                    boundView = previous as Map<String, Int>?
                }

                override fun beginTransaction(): Any? {
                    if (!bindsViewInTransaction) return null
                    if (depth++ == 0) {
                        outerView = pinnedView
                        pinnedView = base
                    }
                    return null
                }

                override fun endTransaction(frame: Any?, cause: Throwable?) {
                    if (!bindsViewInTransaction) return
                    if (--depth == 0) {
                        pinnedView = outerView
                        outerView = null
                    }
                }

                override fun dispose() {}
            }
    }

    @Test
    fun sceneIngressesBindAReadViewForAViewRequiringSource() {
        val source = ViewRequiringSource()
        source.write("k", 7)
        var readInComposition: Int? = null
        val scene =
            ImageComposeScene(
                width = 10,
                height = 10,
                dataSourceContext = DataSourceContext(source),
            ) {
                readInComposition = source.read("k")
                Box(Modifier.fillMaxSize())
            }
        try {
            // From here on every observation must have a view: the frame domain is active and
            // every ingress enters a read scope.
            source.strict = true

            // render drives the phase observers (measure, layout, draw, semantics), each of which
            // fans out through the context to this source, and each phase transaction's
            // publication dispatches invalidations AFTER restoring the thread. Without the read
            // scope the first of those throws before this returns.
            scene.render()
            scene.render()

            assertTrue(
                source.observeCount > 0,
                "the scene must route phase observation through the context",
            )
            assertEquals(7, readInComposition, "composition read through the bound view")
        } finally {
            scene.close()
        }
    }

    /**
     * Every platform ingress that is NOT a scene entry point goes through the shared frame helper:
     * the macOS/Linux/GTK key and pointer hooks, `MacOsTextInputSessionOwner`, iOS accessibility,
     * drag-and-drop and text input - roughly a hundred call sites. It used to open a transaction
     * only, which was sufficient while `beginTransaction` was the sole way a source got its view.
     * It no longer is: binding the view is the read scope's job, so a transaction alone leaves a
     * source blind.
     *
     * Reduced from the reported crash: a MouseUp makes `InputStateTracker` synthesise a key event,
     * which reaches Fleet's shortcut matching through this helper and threw `OutOfDbContext`.
     */
    @Test
    fun theFrameHelperBindsAReadViewAndNotOnlyATransaction() {
        val source = ViewRequiringSource(bindsViewInTransaction = false)
        source.write("k", 7)
        val scene = CanvasLayersComposeScene(dataSourceContext = DataSourceContext(source))
        try {
            scene.setContent { Box(Modifier.fillMaxSize()) }
            source.strict = true

            // Exactly what a platform hook does before calling into application code.
            val value = scene.withFrameTransaction { source.read("k") }

            assertEquals(7, value, "the helper must bind the frame's view, not just transact")
        } finally {
            scene.close()
        }
    }
}
