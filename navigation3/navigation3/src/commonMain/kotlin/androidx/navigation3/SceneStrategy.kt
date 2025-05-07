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

package androidx.navigation3

import androidx.compose.runtime.Composable

/**
 * A strategy that tries to calculate a [SceneStrategyResult] given a list of [NavEntry].
 *
 * If the list of [NavEntry] does not result in a [Scene] for this strategy, `null` will be returned
 * instead to delegate to another strategy.
 */
public fun interface SceneStrategy<T : Any> {
    @Composable
    public fun calculateScene(
        entries: List<NavEntry<T>>,
    ): SceneStrategyResult<T>?

    /**
     * Chains this [SceneStrategy] with another [sceneStrategy] to return a combined
     * [SceneStrategy].
     */
    public infix fun then(sceneStrategy: SceneStrategy<T>): SceneStrategy<T> =
        SceneStrategy { entries ->
            calculateScene(entries) ?: sceneStrategy.calculateScene(entries)
        }
}

/** The result of a [SceneStrategy] calculating a [Scene] from a list of [NavEntry]s. */
public class SceneStrategyResult<T : Any>(
    /** The scene to render. */
    public val scene: Scene<T>,

    /**
     * The resulting [NavEntry]s that should be computed after pressing back updates the backstack.
     *
     * This is required for calculating the proper predictive back state, which may result in a
     * different scene being shown.
     *
     * When predictive back is occurring, this list of entries will be passed through the
     * [SceneStrategy] again, to determine what the resulting scene would be if the back happens.
     */
    public val previousEntries: List<NavEntry<T>>,
)
