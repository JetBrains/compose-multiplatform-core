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

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package androidx.compose.ui.window

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.FrameRecomposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Explicit host frames: these tests do not depend on requestAnimationFrame timing or sleeps. */
class WebMotionDurationScaleTest {
    @Test
    fun initialReducedMotionFinishesTheFirstAnimationOnTheFirstFrame() = runTest {
        withRecomposer(initialReducedMotion = true) { _, preferences, host, composition ->
            val animation = Animatable(0f)
            var finished = false
            var effectScale: MotionDurationScale? = null
            composition.setContent {
                LaunchedEffect(Unit) {
                    effectScale = currentCoroutineContext()[MotionDurationScale]
                    animation.animateTo(1f, tween(1000, easing = LinearEasing))
                    finished = true
                }
            }
            host.frame(0)
            assertSame(preferences.motionDurationScale, effectScale)
            assertEquals(1f, animation.value)
            assertTrue(finished)
        }
    }

    @Test
    fun inFlightFiniteAnimationFinishesAfterQueuedFrameAndMotionCanBeRestored() = runTest {
        withRecomposer { media, _, host, composition ->
            val animation = Animatable(0f)
            var finished = false
            composition.setContent {
                LaunchedEffect(Unit) {
                    animation.animateTo(1f, tween(1000, easing = LinearEasing))
                    finished = true
                }
            }
            host.frame(0)
            host.frame(250)
            assertEquals(0.25f, animation.value, 0.001f)
            media.changeTo(true)
            host.frame(266)
            // SuspendAnimation reads the scale before awaiting a frame. That already queued
            // frame can use the old value; the next frame must finish without stopping delivery.
            assertEquals(0.266f, animation.value, 0.001f)
            host.frame(282)
            assertEquals(1f, animation.value)
            assertTrue(finished)

            media.changeTo(false)
            val restoredAnimation = Animatable(0f)
            composition.setContent {
                LaunchedEffect(restoredAnimation) {
                    restoredAnimation.animateTo(1f, tween(1000, easing = LinearEasing))
                }
            }
            host.frame(500)
            host.frame(750)
            assertTrue(restoredAnimation.value > 0f && restoredAnimation.value < 1f)
        }
    }

    @Test
    fun infiniteTransitionResumesAfterWaitingAtZeroScale() = runTest {
        withRecomposer { media, preferences, host, composition ->
            var animation: State<Float>? = null
            composition.setContent {
                animation = rememberInfiniteTransition().animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing))
                )
            }
            host.frame(0)
            host.frame(250)
            val state = assertNotNull(animation)
            assertEquals(0.25f, state.value, 0.001f)

            media.changeTo(true)
            host.frame(266)
            assertEquals(1f, state.value)
            // Leave it suspended in snapshotFlow, not merely between consecutive frames.
            host.frame(500)
            assertEquals(1f, state.value)

            media.changeTo(false)
            host.frame(600)
            host.frame(850)
            assertEquals(1f, preferences.motionDurationScale.scaleFactor)
            assertEquals(0.25f, state.value, 0.001f)
            assertSame(state, animation)
        }
    }

    private fun TestScope.withRecomposer(
        initialReducedMotion: Boolean = false,
        block: (TestMediaQuery, WebPreferencesObserver, FrameRecomposer, Composition) -> Unit
    ) {
        val media = testMediaQuery(initialReducedMotion)
        val preferences = WebPreferencesObserver(
            document.createElement("div"), TestFontSizeProbe(), media.media
        )
        val host = FrameRecomposer(
            StandardTestDispatcher(testScheduler) + preferences.motionDurationScale
        )
        val composition = Composition(NoOpApplier(), host.compositionContext)
        try {
            block(media, preferences, host, composition)
        } finally {
            composition.dispose()
            host.close()
            preferences.dispose()
            runCurrent()
        }
    }

    private fun FrameRecomposer.frame(timeMillis: Long) {
        performFrame(timeMillis * 1_000_000L)
        performFrameDispatch()
    }

    private class NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun onClear() {}
    }
}
