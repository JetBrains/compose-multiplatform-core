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
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.scene
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A LockingBehavior controls the motion of content as it is following (or "locked" to) another
 * entity, such as a user's head. Currently the options include "static", which does not
 * continuously follow the target, and "lazy", which gradually catches up to the target.
 */
@ExperimentalUserSubspaceApi
public abstract class LockingBehavior internal constructor() {
    protected var targetCurrentPose: Pose = Pose.Identity

    internal abstract suspend fun configure(
        session: Session,
        trailingEntity: Entity,
        lockTo: BodyPart = BodyPart.Head,
        lockDimensions: LockDimensions = LockDimensions.All,
    )

    public companion object {
        /**
         * The content is placed once based on the user's initial pose and does not follow
         * subsequent movements.
         */
        public fun static(): LockingBehavior = StaticLockingBehavior()

        /**
         * Creates a behavior where the content smoothly animates to follow the user's movements,
         * creating a comfortable "lazy follow" effect. This is implemented with the Hermite easing
         * algorithm, which accelerates the content then slows it down towards the end of the
         * motion, giving it a sense of real world physics. The use of the Hermite algorithm is not
         * optional but the total duration of the motion can be modified.
         *
         * @param durationMs Amount of milliseconds it takes for the content to catch up to the
         *   user. Default is 1500 milliseconds. A value less than 100 will be rounded up to 100 to
         *   allow enough time to complete the content movement.
         * @return A [LockingBehavior] instance configured for lazy locking.
         */
        public fun lazy(@IntRange(from = 100) durationMs: Int = 1500): LockingBehavior =
            LazyLockingBehavior(durationMs)
    }
}

/**
 * Creates a behavior where the content smoothly animates to follow the user's movements, creating a
 * comfortable "lazy follow" effect. This is the implementation for LazyLocking which is accessible
 * through the public interface as LockingBehavior.lazy()
 *
 * @param durationMs Amount of milliseconds it takes for the content to catch up to the user.
 *   Default is 1500 milliseconds. A value less than 100 will be rounded up to 100 to allow enough
 *   time to complete the content movement.
 */
@OptIn(ExperimentalUserSubspaceApi::class)
internal class LazyLockingBehavior(private val durationMs: Int = 1500) : LockingBehavior() {
    private val totalFrames: Int

    private companion object {
        private const val TIME_BETWEEN_ANIMATION_TICKS: Long = 50
        private const val TRANSLATION_THRESHOLD: Float = 0.1f
        private const val ROTATION_THRESHOLD: Float = 3f
        private const val HERMITE_CONSTANT1: Float = 3f
        private const val HERMITE_CONSTANT2: Float = 2f
    }

    init {
        totalFrames = (durationMs / TIME_BETWEEN_ANIMATION_TICKS).toInt().coerceAtLeast(1)
    }

    private var trailingEntity: Entity? = null
    private var animationCounter: Int = 999
    private var startPose: Pose = Pose.Identity

    private fun animate() {
        // Apply a Hermite easing to the interpolation
        var t: Float = animationCounter.toFloat() / totalFrames
        t = t * t * (HERMITE_CONSTANT1 - HERMITE_CONSTANT2 * t)
        val nextPose = Pose.lerp(startPose, targetCurrentPose, t)
        trailingEntity?.setPose(nextPose)
        animationCounter++
        if (animationCounter <= totalFrames) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(TIME_BETWEEN_ANIMATION_TICKS)
                animate()
            }
        }
    }

    fun startAnimationCriteria(): Boolean {
        val trailingEntityCurrentPose = trailingEntity?.getPose() ?: Pose.Identity

        val translationDelta =
            (trailingEntityCurrentPose.translation - targetCurrentPose.translation).length
        val rotationDelta =
            Quaternion.angle(trailingEntityCurrentPose.rotation, targetCurrentPose.rotation)

        return translationDelta > TRANSLATION_THRESHOLD || rotationDelta > ROTATION_THRESHOLD
    }

    fun handleNewPose() {
        // If animation is in progress in another thread, do nothing.
        if (animationCounter <= totalFrames) {
            return
        }
        // If the target has moved significantly enough, start the animation over.
        else {
            if (startAnimationCriteria()) {
                val trailingEntityCurrentPose = trailingEntity?.getPose() ?: Pose.Identity
                startPose = trailingEntityCurrentPose
                animationCounter = 1
                animate()
            }
        }
    }

    override suspend fun configure(
        session: Session,
        trailingEntity: Entity,
        lockTo: BodyPart,
        lockDimensions: LockDimensions,
    ) {
        this.trailingEntity = trailingEntity
        if (lockTo != BodyPart.Head) return

        // TODO http://b/448689233 Initial head pose data is not reliable.
        // Skipping over it with delay.
        delay(1000)

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
                                if (lockDimensions.isTranslationXTracked) {
                                    headPose.translation.x
                                } else {
                                    0f
                                },
                            y =
                                if (lockDimensions.isTranslationYTracked) {
                                    headPose.translation.y
                                } else {
                                    0f
                                },
                            z =
                                if (lockDimensions.isTranslationZTracked) {
                                    headPose.translation.z
                                } else {
                                    0f
                                },
                        ),
                    rotation =
                        Quaternion(
                            x =
                                if (lockDimensions.isRotationXTracked) {
                                    headPose.rotation.x
                                } else {
                                    0f
                                },
                            y =
                                if (lockDimensions.isRotationYTracked) {
                                    headPose.rotation.y
                                } else {
                                    0f
                                },
                            z =
                                if (lockDimensions.isRotationZTracked) {
                                    headPose.rotation.z
                                } else {
                                    0f
                                },
                            w = headPose.rotation.w,
                        ),
                )
            handleNewPose()
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
        trailingEntity: Entity,
        lockTo: BodyPart,
        lockDimensions: LockDimensions,
    ) {
        // TODO http://b/448689233 Initial head pose data is not reliable.
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
            if (trailingEntity.getPose() == Pose.Identity) {
                trailingEntity.setPose(targetCurrentPose)
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
