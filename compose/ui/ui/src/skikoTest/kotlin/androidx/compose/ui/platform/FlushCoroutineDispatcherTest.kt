/*
 * Copyright 2022 The Android Open Source Project
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

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class FlushCoroutineDispatcherTest {

    @Test
    fun all_tasks_should_run_with_flush() = runTest {
        val dispatcher = FlushCoroutineDispatcher(this)

        val actualNumbers = mutableListOf<Int>()
        launch(dispatcher) {
            yield()
            actualNumbers.add(1)
            yield()
            yield()
            actualNumbers.add(2)
            yield()
            yield()
            yield()
            actualNumbers.add(3)
        }

        while (dispatcher.hasImmediateTasks()) {
            dispatcher.flush()
        }

        assertEquals(listOf(1, 2, 3), actualNumbers)
    }

    @Test
    fun tasks_should_run_even_without_flush() = runTest {
        val dispatcher = FlushCoroutineDispatcher(this)

        val actualNumbers = mutableListOf<Int>()
        launch(dispatcher) {
            yield()
            actualNumbers.add(1)
            yield()
            yield()
            actualNumbers.add(2)
            yield()
            yield()
            yield()
            actualNumbers.add(3)
        }

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), actualNumbers)
        assertFalse(dispatcher.hasImmediateTasks())
    }

    @Test
    fun delayed_tasks_are_cancelled() = runTest {
        val coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        val dispatcher = FlushCoroutineDispatcher(coroutineScope)
        val job = launch(dispatcher) {
            delay(1.days)
        }
        assertTrue(dispatcher.hasDelayedTasks())
        job.cancel()
        assertFalse(
            dispatcher.hasDelayedTasks(),
            "FlushCoroutineDispatcher has a delayed task that has been cancelled"
        )
    }

    @Test
    fun delayed_tasks_are_cancelled_when_job_is_cancelled_before_delaying_coroutine_is_run() = runTest {
        // Verify that the task is removed from `delayedTasks` even if the job is cancelled
        // before the coroutine launched by `FlushCoroutineDispatcher.scheduleResumeAfterDelay`
        // starts running.

        // To test this, we create a special coroutine dispatcher that conditionally ignores the
        // block it is asked to dispatch. We then use it to avoid dispatching the coroutine
        // launched in `FlushCoroutineDispatcher.scheduleResumeAfterDelay`.
        var ignoreDispatch = false
        val ignoreDelayedTaskLaunchCoroutineDispatcher = object: CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (!ignoreDispatch) {
                    block.run()
                }
            }
        }
        val coroutineScope = CoroutineScope(ignoreDelayedTaskLaunchCoroutineDispatcher)
        val dispatcher = FlushCoroutineDispatcher(coroutineScope)
        val job = launch(dispatcher) {
            ignoreDispatch = true
            delay(1.days)
        }
        // Needed because the cancellation notification is itself dispatched with the coroutine
        // dispatcher. Additionally, it's needed *before* the assertion, because if the assertion
        // fails while ignoreDispatch is true, the cancellation of the coroutine will be ignored and
        // the test will be stuck.
        ignoreDispatch = false
        assertTrue(dispatcher.hasDelayedTasks())
        job.cancel()
        assertFalse(
            actual = dispatcher.hasDelayedTasks(),
            message = "FlushCoroutineDispatcher has a delayed task that has been cancelled"
        )
    }

    @Test
    fun duplicate_identical_tasks_are_executed() = runTest {
        val tasks = mutableListOf<Runnable>()
        val controlledCoroutineDispatcher = object: CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                tasks.add(block)
            }
        }

        val coroutineScope = CoroutineScope(controlledCoroutineDispatcher)
        val dispatcher = FlushCoroutineDispatcher(coroutineScope)
        var executionCount = 0
        val block = Runnable { executionCount++ }
        dispatcher.dispatch(EmptyCoroutineContext, block)
        dispatcher.dispatch(EmptyCoroutineContext, block)
        for (task in tasks) {
            task.run()
        }

        assertEquals(2, executionCount)
    }

    @Test
    fun blocked_task_becomes_immediate_when_unblocked() = runTest {
        val dispatcher = FlushCoroutineDispatcher(this)
        val channel = Channel<Unit>(capacity = 1)
        launch(dispatcher) {
            channel.receive()
        }
        testScheduler.advanceTimeBy(1.milliseconds)
        assertFalse(dispatcher.hasImmediateTasks())
        assertFalse(dispatcher.hasDelayedTasks())
        channel.trySend(Unit)
        assertTrue(dispatcher.hasImmediateTasks())
    }
}
