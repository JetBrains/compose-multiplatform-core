/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.core.telecom.internal

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.telecom.CallEndpointCompat
import kotlinx.coroutines.channels.SendChannel

/**
 * Manages the set of available pre-call `CallEndpointCompat` objects and sends updates to a client
 * via a `SendChannel`. This class ensures the list of devices is always consistent and up-to-date.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class PreCallEndpointsUpdater(
    // The single source of truth for the current audio endpoints.
    // A Set is used for efficient add/remove/contains operations.
    @get:VisibleForTesting
    internal val currentDevices: MutableSet<CallEndpointCompat> = mutableSetOf(),
    private val sendChannel: SendChannel<List<CallEndpointCompat>>,
) {
    companion object {
        private val TAG: String = PreCallEndpointsUpdater::class.java.simpleName
    }

    /**
     * Processes a list of newly available endpoints, adds them to the tracked set, and notifies the
     * client if any new endpoints were actually added.
     */
    fun endpointsAddedUpdate(addedCallEndpoints: List<CallEndpointCompat>) {
        if (currentDevices.addAll(addedCallEndpoints)) {
            updateClient()
        } else {
            Log.d(TAG, "endpointsAddedUpdate: No new endpoints to add, not updating client.")
        }
    }

    /**
     * Processes a list of endpoints that are no longer available, removes them from the tracked
     * set, and notifies the client if any existing endpoints were removed.
     */
    fun endpointsRemovedUpdate(removedCallEndpoints: List<CallEndpointCompat>) {
        if (currentDevices.removeAll(removedCallEndpoints.toSet())) {
            updateClient()
        } else {
            Log.d(
                TAG,
                "endpointsRemovedUpdate: No tracked endpoints were removed, not updating client.",
            )
        }
    }

    /** Sorts the current list of endpoints and sends the result to the client. */
    private fun updateClient() {
        // The sorted() extension creates a new sorted list before sending.
        val sortedList = currentDevices.filterNotNull().sorted()
        sendChannel.trySend(sortedList)
    }
}
