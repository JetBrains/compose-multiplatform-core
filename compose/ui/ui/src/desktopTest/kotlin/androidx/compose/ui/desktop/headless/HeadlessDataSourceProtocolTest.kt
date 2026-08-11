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

package androidx.compose.ui.desktop.headless

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * The [DataSource] protocol as a real frame loop drives it: a headless application, a real
 * [androidx.compose.runtime.Recomposer], real UI-thread affinity and a caller-owned frame clock.
 *
 * The scene-level tests in `androidx.compose.ui.scene` cover the same contract on a bare
 * [androidx.compose.ui.ImageComposeScene], which has no event loop and no scheduling: they cannot
 * see work that is posted rather than run, and posted work is where the two halves of the protocol
 * come apart. The protocol is deliberately split - a read scope
 * ([DataSource.Snapshot.makeCurrent]) binds what reads SEE, a transaction
 * ([DataSource.Snapshot.beginTransaction]) opens what writes go INTO - and only the first is a
 * precondition of reading. A source that binds a thread-local view in the read scope, which is
 * every database-backed source, is blind in any ingress that transacts without entering.
 *
 * That is not hypothetical. It is how Fleet's RhizomeDB source is built, and an ingress that
 * transacted without entering is how Air's first frame died with `OutOfDbContext` - thrown far from
 * the ingress that was actually wrong, because both `enter` and `withTransaction` are inline and
 * leave no stack frame. These tests fail at the ingress instead.
 */
@Category(HeadlessTest::class)
class HeadlessDataSourceProtocolTest {
    private lateinit var app: HeadlessApplication
    private lateinit var scope: CoroutineScope
    private var flagBefore = false

    @Before
    fun setUp() {
        flagBefore = ComposeSceneFeatureFlags.isFrameIsolationEnabled
        app =
            HeadlessApplication.initialize(
                System.getProperty("java.io.tmpdir"),
                frameIsolation = true,
            )
        scope = CoroutineScope(SupervisorJob())
    }

    @After
    fun tearDown() = runBlocking {
        try {
            scope.cancel()
            app.resetForReuse()
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = flagBefore
        }
    }

    /**
     * A source shaped like a database binding: the read view lives in a thread local that only the
     * read scope installs, and opening a transaction is refused while it is absent.
     *
     * The refusal is the whole point. A source that tolerates a missing view - by falling back to
     * the live data, say - turns a broken ingress into a silent correctness bug that surfaces as a
     * torn frame somewhere else. Failing at [beginTransaction] names the ingress while its call
     * stack is still on the stack.
     */
    private class ReadScopeStrictSource : DataSource {
        /** Mirrors the `DbContext` thread local a real database source binds in its read scope. */
        private val boundUnit = ThreadLocal<Any?>()

        /** `enter`/`exit`/`begin`/`end`, in the order the frame loop drove them. */
        val protocol = CopyOnWriteArrayList<String>()

        /**
         * Off until the frame domain is live. Scene CONSTRUCTION observes with no unit in
         * existence - `LayoutNode.attach` drives semantics before `activateFrameDomain()` can run -
         * so nothing can bind a view in that window and a strict source must tolerate its absence
         * until then.
         */
        @Volatile var strict = false

        val isViewBound: Boolean
            get() = boundUnit.get() != null

        override fun <T> observe(
            recordDependency: (Any) -> Boolean,
            recordChange: ((Any) -> Unit)?,
            block: () -> T,
        ): T = block()

        override fun <T> withTransaction(block: () -> T): T = block()

        override fun advanceGlobalSnapshot(): Set<Any> = emptySet()

        override fun takeSnapshot(): DataSource.Snapshot = FrameUnit()

        /** Named for the cycle unit it stands for; `Unit` itself would shadow `kotlin.Unit`. */
        private inner class FrameUnit : DataSource.Snapshot {
            override fun makeCurrent(): Any? {
                val previous = boundUnit.get()
                boundUnit.set(this)
                protocol += "enter"
                return previous
            }

            override fun restoreCurrent(previous: Any?) {
                boundUnit.set(previous)
                protocol += "exit"
            }

            override fun beginTransaction(): Any? {
                check(!strict || boundUnit.get() === this) {
                    "a transaction was opened without entering the read scope first " +
                        "(bound: ${boundUnit.get()})"
                }
                protocol += "begin"
                return null
            }

            override fun endTransaction(frame: Any?, cause: Throwable?) {
                protocol += "end"
            }

            override fun dispose() {}
        }
    }

