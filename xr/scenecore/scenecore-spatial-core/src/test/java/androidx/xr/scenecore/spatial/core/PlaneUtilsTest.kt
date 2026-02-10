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
package androidx.xr.scenecore.spatial.core

import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.testing.math.assertRotation
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PlaneUtilsTest {
    @Test
    fun rotateEntityToPlane_rotatesToPlan() {
        // Moving from (0f, 0f, 0f, 1f) int the common space to (0f, 0f, 0f, 1f) in the plane
        // rotation results in an updated rotation of (-0.707f, 0f, 0f, 0.707f). This quaternion
        // represents a 90-degree rotation around the x-axis Which is expected when the panel is
        // rotated into the plane's reference space.
        val planeRotation = Quaternion(0f, 0f, 0f, 1f)
        val proposedRotation = Quaternion(0f, 0f, 0f, 1f)
        val updatedRotation = PlaneUtils.rotateEntityToPlane(proposedRotation, planeRotation)
        val expectedRotation = Quaternion(-0.707f, 0f, 0f, 0.707f)

        assertRotation(updatedRotation, expectedRotation)
    }
}
