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

package androidx.xr.scenecore

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.xr.arcore.Anchor
import androidx.xr.runtime.Session as PerceptionSession
import androidx.xr.runtime.internal.AnchorEntity as RtAnchorEntity
import androidx.xr.runtime.internal.JxrPlatformAdapter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * An AnchorEntity is created to track a Pose relative to some position or surface in the "Real
 * World." Children of this Entity will remain positioned relative to that location in the real
 * world, for the purposes of creating Augmented Reality experiences.
 *
 * Note that Anchors are only relative to the "real world", and not virtual environments. Also,
 * calling setParent() on an AnchorEntity has no effect, as the parenting of an Anchor is controlled
 * by the system.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class AnchorEntity
private constructor(rtEntity: RtAnchorEntity, entityManager: EntityManager) :
    BaseEntity<RtAnchorEntity>(rtEntity, entityManager) {
    private val state = AtomicReference(rtEntity.state.fromRtState())

    private var onStateChangedListener: OnStateChangedListener? = null

    /** Specifies the current tracking state of the Anchor. */
    @Target(AnnotationTarget.TYPE)
    @IntDef(State.ANCHORED, State.UNANCHORED, State.TIMEDOUT, State.ERROR)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class StateValue

    public object State {
        /**
         * The ANCHORED state means that this Anchor is being actively tracked and updated by the
         * perception stack. The application should expect children to maintain their relative
         * positioning to the system's best understanding of a pose in the real world.
         */
        public const val ANCHORED: Int = 0
        /**
         * An UNANCHORED state could mean that the perception stack hasn't found an anchor for this
         * Space, that it has lost tracking.
         */
        public const val UNANCHORED: Int = 1
        /**
         * The AnchorEntity timed out while searching for an underlying anchor. This it is not
         * possible to recover the AnchorEntity.
         */
        public const val TIMEDOUT: Int = 2
        /**
         * The ERROR state means that something has gone wrong and this AnchorEntity is invalid
         * without the possibility of recovery.
         */
        public const val ERROR: Int = 3
    }

    /** Specifies the current persist state of the Anchor. */
    public enum class PersistState {
        /** The anchor hasn't been requested to persist. */
        PERSIST_NOT_REQUESTED,
        /** The anchor is requested to persist but hasn't been persisted yet. */
        PERSIST_PENDING,
        /** The anchor is persisted successfully. */
        PERSISTED,
    }

    /** Returns the current tracking state for this AnchorEntity. */
    public fun getState(): @StateValue Int = state.get()

    /** Registers a listener callback to be issued when an anchor's state changes. */
    @Suppress("ExecutorRegistration")
    public fun setOnStateChangedListener(onStateChangedListener: OnStateChangedListener?) {
        this.onStateChangedListener = onStateChangedListener
        onStateChangedListener?.onStateChanged(state.get())
    }

    /** Updates the current state. */
    private fun setState(newState: @StateValue Int) {
        state.set(newState)
        onStateChangedListener?.onStateChanged(newState)
    }

    /**
     * Loads the ARCore for XR Anchor using a Jetpack XR Runtime session.
     *
     * @param session the Jetpack XR Runtime session to load the Anchor from.
     * @return the ARCore for XR Anchor corresponding to the native pointer.
     */
    // TODO(b/373711152) : Remove this method once the ARCore for XR API migration is done.
    public fun getAnchor(session: PerceptionSession): Anchor {
        return Anchor.loadFromNativePointer(session, rtEntity.nativePointer)
    }

    public companion object {
        private const val TAG = "AnchorEntity"

        /**
         * Factory method for AnchorEntity.
         *
         * @param adapter JxrPlatformAdapter to use.
         * @param bounds Bounds for this Anchor Entity.
         * @param planeType Orientation for the plane to which this Anchor should attach.
         * @param planeSemantic Semantics for the plane to which this Anchor should attach.
         * @param timeout Maximum time to search for the anchor, if a suitable plane is not found
         *   within the timeout time the AnchorEntity state will be set to TIMED_OUT.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            bounds: Dimensions,
            planeType: @PlaneTypeValue Int,
            planeSemantic: @PlaneSemanticValue Int,
            timeout: Duration = Duration.ZERO,
        ): AnchorEntity {
            val rtAnchorEntity =
                adapter.createAnchorEntity(
                    bounds.toRtDimensions(),
                    planeType.toRtPlaneType(),
                    planeSemantic.toRtPlaneSemantic(),
                    timeout,
                )
            return create(rtAnchorEntity, entityManager)
        }

        /**
         * Factory method for AnchorEntity.
         *
         * @param anchor Anchor to create an AnchorEntity for.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            anchor: Anchor,
        ): AnchorEntity =
            AnchorEntity.create(adapter.createAnchorEntity(anchor.runtimeAnchor), entityManager)

        /**
         * Factory method for AnchorEntity.
         *
         * @param rtAnchorEntity Runtime AnchorEntity instance.
         */
        internal fun create(
            rtAnchorEntity: RtAnchorEntity,
            entityManager: EntityManager,
        ): AnchorEntity {
            val anchorEntity = AnchorEntity(rtAnchorEntity, entityManager)
            rtAnchorEntity.setOnStateChangedListener { newRtState ->
                when (newRtState) {
                    RtAnchorEntity.State.UNANCHORED -> anchorEntity.setState(State.UNANCHORED)
                    RtAnchorEntity.State.ANCHORED -> anchorEntity.setState(State.ANCHORED)
                    RtAnchorEntity.State.TIMED_OUT -> anchorEntity.setState(State.TIMEDOUT)
                    RtAnchorEntity.State.ERROR -> anchorEntity.setState(State.ERROR)
                }
            }
            return anchorEntity
        }

        /**
         * Factory method for AnchorEntity.
         *
         * @param adapter JxrPlatformAdapter to use.
         * @param uuid UUID of the persisted Anchor Entity to create.
         * @param timeout Maximum time to search for the anchor, if a persisted anchor isn't located
         *   within the timeout time the AnchorEntity state will be set to TIMED_OUT.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            uuid: UUID,
            timeout: Duration = Duration.ZERO,
        ): AnchorEntity {
            val rtAnchorEntity = adapter.createPersistedAnchorEntity(uuid, timeout)
            val anchorEntity = AnchorEntity(rtAnchorEntity, entityManager)
            rtAnchorEntity.setOnStateChangedListener { newRtState ->
                when (newRtState) {
                    RtAnchorEntity.State.UNANCHORED -> anchorEntity.setState(State.UNANCHORED)
                    RtAnchorEntity.State.ANCHORED -> anchorEntity.setState(State.ANCHORED)
                    RtAnchorEntity.State.TIMED_OUT -> anchorEntity.setState(State.TIMEDOUT)
                    RtAnchorEntity.State.ERROR -> anchorEntity.setState(State.ERROR)
                }
            }
            return anchorEntity
        }

        /**
         * Public factory function for an AnchorEntity which searches for a location to create an
         * Anchor among the tracked planes available to the perception system.
         *
         * Note that this function will fail if the application has not been granted the
         * "android.permission.SCENE_UNDERSTANDING" permission. Consider using PermissionHelper to
         * help request permission from the User.
         *
         * @param session Session to create the AnchorEntity in.
         * @param bounds Bounds for this AnchorEntity.
         * @param planeType Orientation of plane to which this Anchor should attach.
         * @param planeSemantic Semantics of the plane to which this Anchor should attach.
         * @param timeout The amount of time as a [Duration] to search for the a suitable plane to
         *   attach to. If a plane is not found within the timeout, the returned AnchorEntity state
         *   will be set to AnchorEntity.State.TIMEDOUT. It may take longer than the timeout period
         *   before the anchor state is updated. If the timeout duration is zero it will search for
         *   the anchor indefinitely.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            session: Session,
            bounds: Dimensions,
            planeType: @PlaneTypeValue Int,
            planeSemantic: @PlaneSemanticValue Int,
            timeout: Duration = Duration.ZERO,
        ): AnchorEntity {
            return AnchorEntity.create(
                session.platformAdapter,
                session.entityManager,
                bounds,
                planeType,
                planeSemantic,
                timeout,
            )
        }

        /**
         * Public factory function for an AnchorEntity which uses an Anchor from ARCore for XR.
         *
         * @param session Session to create the AnchorEntity in.
         * @param anchor The PerceptionAnchor to use for this AnchorEntity.
         */
        @JvmStatic
        public fun create(session: Session, anchor: Anchor): AnchorEntity {
            return AnchorEntity.create(session.platformAdapter, session.entityManager, anchor)
        }
    }

    /** Extension function that converts [RtAnchorEntity.State] to [AnchorEntity.State]. */
    private fun Int.fromRtState() =
        when (this) {
            RtAnchorEntity.State.UNANCHORED -> State.UNANCHORED
            RtAnchorEntity.State.ANCHORED -> State.ANCHORED
            RtAnchorEntity.State.TIMED_OUT -> State.TIMEDOUT
            RtAnchorEntity.State.ERROR -> State.ERROR
            else -> throw IllegalArgumentException("Unknown state: $this")
        }

    /**
     * Registers a listener to be called when the Anchor moves relative to its underlying space.
     *
     * <p> The callback is triggered by any anchor movements such as those made by the underlying
     * perception stack to maintain the anchor's position relative to the real world. Any cached
     * data relative to the activity space or any other "space" should be updated when this callback
     * is triggered.
     *
     * @param listener The listener to register if non-null, else stops listening if null.
     * @param executor The executor to run the listener on. Defaults to SceneCore executor if null.
     */
    @JvmOverloads
    @Suppress("ExecutorRegistration")
    public fun setOnSpaceUpdatedListener(
        listener: OnSpaceUpdatedListener?,
        executor: Executor? = null,
    ) {
        rtEntity.setOnSpaceUpdatedListener(listener?.let { { it.onSpaceUpdated() } }, executor)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun interface OnStateChangedListener {
    public fun onStateChanged(newState: @AnchorEntity.StateValue Int)
}
