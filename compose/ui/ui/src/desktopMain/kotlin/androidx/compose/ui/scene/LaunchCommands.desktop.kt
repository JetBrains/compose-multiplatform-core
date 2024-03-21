/*
 * Copyright 2024 The Android Open Source Project
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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

internal actual suspend fun launchEffects(launchCommandReceiveChannel: ReceiveChannel<LaunchCommand>) {
    supervisorScope {
        val scheduledLaunches = mutableListOf<LaunchCommand.ScheduleLaunch>()
        val runningJobs = ConcurrentHashMap<Int, Job>()
        launchCommandReceiveChannel.consumeEach { command ->
            when (command) {
                is LaunchCommand.ScheduleLaunch -> {
                    scheduledLaunches.add(command)
                }

                is LaunchCommand.FlushLaunches -> {
                    for (scheduledLaunch in scheduledLaunches) {
                        val oldJob = runningJobs.remove(scheduledLaunch.id)
                        val job = launch(start = CoroutineStart.ATOMIC) {
                            withContext(NonCancellable) {
                                // NonCancellable is required to guarantee that all previous jobs are finished,
                                // even if the joining one was cancelled by the new one.
                                // Alternative solution: keep the set of spawned jobs and cancelAndJoin them all on every new job
                                oldJob?.cancelAndJoin()
                            }
                            ensureActive()
                            scheduledLaunch.block(this)
                        }
                        runningJobs[scheduledLaunch.id] = job
                        job.invokeOnCompletion { runningJobs.remove(scheduledLaunch.id, job) }
                    }
                    scheduledLaunches.clear()
                }

                is LaunchCommand.Cancel -> {
                    runningJobs[command.id]?.cancel()
                }
            }
        }
    }

}