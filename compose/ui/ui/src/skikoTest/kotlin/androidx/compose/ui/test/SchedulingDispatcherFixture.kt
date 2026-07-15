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

package androidx.compose.ui.test

import androidx.compose.ui.ComposeSchedulingDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * The one explicit way a test configures Compose's internal scheduling dispatcher.
 *
 * Nothing installs this implicitly: a test that constructs a `LayoutNode` or a `ComposeScene` must
 * call [install] (or [installControlled]) and [uninstall], and one that forgets gets the error
 * message from `requiredSchedulingDispatcher` naming exactly that. That is deliberate — an
 * automatic fallback would silently run snapshot apply notifications and RectManager debounces on
 * the wrong thread.
 *
 * [install] installs [Dispatchers.Unconfined]: work posted to it runs inline, synchronously, on
 * whichever thread posts it. This must be the default: scene construction
 * (`SkikoComposeUiTest.createScene`, reached through `runOnUiThread`) runs inside a *blocking*
 * `SwingUtilities.invokeAndWait`, and posts work to this dispatcher as part of that same blocking
 * call (`RectManager` debounce scheduling on attach). A [StandardTestDispatcher] only *queues*
 * that work until something calls `advanceUntilIdle()` — but the only thread that could call it is
 * the one already blocked inside `invokeAndWait`, so nothing ever drains the queue and the test
 * hangs forever. This is not specific to any one test class: it hits any test that builds a scene
 * through `runSkikoComposeUiTest`/`ComposeUiTest`. Running the work inline instead is safe for the
 * overwhelming majority of tests, which never deliberately depend on it being deferred.
 *
 * [installControlled] keeps the old, fully-deferred behaviour for the minority of tests that
 * genuinely want to drive time by hand and observe state between frames/debounces (the house style
 * set by `FrameCycleSnapshotSceneTest`). Do not call it from inside a blocking
 * `runOnUiThread`/`invokeAndWait` call: nothing else can be advancing [scheduler] while that
 * thread is blocked, so the same deadlock applies.
 */
class SchedulingDispatcherFixture {
    private var previous: CoroutineDispatcher? = null
    private var installed = false
    private var controlledScheduler: TestCoroutineScheduler? = null

    /**
     * The scheduler backing [installControlled]. Only meaningful after calling
     * [installControlled] — reading it after a plain [install] (or before installing at all) is a
     * usage error, since there is nothing to advance.
     */
    val scheduler: TestCoroutineScheduler
        get() = controlledScheduler
            ?: error(
                "SchedulingDispatcherFixture.scheduler is only available after " +
                    "installControlled(); this fixture was configured with install() instead, " +
                    "which runs posted work inline and has no scheduler to advance."
            )

    /**
     * Installs an immediate dispatcher: posted work runs inline on the calling thread. Use this
     * unless a test deliberately needs to control timing — see [installControlled].
     */
    fun install() {
        saveOriginalIfFirstInstall()
        controlledScheduler = null
        ComposeSchedulingDispatcher = Dispatchers.Unconfined
    }

    /**
     * Installs a [StandardTestDispatcher] that only runs posted work once [scheduler] is
     * explicitly advanced (e.g. via `advanceUntilIdle()`). For tests that deliberately want to
     * drain debounced work step by step, from a thread that is free to do so.
     */
    fun installControlled(): TestCoroutineScheduler {
        saveOriginalIfFirstInstall()
        val newScheduler = TestCoroutineScheduler()
        controlledScheduler = newScheduler
        ComposeSchedulingDispatcher = StandardTestDispatcher(newScheduler)
        return newScheduler
    }

    fun uninstall() {
        if (installed) {
            ComposeSchedulingDispatcher = previous
            installed = false
            controlledScheduler = null
        }
    }

    private fun saveOriginalIfFirstInstall() {
        if (!installed) {
            previous = ComposeSchedulingDispatcher
            installed = true
        }
    }
}
