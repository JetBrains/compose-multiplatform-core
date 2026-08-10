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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SchedulingDispatcherFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the resilient-mode recovery path: an exception thrown out of a composable is captured
 * in the recomposer's error state ([androidx.compose.runtime.Recomposer.setResilientModeEnabled],
 * turned on for every scene by [ComposeSceneRecomposer]) and recovered from with a scene-scoped
 * hot reload, instead of escaping the recomposer coroutine and freezing the scene.
 */
class ComposeSceneErrorRecoveryTest {
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
    fun recoversFromExceptionThrownDuringRecomposition() {
        var compositions = 0
        // A plain field, not snapshot state: a snapshot write made from the failing composition
        // would be rolled back together with the abandoned composition, re-arming the throw on
        // every retry and turning the test into a crash loop.
        var shouldThrow = false
        val trigger = mutableStateOf(0)
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                content = {
                    compositions++
                    trigger.value
                    if (shouldThrow) {
                        shouldThrow = false
                        error("Simulated composition failure")
                    }
                    Box(Modifier.fillMaxSize())
                },
            )
        try {
            scene.render()
            assertEquals(1, compositions)

            shouldThrow = true
            trigger.value++
            // The recomposition scheduled by the write above throws during this frame; the error
            // is captured in the recomposer's errorState instead of escaping render(), and the
            // recovery collector reloads the scene's content within the same render pass - so the
            // content is composed a third time (the failed attempt plus the recovery).
            scene.render(nanoTime = 16_000_000L)
            assertEquals(3, compositions, "the content must be composed again after recovery")

            // The recovery must settle: no further reloads on error-free frames.
            scene.render(nanoTime = 32_000_000L)
            scene.render(nanoTime = 48_000_000L)
            assertEquals(3, compositions)

            // The scene must stay interactive: a state change still triggers recomposition.
            trigger.value++
            scene.render(nanoTime = 64_000_000L)
            assertEquals(4, compositions)
        } finally {
            scene.close()
        }
    }

    @Test
    fun errorFreeScenesAreNotReloaded() {
        // Regression test: errorState is a StateFlow and replays its current value, so the
        // recovery collector used to fire on the initial `null` at scene construction (and again
        // on the `null` written back by resetErrorState()), discarding all remembered state.
        var rememberInitializations = 0
        var compositions = 0
        val trigger = mutableStateOf(0)
        val scene =
            ImageComposeScene(
                width = 100,
                height = 100,
                content = {
                    compositions++
                    trigger.value
                    remember { rememberInitializations++ }
                    Box(Modifier.fillMaxSize())
                },
            )
        try {
            scene.render()
            trigger.value++
            scene.render(nanoTime = 16_000_000L)
            scene.render(nanoTime = 32_000_000L)

            assertEquals(2, compositions)
            assertEquals(
                1,
                rememberInitializations,
                "remembered state must survive - nothing threw, so nothing may be reloaded",
            )
        } finally {
            scene.close()
        }
    }
}
