/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.material3.adaptive.navigationsuite

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(JUnit4::class)
class NavigationSuiteScaffoldTest {

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_compactWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(windowSizeClass = WindowSizeClass.compute(COMPACT, COMPACT))

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_compactWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(COMPACT, HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat())
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_compactWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(COMPACT, HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat())
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_mediumWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarMedium)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_mediumWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_mediumWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_expandedWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarMedium)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_expandedWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_expandedWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_extraLargeWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarMedium)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_extraLargeWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_extraLargeWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_tableTop_compact() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass = WindowSizeClass.compute(COMPACT, COMPACT),
                isTableTop = true
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_tableTop_medium() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    ),
                isTableTop = true
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarMedium)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_tableTop_expanded() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    ),
                isTableTop = true
            )

        assertThat(NavigationSuiteScaffoldDefaults.navigationSuiteType(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.ShortNavigationBarMedium)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_compactWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(windowSizeClass = WindowSizeClass.compute(COMPACT, COMPACT))

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_compactWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(COMPACT, HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat())
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_compactWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(COMPACT, HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat())
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_mediumWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_mediumWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_mediumWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_MEDIUM_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_expandedWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_expandedWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_expandedWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_extraLargeWidth_compactHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(), COMPACT)
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_extraLargeWidth_mediumHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_MEDIUM_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_extraLargeWidth_expandedHeight() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    )
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationRail)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_tableTop() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass = WindowSizeClass.compute(COMPACT, COMPACT),
                isTableTop = true
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    @Test
    @Suppress("DEPRECATION") // WindowSizeClass#compute is deprecated
    fun navigationLayoutTypeTest_calculateFromAdaptiveInfo_tableTop_expandedWidth() {
        val mockAdaptiveInfo =
            createMockAdaptiveInfo(
                windowSizeClass =
                    WindowSizeClass.compute(
                        WIDTH_DP_EXPANDED_LOWER_BOUND.toFloat(),
                        HEIGHT_DP_EXPANDED_LOWER_BOUND.toFloat()
                    ),
                isTableTop = true
            )

        assertThat(NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(mockAdaptiveInfo))
            .isEqualTo(NavigationSuiteType.NavigationBar)
    }

    private fun createMockAdaptiveInfo(
        windowSizeClass: WindowSizeClass,
        isTableTop: Boolean = false
    ): WindowAdaptiveInfo {
        return WindowAdaptiveInfo(windowSizeClass, Posture(isTabletop = isTableTop))
    }
}

private const val COMPACT = 400f
