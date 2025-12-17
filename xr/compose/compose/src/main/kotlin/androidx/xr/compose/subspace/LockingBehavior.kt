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

package androidx.xr.compose.subspace

import androidx.annotation.IntRange
import androidx.xr.arcore.ArDevice
import androidx.xr.compose.spatial.ExperimentalUserSubspaceApi
import androidx.xr.compose.subspace.layout.CoreGroupEntity
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.scene
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A LockingBehavior controls the motion of content as it is following (or "locked" to) another
 * entity, such as a user's head. Currently the options include "static", which does not
 * continuously follow the target, and "soft", which gradually catches up to the target.
 */
@ExperimentalUserSubspaceApi
public abstract class LockingBehavior internal constructor() {
    protected var targetCurrentPose: Pose = Pose.Identity

    internal abstract suspend fun configure(
        session: Session,
        trailingEntity: CoreGroupEntity,
        lockTo: BodyPart = BodyPart.Head,
        lockDimensions: LockDimensions = LockDimensions.All,
    )

    public companion object {
        public const val DEFAULT_SOFT_DURATION_MS: Int = 1500
        public const val MIN_SOFT_DURATION_MS: Long = 100

        /**
         * The content is placed once based on the user's initial pose and does not follow
         * subsequent movements.
         */
        public fun static(): LockingBehavior = StaticLockingBehavior()

        /**
         * Creates a behavior where the content smoothly animates to follow the user's movements,
         * creating a comfortable "soft follow" effect. This is implemented with the Hermite easing
         * algorithm, which accelerates the content then slows it down towards the end of the
         * motion, giving it a sense of real world physics. The use of the Hermite algorithm is not
         * optional but the total duration of the motion can be modified.
         *
         * @param durationMs Amount of milliseconds it takes for the content to catch up to the
         *   user. Default is `DEFAULT_SOFT_DURATION_MS` milliseconds. A value less than
         *   `MIN_SOFT_DURATION_MS` will be rounded up to `MIN_SOFT_DURATION_MS` to allow enough
         *   time to complete the content movement.
         * @return A [LockingBehavior] instance configured for soft locking.
         */
        public fun soft(
            @IntRange(from = MIN_SOFT_DURATION_MS) durationMs: Int = DEFAULT_SOFT_DURATION_MS
        ): LockingBehavior = SoftLockingBehavior(durationMs)
    }
}

/**
 * Creates a behavior where the content smoothly animates to follow the user's movements, creating a
 * comfortable "soft follow" effect. This is the implementation for SoftLocking which is accessible
 * through the public interface as LockingBehavior.soft()
 *
 * @param durationMs Amount of milliseconds it takes for the content to catch up to the user.
 *   Default is [DEFAULT_SOFT_DURATION_MS] milliseconds. A value less than [MIN_SOFT_DURATION_MS]
 *   will be rounded up to [MIN_SOFT_DURATION_MS] to allow enough time to complete the content
 *   movement.
 */
