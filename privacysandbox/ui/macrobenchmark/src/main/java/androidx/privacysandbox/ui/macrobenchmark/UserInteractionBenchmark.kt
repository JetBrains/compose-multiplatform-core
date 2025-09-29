/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.privacysandbox.ui.macrobenchmark

import android.content.Intent
import android.os.Bundle
import android.view.ViewConfiguration
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.privacysandbox.ui.macrobenchmark.testapp.sdkproviderutils.FragmentOptions
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.testutils.SdkSandboxCrossProcessLatencyMetric
import junit.framework.TestCase.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** User interaction Macrobenchmark tests. */
@LargeTest
@RunWith(Parameterized::class)
class UserInteractionBenchmark(
    private val ciTestConfigType: String,
    private val uiFramework: String,
    private val zOrder: String,
) {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    private lateinit var viewConfiguration: ViewConfiguration

    @Before
    fun setup() {
        // avoid tests for backcompat mode
        assumeFalse(ciTestConfigType == CONFIG_TYPE_COMPAT)

        viewConfiguration =
            ViewConfiguration.get(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun testFlings() =
        benchmarkMeasureRepeated(eventName = "flingEventTransfer") {
            device
                .findObject(By.res(SSV_ID))
                .fling(Direction.DOWN, /* speed= */ viewConfiguration.scaledMaximumFlingVelocity)
            device.waitForIdle()

            device
                .findObject(By.res(SSV_ID))
                .fling(Direction.UP, /* speed= */ viewConfiguration.scaledMinimumFlingVelocity)
            device.waitForIdle()
        }

    @Test
    fun testScrolls() =
        benchmarkMeasureRepeated(eventName = "scrollEventTransfer") {
            device
                .findObject(By.res(SSV_ID))
                .scroll(Direction.DOWN, /* percent= */ 0.6f, /* speed= */ 50)
            device.waitForIdle()

            device
                .findObject(By.res(SSV_ID))
                .scroll(Direction.UP, /* percent= */ 0.6f, /* speed= */ 20)
            device.waitForIdle()
        }

    @OptIn(ExperimentalMetricApi::class) // FrameTimingMetric with processSuffix is experimental
    private fun benchmarkMeasureRepeated(
        eventName: String,
        testBlock: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = APP_PKG_NAME,
            metrics =
                listOf(
                    FrameTimingMetric(), // for app process
                    FrameTimingMetric(
                        processNameSuffix = SDK_SANDBOX_SUFFIX
                    ), // for sandbox process
                    SdkSandboxCrossProcessLatencyMetric(
                        beginPointName = "ContentView#onTouchEvent",
                        endPointName = "BinderAdapterDelegate#notifyMotionEvent",
                        eventName = eventName,
                    ),
                ),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait(
                    intent = getIntentForUserInteractionsFragment(uiFramework, zOrder)
                )
                val remoteLayout =
                    device.wait(
                        Until.findObject(By.descContains(REMOTE_VIEW_DESC)),
                        REMOTE_VIEW_RENDERED_TIMEOUT,
                    )
                assertNotNull("Failed to find remote view layout", remoteLayout)

                testBlock()
            },
        )
    }

    private fun getIntentForUserInteractionsFragment(uiFramework: String, zOrder: String): Intent {
        val intent =
            InstrumentationRegistry.getInstrumentation()
                .context
                .packageManager
                .getLaunchIntentForPackage(APP_PKG_NAME)!!
        val configBundle = Bundle()
        configBundle.putString(
            FragmentOptions.KEY_FRAGMENT,
            FragmentOptions.FRAGMENT_USER_INTERACTIONS,
        )
        configBundle.putString(FragmentOptions.KEY_UI_FRAMEWORK, uiFramework)
        configBundle.putString(FragmentOptions.KEY_Z_ORDER, zOrder)
        intent.putExtras(configBundle)
        return intent
    }

    companion object {
        const val APP_PKG_NAME = "androidx.privacysandbox.ui.macrobenchmark.testapp.target"
        const val SSV_ID = "$APP_PKG_NAME:id/ad_layout"
        const val CONFIG_TYPE_COMPAT = "PRIVACY_SANDBOX_COMPAT"
        const val REMOTE_VIEW_RENDERED_TIMEOUT = 3000L
        const val REMOTE_VIEW_DESC = "scrollable_animation_ad"
        const val SDK_SANDBOX_SUFFIX = "_sdk_sandbox"

        /** Parameterized test config type and z-order. */
        @Parameterized.Parameters(name = "{0}, uiFramework={1}, z-order={2}")
        @JvmStatic
        fun params(): List<Array<String>> {
            val configTypes =
                listOf(
                    InstrumentationRegistry.getArguments()
                        .getString("androidx.testConfigType", "LOCAL_RUN")
                )
            val uiFrameworks =
                listOf(FragmentOptions.UI_FRAMEWORK_COMPOSE, FragmentOptions.UI_FRAMEWORK_VIEW)
            val zOrders = listOf(FragmentOptions.Z_ORDER_ABOVE, FragmentOptions.Z_ORDER_BELOW)
            val params = mutableListOf<Array<String>>()
            for (configType in configTypes) {
                for (uiFrameworkValue in uiFrameworks) {
                    for (zOrderValue in zOrders) {
                        params.add(arrayOf(configType, uiFrameworkValue, zOrderValue))
                    }
                }
            }
            return params
        }
    }
}
