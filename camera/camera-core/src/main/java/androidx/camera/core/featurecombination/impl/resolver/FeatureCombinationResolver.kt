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

package androidx.camera.core.featurecombination.impl.resolver

import androidx.camera.core.UseCase
import androidx.camera.core.featurecombination.Feature
import androidx.camera.core.featurecombination.impl.ResolvedFeatureCombination

/**
 * Defines how feature combination options (i.e. preferred features) are resolved based on whether a
 * feature combination is supported or not.
 *
 * @see resolveFeatureCombination
 */
public interface FeatureCombinationResolver {
    /**
     * Returns a [FeatureCombinationResolutionResult] which is either a supported
     * [ResolvedFeatureCombination] instance or some error.
     *
     * @param useCases The set of use cases to use with a feature combination.
     * @param requiredFeatures The set of required [Feature]s, empty by default.
     * @param orderedPreferredFeatures The list of preferred features in descending order of
     *   priority, empty by default.
     * @return A [FeatureCombinationResolutionResult] indicating the result. In case of success, it
     *   will contain a supported [ResolvedFeatureCombination].
     * @throws IllegalArgumentException If both the [requiredFeatures] and
     *   [orderedPreferredFeatures] are empty.
     */
    @Throws(IllegalStateException::class)
    public fun resolveFeatureCombination(
        useCases: Set<UseCase>,
        requiredFeatures: Set<Feature> = emptySet(),
        orderedPreferredFeatures: List<Feature> = emptyList()
    ): FeatureCombinationResolutionResult
}
