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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.use
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Behavioral tests for frame-cycle snapshot isolation.
 *
 * The equivalent contracts are also verified at the runtime level in
 * DataSourceSnapshotTests(-Jvm).
 */
class FrameCycleSnapshotSceneTest {

    private val schedulingDispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun installSchedulingDispatcher() {
        schedulingDispatcher.install()
    }

    @AfterTest
    fun uninstallSchedulingDispatcher() {
        schedulingDispatcher.uninstall()
    }

    private fun <T> withFrameIsolation(block: () -> T): T {
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = true
        return try {
            block()
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
        }
    }

    @Test
    fun externalPublicationDuringAFrameIsInvisibleToLayoutAndDraw() = withFrameIsolation {
        val state = mutableStateOf(0)
        var measured = -1
        var drawn = -1
        ImageComposeScene(width = 100, height = 100) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        thread { Snapshot.withMutableSnapshot { state.value = 1 } }.join()
                        measured = state.value
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .drawBehind { drawn = state.value }
            )
        }.use { scene ->
            scene.render(nanoTime = 0L)
            assertEquals(0, measured)
            assertEquals(0, drawn)
            scene.render(nanoTime = 16_000_000L) // the pin swap makes it visible
            assertEquals(1, measured)
            assertEquals(1, drawn)
        }
    }

    @Test
    fun eventSpansObserveTheLatestFrameWorldAndPublishOnReturn() = withFrameIsolation {
        val fromEvent = mutableStateOf(0)
        var composed = -1
        ImageComposeScene(width = 100, height = 100) {
            composed = fromEvent.value
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { fromEvent.value = 42 }
            )
        }.use { scene ->
            scene.render(nanoTime = 0L)
            assertEquals(0, fromEvent.value)
            assertEquals(0, composed)

            // The click lands inside the filling Box; the pointer span runs the tap
            // handler's write inside the cycle unit and publishes it at return.
            scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 50f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 50f))

            // The write is globally visible (readable from outside the scene/unit)
            // immediately after the event call returns - it does not wait for a render.
            assertEquals(42, fromEvent.value)
            // But composition hasn't been re-run yet: that only happens on the next
            // render's recomposition pass, not synchronously during the event.
            assertEquals(0, composed)

            scene.render(nanoTime = 16_000_000L)
            assertEquals(42, composed)
        }
    }

    @Test
    fun effectTaskFlushedDuringAnEventSpanMergesIntoItsSinglePublish() = withFrameIsolation {
        val fromEvent = mutableStateOf(0)
        val fromEffect = mutableStateOf(0)
        var composedFromEvent = -1
        var composedFromEffect = -1
        // A queuing dispatcher, NOT the default Unconfined. Under Unconfined the effect
        // dispatcher's eager launch fallback runs the task inline at dispatch time in a
        // Job-stripped scope, where a publish-while-entered crash is swallowed into the
        // platform uncaught handler and its pending write is published by the outer span
        // anyway - the regression would stay green. With a queuing dispatcher the fallback
        // only enqueues (never advanced by this test), so the task's one execution is
        // sendPointerEvent's own performScheduledEffects() flush, synchronously on the
        // event call stack while the span's unit is still entered - the exact shape of the
        // original crash. clickable delivers onClick synchronously during event dispatch
        // regardless of this dispatcher (plain onPointerEvent state machine; even the
        // suspending-gesture path resumes awaiters interceptor-free by design).
        val sceneDispatcher = StandardTestDispatcher()
        ImageComposeScene(
            width = 100,
            height = 100,
            coroutineContext = sceneDispatcher,
        ) {
            composedFromEvent = fromEvent.value
            composedFromEffect = fromEffect.value
            val scope = rememberCoroutineScope()
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable {
                        // Direct write: recorded straight into the span's entered unit.
                        fromEvent.value = 7
                        // Queued effect write: lands in the effect dispatcher's queue and
                        // is flushed by performScheduledEffects() at the tail of this same
                        // sendPointerEvent span. The wrapper must only enter (merge)
                        // because the span already entered the unit; publishing instead
                        // would violate publish()'s not-entered precondition - the direct
                        // regression coverage for the outermost-publishes fix.
                        scope.launch { fromEffect.value = 13 }
                    }
            )
        }.use { scene ->
            scene.render(nanoTime = 0L)
            assertEquals(0, fromEvent.value)
            assertEquals(0, fromEffect.value)

            // Must complete without throwing: the effect task runs inside flush() on this
            // call stack, so a regression that publishes the nested slice on its own
            // throws IllegalStateException out of sendPointerEvent (withFrameTransaction
            // rethrows task failures) instead of returning.
            scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 50f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 50f))

            // Both the direct write and the queued effect's write are globally visible
            // as soon as the event call returns - one atomic publish for the whole span.
            assertEquals(7, fromEvent.value)
            assertEquals(13, fromEffect.value)
            // Composition still lags until the next render.
            assertEquals(0, composedFromEvent)
            assertEquals(0, composedFromEffect)

            scene.render(nanoTime = 16_000_000L)
            assertEquals(7, composedFromEvent)
            assertEquals(13, composedFromEffect)
        }
    }
}
