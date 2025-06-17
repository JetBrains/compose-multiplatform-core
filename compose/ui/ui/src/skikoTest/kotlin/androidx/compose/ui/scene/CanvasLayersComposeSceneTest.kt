/*
 * Copyright 2024 The Android Open Source Project
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class CanvasLayersComposeSceneTest {

    @Test
    fun sceneSizeChangeTriggersInvalidation() = runTest(StandardTestDispatcher()) {
        var invalidationCount = 0
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
            invalidate = { invalidationCount++ }
        ).use { scene ->
            scene.setContent { Box(Modifier.fillMaxSize()) }

            assertEquals(1, invalidationCount)
            scene.size = IntSize(120, 120)
            assertEquals(2, invalidationCount)
        }
    }

    @Test
    fun cancelClickForGestureOwner() = runTest(StandardTestDispatcher()) {
        var rootCancelled = false
        var popupCancelled = false
        CanvasLayersComposeScene(
            size = IntSize(100, 100),
            coroutineContext = coroutineContext,
        ).use { scene ->
            scene.setContent {
                Box(modifier = Modifier.fillMaxSize().onCancel { rootCancelled = true })

                Dialog(onDismissRequest = {}, properties = DialogProperties()) {
                    Box(modifier = Modifier.fillMaxSize().onCancel { popupCancelled = true })
                }
            }

            scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
            scene.cancelPointerInput()

            assertFalse(rootCancelled)
            assertTrue(popupCancelled)
        }
    }
}