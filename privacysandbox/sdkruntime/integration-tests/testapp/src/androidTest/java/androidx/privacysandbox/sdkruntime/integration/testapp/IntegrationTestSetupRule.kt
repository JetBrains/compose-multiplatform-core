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

package androidx.privacysandbox.sdkruntime.integration.testapp

import androidx.core.os.BuildCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.testutils.withActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource

/** Setup and cleanup for SdkRuntime integration tests */
class IntegrationTestSetupRule : ExternalResource() {

    lateinit var activityScenario: ActivityScenario<TestMainActivity>

    val isCompatRun: Boolean by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets.list("") ?: emptyArray()
        assets.contains("RuntimeEnabledSdkTable.xml") // Present only in compat runs
    }

    val uiDevice: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    override fun before() {
        activityScenario = ActivityScenario.launch(TestMainActivity::class.java)
    }

    override fun after() {
        try {
            activityScenario.withActivity<TestMainActivity, Unit> { api.resetTestState() }
        } finally {
            activityScenario.close()
        }
    }

    suspend fun testAppApi() = activity().api

    /**
     * Some features available in non-compat case only since particular AdServices module version.
     *
     * This method helps to test case when feature is AVAILABLE (either because of compat run or
     * because of supported by AdService module)
     */
    fun assumeCompatRunOrAdServicesVersionAtLeast(version: Int) {
        assumeTrue(isCompatRun || BuildCompat.AD_SERVICES_EXTENSION_INT >= version)
    }

    /**
     * Some features available in non-compat case only since particular AdServices module version.
     *
     * This method helps to test case when feature is NOT AVAILABLE (because of sandbox run with
     * AdService module version not supporting this feature )
     */
    fun assumeSandboxRunAndAdServicesVersionBelow(version: Int) {
        assumeTrue(!isCompatRun && BuildCompat.AD_SERVICES_EXTENSION_INT < version)
    }

    private suspend fun activity(): TestMainActivity = suspendCancellableCoroutine {
        activityScenario.onActivity { activity -> it.resume(activity) }
    }
}