    private fun newStrictWindow(source: ReadScopeStrictSource): HeadlessWindow {
        val window = app.createWindow(ApplicationSession(scope, DataSourceContext(source))) {}
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            Box(Modifier.fillMaxSize())
        }
        // The frame domain is active from here on, so every ingress can and must bind a view.
        source.strict = true
        return window
    }

    @Test
    fun aRenderedFrameEntersTheReadScopeBeforeItOpensATransaction() = runBlocking {
        val source = ReadScopeStrictSource()
        val window = newStrictWindow(source)
        for (frame in 1..3) {
            withContext(ComposeUIDispatcher) { window.render(nanoTime = frame * 16_000_000L) }
            app.awaitIdle()
        }
        withContext(ComposeUIDispatcher) { window.dispose() }

        assertTrue(source.protocol.contains("begin"), "no transaction was opened: ${source.protocol}")
        assertEquals(
            "enter",
            source.protocol.first(),
            "the first thing a frame does must be to bind the read view: ${source.protocol}",
        )
    }

    /**
     * The ingresses that are not scene entry points: pointer and key delivery. They reach
     * application code through the shared frame helper rather than through `render`, and they are
     * where Air actually crashed - a `MouseUp` synthesises a key event, which reaches shortcut
     * matching, which reads the database.
     */
    @Test
    fun pointerAndKeyIngressesEnterTheReadScopeToo() = runBlocking {
        val source = ReadScopeStrictSource()
        val window = newStrictWindow(source)
        withContext(ComposeUIDispatcher) { window.render(nanoTime = 16_000_000L) }
        app.awaitIdle()
        val afterFirstFrame = source.protocol.size

        app.sendMouseEnter(window.id, DpOffset(5.dp, 5.dp))
        app.sendMouseDown(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        app.sendMouseUp(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        app.sendKeyDown(Key.A, window.id, codePoint = 'a'.code)
        app.sendKeyUp(Key.A, window.id, codePoint = 'a'.code)
        app.awaitIdle()
        withContext(ComposeUIDispatcher) { window.dispose() }

        val fromInput = source.protocol.drop(afterFirstFrame)
        assertTrue(
            fromInput.contains("enter"),
            "input delivery bound no read view: $fromInput",
        )
    }

    /**
     * Every ingress must leave the thread exactly as it found it. A leaked binding is worse than a
     * missing one: the next unrelated task on the loop thread reads a view that belongs to a frame
     * that is over, sees a consistent-looking but arbitrarily old world, and never fails.
     */
    @Test
    fun everyIngressLeavesTheThreadUnbound() = runBlocking {
        val source = ReadScopeStrictSource()
        val window = newStrictWindow(source)
        for (frame in 1..3) {
            withContext(ComposeUIDispatcher) { window.render(nanoTime = frame * 16_000_000L) }
            app.awaitIdle()
            withContext(ComposeUIDispatcher) {
                assertTrue(!source.isViewBound, "a read view outlived frame $frame")
            }
        }
        app.sendMouseEnter(window.id, DpOffset(5.dp, 5.dp))
        app.sendMouseDown(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        app.awaitIdle()
        withContext(ComposeUIDispatcher) {
            assertTrue(!source.isViewBound, "a read view outlived input delivery")
        }
        withContext(ComposeUIDispatcher) { window.dispose() }

        assertEquals(
            source.protocol.count { it == "enter" },
            source.protocol.count { it == "exit" },
            "unbalanced read scopes: ${source.protocol}",
        )
        assertEquals(
            source.protocol.count { it == "begin" },
            source.protocol.count { it == "end" },
            "unbalanced transactions: ${source.protocol}",
        )
    }

    /**
     * With isolation off there is no frame unit at all, so nothing is entered and nothing is
     * transacted - the ingresses run the block bare. That is a deliberate degradation and not a
     * fallback: a source that needs a bound view cannot work in this mode, which is exactly why the
     * shipping product turns isolation on and why Fleet's test venue sets `-Dcompose.frameIsolation`
     * rather than testing the default.
     *
     * Pinned so the mode cannot start half-working - a partial protocol (transactions but no read
     * scope) would be the one shape that fails late and far away instead of not at all.
     */
    @Test
    fun withFrameIsolationOffNoUnitIsEnteredAndNoneTransacts() = runBlocking {
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
        val source = ReadScopeStrictSource()
        val window = app.createWindow(ApplicationSession(scope, DataSourceContext(source))) {}
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            Box(Modifier.fillMaxSize())
        }
        source.strict = true
        for (frame in 1..3) {
            withContext(ComposeUIDispatcher) { window.render(nanoTime = frame * 16_000_000L) }
            app.awaitIdle()
        }
        app.sendMouseEnter(window.id, DpOffset(5.dp, 5.dp))
        app.sendMouseDown(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        app.awaitIdle()
        withContext(ComposeUIDispatcher) { window.dispose() }

        assertEquals(emptyList(), source.protocol.toList())
    }
}
