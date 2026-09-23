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

package androidx.compose.ui.platform

import androidx.compose.runtime.Recomposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.scene.CanvasLayersComposeScene
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

/**
 * Regression coverage for the leak `HeadlessWindow`'s constructor used to have.
 *
 * `HeadlessWindow` builds a `FrameRecomposer` -- which immediately registers a live `Recomposer`
 * with `GlobalSnapshotManager` and starts its `runRecomposeAndApplyChanges` coroutine, which in
 * turn registers with Compose's global apply-observer list -- and only afterwards builds the
 * `ComposeScene` that uses it. Building that scene constructs a `RootNodeOwner`, which used to
 * throw on every headless window on Linux (the `GtkApplication` clipboard bug). Because the throw
 * happened in a later property initializer of the same constructor, `HeadlessWindow` never
 * finished constructing, so nothing was ever able to reach its `frameRecomposer` field to close
 * it: the Recomposer -- and everything it retained for that test -- leaked for the life of the
 * process. `HeadlessWindow` now closes `frameRecomposer` in that case (see `HeadlessWindow.kt`).
 *
 * This test proves the underlying contract that fix relies on -- `FrameRecomposer.close()`
 * actually tears the Recomposer down even when the scene it was building never finished
 * construction -- using a synthetic failure so it holds regardless of platform or of whether the
 * Gtk-specific bug is present. See `HeadlessClipboardTest` for the headless-backend-specific
 * regression coverage.
 */
@OptIn(InternalComposeUiApi::class)
class FrameRecomposerDisposalTest {
    @Test
    fun closeShutsDownTheRecomposerAfterFailedSceneConstruction() = runBlocking {
        val job = Job()
        val frameRecomposer = FrameRecomposer(coroutineContext = Dispatchers.Default + job)
        val recomposer = frameRecomposer.compositionContext as Recomposer
        val failure = RuntimeException(
            "synthetic failure standing in for RootNodeOwner's platform-clipboard bug",
        )

        val throwingPlatformContext = object : PlatformContext by PlatformContext.Empty() {
            override val dragAndDropManager: PlatformDragAndDropManager
                get() = throw failure
        }

        val caught = assertFailsWith<RuntimeException> {
            CanvasLayersComposeScene(
                frameRecomposer = frameRecomposer,
                platformContext = throwingPlatformContext,
            )
        }
        assertEquals(failure, caught)

        // What HeadlessWindow.kt's catch block does today.
        frameRecomposer.close()

        withTimeout(5_000) {
            recomposer.currentState.first { it == Recomposer.State.ShutDown }
        }

        job.cancel()
    }
}
