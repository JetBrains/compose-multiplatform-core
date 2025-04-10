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

package androidx.xr.runtime.internal

import androidx.annotation.RestrictTo
import androidx.xr.runtime.math.Matrix4
import androidx.xr.runtime.math.Vector3

/**
 * InputEvent for XR Runtime Platform.
 *
 * @param source the source of the input event.
 * @param pointerType the pointer type of the input event.
 * @param timestamp the time this event occurred, in the android.os.SystemClock#uptimeMillis time
 *   base. This field mirrors the XR Extensions InputEvent.
 * @param origin the origin of the ray, in the receiver's activity space. Will be zero if the source
 *   is not ray-based (eg, direct touch).
 * @param direction a point indicating the direction the ray is pointing in, in the receiver's
 *   activity space. The ray is a vector starting at the origin point and passing through the
 *   direction point.
 * @param action returns the current action associated with the input event.
 * @param hitInfo on the first entity (closest to the ray origin) that was hit by the input ray, if
 *   any. This info will be null if no Entity was hit.
 * @param secondaryHitInfo info on the second entity for the same task that was hit by the input
 *   ray, if any.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class InputEvent(
    @Source public val source: Int,
    @PointerType public val pointerType: Int,
    public val timestamp: Long,
    public val origin: Vector3,
    public val direction: Vector3,
    @Action public val action: Int,
    public val hitInfo: HitInfo?,
    public val secondaryHitInfo: HitInfo?,
) {
    public companion object {
        /**
         * There's a possibility of ABI mismatch here when the concrete platformAdapter starts
         * receiving input events with an updated field, such as if a newer source or pointer type
         * has been added to the underlying platform OS. We need to perform a version check when the
         * platformAdapter is constructed to ensure that the application doesn't receive anything it
         * wasn't compiled against.
         */
        // TODO: b/343468347 - Implement a version check for xr extensions when creating the
        // concrete
        // platform adapter.

        /** Unknown source. */
        public const val SOURCE_UNKNOWN: Int = 0

        /**
         * Event is based on the user's head. Ray origin is at average between eyes, pushed out to
         * the near clipping plane for both eyes and points in direction head is facing. Action
         * state is based on volume up button being depressed.
         *
         * <p>Events from this source are considered sensitive and hover events are never sent.
         */
        public const val SOURCE_HEAD: Int = 1

        /**
         * Event is based on (one of) the user's controller(s). Ray origin and direction are for a
         * controller aim pose as defined by OpenXR. (<a
         * href="https://registry.khronos.org/OpenXR/specs/1.1/html/xrspec.html#semantic-paths-standard-pose-identifiers">...</a>)
         * Action state is based on the primary button on the controller, usually the bottom-most
         * face button.
         */
        public const val SOURCE_CONTROLLER: Int = 2

        /**
         * Event is based on one of the user's hands. Ray is a hand aim pose, with origin between
         * thumb and forefinger and points in direction based on hand orientation. Action state is
         * based on a pinch gesture.
         */
        public const val SOURCE_HANDS: Int = 3

        /**
         * Event is based on a 2D mouse pointing device. Ray origin behaves the same as for
         * DEVICE_TYPE_HEAD and points in direction based on mouse movement. During a drag, the ray
         * origin moves approximating hand motion. The scrollwheel moves the ray away from / towards
         * the user. Action state is based on the primary mouse button.
         */
        public const val SOURCE_MOUSE: Int = 4

        /**
         * Event is based on a mix of the head, eyes, and hands. Ray origin is at average between
         * eyes and points in direction based on a mix of eye gaze direction and hand motion. During
         * a two-handed zoom/rotate gesture, left/right pointer events will be issued; otherwise,
         * default events are issued based on the gaze ray. Action state is based on if the user has
         * done a pinch gesture or not.
         *
         * <p>Events from this source are considered sensitive and hover events are never sent.
         */
        public const val SOURCE_GAZE_AND_GESTURE: Int = 5

        /**
         * Default pointer type for the source (no handedness). Occurs for SOURCE_UNKNOWN,
         * SOURCE_HEAD, SOURCE_MOUSE, and SOURCE_GAZE_AND_GESTURE.
         */
        public const val POINTER_TYPE_DEFAULT: Int = 0

        /**
         * Left hand / controller pointer. Occurs for SOURCE_CONTROLLER, SOURCE_HANDS, and
         * SOURCE_GAZE_AND_GESTURE.
         */
        public const val POINTER_TYPE_LEFT: Int = 1

        /**
         * Right hand / controller pointer. Occurs for SOURCE_CONTROLLER, SOURCE_HANDS, and
         * SOURCE_GAZE_AND_GESTURE.
         */
        public const val POINTER_TYPE_RIGHT: Int = 2

        /** The primary action button or gesture was just pressed / started. */
        public const val ACTION_DOWN: Int = 0

        /**
         * The primary action button or gesture was just released / stopped. The hit info represents
         * the node that was originally hit (ie, as provided in the ACTION_DOWN event).
         */
        public const val ACTION_UP: Int = 1

        /**
         * The primary action button or gesture was pressed/active in the previous event, and is
         * still pressed/active. The hit info represents the node that was originally hit (ie, as
         * provided in the ACTION_DOWN event). The hit position may be null if the pointer is no
         * longer hitting that node.
         */
        public const val ACTION_MOVE: Int = 2

        /**
         * While the primary action button or gesture was held, the pointer was disabled. This
         * happens if you are using controllers and the battery runs out, or if you are using a
         * source that transitions to a new pointer type, eg SOURCE_GAZE_AND_GESTURE.
         */
        public const val ACTION_CANCEL: Int = 3

        /**
         * The primary action button or gesture is not pressed, and the pointer ray continued to hit
         * the same node. The hit info represents the node that was hit (may be null if pointer
         * capture is enabled).
         *
         * <p>Hover input events are never provided for sensitive source types.
         */
        public const val ACTION_HOVER_MOVE: Int = 4

        /**
         * The primary action button or gesture is not pressed, and the pointer ray started to hit a
         * new node. The hit info represents the node that is being hit (may be null if pointer
         * capture is enabled).
         *
         * <p>Hover input events are never provided for sensitive source types.
         */
        public const val ACTION_HOVER_ENTER: Int = 5

        /**
         * The primary action button or gesture is not pressed, and the pointer ray stopped hitting
         * the node that it was previously hitting. The hit info represents the node that was being
         * hit (may be null if pointer capture is enabled).
         *
         * <p>Hover input events are never provided for sensitive source types.
         */
        public const val ACTION_HOVER_EXIT: Int = 6

        /**
         * Info about the hit result of the ray.
         *
         * @param inputEntity the entity that was hit by the input ray.
         *
         * <p>ACTION_MOVE, ACTION_UP, and ACTION_CANCEL events will report the same node as was hit
         * during the initial ACTION_DOWN.
         *
         * @param hitPosition the position of the hit in the receiver's activity space.
         *
         * <p>All events may report the current ray's hit position. This can be null if there no
         * longer is a collision between the ray and the input node (eg, during a drag event).
         *
         * @param transform the matrix transforming activity space coordinates into the hit entity's
         *   local coordinate space.
         */
        public class HitInfo(
            public val inputEntity: Entity,
            public val hitPosition: Vector3,
            public val transform: Matrix4,
        ) {}
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public annotation class Source {
        public companion object {
            public const val SOURCE_UNKNOWN: Int = 0
            public const val SOURCE_HEAD: Int = 1
            public const val SOURCE_CONTROLLER: Int = 2
            public const val SOURCE_HANDS: Int = 3
            public const val SOURCE_MOUSE: Int = 4
            public const val SOURCE_GAZE_AND_GESTURE: Int = 5
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public annotation class PointerType {
        public companion object {
            public const val POINTER_TYPE_DEFAULT: Int = 0
            public const val POINTER_TYPE_LEFT: Int = 1
            public const val POINTER_TYPE_RIGHT: Int = 2
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public annotation class Action {
        public companion object {
            public const val ACTION_DOWN: Int = 0
            public const val ACTION_UP: Int = 1
            public const val ACTION_MOVE: Int = 2
            public const val ACTION_CANCEL: Int = 3
            public const val ACTION_HOVER_MOVE: Int = 4
            public const val ACTION_HOVER_ENTER: Int = 5
            public const val ACTION_HOVER_EXIT: Int = 6
        }
    }
}
