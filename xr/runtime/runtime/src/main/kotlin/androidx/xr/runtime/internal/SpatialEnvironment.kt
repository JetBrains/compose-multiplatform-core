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

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import java.util.Objects
import java.util.function.Consumer

/**
 * Interface for updating the background image/geometry and passthrough settings.
 *
 * <p>The application can set either / both a skybox and a glTF for geometry, then toggle their
 * visibility by enabling or disabling passthrough. The skybox and geometry will be remembered
 * across passthrough mode changes.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SpatialEnvironment {
    /**
     * Gets the current passthrough opacity value between 0 and 1 where 0.0f means no passthrough,
     * and 1.0f means full passthrough.
     *
     * <p>This value can be overwritten by user-enabled or system-enabled passthrough and will not
     * always match the opacity value returned by [getPassthroughOpacityPreference].
     */
    public val currentPassthroughOpacity: Float

    /**
     * Gets the preferred spatial environment for the application.
     *
     * <p>The returned value is always what was most recently supplied to
     * [setSpatialEnvironmentPreference], or null if no preference has been set.
     *
     * <p>See [isSpatialEnvironmentPreferenceActive] or the [OnSpatialEnvironmentChangedListener]
     * events to know when this preference becomes active.
     */
    public val spatialEnvironmentPreference: SpatialEnvironmentPreference?

    /**
     * Gets the last passthrough opacity requested through [setPassthroughOpacityPreference].
     *
     * <p>This may be different from the actual current state returned by
     * [getCurrentPassthroughOpacity], but it should be applied as soon as the
     * [SpatialCapabilities.SPATIAL_CAPABILITY_PASSTHROUGH_CONTROL] capability is gained. Defaults
     * to null, if [setPassthroughOpacityPreference] was never called.
     *
     * <p>If set to null, the passthrough opacity will default to the user preference managed
     * through the system.
     */
    public val passthroughOpacityPreference: Float?

    /**
     * Notifies an application when the passthrough state changes, such as when the application
     * enters or exits passthrough or when the passthrough opacity changes. This [listener] will be
     * called on the Application's UI thread.
     */
    public fun addOnPassthroughOpacityChangedListener(listener: Consumer<Float>)

    /** Remove a listener previously added by [addOnPassthroughOpacityChangedListener]. */
    public fun removeOnPassthroughOpacityChangedListener(listener: Consumer<Float>)

    /**
     * Returns true if the environment set by [setSpatialEnvironmentPreference] is active.
     *
     * <p>Spatial environment preference set through [setSpatialEnvironmentPreference] are shown
     * when this is true, but passthrough or other objects in the scene could partially or totally
     * occlude them. When this is false, the default system environment will be active instead.
     */
    public fun isSpatialEnvironmentPreferenceActive(): Boolean

    /**
     * Sets the preferred spatial environment for the application.
     *
     * <p>Note that this method only sets a preference and does not cause an immediate change unless
     * [isSpatialEnvironmentPreferenceActive] is already true. Once the device enters a state where
     * the XR background can be changed and the
     * [SpatialCapabilities.SPATIAL_CAPABILITY_APP_ENVIRONMENTS] capability is available, the
     * preferred spatial environment for the application will be automatically displayed.
     *
     * <p>Setting the preference to null will disable the preferred spatial environment for the
     * application, meaning the default system environment will be displayed instead.
     *
     * <p>If the given [SpatialEnvironmentPreference] is not null, but all of its properties are
     * null, then the spatial environment will consist of a black skybox and no geometry
     * [isSpatialEnvironmentPreferenceActive] is true.
     *
     * <p>Changes to the Environment state will be notified via the
     * [OnSpatialEnvironmentChangedListener].
     */
    public fun setSpatialEnvironmentPreference(
        preference: SpatialEnvironmentPreference?
    ): @SetSpatialEnvironmentPreferenceResult Int

    /**
     * Sets the preference for passthrough state by requesting a change in passthrough opacity.
     *
     * <p>Passthrough visibility cannot be set directly to on/off modes. Instead, a desired
     * passthrough opacity value between 0.0f and 1.0f can be requested which will dictate which
     * mode is used. A passthrough opacity within 0.01f of 0.0f will disable passthrough, and will
     * be returned as 0.0f by [getPassthroughOpacityPreference]. An opacity value within 0.01f of
     * 1.0f will enable full passthrough and it will be returned as 1.0f by
     * [getPassthroughOpacityPreference]. Any other value in the range will result in a
     * semi-transparent passthrough.
     *
     * <p>Requesting to set passthrough opacity to a value that is not in the range of 0.0f to 1.0f
     * will result in the value getting clamped to 0.0f or 1.0f depending on which one is closer.
     *
     * <p>If the value is set to null, the opacity will be managed by the system.
     *
     * <p>Requests to change opacity are only immediately attempted to be honored if the activity
     * has the [SpatialCapabilities.SPATIAL_CAPABILITY_PASSTHROUGH_CONTROL] capability. When the
     * request is honored, this returns [SetPassthroughOpacityPreferenceChangeApplied]. When the
     * activity does not have the capability to control the passthrough state, this returns
     * [SetPassthroughOpacityPreferenceChangePending] to indicate that the application passthrough
     * opacity preference has been set and is pending to be automatically applied when the app
     * regains capabilities to control passthrough state.
     *
     * <p>When passthrough state changes, whether due to this request succeeding or due to any other
     * system or user initiated change, [OnPassthroughOpacityChangedListener] will be notified.
     */
    public fun setPassthroughOpacityPreference(
        passthroughOpacityPreference: Float?
    ): @SetPassthroughOpacityPreferenceResult Int

    /**
     * Notifies an application whether or not the preferred spatial environment for the application
     * is active.
     *
     * <p>The environment will try to transition to the application environment when a non-null
     * preference is set through [setSpatialEnvironmentPreference] and the application has the
     * [SpatialCapabilities.SPATIAL_CAPABILITY_APP_ENVIRONMENTS] capability. The environment
     * preferences will otherwise not be active.
     *
     * <p>The listener consumes a boolean value that is true if the environment preference is active
     * when the listener is notified.
     *
     * <p>This listener will be invoked on the Application's UI thread.
     */
    public fun addOnSpatialEnvironmentChangedListener(listener: Consumer<Boolean>)

    /** Remove a listener previously added by [addOnSpatialEnvironmentChangedListener]. */
    public fun removeOnSpatialEnvironmentChangedListener(listener: Consumer<Boolean>)

    /** Result values for calls to SpatialEnvironment.setPassthroughOpacityPreference */
    @IntDef(
        SetPassthroughOpacityPreferenceResult.CHANGE_APPLIED,
        SetPassthroughOpacityPreferenceResult.CHANGE_PENDING,
    )
    @Target(AnnotationTarget.TYPE)
    @Retention(AnnotationRetention.SOURCE)
    @Suppress("PublicTypedef")
    public annotation class SetPassthroughOpacityPreferenceResult {
        public companion object {
            /**
             * The call to [setPassthroughOpacityPreference] succeeded and should now be visible.
             */
            public const val CHANGE_APPLIED: Int = 0
            /**
             * The preference has been set, but will be applied only when the
             * [SpatialCapabilities.SPATIAL_CAPABILITY_PASSTHROUGH_CONTROL] is acquired
             */
            public const val CHANGE_PENDING: Int = 1
        }
    }

    /** Result values for calls to SpatialEnvironment.setSpatialEnvironmentPreference */
    @IntDef(
        SetSpatialEnvironmentPreferenceResult.CHANGE_APPLIED,
        SetSpatialEnvironmentPreferenceResult.CHANGE_PENDING,
    )
    @Target(AnnotationTarget.TYPE)
    @Retention(AnnotationRetention.SOURCE)
    @Suppress("PublicTypedef")
    public annotation class SetSpatialEnvironmentPreferenceResult {
        public companion object {
            /**
             * The call to [setSpatialEnvironmentPreference] succeeded and should now be visible.
             */
            public const val CHANGE_APPLIED: Int = 0
            /**
             * The call to [setSpatialEnvironmentPreference] successfully applied the preference,
             * but it is not immediately visible due to requesting a state change while the activity
             * does not have the [SpatialCapabilities.SPATIAL_CAPABILITY_APP_ENVIRONMENTS]
             * capability to control the app environment state. The preference was still set and
             * will be applied when the capability is gained.
             */
            public const val CHANGE_PENDING: Int = 1
        }
    }

    /**
     * A class that represents the user's preferred spatial environment.
     *
     * @param geometry the preferred geometry for the environment based on a pre-loaded glTF model.
     *   If null, there will be no geometry.
     * @param skybox the preferred skybox for the environment based on a pre-loaded EXR Image. If
     *   null, it will be all black.
     * @param geometryMaterial the material to override a given mesh in the geometry. If null, the
     *   material will not override any mesh.
     * @param geometryMeshName the name of the mesh to override with the material. If null, the
     *   material will not override any mesh.
     * @param geometryAnimationName the name of the animation to play on the geometry. If null, the
     *   geometry will not play any animation. Note that the animation will be played in loop.
     */
    public class SpatialEnvironmentPreference
    @JvmOverloads
    constructor(
        public val skybox: ExrImageResource?,
        public val geometry: GltfModelResource?,
        public val geometryMaterial: MaterialResource? = null,
        public val geometryMeshName: String? = null,
        public val geometryAnimationName: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpatialEnvironmentPreference) return false

            return skybox == other.skybox &&
                geometry == other.geometry &&
                geometryMaterial == other.geometryMaterial &&
                geometryMeshName == other.geometryMeshName &&
                geometryAnimationName == other.geometryAnimationName
        }

        override fun hashCode(): Int {
            return Objects.hash(skybox, geometry)
        }
    }
}
