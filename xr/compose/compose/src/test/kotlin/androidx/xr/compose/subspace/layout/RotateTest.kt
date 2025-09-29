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

package androidx.xr.compose.subspace.layout

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.compose.spatial.ApplicationSubspace
import androidx.xr.compose.subspace.SpatialBox
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.testing.SubspaceTestingActivity
import androidx.xr.compose.testing.assertRotationInRootIsEqualTo
import androidx.xr.compose.testing.onSubspaceNodeWithTag
import androidx.xr.compose.testing.setContentWithCompatibilityForXr
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [rotate] modifier. */
@RunWith(AndroidJUnit4::class)
class RotateTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<SubspaceTestingActivity>()

    @Test
    fun rotation_canApplySingleRotation() {
        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(
                    SubspaceModifier.testTag("panel").rotate(pitch = 90f, yaw = 0f, roll = 0f)
                ) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(Quaternion(0.70710677f, 0.0f, 0.0f, 0.70710677f))
    }

    @Test
    fun rotation_canRotateAcrossTwoAxis() {
        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(
                    SubspaceModifier.testTag("panel").rotate(Vector3(0.0f, 1.0f, 1.0f), 90.0f)
                ) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(Quaternion(0.0f, 0.49999997f, 0.49999997f, 0.70710677f))
    }

    @Test
    fun rotate_zeroRotation_appliesIdentity() {
        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(SubspaceModifier.testTag("panel").rotate(0f, 0f, 0f)) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(Quaternion.Identity)
    }

    @Test
    fun rotate_quaternionOverload_isAppliedCorrectly() {
        val rotation = Quaternion.fromAxisAngle(Vector3(1f, 1f, 0f).toNormalized(), 60f)

        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(SubspaceModifier.testTag("panel").rotate(rotation)) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule.onSubspaceNodeWithTag("panel").assertRotationInRootIsEqualTo(rotation)
    }

    @Test
    fun rotate_negativeAngles_areAppliedCorrectly() {
        val expectedRotation = Quaternion.fromEulerAngles(pitch = -90f, yaw = 0f, roll = -45f)

        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(
                    SubspaceModifier.testTag("panel").rotate(pitch = -90f, yaw = 0f, roll = -45f)
                ) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(expectedRotation)
    }

    @Test
    fun rotate_updatesWhenStateChanges() {
        var currentRotation by mutableStateOf(Quaternion.fromEulerAngles(pitch = 10f, 0f, 0f))

        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(SubspaceModifier.rotate(currentRotation).testTag("panel")) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(currentRotation)

        currentRotation = Quaternion.fromEulerAngles(pitch = 0f, yaw = -45f, roll = 0f)

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(currentRotation)
    }

    @Test
    fun rotate_chainedRotations_areComposed() {
        val rot1 = Quaternion.fromEulerAngles(pitch = 45f, yaw = 0f, roll = 0f)
        val rot2 = Quaternion.fromEulerAngles(pitch = 0f, yaw = 30f, roll = 0f)
        // The final rotation should be the dot product of the two.
        // Outer modifiers are applied after inner ones, so the order is rot1 * rot2.
        val expectedRotation = rot1 * rot2

        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialPanel(SubspaceModifier.testTag("panel").rotate(rot2).rotate(rot1)) {
                    Text(text = "Panel")
                }
            }
        }

        composeTestRule
            .onSubspaceNodeWithTag("panel")
            .assertRotationInRootIsEqualTo(expectedRotation)
    }

    @Test
    fun rotate_childRotation_isCombinedWithParentRotation() {
        val parentRot = Quaternion.fromEulerAngles(pitch = 30f, yaw = 0f, roll = 30f)
        val childRot = Quaternion.fromEulerAngles(pitch = 0f, yaw = 45f, roll = 0f)

        composeTestRule.setContentWithCompatibilityForXr {
            ApplicationSubspace {
                SpatialBox(SubspaceModifier.rotate(parentRot)) {
                    SpatialPanel(SubspaceModifier.rotate(childRot).testTag("child")) {
                        Text(text = "Panel")
                    }
                }
            }
        }

        // The rotate modifier combines rotations in `local * parent` order.
        val expectedRotation = childRot * parentRot

        composeTestRule
            .onSubspaceNodeWithTag("child")
            .assertRotationInRootIsEqualTo(expectedRotation)
    }
}
