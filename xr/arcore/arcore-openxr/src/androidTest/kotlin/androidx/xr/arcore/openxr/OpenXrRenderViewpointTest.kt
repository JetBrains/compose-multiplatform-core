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

// TODO(b/494286565) - Remove deprecation suppression when androidx.xr.runtime.FieldOfView is
// removed.
@file:Suppress("DEPRECATION")

package androidx.xr.arcore.openxr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.xr.runtime.FieldOfView as OldFieldOfView
import androidx.xr.runtime.math.FieldOfView
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// TODO - b/382119583: Remove the @SdkSuppress annotation once "androidx.xr.arcore.openxr.test"
// supports a
// lower SDK version.
@SdkSuppress(minSdkVersion = 29)
@LargeTest
@RunWith(AndroidJUnit4::class)
class OpenXrRenderViewpointTest {
    private lateinit var underTest: OpenXrRenderViewpoint

    @Before
    fun setUp() {
        underTest = OpenXrRenderViewpoint()
    }

    @Test
    fun update_alignWithState() {
        val viewCameraState =
            ViewCameraState(
                Pose(Vector3(1f, 2f, 3f), Quaternion(1f, 2f, 3f, 4f)),
                FieldOfView(45.0f, 45.0f, 30.0f, 30.0f),
            )
        check(underTest.pose == Pose())
        check(convertToNewFieldOfView(underTest.fieldOfView) == FieldOfView(0f, 0f, 0f, 0f))

        underTest.update(viewCameraState)

        assertThat(underTest.pose).isEqualTo(viewCameraState.pose)

        assertThat(convertToNewFieldOfView(underTest.fieldOfView))
            .isEqualTo(viewCameraState.fieldOfView)
    }

    // TODO(b/494286565) - Remove this function when androidx.xr.runtime.FieldOfView is removed.
    private fun convertToNewFieldOfView(fov: OldFieldOfView): FieldOfView {
        return FieldOfView(fov.angleLeft, fov.angleRight, fov.angleUp, fov.angleDown)
    }
}
