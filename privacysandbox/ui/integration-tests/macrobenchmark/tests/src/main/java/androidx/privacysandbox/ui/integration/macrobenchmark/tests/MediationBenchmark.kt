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

package androidx.privacysandbox.ui.integration.macrobenchmark.tests

import android.content.Intent
import android.os.Bundle
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.testutils.SdkSandboxCrossProcessLatencyMetric
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Mediation CUJs Macrobenchmark tests. */
@LargeTest
@RunWith(Parameterized::class)
class MediationBenchmark(@Suppress("unused") private val ciTestConfigType: String) {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun nonMediatedAdRender() =
        benchmarkRule.measureRepeated(
            packageName = APP_PKG_NAME,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onSessionOpened",
                        "sessionOpened",
                    ),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onUiDisplayed",
                        "uiDisplay",
                    ),
                ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait(getIntentForTestAppLaunch(NO_MEDIATION_INTENT_EXTRA_VALUE))

            device.wait(Until.findObject(By.clazz(WEBVIEW_CLASS_NAME)), NON_REFRESHADS_TIMEOUT_MS)
        }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun mediateeInRuntimeAdRender() =
        benchmarkRule.measureRepeated(
            packageName = APP_PKG_NAME,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onSessionOpened",
                        "mediatorSessionOpened",
                    ),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onSessionOpened",
                        "mediateeSessionOpened",
                        occurrenceOfBeginTrace =
                            SdkSandboxCrossProcessLatencyMetric.TraceOccurrence.LAST,
                        occurrenceOfEndTrace =
                            SdkSandboxCrossProcessLatencyMetric.TraceOccurrence.LAST,
                    ),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onUiDisplayed",
                        "uiDisplay",
                    ),
                ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait(getIntentForTestAppLaunch(RE_MEDIATION_INTENT_EXTRA_VALUE))

            device.wait(Until.findObject(By.clazz(WEBVIEW_CLASS_NAME)), NON_REFRESHADS_TIMEOUT_MS)
        }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun mediateeInAppAdRender() =
        benchmarkRule.measureRepeated(
            packageName = APP_PKG_NAME,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onSessionOpened",
                        "sessionOpened",
                    ),
                    SdkSandboxCrossProcessLatencyMetric(
                        "checkClientOpenSession",
                        "onUiDisplayed",
                        "uiDisplay",
                    ),
                ),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait(getIntentForTestAppLaunch(INAPP_MEDIATION_INTENT_EXTRA_VALUE))

            device.wait(Until.findObject(By.clazz(WEBVIEW_CLASS_NAME)), NON_REFRESHADS_TIMEOUT_MS)
        }

    private fun getIntentForTestAppLaunch(mediationOption: String): Intent {
        val intent =
            InstrumentationRegistry.getInstrumentation()
                .context
                .packageManager
                .getLaunchIntentForPackage(APP_PKG_NAME)
        val configBundle = Bundle()
        addAdTypeAndFragmentIntentValues(configBundle)
        configBundle.putString(MEDIATION_INTENT_EXTRA_KEY, mediationOption)
        intent?.putExtras(configBundle)
        return intent!!
    }

    private fun addAdTypeAndFragmentIntentValues(configBundle: Bundle) {
        configBundle.putString(AD_TYPE_INTENT_EXTRA_KEY, WEBVIEW_AD_TYPE_INTENT_EXTRA_VALUE)
        configBundle.putString(FRAGMENT_INTENT_EXTRA_KEY, RESIZE_FRAGMENT_INTENT_EXTRA_VALUE)
    }

    companion object {
        /** Constants. */
        const val APP_PKG_NAME =
            "androidx.privacysandbox.ui.integration.macrobenchmark.testapp.target"
        const val MEDIATION_INTENT_EXTRA_KEY = "mediation"
        const val AD_TYPE_INTENT_EXTRA_KEY = "ad-type"
        const val FRAGMENT_INTENT_EXTRA_KEY = "fragment"

        const val NO_MEDIATION_INTENT_EXTRA_VALUE = "non-mediated"
        const val RE_MEDIATION_INTENT_EXTRA_VALUE = "in-runtime-with-overlay"
        const val INAPP_MEDIATION_INTENT_EXTRA_VALUE = "in-app"

        const val WEBVIEW_AD_TYPE_INTENT_EXTRA_VALUE = "basic-webview"
        const val RESIZE_FRAGMENT_INTENT_EXTRA_VALUE = "resize"

        const val NON_REFRESHADS_TIMEOUT_MS = 3000L

        const val WEBVIEW_CLASS_NAME = "android.webkit.Webview"

        /** Add test config type (main or compat) to test name. */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun params(): List<String> =
            listOf(
                InstrumentationRegistry.getArguments()
                    .getString("androidx.testConfigType", "LOCAL_RUN")
            )
    }
}
