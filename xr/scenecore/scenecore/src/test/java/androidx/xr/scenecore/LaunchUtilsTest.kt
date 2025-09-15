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
import androidx.xr.scenecore.internal.ActivitySpace as RtActivitySpace
import androidx.xr.scenecore.internal.JxrPlatformAdapter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchUtilsTest {
    private val fakeRuntimeFactory = FakePerceptionRuntimeFactory()
    private val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
    private val activity: ComponentActivity = activityController.create().start().get()
    private val mockPlatformAdapter = mock<JxrPlatformAdapter>()
    private lateinit var session: Session

    @Before
    fun setUp() {
        // A minimal setup is needed to create a Session instance.
        // The session needs access to the mockPlatformAdapter.
        val mockActivitySpace = mock<RtActivitySpace>()
        whenever(mockPlatformAdapter.activitySpace).thenReturn(mockActivitySpace)
        whenever(mockPlatformAdapter.activitySpaceRootImpl).thenReturn(mockActivitySpace)
        whenever(mockPlatformAdapter.mainPanelEntity).thenReturn(mock())
        whenever(mockPlatformAdapter.spatialEnvironment).thenReturn(mock())
        whenever(mockPlatformAdapter.perceptionSpaceActivityPose).thenReturn(mock())
        session =
            Session(
                activity,
                runtimes = listOf(fakeRuntimeFactory.createRuntime(activity), mockPlatformAdapter),
            )
    }

    @Test
    fun configureBundleForFullSpaceMode_Launch_callsThrough() {
        // Test that Session calls into the runtime.
        val bundle = Bundle().apply { putString("testkey", "testval") }
        whenever(mockPlatformAdapter.setFullSpaceMode(any())).thenReturn(bundle)
        @Suppress("UNUSED_VARIABLE")
        val unused = createBundleForFullSpaceModeLaunch(session, bundle)
        verify(mockPlatformAdapter).setFullSpaceMode(bundle)
    }

    @Test
    fun configureBundleForFullSpaceModeLaunchWithEnvironmentInherited_callsThrough() {
        // Test that Session calls into the runtime.
        val bundle = Bundle().apply { putString("testkey", "testval") }
        whenever(mockPlatformAdapter.setFullSpaceModeWithEnvironmentInherited(any()))
            .thenReturn(bundle)
        @Suppress("UNUSED_VARIABLE")
        val unused = createBundleForFullSpaceModeLaunchWithEnvironmentInherited(session, bundle)
        verify(mockPlatformAdapter).setFullSpaceModeWithEnvironmentInherited(bundle)
    }
}