@OptIn(ExperimentalUserSubspaceApi::class)
internal class SoftLockingBehavior(private val durationMs: Int = DEFAULT_SOFT_DURATION_MS) :
    LockingBehavior() {

    private var currentAnimationJob: Job? = null
    private var trailingEntity: CoreGroupEntity? = null
    private var startPose: Pose = Pose.Identity
    private var endPose: Pose = Pose.Identity
    // Ensure at least one frame of animation
    private val totalFrames: Int =
        (durationMs / TIME_BETWEEN_ANIMATION_TICKS).toInt().coerceAtLeast(1)
    private var currentFrame: Int = 0

    override suspend fun configure(
        session: Session,
        trailingEntity: CoreGroupEntity,
        lockTo: BodyPart,
        lockDimensions: LockDimensions,
    ) = coroutineScope {
        this@SoftLockingBehavior.trailingEntity = trailingEntity
        if (lockTo != BodyPart.Head) return@coroutineScope

        // TODO(b/448689233): Initial head pose data is not reliable.
        // Skipping over it with delay.
        delay(1000)

        val initialPose = trailingEntity.poseInMeters

        val arDevice = ArDevice.getInstance(session)
        arDevice.state.collect { state ->
            val headPose =
                session.scene.perceptionSpace.transformPoseTo(
                    state.devicePose,
                    session.scene.activitySpace,
                )

            // Determine the target pose using the source pose but ignoring the
            // dimensions not specified in parameter lockDimensions.
            targetCurrentPose =
                Pose(
                    translation =
                        Vector3(
                            x =
                                getTrackedValue(
                                    lockDimensions.isTranslationXTracked,
                                    headPose.translation.x,
                                    initialPose.translation.x,
                                ),
                            y =
                                getTrackedValue(
                                    lockDimensions.isTranslationYTracked,
                                    headPose.translation.y,
                                    initialPose.translation.y,
                                ),
                            z =
                                getTrackedValue(
                                    lockDimensions.isTranslationZTracked,
                                    headPose.translation.z,
                                    initialPose.translation.z,
                                ),
                        ),
                    rotation =
                        Quaternion(
                            x =
                                getTrackedValue(
                                    lockDimensions.isRotationXTracked,
                                    headPose.rotation.x,
                                    initialPose.rotation.x,
                                ),
                            y =
                                getTrackedValue(
                                    lockDimensions.isRotationYTracked,
                                    headPose.rotation.y,
                                    initialPose.rotation.y,
                                ),
                            z =
                                getTrackedValue(
                                    lockDimensions.isRotationZTracked,
                                    headPose.rotation.z,
                                    initialPose.rotation.z,
                                ),
                            w = headPose.rotation.w,
                        ),
                )
            handleNewPose(this)
        }
    }

    /*
     * Helper to return the tracked value if enabled, otherwise the fallback (initial) value.
     */
    private fun getTrackedValue(
        isTracked: Boolean,
        currentValue: Float,
        fallbackValue: Float,
    ): Float {
        return if (isTracked) currentValue else fallbackValue
    }

    private suspend fun handleNewPose(scope: CoroutineScope) {
        // If the target has moved significantly enough, start the animation over.
        if (shouldStartAnimation()) {
            currentAnimationJob?.cancel()
            startPose = trailingEntity?.poseInMeters ?: Pose.Identity
            endPose = targetCurrentPose
            currentFrame = 1
            currentAnimationJob = scope.launch { animate() }
        }
    }

    // TODO(b/451647909): Investigate if Compose's built in animation APIs could handle the logic.
    private suspend fun animate() {
        while (currentFrame <= totalFrames) {
            // Calculate the raw and linear progress of the animation (a value from 0.0 to 1.0).
            val linearProgress: Float = currentFrame.toFloat() / totalFrames
            val easedProgress: Float = smoothstep(linearProgress)

            val nextPose = Pose.lerp(startPose, endPose, easedProgress)
            trailingEntity?.poseInMeters = nextPose
            currentFrame++

            delay(TIME_BETWEEN_ANIMATION_TICKS)
        }
    }

    private fun shouldStartAnimation(): Boolean {
        // Check the current position of the target entity compared to where the trailingEntity
        // is planning to be (endPose).
        val translationDelta = (endPose.translation - targetCurrentPose.translation).length
        val rotationDelta = Quaternion.angle(endPose.rotation, targetCurrentPose.rotation)

        return translationDelta > TRANSLATION_THRESHOLD || rotationDelta > ROTATION_THRESHOLD
    }

    private companion object {
        private const val TIME_BETWEEN_ANIMATION_TICKS: Long = 10
        private const val TRANSLATION_THRESHOLD: Float = 0.1f
        private const val ROTATION_THRESHOLD: Float = 3f

        /**
         * Applies Smoothstep function (a specific implementation of a Cubic Hermite interpolation
         * curve). to a linear value. This creates a smooth S-curve effect that goes through
         * "ease-in, accelerate, then ease-out" effect for animations.
         *
         * The function uses the formula `f(t) = 3t² - 2t³`. The coefficients 3 and 2 are
         * mathematically derived to be the simplest polynomial that satisfies four essential
         * conditions for a smooth transition:
         * 1. It starts at 0 (f(0) = 0).
         * 2. It ends at 1 (f(1) = 1).
         * 3. Its rate of change (speed) is 0 at the start (f'(0) = 0).
         * 4. Its rate of change (speed) is 0 at the end (f'(1) = 0).
         *
         * @param x A value between 0.0 and 1.0.
         * @return The smoothed value, also between 0.0 and 1.0.
         */
        private fun smoothstep(x: Float): Float {
            return x * x * (3f - 2f * x)
        }
    }
}

