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

package org.jetbrains.compose.benchmarks.data

/** Benchmark cases with stable IDs used by the launcher and reports. */
enum class BenchmarkCase(val id: String) {
    AnimatedVisibility("AnimatedVisibility"),
    LazyGrid("LazyGrid"),
    LazyGridItemLaunchedEffect("LazyGrid-ItemLaunchedEffect"),
    LazyGridSmoothScroll("LazyGrid-SmoothScroll"),
    LazyGridSmoothScrollItemLaunchedEffect("LazyGrid-SmoothScroll-ItemLaunchedEffect"),
    VisualEffects("VisualEffects"),
    LazyList("LazyList"),
    MultipleComponents("MultipleComponents"),
    MultipleComponentsNoVectorGraphics("MultipleComponents-NoVectorGraphics"),
    TextLayout("TextLayout"),
    CanvasDrawing("CanvasDrawing"),
    HeavyShader("HeavyShader"),
}
