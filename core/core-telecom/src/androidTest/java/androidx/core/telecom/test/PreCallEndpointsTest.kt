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

package androidx.core.telecom.test

import android.os.Build.VERSION_CODES
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.internal.CallEndpointUuidTracker
import androidx.core.telecom.internal.PreCallEndpointsUpdater
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(minSdkVersion = VERSION_CODES.O /* api=26 */)
class PreCallEndpointsTest {
    private val sessionId: Int = 111

    // Test data reused across multiple tests
    private val earpiece =
        CallEndpointCompat("Earpiece", CallEndpointCompat.TYPE_EARPIECE, sessionId)
    private val speaker = CallEndpointCompat("Speaker", CallEndpointCompat.TYPE_SPEAKER, sessionId)
    private val bluetooth =
        CallEndpointCompat("Bluetooth", CallEndpointCompat.TYPE_BLUETOOTH, sessionId)
    private val wiredHeadset =
        CallEndpointCompat("Wired", CallEndpointCompat.TYPE_WIRED_HEADSET, sessionId)
    private val bluetooth_A =
        CallEndpointCompat("A_Headset", CallEndpointCompat.TYPE_BLUETOOTH, sessionId)
    private val bluetooth_B =
        CallEndpointCompat("B_Headset", CallEndpointCompat.TYPE_BLUETOOTH, sessionId)
    private val bluetooth_C =
        CallEndpointCompat("C_Headset", CallEndpointCompat.TYPE_BLUETOOTH, sessionId)

    @After
    fun tearDown() {
        // Clean up the static tracker after each test to ensure isolation.
        CallEndpointUuidTracker.endSession(sessionId)
    }

    @Test
    fun initialState_isCorrectlySet() {
        val initialEndpoints = mutableSetOf(earpiece, speaker)
        val updater = PreCallEndpointsUpdater(initialEndpoints, Channel())

        assertEquals(2, updater.currentDevices.size)
        assertTrue(
            "The set should contain the initial earpiece endpoint",
            updater.currentDevices.contains(earpiece),
        )
        assertTrue(
            "The set should contain the initial speaker endpoint",
            updater.currentDevices.contains(speaker),
        )
    }

    @Test
    fun endpointsAddedUpdate_whenSetChanges_sendsSortedUpdate() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val updater = PreCallEndpointsUpdater(mutableSetOf(earpiece), sendChannel)

        // Action: Add a new endpoint that will be sorted before the earpiece
        updater.endpointsAddedUpdate(listOf(speaker))

