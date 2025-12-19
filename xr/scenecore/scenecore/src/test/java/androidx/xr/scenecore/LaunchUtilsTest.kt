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

package androidx.xr.scenecore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.xr.arcore.testing.FakePerceptionRuntimeFactory
import androidx.xr.runtime.Session
import androidx.xr.scenecore.runtime.SceneRuntime
import androidx.xr.scenecore.testing.FakeSceneRuntimeFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [org.robolectric.annotation.Config.TARGET_SDK])
class LaunchUtilsTest {
    private val fakeRuntimeFactory = FakePerceptionRuntimeFactory()
    private val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
    private val activity: ComponentActivity = activityController.create().start().get()
    private lateinit var fakeSceneRuntime: SceneRuntime
    private lateinit var session: Session

    @Before
    fun setUp() {
        val fakeSceneRuntimeFactory = FakeSceneRuntimeFactory()
        fakeSceneRuntime = fakeSceneRuntimeFactory.create(activity)
        session =
            Session(
                activity,
                runtimes = listOf(fakeRuntimeFactory.createRuntime(activity), fakeSceneRuntime),
            )
    }

    @Test
    fun configureBundleForFullSpaceMode_Launch_callsThrough() {
        // Test that Session calls into the runtime.
        val bundle = Bundle().apply { putString("testkey", "testval") }
        @Suppress("UNUSED_VARIABLE")
        val result = createBundleForFullSpaceModeLaunch(session, bundle)

        assertThat(result).isEqualTo(bundle)
    }

    @Test
    fun configureBundleForFullSpaceModeLaunchWithEnvironmentInherited_callsThrough() {
        // Test that Session calls into the runtime.
        val bundle = Bundle().apply { putString("testkey", "testval") }
        @Suppress("UNUSED_VARIABLE")
        val result = createBundleForFullSpaceModeLaunchWithEnvironmentInherited(session, bundle)

        assertThat(result).isEqualTo(bundle)
    }
}
