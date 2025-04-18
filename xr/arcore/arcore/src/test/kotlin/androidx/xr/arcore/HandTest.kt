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

package androidx.xr.arcore

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.xr.runtime.Config
import androidx.xr.runtime.HandTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakePerceptionManager
import androidx.xr.runtime.testing.FakeRuntimeAnchor
import androidx.xr.runtime.testing.FakeRuntimeHand
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HandTest {

    private val handJointBufferSize: Int = 728
    private val tolerance = 1e-4f
    private lateinit var xrResourcesManager: XrResourcesManager
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var session: Session

    @get:Rule
    val grantPermissionRule =
        GrantPermissionRule.grant(
            "android.permission.SCENE_UNDERSTANDING_COARSE",
            "android.permission.HAND_TRACKING",
        )

    @Before
    fun setUp() {
        xrResourcesManager = XrResourcesManager()
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        FakeRuntimeAnchor.anchorsCreated = 0
    }

    @After
    fun tearDown() {
        xrResourcesManager.clear()
    }

    @Test
    fun left_returnsLeftHand() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
                check(Hand.left(session) != null)
                check(Hand.left(session)!!.state.value.trackingState != TrackingState.Tracking)
                check(Hand.left(session)!!.state.value.handJoints.isEmpty())

                val leftRuntimeHand = perceptionManager.leftHand!! as FakeRuntimeHand
                leftRuntimeHand.trackingState = TrackingState.Tracking
                val expectedHandJoints: Map<HandJointType, Pose> =
                    HandJointType.values().associate { joint ->
                        val i = joint.ordinal.toFloat()
                        joint to
                            Pose(
                                Vector3(i + 0.5f, i + 0.6f, i + 0.7f),
                                Quaternion(i + 0.1f, i + 0.2f, i + 0.3f, i + 0.4f),
                            )
                    }
                leftRuntimeHand.handJointsBuffer = generateTestBuffer(expectedHandJoints)
                awaitNewCoreState(session, testScope)

                assertThat(Hand.left(session)!!.state.value.trackingState)
                    .isEqualTo(TrackingState.Tracking)
                for (jointType in HandJointType.values()) {
                    val leftHandJoints = Hand.left(session)!!.state.value.handJoints
                    assertThat(leftHandJoints[jointType]!!.translation)
                        .isEqualTo(expectedHandJoints[jointType]!!.translation)
                    assertRotationEquals(
                        leftHandJoints[jointType]!!.rotation,
                        expectedHandJoints[jointType]!!.rotation,
                    )
                }
            }
        }

    @Test
    fun left_handTrackingDisabled_throwsIllegalStateException() =
        createTestSessionAndRunTest(testDispatcher) {
            session.configure(Config(handTracking = HandTrackingMode.Disabled))

            assertFailsWith<IllegalStateException> { Hand.left(session) }
        }

    @Test
    fun right_returnsRightHand() =
        createTestSessionAndRunTest(testDispatcher) {
            runTest(testDispatcher) {
                val perceptionManager = session.runtime.perceptionManager as FakePerceptionManager
                check(Hand.right(session) != null)
                check(Hand.right(session)!!.state.value.trackingState != TrackingState.Tracking)
                check(Hand.right(session)!!.state.value.handJoints.isEmpty())

                val rightRuntimeHand = perceptionManager.rightHand!! as FakeRuntimeHand
                rightRuntimeHand.trackingState = TrackingState.Tracking
                val expectedHandJoints: Map<HandJointType, Pose> =
                    HandJointType.values().associate { joint ->
                        val i = joint.ordinal.toFloat()
                        joint to
                            Pose(
                                Vector3(i + 0.5f, i + 0.6f, i + 0.7f),
                                Quaternion(i + 0.1f, i + 0.2f, i + 0.3f, i + 0.4f),
                            )
                    }
                rightRuntimeHand.handJointsBuffer = generateTestBuffer(expectedHandJoints)
                awaitNewCoreState(session, testScope)

                assertThat(Hand.right(session)!!.state.value.trackingState)
                    .isEqualTo(TrackingState.Tracking)
                for (jointType in HandJointType.values()) {
                    val rightHandJoints = Hand.right(session)!!.state.value.handJoints
                    assertThat(rightHandJoints[jointType]!!.translation)
                        .isEqualTo(expectedHandJoints[jointType]!!.translation)
                    assertRotationEquals(
                        rightHandJoints[jointType]!!.rotation,
                        expectedHandJoints[jointType]!!.rotation,
                    )
                }
            }
        }

    @Test
    fun right_handTrackingDisabled_throwsIllegalStateException() =
        createTestSessionAndRunTest(testDispatcher) {
            session.configure(Config(handTracking = HandTrackingMode.Disabled))

            assertFailsWith<IllegalStateException> { Hand.right(session) }
        }

    @Test
    fun update_stateMachesRuntimeHand() = runBlocking {
        val runtimeHand = FakeRuntimeHand()
        val underTest = Hand(runtimeHand)
        check(underTest.state.value.trackingState != TrackingState.Tracking)
        check(underTest.state.value.handJoints.isEmpty())

        runtimeHand.trackingState = TrackingState.Tracking
        val expectedHandJoints: Map<HandJointType, Pose> =
            HandJointType.values().associate { joint ->
                val i = joint.ordinal.toFloat()
                joint to
                    Pose(
                        Vector3(i + 0.5f, i + 0.6f, i + 0.7f),
                        Quaternion(i + 0.1f, i + 0.2f, i + 0.3f, i + 0.4f),
                    )
            }
        runtimeHand.handJointsBuffer = generateTestBuffer(expectedHandJoints)
        underTest.update()

        assertThat(underTest.state.value.trackingState).isEqualTo(TrackingState.Tracking)
        for (jointType in HandJointType.values()) {
            val handJoints = underTest.state.value.handJoints
            assertThat(handJoints[jointType]!!.translation)
                .isEqualTo(expectedHandJoints[jointType]!!.translation)
            assertRotationEquals(
                handJoints[jointType]!!.rotation,
                expectedHandJoints[jointType]!!.rotation,
            )
        }
    }

    private fun createTestSessionAndRunTest(
        coroutineDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
        testBody: () -> Unit,
    ) {
        ActivityScenario.launch(Activity::class.java).use {
            it.onActivity { activity ->
                session =
                    (Session.create(activity, coroutineDispatcher) as SessionCreateSuccess).session
                xrResourcesManager.lifecycleManager = session.runtime.lifecycleManager

                testBody()
            }
        }
    }

    /** Resumes and pauses the session just enough to emit a new CoreState. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitNewCoreState(session: Session, testScope: TestScope) {
        session.resume()
        testScope.advanceUntilIdle()
        session.pause()
    }

    fun generateTestBuffer(handJoints: Map<HandJointType, Pose>): ByteBuffer {
        val buffer = ByteBuffer.allocate(handJointBufferSize).order(ByteOrder.nativeOrder())

        repeat(26) {
            val handJointType = HandJointType.values()[it]
            val handJointPose = handJoints[handJointType]!!
            buffer.putFloat(handJointPose.rotation.x)
            buffer.putFloat(handJointPose.rotation.y)
            buffer.putFloat(handJointPose.rotation.z)
            buffer.putFloat(handJointPose.rotation.w)
            buffer.putFloat(handJointPose.translation.x)
            buffer.putFloat(handJointPose.translation.y)
            buffer.putFloat(handJointPose.translation.z)
        }

        buffer.flip()
        return buffer
    }

    fun assertRotationEquals(actual: Quaternion, expected: Quaternion) {
        assertThat(actual.x).isWithin(tolerance).of(expected.x)
        assertThat(actual.y).isWithin(tolerance).of(expected.y)
        assertThat(actual.z).isWithin(tolerance).of(expected.z)
        assertThat(actual.w).isWithin(tolerance).of(expected.w)
    }
}
