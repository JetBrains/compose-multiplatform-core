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

import androidx.annotation.RestrictTo
import androidx.xr.runtime.HandTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.Hand as RuntimeHand
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contains the tracking information of one of the user's hands. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class Hand internal constructor(internal val runtimeHand: RuntimeHand) : Updatable {
    /** * Companion object holding info to the left and right hands. */
    public companion object {
        /**
         * Returns the Hand object that corresponds to the user's left hand when available.
         *
         * @param session the currently active [Session].
         * @throws [IllegalStateException] if [HandTrackingMode] is set to Disabled.
         */
        @JvmStatic
        public fun left(session: Session): Hand? {
            val perceptionStateExtender = getPerceptionStateExtender(session)
            val config = perceptionStateExtender.xrResourcesManager.lifecycleManager.config
            check(config.handTracking != HandTrackingMode.Disabled) {
                "Config.HandTrackingMode is set to Disabled."
            }
            return perceptionStateExtender.xrResourcesManager.leftHand
        }

        /**
         * Returns the Hand object that corresponds to the user's right hand when available.
         *
         * @param session the currently active [Session].
         * @throws [IllegalStateException] if [HandTrackingMode] is set to Disabled.
         */
        @JvmStatic
        public fun right(session: Session): Hand? {
            val perceptionStateExtender = getPerceptionStateExtender(session)
            val config = perceptionStateExtender.xrResourcesManager.lifecycleManager.config
            check(config.handTracking != HandTrackingMode.Disabled) {
                "Config.HandTrackingMode is set to Disabled."
            }
            return perceptionStateExtender.xrResourcesManager.rightHand
        }

        private fun getPerceptionStateExtender(session: Session): PerceptionStateExtender {
            val perceptionStateExtender: PerceptionStateExtender? =
                session.stateExtenders.filterIsInstance<PerceptionStateExtender>().first()
            check(perceptionStateExtender != null) { "PerceptionStateExtender is not available." }
            return perceptionStateExtender
        }
    }

    /**
     * The representation of the current state of [Hand].
     *
     * @param trackingState the current [TrackingState] of the hand.
     * @param handJointsBuffer the [ByteBuffer] containing the pose of each joint in the hand.
     */
    public class State(
        public val trackingState: TrackingState,
        public val handJointsBuffer: ByteBuffer,
    ) {

        private class JointsMap(
            val trackingState: TrackingState,
            val handJointsBuffer: ByteBuffer
        ) : Map<HandJointType, Pose> {
            override val entries: Set<Map.Entry<HandJointType, Pose>>
                get() =
                    if (trackingState == TrackingState.Tracking) {
                        RuntimeHand.parseHandJoint(trackingState, handJointsBuffer).entries.toSet()
                    } else {
                        emptySet()
                    }

            override val keys: Set<HandJointType>
                get() =
                    if (trackingState == TrackingState.Tracking) HandJointType.values().toSet()
                    else emptySet()

            override val size: Int
                get() = keys.size

            override val values: Collection<Pose>
                get() = entries.map { it.value }

            override fun containsKey(key: HandJointType): Boolean {
                return keys.contains(key)
            }

            override fun containsValue(value: Pose): Boolean {
                return values.contains(value)
            }

            override fun get(key: HandJointType): Pose? =
                if (trackingState == TrackingState.Tracking) locateHandJointFromBuffer(key)
                else null

            override fun isEmpty(): Boolean {
                return trackingState != TrackingState.Tracking
            }

            private fun locateHandJointFromBuffer(handJointType: HandJointType): Pose {
                val buffer = handJointsBuffer.duplicate().order(ByteOrder.nativeOrder())
                val bytePerPose = 7 * 4
                val byteOffset = handJointType.ordinal * bytePerPose
                buffer.position(byteOffset)
                val qx = buffer.float
                val qy = buffer.float
                val qz = buffer.float
                val qw = buffer.float
                val px = buffer.float
                val py = buffer.float
                val pz = buffer.float
                return Pose(Vector3(px, py, pz), Quaternion(qx, qy, qz, qw))
            }
        }

        /**
         * Returns the current pose of each joint in the hand.
         *
         * @return a map of [HandJointType] to [Pose] representing the current pose of each joint in
         *   the hand.
         */
        public val handJoints: Map<HandJointType, Pose> = JointsMap(trackingState, handJointsBuffer)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false
            return trackingState == other.trackingState &&
                handJointsBuffer == other.handJointsBuffer
        }

        override fun hashCode(): Int {
            var result = trackingState.hashCode()
            result = 31 * result + handJointsBuffer.hashCode()
            return result
        }
    }

    private val _state =
        MutableStateFlow<State>(State(TrackingState.Paused, ByteBuffer.allocate(0)))
    /** The current [State] of this hand. */
    public val state: StateFlow<State> = _state.asStateFlow()

    override suspend fun update() {
        _state.emit(State(runtimeHand.trackingState, runtimeHand.handJointsBuffer))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hand) return false
        return runtimeHand == other.runtimeHand
    }

    override fun hashCode(): Int = runtimeHand.hashCode()
}
