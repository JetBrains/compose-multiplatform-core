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

package androidx.compose.ui.platform

import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.test.SchedulingDispatcherFixture
import androidx.compose.ui.unit.IntSize
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * A resilient [FrameRecomposer] recovers from a composition error by reloading its compositions.
 * Content that fails again on every reload must not be reloaded without end: after
 * [MaxConsecutiveFailedReloads] failed reloads the error leaves the host through its exception
 * handler, as it would without resilient mode.
 */
class FrameRecomposerResilienceTest {
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
    fun givesUpReloadingContentThatKeepsFailing() = runTest(StandardTestDispatcher()) {
        val unhandled = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> unhandled += e }
        var compositions = 0
        val frameRecomposer = FrameRecomposer(coroutineContext + handler)
        CanvasLayersComposeScene(
            frameRecomposer = frameRecomposer,
            size = IntSize(100, 100),
        ).use { scene ->
            scene.setContent {
                compositions++
                error("broken content")
            }
            testScheduler.advanceUntilIdle()
        }
        frameRecomposer.close()

        assertEquals(
            1 + MaxConsecutiveFailedReloads,
            compositions,
            "the first composition and each reload compose once, then no more",
        )
        assertEquals(listOf("broken content"), unhandled.map { it.message })
    }

    @Test
    fun aNonResilientHostLetsTheErrorEscapeAtOnce() = runTest(StandardTestDispatcher()) {
        var compositions = 0
        val frameRecomposer = FrameRecomposer(coroutineContext, resilient = false)
        val error =
            CanvasLayersComposeScene(
                frameRecomposer = frameRecomposer,
                size = IntSize(100, 100),
            ).use { scene ->
                kotlin.test.assertFailsWith<IllegalStateException> {
                    scene.setContent {
                        compositions++
                        error("broken content")
                    }
                }
            }
        frameRecomposer.close()

        assertEquals("broken content", error.message)
        assertEquals(1, compositions)
    }
}
