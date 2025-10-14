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

package androidx.privacysandbox.sdkruntime.integration.testapp

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Wait for [expectedEventCount] events and return them in [await] call result. */
class EventCollector<EventClass : Any>(expectedEventCount: Int) {
    private val async = CountDownLatch(expectedEventCount)
    private val events = mutableListOf<EventClass>()

    fun onEvent(event: EventClass) {
        synchronized(events) { events.add(event) }
        async.countDown()
    }

    fun await(timeOutMs: Long = TIMEOUT_MS): List<EventClass> {
        if (!async.await(timeOutMs, TimeUnit.MILLISECONDS)) {
            throw TimeoutException("Timeout for onEvent()")
        }
        return synchronized(events) { events.toList() }
    }

    private companion object {
        const val TIMEOUT_MS = 5000.toLong()
    }
}
