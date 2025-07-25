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

package androidx.compose.material3.adaptive

import android.content.res.Configuration
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.window.layout.WindowMetrics
import androidx.window.testing.layout.WindowMetricsCalculatorRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class CurrentWindowSizeTest {
    private val composeRule = createComposeRule()
    private val windowMetricsCalculatorRule = WindowMetricsCalculatorRule()

    @get:Rule
    val rule: TestRule = RuleChain.outerRule(windowMetricsCalculatorRule).around(composeRule)

    @Test
    fun test_currentWindowSize() {
        var actualWindowSize: IntSize = IntSize.Zero

        val mockWindowSize = mutableStateOf(MockWindowSize1)

        composeRule.setContent {
            val testConfiguration = Configuration(LocalConfiguration.current)
            testConfiguration.screenWidthDp = mockWindowSize.value.width
            testConfiguration.screenHeightDp = mockWindowSize.value.height
            windowMetricsCalculatorRule.overrideWindowSize(mockWindowSize)
            CompositionLocalProvider(LocalConfiguration provides testConfiguration) {
                actualWindowSize = currentWindowSize()
            }
        }

        composeRule.runOnIdle { assertThat(actualWindowSize).isEqualTo(MockWindowSize1) }

        mockWindowSize.value = MockWindowSize2

        composeRule.runOnIdle { assertThat(actualWindowSize).isEqualTo(MockWindowSize2) }
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun test_currentWindowDpSize() {
        var actualWindowSize: DpSize = DpSize.Zero

        val mockWindowSize = mutableStateOf(MockWindowSize1)

        composeRule.setContent {
            val testConfiguration = Configuration(LocalConfiguration.current)
            testConfiguration.screenWidthDp = mockWindowSize.value.width
            testConfiguration.screenHeightDp = mockWindowSize.value.height
            windowMetricsCalculatorRule.overrideWindowSize(mockWindowSize)
            CompositionLocalProvider(
                LocalConfiguration provides testConfiguration,
                LocalDensity provides MockWindowDensity
            ) {
                actualWindowSize = currentWindowDpSize()
            }
        }

        composeRule.runOnIdle {
            assertThat(actualWindowSize)
                .isEqualTo(with(MockWindowDensity) { MockWindowSize1.toSize().toDpSize() })
        }

        mockWindowSize.value = MockWindowSize2

        composeRule.runOnIdle {
            assertThat(actualWindowSize)
                .isEqualTo(with(MockWindowDensity) { MockWindowSize2.toSize().toDpSize() })
        }
    }
}

internal fun WindowMetricsCalculatorRule.overrideWindowSize(mockWindowSize: State<IntSize>) {
    overrideCurrentWindowBounds(
        WindowMetrics(
            Rect(0, 0, mockWindowSize.value.width, mockWindowSize.value.height),
            MockWindowDensity.density
        )
    )
}

private val MockWindowSize1 = IntSize(1000, 600)
private val MockWindowSize2 = IntSize(800, 400)
private val MockWindowDensity = Density(2.5F)
