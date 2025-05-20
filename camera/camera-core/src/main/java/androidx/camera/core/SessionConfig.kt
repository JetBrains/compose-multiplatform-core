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

package androidx.camera.core

import android.util.Range
import androidx.annotation.RestrictTo
import androidx.camera.core.featurecombination.Feature
import androidx.camera.core.featurecombination.impl.UseCaseType
import androidx.camera.core.featurecombination.impl.UseCaseType.Companion.getFeatureComboUseCaseType
import androidx.camera.core.impl.StreamSpec.FRAME_RATE_RANGE_UNSPECIFIED

/**
 * Represents a session configuration to start a camera session. When used with `camera-lifecycle`,
 * this SessionConfig is expected to be used for starting a camera session (e.g. by being bound to
 * the [androidx.lifecycle.LifecycleOwner] via
 * `androidx.camera.lifecycle.ProcessCameraProvider.bindToLifecycle` API which allows the lifecycle
 * events to start and stop the camera session with this given configuration).
 *
 * It consists of a collection of [UseCase], session parameters to be applied on the camera session,
 * and common properties like the field-of-view defined by [ViewPort], the [CameraEffect], required
 * or preferred [Feature]s etc.
 *
 * @property useCases The list of [UseCase] to be attached to the camera and receive camera data.
 *   This can't be empty.
 * @property viewPort The [ViewPort] to be applied on the camera session. If not set, the default is
 *   no viewport.
 * @property effects The list of [CameraEffect] to be applied on the camera session. If not set, the
 *   default is no effects.
 * @property requiredFeatures A set of `Feature` that are mandatory for the camera configuration. An
 *   [IllegalStateException] is thrown during camera configuration if adding all of the required
 *   features is not supported on a device. The [CameraInfo.isFeatureCombinationSupported] API can
 *   be used to check if the required features are supported. Alternatively, the features can be
 *   provided through the `preferredFeatures` property so that some features can be discarded
 *   according to priority if all of them are not supported. Note that [CameraEffect] or
 *   [ImageAnalysis] use case is currently not supported if this parameter is used.
 * @property preferredFeatures A list of preferred [Feature] that should be ordered according to
 *   priority in descending order, i.e. a `Feature` with a lower index in the list will be
 *   considered to have a higher priority. These features will be selected on a best-effort basis
 *   according to the priority. Note that [CameraEffect] or [ImageAnalysis] use case is currently
 *   not supported if this parameter is used.
 * @throws IllegalArgumentException If the combination of config options are conflicting or
 *   unsupported, e.g.
 *     - if any of the required features is not supported on the device
 *     - if same feature is present multiple times in [preferredFeatures]
 *     - if same feature is present in both [requiredFeatures] and [preferredFeatures]
 *     - if [ImageAnalysis] use case is added with [requiredFeatures] or [preferredFeatures]
 *     - if a [CameraEffect] is set with [requiredFeatures] or [preferredFeatures]
 *
 * @See androidx.camera.lifecycle.ProcessCameraProvider.bindToLifecycle
 */
