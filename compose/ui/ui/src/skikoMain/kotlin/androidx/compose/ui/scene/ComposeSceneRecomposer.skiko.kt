/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DataSource
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.internal.SnapshotHolder
import androidx.compose.runtime.withTransaction
import androidx.compose.ui.desktop.logging.logger
import androidx.compose.ui.platform.FlushCoroutineDispatcher
import androidx.compose.ui.util.trace
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

/**
 * The scheduler for performing recomposition and applying updates to one or more [Composition]s.
 *
 * The main difference from [Recomposer] is separate dispatchers for LaunchEffect and other
 * recompositions that allows more precise status checking.
 *
 * @param coroutineContext The coroutine context to use for the compositor.
 * @param frameSnapshotHolder The scene-owned cell carrying the scene's DataSourceContext
 * and (while frame isolation is on) the current frame-cycle [DataSource.Snapshot];
 * effect-dispatcher slices run inside it (publishing on return) and the [Recomposer]
 * reads it from its context.
 * @param elements Additional coroutine context elements to include in context.
 */
internal class ComposeSceneRecomposer(
    coroutineContext: CoroutineContext,
    private val frameSnapshotHolder: SnapshotHolder,
    vararg elements: CoroutineContext.Element
) {
    private val job = Job()
    private val coroutineScope = CoroutineScope(coroutineContext + job)

    /**
     * We use [FlushCoroutineDispatcher] not only because we need [FlushCoroutineDispatcher.flush]
     * for LaunchEffect tasks, but also to know whether it is idle (has no scheduled tasks).
     *
     * Each task is one slice of the frame cycle when frame isolation is enabled: it runs
     * inside the cycle snapshot and publishes its writes atomically on return.
     */
    private val effectDispatcher = FlushCoroutineDispatcher(coroutineScope) { task ->
        // Use `current` (not the fail-fast `checkedCurrent`): effect tasks can be dispatched
        // during scene construction, before the factory activates the frame domain (e.g.
        // RootNodeOwner's init synchronously launches a snapshotFlow collector). In that
        // pre-activation window `current` is null and the task runs bare, exactly as on the
        // flag-off path; once activated it runs isolated in the standing pin.
        frameSnapshotHolder.current?.withTransaction(task) ?: task()
    }
    private val recomposeDispatcher = FlushCoroutineDispatcher(coroutineScope)
    private val recomposer =
        Recomposer(coroutineContext + job + effectDispatcher + frameSnapshotHolder)

    /**
     * `true` if there is any pending work scheduled, regardless of whether it is currently running.
     */
    val hasPendingWork: Boolean
        get() = recomposer.hasPendingWork ||
        effectDispatcher.hasImmediateTasks() ||
        recomposeDispatcher.hasImmediateTasks()

    val compositionContext: CompositionContext
        get() = recomposer

    init {
        var context: CoroutineContext = recomposeDispatcher
        for (element in elements) {
            context += element
        }
        coroutineScope.launch(context,
            start = CoroutineStart.UNDISPATCHED
        ) {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    /**
     * Perform all scheduled recomposer tasks and wait for the tasks which are already
     * performing in the recomposition scope.
     */
    fun performScheduledRecomposerTasks() =
        trace("ComposeSceneRecomposer:performScheduledRecomposerTasks") {
            recomposeDispatcher.flush()
        }

    /**
     * Perform all scheduled effects.
     */
    fun performScheduledEffects() = trace("ComposeSceneRecomposer:performScheduledEffects") {
        effectDispatcher.flush()
    }

    /**
     * Schedules the given [block] to run with the effects dispatcher.
     */
    fun scheduleAsEffect(block: () -> Unit) {
        effectDispatcher.dispatch(job, Runnable(block))
    }

    /**
     * Permanently shut down this [ComposeSceneRecomposer] for future use.
     *
     * @see Recomposer.cancel
     */
    fun cancel() {
        recomposer.cancel()
        job.cancel()
    }

    private companion object {
        val logger = logger<ComposeSceneRecomposer>()
    }
}