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

import androidx.compose.ui.ComposeUIDispatcherOverride
import androidx.compose.ui.asComposeUiMainDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

/**
 * [GlobalSnapshotManager] captures [GlobalSnapshotManagerDispatcher] exactly once per JVM into a
 * scope that never resets. These tests pin the property that makes that safe when a JVM switches
 * active application (a test runner alternating headless and real-UI apps): the captured dispatcher
 * re-resolves [ComposeUIDispatcher] on every dispatch, so it always targets the CURRENT override
 * rather than whichever app happened to start Compose first.
 */
class GlobalSnapshotManagerDispatcherTest {

    @AfterTest
    fun tearDown() {
        ComposeUIDispatcherOverride = null
    }

    /** Runs posted work inline and records that it, specifically, was the target of the dispatch. */
    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }

    @Test
    fun dispatcherIsStableAcrossReads() {
        // GlobalSnapshotManager reads it once; it must be the same object every time so the
        // indirection — not a snapshot of the current value — is what gets captured.
        assertSame(GlobalSnapshotManagerDispatcher, GlobalSnapshotManagerDispatcher)
    }

    @Test
    fun captureFollowsTheCurrentOverrideAcrossAnAppSwitch() {
        // Capture the dispatcher ONCE, exactly as GlobalSnapshotManager.ensureStarted does.
        val captured = GlobalSnapshotManagerDispatcher

        val firstApp = RecordingDispatcher()
        ComposeUIDispatcherOverride = firstApp.asComposeUiMainDispatcher()
        CoroutineScope(captured).launch { }
        assertEquals(1, firstApp.dispatchCount, "work should dispatch to the first app's dispatcher")

        // Switch the active application's UI dispatcher — as a headless -> real-UI switch would.
        val secondApp = RecordingDispatcher()
        ComposeUIDispatcherOverride = secondApp.asComposeUiMainDispatcher()
        CoroutineScope(captured).launch { }

        assertEquals(1, secondApp.dispatchCount, "the once-captured dispatcher must now target the second app")
        assertEquals(
            1,
            firstApp.dispatchCount,
            "the first app's dispatcher must NOT be used after the switch (no stale binding)",
        )
    }
}
