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

package androidx.compose.ui.keepscreenon

internal class KeepScreenOnManager {
    val isKeepScreenOnEnabled: Boolean get() = keepScreenOnCount > 0
    private val clientCounts = mutableMapOf<Any, Int>()
    private var keepScreenOnCount = 0

    fun incrementKeepScreenOnCount(client: Any) {
        val incrementedCount = (clientCounts.getOrPut(client) { 0 }) + 1
        clientCounts[client] = incrementedCount
        keepScreenOnCount++
    }

    fun decrementKeepScreenOnCount(client: Any) {
        val count = clientCounts.getOrElse(client) { return }

        val decrementedCount = count - 1
        if (decrementedCount < 1) {
            clientCounts.remove(client)
        } else {
            clientCounts[client] = decrementedCount
        }
        keepScreenOnCount--
    }

    companion object {
        val instance = KeepScreenOnManager()
    }
}