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
 * A specific scene to render 1 or more [NavEntry]s.
 *
 * The rendering for [content] should invoke the content for each [NavEntry] contained in [entries]
 * at most once.
 */
public interface Scene<T : Any> {
    /** The key identifying the [Scene]. */
    public val key: Any

    /** The list of [NavEntry]s that can be displayed in this scene. */
    public val entries: List<NavEntry<T>>

    /** The content rendering the [Scene] itself. */
    public val content: @Composable () -> Unit
}
