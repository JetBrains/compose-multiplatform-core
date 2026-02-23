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

package androidx.compose.material3.benchmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.LayeredComposeTestCase
import androidx.compose.testutils.ToggleableTestCase
import androidx.compose.testutils.benchmark.ComposeBenchmarkRule
import androidx.compose.testutils.benchmark.benchmarkToFirstPixel
import androidx.compose.testutils.benchmark.toggleStateBenchmarkComposeMeasureLayout
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class ScaffoldBenchmark {

    @get:Rule val benchmarkRule = ComposeBenchmarkRule()

    @Test
    fun scaffoldWithoutTopBar_first_pixel() {
        benchmarkRule.benchmarkToFirstPixel { ScaffoldTestCase(hasTopBar = false) }
    }

    @Test
    fun scaffoldWithTopBar_first_pixel() {
        benchmarkRule.benchmarkToFirstPixel { ScaffoldTestCase(hasTopBar = true) }
    }

    @Test
    fun scaffoldWithTopBar_toggleTopBarSize() {
        benchmarkRule.toggleStateBenchmarkComposeMeasureLayout({
            ScaffoldTestCase(hasTopBar = true)
        })
    }

    private class ScaffoldTestCase(private val hasTopBar: Boolean) :
        LayeredComposeTestCase(), ToggleableTestCase {

        var appbarSize by mutableStateOf(TopAppBarDefaults.LargeAppBarCollapsedHeight)

        @Composable
        override fun MeasuredContent() {
            Scaffold(
                topBar = {
                    if (hasTopBar) {
                        Box(Modifier.fillMaxWidth().height(appbarSize))
                    }
                }
            ) { contentPadding ->
                Box(Modifier.padding(contentPadding).fillMaxSize())
            }
        }

        override fun toggleState() {
            appbarSize =
                if (appbarSize == TopAppBarDefaults.LargeAppBarCollapsedHeight) {
                    TopAppBarDefaults.LargeAppBarExpandedHeight
                } else {
                    TopAppBarDefaults.LargeAppBarCollapsedHeight
                }
        }
    }
}
