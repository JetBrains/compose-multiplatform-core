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

internal data class SinglePaneScene<T : Any>(
    override val key: T,
    val entry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = { entry.content.invoke(entry.key) }
}

/**
 * A [SceneStrategy] that always creates a 1-entry [Scene] simply displaying the last entry in the
 * list.
 */
public class SinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    @Composable
    override fun calculateScene(entries: List<NavEntry<T>>): SceneStrategyResult<T> =
        SceneStrategyResult(
            scene =
                SinglePaneScene(
                    key = entries.last().key,
                    entry = entries.last(),
                ),
            previousEntries = entries.dropLast(1)
        )
}

@Composable
internal fun <T : Any> SceneStrategy<T>.calculateSceneWithSinglePaneFallback(
    entries: List<NavEntry<T>>
): SceneStrategyResult<T> =
    calculateScene(entries) ?: SinglePaneSceneStrategy<T>().calculateScene(entries)