// TODO: b/414298514 - The class-wide restriction to remove when making SessionConfig API public.
//  Removing only this restriction won't make the feature combo params public.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@ExperimentalSessionConfig
public open class SessionConfig
// TODO: b/384404392 - Remove when feature combo impl. is ready. The feature combo params should be
//  kept restricted until then.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    useCases: List<UseCase>,
    public val viewPort: ViewPort? = null,
    public val effects: List<CameraEffect> = emptyList(),
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val requiredFeatures: Set<Feature> = emptySet(),
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val preferredFeatures: List<Feature> = emptyList(),
) {
    public val useCases: List<UseCase> = useCases.distinct()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public open val isLegacy: Boolean = false
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open val targetHighSpeedFrameRate: Range<Int> = FRAME_RATE_RANGE_UNSPECIFIED

    init {
        validateFeatureCombination()
    }

    // TODO: b/384404392 - Remove this extra constructor when feature combo impl. is ready. The
    //  feature combo params should be kept restricted until the impl. is ready. In order to unblock
    //  the release of SessionConfig, we are splitting the constructor with and without the feature
    //  params so that the one with feature params can be marked restricted. When feature combo
    //  impl. is completed, we can just remove this constructor while removing the restricted
    //  annotation for the primary constructor. Due to @JvmOverloads, only two new constructors will
    //  be added without changing any previous one, so it won't be an API breaking change.
    @JvmOverloads
    public constructor(
        useCases: List<UseCase>,
        viewPort: ViewPort? = null,
        effects: List<CameraEffect> = emptyList()
    ) : this(
        useCases = useCases,
        viewPort = viewPort,
        effects = effects,
        requiredFeatures = emptySet(),
        preferredFeatures = emptyList()
    ) {
        validateFeatureCombination()
    }

    private fun validateFeatureCombination() {
        if (requiredFeatures.isEmpty() && preferredFeatures.isEmpty()) {
            return
        }

        // Currently, there is only feature instance possible per type. But this can change in
        // future, e.g. a VIDEO_STABILIZATION feature object may need to be added in future.
        validateRequiredFeatures()

        require(preferredFeatures.distinct().size == preferredFeatures.size) {
            "Duplicate values in preferredFeatures($preferredFeatures)"
        }

        val duplicateFeatures = requiredFeatures.intersect(preferredFeatures)
        require(duplicateFeatures.isEmpty()) {
            "requiredFeatures and preferredFeatures have duplicate values: $duplicateFeatures"
        }

        useCases.forEach {
            require(it.getFeatureComboUseCaseType() != UseCaseType.UNDEFINED) {
                "$it is not supported with feature combination"
            }
        }

        require(effects.isEmpty()) { "Effects aren't supported with feature combination yet" }
    }

    /**
     * Validates that there are no conflicting values for the same feature in [requiredFeatures].
     *
     * @throws IllegalArgumentException If there are conflicting values for the same feature.
     */
    private fun validateRequiredFeatures() {
        val requiredFeatureTypes = requiredFeatures.map { it.featureTypeInternal }.distinct()
        requiredFeatureTypes.forEach { featureType ->
            val distinctFeaturesPerType =
                requiredFeatures.filter { it.featureTypeInternal == featureType }

            require(distinctFeaturesPerType.size <= 1) {
                "requiredFeatures has conflicting feature values: $distinctFeaturesPerType"
            }
        }
    }

    /** Builder for [SessionConfig] */
    public class Builder(private val useCases: List<UseCase>) {
        private var viewPort: ViewPort? = null
        private var effects: MutableList<CameraEffect> = mutableListOf()
        private val requiredFeatures = mutableListOf<Feature>()
        private val preferredFeatures = mutableListOf<Feature>()

        public constructor(vararg useCases: UseCase) : this(useCases.toList())

        /** Sets the [ViewPort] to be applied on the camera session. */
        public fun setViewPort(viewPort: ViewPort): Builder {
            this.viewPort = viewPort
            return this
        }

        /** Adds a [CameraEffect] to be applied on the camera session. */
        public fun addEffect(effect: CameraEffect): Builder {
            this.effects.add(effect)
            return this
        }

        /**
         * Adds a list of features that are mandatory for the camera configuration.
         *
         * If all the features are not supported, an [IllegalStateException] will be thrown during
         * camera configuration.
         *
         * @param features The vararg of `Feature` objects to add to the required features.
         * @return The [Builder] instance, allowing for method chaining.
         * @see androidx.camera.core.SessionConfig.requiredFeatures
         */
        // TODO: b/384404392 - Remove when feature combo impl. is ready.
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun addRequiredFeatures(vararg features: Feature): Builder {
            requiredFeatures.addAll(features)
            return this
        }

        /**
         * Sets the list of preferred features that is ordered according to priority in descending
         * order.
         *
         * These features will be selected on a best-effort basis according to the priority. The
         * feature that is ordered first in the list (i.e. has a lower index) will be prioritized
         * higher than a feature ordered later in the list.
         *
         * @param features The list of preferred features, ordered by preference.
         * @return The [Builder] instance, allowing for method chaining.
         * @see androidx.camera.core.SessionConfig.preferredFeatures
         */
        // TODO: b/384404392 - Remove when feature combo impl. is ready.
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun setPreferredFeatures(vararg features: Feature): Builder {
            preferredFeatures.clear()
            preferredFeatures.addAll(features)
            return this
        }

        /** Builds a [SessionConfig] from the current configuration. */
        public fun build(): SessionConfig {
            return SessionConfig(
                useCases = useCases,
                viewPort = viewPort,
                effects = effects.toList(),
                requiredFeatures = requiredFeatures.toSet(),
                preferredFeatures = preferredFeatures.toList(),
            )
        }
    }
}

/** The legacy SessionConfig which allows sequential binding. This is used internally. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OptIn(ExperimentalSessionConfig::class)
public class LegacySessionConfig(
    useCases: List<UseCase>,
    viewPort: ViewPort? = null,
    effects: List<CameraEffect> = emptyList(),
    public override val targetHighSpeedFrameRate: Range<Int> = FRAME_RATE_RANGE_UNSPECIFIED
) : SessionConfig(useCases, viewPort, effects) {
    public override val isLegacy: Boolean = true

    public constructor(
        useCaseGroup: UseCaseGroup
    ) : this(useCaseGroup.useCases, useCaseGroup.viewPort, useCaseGroup.effects)
}
