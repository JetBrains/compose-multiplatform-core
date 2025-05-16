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

package androidx.camera.camera2.pipe.internal

import androidx.annotation.GuardedBy
import androidx.camera.camera2.pipe.core.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CameraPipeLifetime is an internal class designed to facilitate CameraPipe shutdown. It does so in
 * an ordered manner:
 * 1. First, we shut down the camera backends (which closes all cameras).
 * 2. Then we cancel coroutine scopes.
 * 3. Finally, with scopes cancelled, we shut down the threads CameraPipe created.
 *
 * Internal classes that require a CameraPipe-level shutdown routine should invoke
 * [addShutdownAction] to register their respective shutdown action and type.
 */
@Singleton
internal class CameraPipeLifetime @Inject constructor() {
    private val lock = Any()

    @GuardedBy("lock") private var isShutdown = false

    @GuardedBy("lock") private val shutdownTasks = mutableListOf<ShutdownTask>()

    fun addShutdownAction(shutdownType: ShutdownType, shutdownAction: Runnable) {
        val success =
            synchronized(lock) {
                if (isShutdown) {
                    false
                } else {
                    shutdownTasks.add(ShutdownTask(shutdownType, shutdownAction))
                    true
                }
            }
        if (!success) {
            Log.info { "CameraPipeLifetime already shut down. Executing action immediately..." }
            shutdownAction.run()
        }
    }

    fun shutdown() {
        val tasks =
            synchronized(lock) {
                if (isShutdown) {
                    return
                }
                isShutdown = true

                val tasks = shutdownTasks.toList()
                shutdownTasks.clear()
                tasks
            }

        shutdownTasksWithType(tasks, ShutdownType.CAMERA)
        shutdownTasksWithType(tasks, ShutdownType.SCOPE)
        shutdownTasksWithType(tasks, ShutdownType.THREAD)
    }

    private fun shutdownTasksWithType(
        shutdownTasks: List<ShutdownTask>,
        shutdownType: ShutdownType
    ) {
        val tasks = shutdownTasks.filter { it.type == shutdownType }
        Log.debug { "Shutting down $shutdownType tasks (${tasks.size} total)" }
        for (task in tasks) {
            task.action.run()
        }
    }

    private data class ShutdownTask(val type: ShutdownType, val action: Runnable)

    internal enum class ShutdownType {
        CAMERA,
        SCOPE,
        THREAD,
    }
}
