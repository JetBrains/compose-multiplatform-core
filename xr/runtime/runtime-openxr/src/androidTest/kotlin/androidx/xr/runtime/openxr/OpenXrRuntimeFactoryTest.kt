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

package androidx.xr.runtime.openxr

import android.app.Activity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.xr.runtime.internal.RuntimeFactory
import com.google.common.truth.Truth.assertThat
import java.util.ServiceLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// TODO - b/382119583: Remove the @SdkSuppress annotation once "androidx.xr.runtime.openxr.test"
// supports a
// lower SDK version.
@SdkSuppress(minSdkVersion = 29)
@LargeTest
@RunWith(AndroidJUnit4::class)
class OpenXrRuntimeFactoryTest {

    companion object {
        init {
            System.loadLibrary("androidx.xr.runtime.openxr.test")
        }
    }

    @get:Rule val activityRule = ActivityScenarioRule(Activity::class.java)

    @Test
    fun class_isDiscoverableViaServiceLoader() {
        assertThat(ServiceLoader.load(RuntimeFactory::class.java).iterator().next())
            .isInstanceOf(OpenXrRuntimeFactory::class.java)
    }

    @Test
    fun createRuntime_createsOpenXrRuntime() {
        activityRule.scenario.onActivity {
            val underTest = OpenXrRuntimeFactory()

            assertThat(underTest.createRuntime(it)).isInstanceOf(OpenXrRuntime::class.java)
        }
    }
}
