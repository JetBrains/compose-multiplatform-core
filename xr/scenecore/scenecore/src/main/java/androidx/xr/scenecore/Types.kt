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

/**
 * Dimensions of a 3D object.
 *
 * @param width Width.
 * @param height Height.
 * @param depth Depth.
 */
@Suppress("DataClassDefinition")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class Dimensions(
    public val width: Float = 0f,
    public val height: Float = 0f,
    public val depth: Float = 0f,
) {
    override fun toString(): String {
        return super.toString() + ": w $width x h $height x d $depth"
    }
}

/**
 * Dimensions of a 2D surface in Pixels.
 *
 * @param width Integer Width.
 * @param height Integer Height.
 */
@Suppress("DataClassDefinition")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class PixelDimensions(public val width: Int = 0, public val height: Int = 0) {
    override fun toString(): String {
        return super.toString() + ": w $width x h $height"
    }
}

/**
 * The angles (in radians) representing the sides of the view frustum. These are not expected to
 * change over the lifetime of the session but in rare cases may change due to updated camera
 * settings.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class Fov(
    public val angleLeft: Float,
    public val angleRight: Float,
    public val angleUp: Float,
    public val angleDown: Float,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fov) return false

        return angleLeft == other.angleLeft &&
            angleRight == other.angleRight &&
            angleUp == other.angleUp &&
            angleDown == other.angleDown
    }

    override fun hashCode(): Int {
        var result = angleLeft.hashCode()
        result = 31 * result + angleRight.hashCode()
        result = 31 * result + angleUp.hashCode()
        result = 31 * result + angleDown.hashCode()
        return result
    }

    override fun toString(): String {
        return "Fov(angleLeft=$angleLeft, angleRight=$angleRight, angleUp=$angleUp, angleDown=$angleDown)"
    }

    @JvmOverloads
    public fun copy(
        angleLeft: Float = this.angleLeft,
        angleRight: Float = this.angleRight,
        angleUp: Float = this.angleUp,
        angleDown: Float = this.angleDown,
    ): Fov {
        return Fov(angleLeft, angleRight, angleUp, angleDown)
    }
}

/** Type of plane based on orientation i.e. Horizontal or Vertical. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object PlaneType {
    public const val HORIZONTAL: Int = 0
    public const val VERTICAL: Int = 1
    public const val ANY: Int = 2
}

@RestrictTo(RestrictTo.Scope.LIBRARY)
@Retention(AnnotationRetention.SOURCE)
@IntDef(PlaneType.HORIZONTAL, PlaneType.VERTICAL, PlaneType.ANY)
@Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
internal annotation class PlaneTypeValue

/** Semantic plane types. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object PlaneSemantic {
    public const val WALL: Int = 0
    public const val FLOOR: Int = 1
    public const val CEILING: Int = 2
    public const val TABLE: Int = 3
    public const val ANY: Int = 4
}

@RestrictTo(RestrictTo.Scope.LIBRARY)
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    PlaneSemantic.WALL,
    PlaneSemantic.FLOOR,
    PlaneSemantic.CEILING,
    PlaneSemantic.TABLE,
    PlaneSemantic.ANY,
)
@Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
internal annotation class PlaneSemanticValue