        // Assertion: Receive the updated list from the channel
        val updatedList = sendChannel.receive()
        assertEquals("List should have 2 endpoints after addition", 2, updatedList.size)
        // Verify the list is sorted according to CallEndpointCompat.compareTo rules
        assertEquals("Speaker should be the first element (higher rank)", speaker, updatedList[0])
        assertEquals("Earpiece should be the second element (lower rank)", earpiece, updatedList[1])
    }

    @Test
    fun endpointsAddedUpdate_whenSetDoesNotChange_sendsNoUpdate() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val updater = PreCallEndpointsUpdater(mutableSetOf(earpiece), sendChannel)

        // Action: Attempt to add an endpoint that's already present
        updater.endpointsAddedUpdate(listOf(earpiece))

        // Assertion: Channel should remain empty
        val result = sendChannel.tryReceive().getOrNull()
        assertNull("Channel should not receive an update if the set doesn't change", result)
    }

    @Test
    fun endpointsRemovedUpdate_whenSetChanges_sendsSortedUpdate() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val updater =
            PreCallEndpointsUpdater(mutableSetOf(earpiece, speaker, bluetooth), sendChannel)

        // Action: Remove an endpoint
        updater.endpointsRemovedUpdate(listOf(speaker))

        // Assertion: Receive the updated list from the channel
        val updatedList = sendChannel.receive()
        assertEquals("List should have 2 endpoints after removal", 2, updatedList.size)
        // Verify the remaining items are still sorted correctly
        assertEquals(bluetooth, updatedList[0])
        assertEquals(earpiece, updatedList[1])
    }

    @Test
    fun updateClient_sendsSafeAndEffectivelyImmutableList() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val updater = PreCallEndpointsUpdater(mutableSetOf(earpiece, speaker), sendChannel)

        // Action: Trigger an update
        updater.endpointsAddedUpdate(listOf(wiredHeadset))

        // Receive the list that was sent to the client
        val receivedList = sendChannel.receive()

        // This is the definitive test. We assert that attempting to modify
        // the list throws an exception, which is the desired behavior.
        assertThrows(UnsupportedOperationException::class.java) {
            // This line should fail, proving the client cannot mutate the list.
            (receivedList as MutableList).clear()
        }
    }

    @Test
    fun updateClient_sendsCorrectlySortedList() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        // Initialize with an unsorted set
        val updater = PreCallEndpointsUpdater(mutableSetOf(earpiece, speaker), sendChannel)

        // Action
        updater.endpointsAddedUpdate(listOf(wiredHeadset, bluetooth))

        // Assertion
        val updatedList = sendChannel.receive()

        // The expected order is defined by the rank in CallEndpointCompat:
        // 1. WIRED_HEADSET, 2. BLUETOOTH, 3. SPEAKER, 4. EARPIECE
        val expectedOrder = listOf(wiredHeadset, bluetooth, speaker, earpiece)

        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
    }

    @Test
    fun endpointsAddedUpdate_withMultipleNewBluetoothDevices_sendsOneSortedUpdate() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val updater = PreCallEndpointsUpdater(mutableSetOf(earpiece), sendChannel)

        // Action: Add two new Bluetooth devices in a single call.
        updater.endpointsAddedUpdate(listOf(bluetooth_C, bluetooth_A))

        // Assertion: Receive the updated list from the channel.
        val updatedList = sendChannel.receive()

        // Verify the list contains all devices and is sorted correctly.
        // Bluetooth devices should be grouped first, then sorted alphabetically by name.
        val expectedOrder = listOf(bluetooth_A, bluetooth_C, earpiece)
        assertEquals("List should contain 3 endpoints after addition", 3, updatedList.size)
        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
    }

    @Test
    fun endpointsRemovedUpdate_withMultipleBluetoothDevices_sendsOneUpdate() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        val initialSet = mutableSetOf(earpiece, bluetooth_A, speaker, bluetooth_C)
        val updater = PreCallEndpointsUpdater(initialSet, sendChannel)

        // Action: Remove two existing Bluetooth devices in a single call.
        updater.endpointsRemovedUpdate(listOf(bluetooth_C, bluetooth_A))

        // Assertion: Receive the updated list from the channel.
        val updatedList = sendChannel.receive()
        val expectedOrder = listOf(speaker, earpiece)

        assertEquals("List should contain 2 endpoints after removal", 2, updatedList.size)
        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
        assertFalse(updatedList.contains(bluetooth_A))
        assertFalse(updatedList.contains(bluetooth_C))
    }

    @Test
    fun multipleBluetoothDevices_areSortedAlphabeticallyByName() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        // Initialize with an unsorted set containing multiple BT devices.
        val initialSet = mutableSetOf(speaker, bluetooth_C, earpiece, bluetooth_A)
        val updater = PreCallEndpointsUpdater(initialSet, sendChannel)

        // Action: Trigger an update by adding another device.
        updater.endpointsAddedUpdate(listOf(bluetooth_B))

        // Assertion: Receive the updated list and verify the complete sort order.
        val updatedList = sendChannel.receive()

        // The expected order first groups by type rank, then sorts alphabetically by name.
        val expectedOrder =
            listOf(
                bluetooth_A, // TYPE_BLUETOOTH, Name "A..."
                bluetooth_B, // TYPE_BLUETOOTH, Name "B..."
                bluetooth_C, // TYPE_BLUETOOTH, Name "C..."
                speaker, // TYPE_SPEAKER
                earpiece, // TYPE_EARPIECE
            )
        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
    }

    @Test
    fun addBluetoothDevice_whenAnotherAlreadyExists_maintainsSortOrder() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        // Start with a BT device already in the set.
        val updater = PreCallEndpointsUpdater(mutableSetOf(bluetooth_B, earpiece), sendChannel)

        // Action: Add a new BT device that should be sorted before the existing one.
        updater.endpointsAddedUpdate(listOf(bluetooth_A))

        // Assertion: Receive the update and verify the new device is sorted correctly.
        val updatedList = sendChannel.receive()
        val expectedOrder = listOf(bluetooth_A, bluetooth_B, earpiece)

        assertEquals("List should contain 3 endpoints after addition", 3, updatedList.size)
        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
    }

    @Test
    fun removeBluetoothDevice_whenMultipleExist_updatesListCorrectly() = runTest {
        val sendChannel = Channel<List<CallEndpointCompat>>(Channel.BUFFERED)
        // Start with multiple BT devices.
        val updater =
            PreCallEndpointsUpdater(mutableSetOf(bluetooth_A, bluetooth_C, speaker), sendChannel)

        // Action: Remove one of the BT devices.
        updater.endpointsRemovedUpdate(listOf(bluetooth_A))

        // Assertion: Receive the update and verify the correct device was removed.
        val updatedList = sendChannel.receive()
        val expectedOrder = listOf(bluetooth_C, speaker)

        assertEquals("List should contain 2 endpoints after removal", 2, updatedList.size)
        assertThat(updatedList).containsExactlyElementsIn(expectedOrder).inOrder()
    }
}
