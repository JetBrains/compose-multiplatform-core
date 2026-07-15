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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.DataSourceContext
import androidx.compose.runtime.invalidateDependants
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end proof that a custom [DataSource]'s reads are tracked during measure, draw and
 * semantics - not only during composition.
 *
 * Until this test existed, the fix was only proven at the `SnapshotStateObserver` level: nothing
 * showed that `Modifier.layout {}`, `Modifier.drawBehind {}` or `Modifier.semantics {}` actually
 * re-run when a source that is NOT the built-in snapshot substrate changes. [HookOnlySource] below
 * is deliberately shaped to make that provable: it records reads only through the recorder handed
 * to it by [DataSource.observe], never through the static [DataSource.recordDependency]. A source
 * that used the static recorder would already have worked before the fix (it goes through the
 * thread-local recorder chain regardless of where the read happens), which would make a test built
 * on it vacuous.
 *
 * What these three tests actually guard: `SnapshotStateObserver.observeReads` threading the
 * delivery domain's context through to `scopeMap.observe` - `scopeMap.observe(scope,
 * deliveryDomain?.context, readObserver, block)` in `SnapshotStateObserver.kt` - which is what
 * makes measure, draw and semantics call `DataSourceContext.observe` (and therefore each foreign
 * member's own `observe()` hook) at all. Before that `context` argument existed (i.e.
 * `scopeMap.observe(scope, readObserver, block)`, with `null` standing in for its absence), no
 * member hook was ever installed outside composition. Verified as the vacuity anchor: forcing that
 * argument to `null` makes all three tests fail on the real assertion; restoring it makes them pass
 * again. Task 4's `foreignRecordDependency` indirection (`ObservationWrapper.invoke` in
 * `DataSource.kt`) sits on top of this and only changes behaviour for
 * `withoutReadObservation`/`derivedStateOf` paths, so reverting *that* one-liner leaves these tests
 * green - it is the wrong anchor for this file.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DataSourcePhaseTrackingTest {
    private val dispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun setUp() {
        dispatcher.install()
    }

    @AfterTest
    fun tearDown() {
        dispatcher.uninstall()
    }

    @Test
    fun aForeignSourceReadInDrawBehindTriggersRedraw() {
        val source = HookOnlySource()
        var draws = 0
        var drawnValue: Int? = null
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                dataSourceContext = DataSourceContext(source),
                content = {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            draws++
                            drawnValue = source.read("k")
                        }
                    )
                },
            )
        try {
            scene.render()
            val before = draws
            // A store-only change: no snapshot state is written anywhere.
            source.write("k", 42)
            source.advanceAndInvalidate()
            scene.render()
            assertTrue(draws > before, "the draw phase must re-run after the source changed")
            assertEquals(42, drawnValue)
        } finally {
            scene.close()
        }
    }

    @Test
    fun aForeignSourceReadInLayoutTriggersRemeasure() {
        val source = HookOnlySource()
        var measures = 0
        var measuredValue: Int? = null
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                dataSourceContext = DataSourceContext(source),
                content = {
                    Box(
                        Modifier.fillMaxSize().layout { measurable, constraints ->
                            measures++
                            measuredValue = source.read("k")
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                    )
                },
            )
        try {
            scene.render()
            val before = measures
            // A store-only change: no snapshot state is written anywhere.
            source.write("k", 42)
            source.advanceAndInvalidate()
            scene.render()
            assertTrue(measures > before, "the measure phase must re-run after the source changed")
            assertEquals(42, measuredValue)
        } finally {
            scene.close()
        }
    }

    @Test
    fun aForeignSourceReadInSemanticsTriggersRecomputation() {
        val source = HookOnlySource()
        var semanticsRuns = 0
        var semanticsValue: Int? = null
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                dataSourceContext = DataSourceContext(source),
                content = {
                    Box(
                        Modifier.fillMaxSize().semantics {
                            semanticsRuns++
                            semanticsValue = source.read("k")
                        }
                    )
                },
            )
        try {
            scene.render()
            val before = semanticsRuns
            // A store-only change: no snapshot state is written anywhere.
            source.write("k", 42)
            source.advanceAndInvalidate()
            scene.render()
            assertTrue(
                semanticsRuns > before,
                "the semantics configuration must be recomputed after the source changed",
            )
            assertEquals(42, semanticsValue)
        } finally {
            scene.close()
        }
    }
}

/**
 * A hook-only [DataSource]: records reads ONLY through the recorder it is handed in [observe],
 * never through the static [DataSource.recordDependency]. That is the exact shape this file's tests
 * need - a static-recorder source already worked before the fix under test, because it goes through
 * the thread-local recorder chain regardless of where the read happens, which is why
 * `FrameCycleSnapshotSceneTest`'s static-recorder source is the wrong model to copy here.
 *
 * Writes are buffered until [advanceAndInvalidate] publishes them, so a write is invisible to
 * [read] until something explicitly drains it - modelling an external store rather than a
 * snapshot-backed one.
 */
private class HookOnlySource : DataSource {
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

    /** Buffers a write. Invisible to [read] until [advanceAndInvalidate]. */
    fun write(key: String, value: Int) {
        buffered[key] = value
        changedKeys.add(key)
    }

    /** Publishes buffered writes AND delivers their identifiers, in one call. */
    fun advanceAndInvalidate() {
        invalidateDependants(advanceGlobalSnapshot())
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
