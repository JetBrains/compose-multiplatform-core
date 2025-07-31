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
import androidx.xr.runtime.math.FloatSize3d

/**
 * A resize event which is sent in response to the User interacting with the [ResizableComponent].
 *
 * @param entity The [Entity] being resized.
 * @param resizeState The state of the resize event.
 * @param newSize The new proposed size of the Entity in local space.
 */
public class ResizeEvent(
    public val entity: Entity,
    @ResizeStateValue public val resizeState: Int,
    public val newSize: FloatSize3d,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResizeEvent) return false

        if (entity != other.entity) return false
        if (resizeState != other.resizeState) return false
        if (newSize != other.newSize) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entity.hashCode()
        result = 31 * result + resizeState.hashCode()
        result = 31 * result + newSize.hashCode()
        return result
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        ResizeState.RESIZE_STATE_UNKNOWN,
        ResizeState.RESIZE_STATE_START,
        ResizeState.RESIZE_STATE_ONGOING,
        ResizeState.RESIZE_STATE_END,
    )
    internal annotation class ResizeStateValue

    public object ResizeState {
        /** Constant for {@link resizeState}: The resize state is unknown. */
        public const val RESIZE_STATE_UNKNOWN: Int = 0
        /** Constant for {@link resizeState}: The user has started dragging the resize handles. */
        public const val RESIZE_STATE_START: Int = 1
        /** Constant for {@link resizeState}: The user is continuing to drag the resize handles. */
        public const val RESIZE_STATE_ONGOING: Int = 2
        /** Constant for {@link resizeState}: The user has stopped dragging the resize handles. */
        public const val RESIZE_STATE_END: Int = 3
    }
}
