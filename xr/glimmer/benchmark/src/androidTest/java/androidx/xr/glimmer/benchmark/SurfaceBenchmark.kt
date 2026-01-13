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

package androidx.xr.glimmer.benchmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.testutils.LayeredComposeTestCase
import androidx.compose.testutils.ToggleableTestCase
import androidx.compose.testutils.benchmark.ComposeBenchmarkRule
import androidx.compose.testutils.doFramesUntilNoChangesPending
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.surface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SurfaceBenchmark {

    @get:Rule val benchmarkRule = ComposeBenchmarkRule()
    @get:Rule val glimmerRule = GlimmerRule()

    /**
     * Measures the time to do composition, measure, layout and draw to render the first frame
     * immediately after the `surface` modifier is applied.
     */
    @Test
    fun surface_firstFrame() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()
                        // Add surface as modifier
                        getTestCase().toggleState()
                    }

                    doFrame()

                    runWithMeasurementDisabled { disposeContent() }
                }
            }
        }
    }

    /** Measures the time to draw the first frame for `surface` that is added as modifier. */
    @Test
    fun surface_firstDraw() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()
                        // Add surface as modifier
                        getTestCase().toggleState()

                        recompose()
                        requestLayout()
                        measure()
                        layout()
                        drawPrepare()
                    }

                    draw()

                    runWithMeasurementDisabled {
                        drawFinish()
                        disposeContent()
                    }
                }
            }
        }
    }

    /**
     * Measures the time to do composition, measure, layout and draw to render the second frame
     * after focus animation started. This captures the frame cost of the `surface` focus animation
     * starting.
     */
    @Test
    fun surface_secondFrameFocusAnimation() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()

                        // Add surface as modifier
                        getTestCase().toggleState()

                        // Animation with progress == 0 (not expensive)
                        doFrame()
                    }

                    doFrame()

                    runWithMeasurementDisabled { disposeContent() }
                }
            }
        }
    }

    /**
     * Measures the time to draw the second frame after focus animation is started. This captures
     * the frame cost of the `surface` focus animation starting.
     */
    @Test
    fun surface_secondDrawFocusAnimation() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()

                        // Add surface as modifier
                        getTestCase().toggleState()

                        // Animation with progress == 0 (not expensive)
                        doFrame()

                        recompose()
                        requestLayout()
                        measure()
                        layout()
                        drawPrepare()
                    }

                    draw()

                    runWithMeasurementDisabled {
                        drawFinish()
                        disposeContent()
                    }
                }
            }
        }
    }

    /**
     * Measures the time to do composition, measure, layout and draw for the third frame after the
     * focus animation started. This captures the frame cost of the `surface` animation continuing.
     */
    @Test
    fun surface_thirdFrameFocusAnimation() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()

                        // Add surface as modifier
                        getTestCase().toggleState()

                        // Animation with progress == 0 (not expensive)
                        doFrame()

                        // First frame of animation.
                        doFrame()
                    }

                    doFrame()

                    runWithMeasurementDisabled { disposeContent() }
                }
            }
        }
    }

    /**
     * Measures the time to draw for third frame after the focus animation is started. This captures
     * the frame cost of the surface animation continuing.
     */
    @Test
    fun surface_thirdDrawFocusAnimation() {
        with(benchmarkRule) {
            runBenchmarkFor({ SurfaceTestCase() }) {
                measureRepeatedOnUiThread {
                    runWithMeasurementDisabled {
                        doFramesUntilNoChangesPending()

                        // Add surface as modifier
                        getTestCase().toggleState()

                        // Animation with progress == 0 (not expensive)
                        doFrame()

                        // First frame of animation.
                        doFrame()

                        recompose()
                        requestLayout()
                        measure()
                        layout()
                        drawPrepare()
                    }

                    draw()

                    runWithMeasurementDisabled {
                        drawFinish()
                        disposeContent()
                    }
                }
            }
        }
    }
}

private class SurfaceTestCase : LayeredComposeTestCase(), ToggleableTestCase {

    private val addSurfaceModifier = mutableStateOf(false)

    @Composable
    override fun MeasuredContent() {
        Box(
            modifier =
                Modifier.size(100.dp)
                    .then(if (addSurfaceModifier.value) Modifier.surface() else Modifier)
        )
    }

    override fun toggleState() {
        addSurfaceModifier.value = !addSurfaceModifier.value
    }

    @Composable
    override fun ContentWrappers(content: @Composable () -> Unit) {
        GlimmerTheme { content() }
    }
}
