/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.scene

/**
 * Feature flags for [ComposeScene] behavior.
 */
internal object ComposeSceneFeatureFlags {
    /**
     * When enabled, each scene runs all of its work — frame rendering, input dispatch,
     * content (re)composition and effect-coroutine slices on the scene dispatcher —
     * inside one frame-cycle [androidx.compose.runtime.DataSource.Snapshot]: every slice
     * observes the world the latest frame was generated from plus the cycle's own
     * published writes; each slice publishes atomically on return; external publications
     * become visible only at the next frame's pin swap. Conflicting concurrent changes
     * surface as [androidx.compose.runtime.snapshots.SnapshotApplyConflictException] at
     * the failing slice.
     *
     * On desktop this is initialized from the `compcompose.frameIsolation system property.
     *
     * Read once per scene, at construction (see `BaseComposeScene.init`): a scene's
     * isolation mode is fixed for its lifetime from whatever this flag's value was at
     * that moment. Changing this flag afterwards has no effect on already-constructed
     * scenes, only on ones constructed later. Tests that toggle this flag must set it
     * before constructing the scene under test.
     */
    var isFrameIsolationEnabled: Boolean = false
}
