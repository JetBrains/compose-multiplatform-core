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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import androidx.compose.ui.test.SchedulingDispatcherFixture

class ComposeSceneHotReloadTest {

    private val schedulingDispatcher = SchedulingDispatcherFixture()

    @BeforeTest
    fun installSchedulingDispatcher() {
        schedulingDispatcher.install()
    }

    @AfterTest
    fun uninstallSchedulingDispatcher() {
        schedulingDispatcher.uninstall()
    }

    @Test
    fun discardsStateAndComposesContentAgain() = runTest(StandardTestDispatcher()) {
        var compositions = 0
        var rememberInitializations = 0
        var disposals = 0
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
        ).use { scene ->
            scene.setContent {
                compositions++
                remember { rememberInitializations++ }
                DisposableEffect(Unit) {
                    onDispose { disposals++ }
                }
                Box(Modifier.fillMaxSize())
            }

            assertEquals(1, compositions)
            assertEquals(1, rememberInitializations)
            assertEquals(0, disposals)

            scene.simulateHotReload()

            assertEquals(2, compositions)
            assertEquals(2, rememberInitializations)
            assertEquals(1, disposals)
        }
    }

    @Test
    fun affectsOnlyItsOwnScene() = runTest(StandardTestDispatcher()) {
        var reloadedCompositions = 0
        var untouchedCompositions = 0
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
        ).use { reloadedScene ->
            CanvasLayersComposeScene(
                size = IntSize(100, 100),
                coroutineContext = coroutineContext,
            ).use { untouchedScene ->
                reloadedScene.setContent {
                    reloadedCompositions++
                    Box(Modifier.fillMaxSize())
                }
                untouchedScene.setContent {
                    untouchedCompositions++
                    Box(Modifier.fillMaxSize())
                }

                reloadedScene.simulateHotReload()

                assertEquals(2, reloadedCompositions)
                assertEquals(1, untouchedCompositions)
            }
        }
    }

    @Test
    fun contentStaysInteractiveAndInvalidatesAfterHotReload() = runTest(StandardTestDispatcher()) {
        var invalidationCount = 0
        var clicks = 0
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
            invalidate = { invalidationCount++ }
        ).use { scene ->
            scene.setContent {
                Box(Modifier.fillMaxSize().clickable { clicks++ })
            }
            scene.click()
            assertEquals(1, clicks)

            val invalidationsBeforeReload = invalidationCount
            scene.simulateHotReload()

            // The reload replaced the node tree, so a new frame has to be rendered.
            assertEquals(invalidationsBeforeReload + 1, invalidationCount)

            scene.click()
            assertEquals(2, clicks)
        }
    }

    @Test
    fun reloadsContentOfLayers() = runTest(StandardTestDispatcher()) {
        var dialogCompositions = 0
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
        ).use { scene ->
            scene.setContent {
                Dialog(onDismissRequest = {}, properties = DialogProperties()) {
                    dialogCompositions++
                    Box(Modifier.fillMaxSize())
                }
            }

            assertEquals(1, dialogCompositions)

            scene.simulateHotReload()

            assertEquals(2, dialogCompositions)
        }
    }
}

private fun ComposeScene.click(position: Offset = Offset(10f, 10f)) {
    sendPointerEvent(PointerEventType.Press, position)
    sendPointerEvent(PointerEventType.Release, position)
}