/**
 * This is the implementation for StaticLocking which is accessible through the public interface as
 * LockingBehavior.static()
 */
@OptIn(ExperimentalUserSubspaceApi::class)
internal class StaticLockingBehavior() : LockingBehavior() {
    override suspend fun configure(
        session: Session,
        trailingEntity: CoreGroupEntity,
        lockTo: BodyPart,
        lockDimensions: LockDimensions,
    ) {
        // TODO(b/448689233): Initial head pose data is not reliable.
        // Skipping over it with delay.
        delay(1000)

        val arDevice = ArDevice.getInstance(session)
        arDevice.state.collect { state ->
            val targetCurrentPose =
                session.scene.perceptionSpace.transformPoseTo(
                    state.devicePose,
                    session.scene.activitySpace,
                )

            // Update the trailingEntity just once.
            if (trailingEntity.poseInMeters == Pose.Identity) {
                trailingEntity.poseInMeters = targetCurrentPose
                coroutineContext.cancel()
            }
        }
    }
}

/** A type-safe representation of a part of the user's body that can be used as a lock target. */
@JvmInline
@ExperimentalUserSubspaceApi
public value class BodyPart private constructor(private val value: Int) {
    public companion object {
        /**
         * The "Head" represents both where the user is located and where they are looking.
         *
         * This is typically tracked in order to move content to where it's always in the user's
         * field of view. Please configure headTracking to use this feature. This configure can be
         * set within the session as follows session.config.headTracking ==
         * Config.HeadTrackingMode.LAST_KNOWN
         *
         * **Permissions:** Note that this requires the `android.permission.HEAD_TRACKING`
         * permission.
         */
        public val Head: BodyPart = BodyPart(1)
    }
}

/**
 * A set of boolean flags which determine the dimensions of movement that are tracked.
 *
 * This is intended to be used with a [LockingBehavior]. These dimensions can be used to control how
 * one entity is locked to another. For example, if a dev wants to place a marker on the floor
 * showing a user's position in a room, they might want to track only translationX and translationZ.
 * Possible values are: translationX, translationY, translationZ, rotationX, rotationY, rotationZ or
 * "ALL".
 */
@ExperimentalUserSubspaceApi
public class LockDimensions(
    public val isTranslationXTracked: Boolean = false,
    public val isTranslationYTracked: Boolean = false,
    public val isTranslationZTracked: Boolean = false,
    public val isRotationXTracked: Boolean = false,
    public val isRotationYTracked: Boolean = false,
    public val isRotationZTracked: Boolean = false,
) {
    public companion object {
        /**
         * LockDimensions.ALL is provided as a convenient way to specify all 6 dimensions of a pose.
         */
        public val All: LockDimensions =
            LockDimensions(
                isTranslationXTracked = true,
                isTranslationYTracked = true,
                isTranslationZTracked = true,
                isRotationXTracked = true,
                isRotationYTracked = true,
                isRotationZTracked = true,
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LockDimensions) return false

        if (isTranslationXTracked != other.isTranslationXTracked) return false
        if (isTranslationYTracked != other.isTranslationYTracked) return false
        if (isTranslationZTracked != other.isTranslationZTracked) return false
        if (isRotationXTracked != other.isRotationXTracked) return false
        if (isRotationYTracked != other.isRotationYTracked) return false
        if (isRotationZTracked != other.isRotationZTracked) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isTranslationXTracked.hashCode()
        result = 31 * result + isTranslationYTracked.hashCode()
        result = 31 * result + isTranslationZTracked.hashCode()
        result = 31 * result + isRotationXTracked.hashCode()
        result = 31 * result + isRotationYTracked.hashCode()
        result = 31 * result + isRotationZTracked.hashCode()
        return result
    }

    override fun toString(): String {
        return "LockDimensions(" +
            "translationX=${isTranslationXTracked}, " +
            "translationY=${isTranslationYTracked}, " +
            "translationZ=${isTranslationZTracked}, " +
            "rotationX=${isRotationXTracked}, " +
            "rotationY=${isRotationYTracked}, " +
            "rotationZ=${isRotationZTracked})"
    }
}
