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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.xr.runtime.CoreState
import androidx.xr.runtime.internal.HandJointType
import androidx.xr.runtime.internal.Trackable as RuntimeTrackable
import androidx.xr.runtime.internal.TrackingState
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.testing.FakeRuntime
import androidx.xr.runtime.testing.FakeRuntimeFactory
import androidx.xr.runtime.testing.FakeRuntimeHand
import androidx.xr.runtime.testing.FakeRuntimePlane
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerceptionStateExtenderTest {

    private val handJointBufferSize: Int = 728
    private val tolerance = 1e-4f
    private lateinit var fakeRuntime: FakeRuntime
    private lateinit var timeSource: TestTimeSource
    private lateinit var underTest: PerceptionStateExtender

    @Before
    fun setUp() {
        fakeRuntime = FakeRuntimeFactory().createRuntime(Activity())
        timeSource = fakeRuntime.lifecycleManager.timeSource
        underTest = PerceptionStateExtender()
    }

    @After
    fun tearDown() {
        underTest.close()
    }

    @Test
    fun extend_notInitialized_throwsIllegalStateException(): Unit = runBlocking {
        val coreState = CoreState(timeSource.markNow())

        assertFailsWith<IllegalStateException> { underTest.extend(coreState) }
    }

    @Test
    fun extend_withOneState_addsAllTrackablesToTheCollection(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)

        val timeMark = timeSource.markNow()
        val coreState = CoreState(timeMark)
        val runtimeTrackable: RuntimeTrackable = FakeRuntimePlane()
        fakeRuntime.perceptionManager.addTrackable(runtimeTrackable)

        // act
        underTest.extend(coreState)

        // assert
        val perceptionState = coreState.perceptionState!!
        assertThat(perceptionState.trackables).hasSize(1)
        assertThat(convertTrackable(perceptionState.trackables.last())).isEqualTo(runtimeTrackable)
    }

    @Test
    fun extend_withTwoStates_updatesAllTrackablesInTheirCollection(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)
        fakeRuntime.perceptionManager.addTrackable(FakeRuntimePlane())
        underTest.extend(CoreState(timeSource.markNow()))

        timeSource += 10.milliseconds
        val timeMark = timeSource.markNow()
        fakeRuntime.perceptionManager.trackables.clear()
        val runtimeTrackable = FakeRuntimePlane()
        fakeRuntime.perceptionManager.addTrackable(runtimeTrackable)
        val coreState = CoreState(timeMark)

        // act
        underTest.extend(coreState)

        // assert
        val perceptionState = coreState.perceptionState!!
        assertThat(perceptionState.trackables).hasSize(1)
        assertThat(convertTrackable(perceptionState.trackables.last())).isEqualTo(runtimeTrackable)
    }

    @Test
    fun extend_withTwoStates_trackableStatusUpdated(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)
        val runtimeTrackable = FakeRuntimePlane()
        fakeRuntime.perceptionManager.addTrackable(runtimeTrackable)
        val coreState = CoreState(timeSource.markNow())
        underTest.extend(coreState)
        check(
            coreState.perceptionState!!.trackables.last().state.value.trackingState ==
                TrackingState.Tracking
        )

        // act
        timeSource += 10.milliseconds
        runtimeTrackable.trackingState = TrackingState.Stopped
        val coreState2 = CoreState(timeSource.markNow())
        underTest.extend(coreState2)

        // assert
        assertThat(coreState2.perceptionState!!.trackables.last().state.value.trackingState)
            .isEqualTo(TrackingState.Stopped)
    }

    @Test
    fun extend_withTwoStates_handStatesUpdated(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)
        val coreState = CoreState(timeSource.markNow())
        underTest.extend(coreState)
        check(coreState.perceptionState!!.leftHand != null)
        check(coreState.perceptionState!!.rightHand != null)
        check(
            coreState.perceptionState!!.leftHand!!.state.value.trackingState !=
                TrackingState.Tracking
        )
        check(coreState.perceptionState!!.leftHand!!.state.value.handJoints.isEmpty())
        check(
            coreState.perceptionState!!.rightHand!!.state.value.trackingState !=
                TrackingState.Tracking
        )
        check(coreState.perceptionState!!.rightHand!!.state.value.handJoints.isEmpty())

        // act
        timeSource += 10.milliseconds
        val handJoints: Map<HandJointType, Pose> =
            HandJointType.values().associate { joint ->
                val i = joint.ordinal.toFloat()
                joint to
                    Pose(
                        Vector3(i + 0.5f, i + 0.6f, i + 0.7f),
                        Quaternion(i + 0.1f, i + 0.2f, i + 0.3f, i + 0.4f),
                    )
            }

        val leftRuntimeHand = fakeRuntime.perceptionManager.leftHand!! as FakeRuntimeHand
        val rightRuntimeHand = fakeRuntime.perceptionManager.rightHand!! as FakeRuntimeHand
        leftRuntimeHand.trackingState = TrackingState.Tracking
        leftRuntimeHand.handJointsBuffer = generateTestBuffer(handJoints)
        rightRuntimeHand.trackingState = TrackingState.Tracking
        rightRuntimeHand.handJointsBuffer = generateTestBuffer(handJoints)
        val coreState2 = CoreState(timeSource.markNow())
        underTest.extend(coreState2)

        // assert
        assertThat(coreState2.perceptionState!!.leftHand!!.state.value.trackingState)
            .isEqualTo(TrackingState.Tracking)
        assertThat(coreState2.perceptionState!!.rightHand!!.state.value.trackingState)
            .isEqualTo(TrackingState.Tracking)
        for (jointType in HandJointType.values()) {
            val leftHandJoints = coreState2.perceptionState!!.leftHand!!.state.value.handJoints
            val rightHandJoints = coreState2.perceptionState!!.rightHand!!.state.value.handJoints
            assertThat(leftHandJoints[jointType]!!.translation)
                .isEqualTo(handJoints[jointType]!!.translation)
            assertRotationEquals(
                leftHandJoints[jointType]!!.rotation,
                handJoints[jointType]!!.rotation
            )
            assertThat(rightHandJoints[jointType]!!.translation)
                .isEqualTo(handJoints[jointType]!!.translation)
            assertRotationEquals(
                rightHandJoints[jointType]!!.rotation,
                handJoints[jointType]!!.rotation
            )
        }
    }

    @Test
    fun extend_perceptionStateMapSizeExceedsMax(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)
        val timeMark = timeSource.markNow()
        val coreState = CoreState(timeMark)

        // act
        underTest.extend(coreState)
        // make sure the perception state is added to the map at the beginning
        check(coreState.perceptionState != null)

        for (i in 1..PerceptionStateExtender.MAX_PERCEPTION_STATE_EXTENSION_SIZE) {
            timeSource += 10.milliseconds
            underTest.extend(CoreState(timeSource.markNow()))
        }

        // assert
        assertThat(coreState.perceptionState).isNull()
    }

    @Test
    fun close_cleanUpData(): Unit = runBlocking {
        // arrange
        underTest.initialize(fakeRuntime)
        val timeMark = timeSource.markNow()
        val coreState = CoreState(timeMark)
        underTest.extend(coreState)
        // make sure the perception state is added to the map at the beginning
        check(coreState.perceptionState != null)

        // act
        underTest.close()

        // assert
        assertThat(coreState.perceptionState).isNull()
    }

    private fun convertTrackable(trackable: Trackable<Trackable.State>): RuntimeTrackable {
        return when (trackable) {
            is Plane -> trackable.runtimePlane
            else ->
                throw IllegalArgumentException("Unsupported trackable type: ${trackable.javaClass}")
        }
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
