/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.SkikoComposeTestBase
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.touch
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class BaseComposeSceneTest : SkikoComposeTestBase() {

    @Test
    fun testMoveEventsConsumption() = runComposeSceneTest { scene ->
        var consumeAll = false
        scene.setContent {
            Box(modifier = Modifier.fillMaxSize().pointerInput(PointerEventPass.Initial) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (consumeAll) {
                            event.changes.forEach {
                                if ((it.previousPosition - it.position) != Offset.Zero) it.consume()
                            }
                        }
                    }
                }
            })
        }
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        assertFalse(
            scene.sendPointerEvent(PointerEventType.Move, Offset(11f, 10f))
                .anyMovementConsumed
        )
        assertFalse(
            scene.sendPointerEvent(PointerEventType.Release, Offset(12f, 10f))
                .anyMovementConsumed
        )

        consumeAll = true

        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        assertTrue(
            scene.sendPointerEvent(PointerEventType.Move, Offset(11f, 10f))
                .anyMovementConsumed
        )
        assertTrue(
            scene.sendPointerEvent(PointerEventType.Release, Offset(12f, 10f))
                .anyMovementConsumed
        )
    }

    @Test
    fun cancelAllPointersShouldCancelInputCoroutines() = runComposeSceneTest { scene ->
        var cancellationsCount = 0
        scene.setContent {
            Box(modifier = Modifier.fillMaxSize().onCancel {
                cancellationsCount++
            })
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        scene.cancelPointerInput()

        assertEquals(1, cancellationsCount)
    }

    @Test
    fun dragScrollIsAppliedSynchronously() = runComposeSceneTest { scene ->
        val scrollState = ScrollState(0)
        var observedScroll = 0
        scene.setContent {
            LaunchedEffect(Unit) {
                snapshotFlow { scrollState.value }.collect { observedScroll = it }
            }
            Box(Modifier.size(100.dp).verticalScroll(scrollState)) {
                Box(Modifier.size(200.dp))
            }
        }

        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            pointers = listOf(touch(50f, 50f, pressed = true))
        )
        testScheduler.advanceUntilIdle()

        resetInvalidations()
        scene.sendPointerEvent(
            eventType = PointerEventType.Move,
            pointers = listOf(touch(50f, 10f, pressed = true))
        )

        assertNotEquals(0, scrollState.value)
        assertEquals(
            scrollState.value,
            observedScroll,
            "the scroll must be applied within sendPointerEvent"
        )
        assertTrue(
            scene.hasInvalidations() && invalidateTotal > 0,
            "the scroll must invalidate the scene within sendPointerEvent"
        )
    }

    @Test
    fun scrollWheelIsAppliedSynchronously() = runComposeSceneTest { scene ->
        val scrollState = ScrollState(0)
        var observedScroll = 0
        scene.setContent {
            LaunchedEffect(Unit) {
                snapshotFlow { scrollState.value }.collect { observedScroll = it }
            }
            Box(Modifier.size(100.dp).verticalScroll(scrollState)) {
                Box(Modifier.size(200.dp))
            }
        }
        testScheduler.advanceUntilIdle()

        resetInvalidations()
        scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = Offset(50f, 50f),
            scrollDelta = Offset(0f, 40f)
        )

        assertNotEquals(0, scrollState.value)
        assertEquals(
            scrollState.value,
            observedScroll,
            "the scroll must be applied within sendPointerEvent"
        )
        assertTrue(
            scene.hasInvalidations() && invalidateTotal > 0,
            "the scroll must invalidate the scene within sendPointerEvent"
        )
    }

    @Test
    fun cancelAllPointersShouldCancelClicks() = runComposeSceneTest { scene ->
        var clicksCount = 0
        scene.setContent {
            Box(modifier = Modifier.fillMaxSize().clickable {
                clicksCount++
            })
        }

        // Perform first click
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        scene.sendPointerEvent(PointerEventType.Release, Offset(40f, 40f))

        // Start and cancel click
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        scene.cancelPointerInput()
        scene.sendPointerEvent(PointerEventType.Release, Offset(40f, 40f))

        // Perform second click
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        scene.sendPointerEvent(PointerEventType.Release, Offset(40f, 40f))

        // Should be only two clicks
        assertEquals(2, clicksCount)
    }
}

class ComposeSceneTestScope(private val testScope: TestScope) {
    val testScheduler by testScope::testScheduler

    var invalidateLayout = 0
    var invalidateDraw = 0

    val invalidateTotal get() = invalidateLayout + invalidateDraw

    fun resetInvalidations() {
        invalidateLayout = 0
        invalidateDraw = 0
    }
}

private fun runComposeSceneTest(
    size: IntSize = IntSize(100, 100),
    block: suspend ComposeSceneTestScope.(scene: ComposeScene) -> Unit,
) = runTest(StandardTestDispatcher()) {
    val frameRecomposer = FrameRecomposer(coroutineContext)
    val testScope = ComposeSceneTestScope(this)
    CanvasLayersComposeScene(
        frameRecomposer = frameRecomposer,
        size = size,
        invalidateLayout = { testScope.invalidateLayout++ },
        invalidateDraw = { testScope.invalidateDraw++ },
    ).use {
        testScope.block(it)
        testScope.resetInvalidations()
    }
    PlatformLayersComposeScene(
        frameRecomposer = frameRecomposer,
        size = size,
        invalidateLayout = { testScope.invalidateLayout++ },
        invalidateDraw = { testScope.invalidateDraw++ },
    ).use {
        testScope.block(it)
        testScope.resetInvalidations()
    }
    frameRecomposer.close()
}

internal fun Modifier.onCancel(onCancel: () -> Unit) = this then TestCancellable(onCancel)

private class TestCancellable(
    private val onCancel: () -> Unit
) : ModifierNodeElement<CancellableNode>() {
    override fun create() = CancellableNode(onCancel)
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = false
    override fun update(node: CancellableNode) { node.onCancel = onCancel }
}

private class CancellableNode(
    var onCancel: () -> Unit
): DelegatingNode(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {}

    override fun onCancelPointerInput() {
        onCancel()
    }
}
