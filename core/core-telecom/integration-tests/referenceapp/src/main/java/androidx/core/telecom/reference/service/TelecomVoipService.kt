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

package androidx.core.telecom.reference.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.telecom.DisconnectCause
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import androidx.core.telecom.CallsManager.Companion.CAPABILITY_SUPPORTS_VIDEO_CALLING
import androidx.core.telecom.reference.model.CallData
import androidx.core.telecom.reference.model.CallState
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * A [LifecycleService] implementing [VoipService] to manage VoIP calls using the Android Telecom
 * framework.
 *
 * This service handles registering the app with Telecom, adding new calls, managing the lifecycle
 * and state of each active call using coroutines, and providing updates to observers (like a
 * repository or ViewModel) via a [StateFlow]. It requires API level S (31) or higher.
 */
@RequiresApi(Build.VERSION_CODES.S)
class TelecomVoipService() : LocalIcsBinder, LifecycleService(), VoipService {
    private val localBinder =
        object : LocalIcsBinder.Connector, Binder() {
            override fun getService(): LocalIcsBinder {
                return this@TelecomVoipService
            }
        }

    var mContext: Context? = null
    private val callsManager: CallsManager by lazy { CallsManager(mContext!!) }
    private val audioManager: AudioManager by lazy {
        mContext!!.getSystemService(AUDIO_SERVICE) as AudioManager
    }
    private val nextCallId = AtomicInteger(0)
    // Private MutableStateFlow to hold the current state (list of calls).
    // StateFlow is often better for representing *state* that UI observes.
    // It always holds the latest value and emits it to new collectors.
    private val _callDataList = MutableStateFlow<List<CallData>>(emptyList())
    // Public immutable Flow for observation. Flow of Data:
    // TelecomVoipService --> CallRepository --> InCallModel --> InCallScreen --> InCallUi
    override val callDataUpdates: StateFlow<List<CallData>> = _callDataList.asStateFlow()
    // Map to hold active call control actions and associated coroutine job. This is needed so that
    // when the user requests a call action for a particular call, the correct call control scope
    // is used to transition the call state. Also, the job for the call is stored so that it can
    // be canceled and cleaned up when the call is ended.
    private val activeCalls = mutableMapOf<String, CallController>()
    // Map to hold jobs that delay the removal of call data from the UI.  2 second delay was
    // added to show calls are disconnected before removal.
    private val delayedRemovalJobs = mutableMapOf<String, Job>()

    companion object {
        private const val TAG = "TelecomVoipService"
        private val CALL_CONNECTS_DELAY = 2.seconds
        private val CALL_REMOVE_DELAY = 2.seconds
    }

    // Actions that can be performed on an active call
    private data class CallActions(
        val setActiveChannel: Channel<Unit> = Channel(Channel.CONFLATED),
        val setInactiveChannel: Channel<Unit> = Channel(Channel.CONFLATED),
        val disconnectChannel: Channel<Unit> = Channel(Channel.CONFLATED),
        val switchAudioChannel: Channel<CallEndpointCompat> = Channel(Channel.CONFLATED)
    )

    // Encapsulates channels and the handling coroutine Job for a single call
    private class CallController(
        val job: Job, // The main job handling this call's lifecycle and actions
        val actions: CallActions
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate service started")
        mContext = application.applicationContext
        callsManager.registerAppWithTelecom(CAPABILITY_SUPPORTS_VIDEO_CALLING)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy service destroyed")
        activeCalls.values.forEach { it.job.cancel() }
        activeCalls.clear()
        delayedRemovalJobs.values.forEach { it.cancel() }
        delayedRemovalJobs.clear()
        // Reset state
        _callDataList.value = emptyList()
    }

    override fun addCall(callAttributes: CallAttributesCompat) {
        val callActions = CallActions()
        val callId: String = nextCallId.getAndIncrement().toString()
        // Launch a *supervisorJob* for this specific call within the service's lifecycleScope.
        // This ensures failure/cancellation of one call doesn't affect the service or other calls.
        val callJob =
            CoroutineScope(
                    lifecycleScope.coroutineContext +
                        SupervisorJob() +
                        CoroutineName("Call-$callId")
                )
                .launch {
                    try {
                        callsManager.addCall(
                            callAttributes,
                            { /* onAnswer */
                                updateCallDataInternal(callId) {
                                    it.copy(callState = CallState.ACTIVE)
                                }
                            },
                            { /* onDisconnect */
                                handleCallDisconnectedInternal(callId, it)
                            },
                            { /* onSetActive */
                                updateCallDataInternal(callId) {
                                    it.copy(callState = CallState.ACTIVE)
                                }
                            },
                            { /* onSetInactive */
                                updateCallDataInternal(callId) {
                                    it.copy(callState = CallState.INACTIVE)
                                }
                            },
                        ) {
                            Log.d(TAG, "[$callId] CallControlScope active")
                            val callControlScope: CallControlScope = this
                            launch {
                                initializeAndMonitorCall(callId, callAttributes)
                                // Keep this coroutine alive by handling actions until cancelled
                                handleCallActions(callId, callActions, callControlScope)
                            }
                            launch {
                                // For outgoing or incoming calls, simulate the remote user
                                // connecting or the local user answering the call.
                                delay(CALL_CONNECTS_DELAY)
                                setCallActive(callId)
                            }
                        }
                    } catch (_: CancellationException) {
                        // Expected cancellation during cleanup
                        Log.i(TAG, "[$callId] Call coroutine cancelled as part of cleanup.")
                    } catch (e: Exception) {
                        Log.e(TAG, "[$callId] Exception during call management", e)
                        updateCallDataInternal(callId) {
                            it.copy(
                                callState = CallState.UNKNOWN,
                                callException = CallException(CallException.ERROR_UNKNOWN)
                            )
                        }
                    } finally {
                        // This block executes when the callJob coroutine completes or is cancelled
                        Log.i(TAG, "Finally block: [$callId] Call job finished.")
                        ensureActiveCallRemoved(callId) // ensure cleanup
                    }
                }
        activeCalls[callId] = CallController(callJob, callActions)
    }

    /**
     * Executed within the CallControlScope after Telecom successfully adds the call. Initializes
     * the CallData state and starts collecting updates from Telecom flows.
     */
    private suspend fun CallControlScope.initializeAndMonitorCall(
        callId: String,
        callAttributes: CallAttributesCompat,
    ) {
        // 1. Get Initial State (Safely)
        val initialEndpoint = currentCallEndpoint.take(1).singleOrNull()
        val initialEndpoints = availableEndpoints.take(1).singleOrNull() ?: emptyList()
        val initialMuted = isMuted.take(1).singleOrNull() ?: false
        val initialState =
            when (callAttributes.direction) {
                CallAttributesCompat.DIRECTION_OUTGOING -> CallState.DIALING
                else -> CallState.RINGING
            }

        // 2. Add Initial CallData to the StateFlow
        val initialCallData =
            createInitialCallData(callId, callAttributes, initialState)
                .copy(
                    currentEndpoint = initialEndpoint,
                    availableEndpoints = initialEndpoints,
                    isMuted = initialMuted
                )
        _callDataList.update { currentList ->
            // Avoid duplicates if somehow added already
            if (currentList.any { it.callId == callId }) currentList
            else currentList + initialCallData
        }

        // 3. Launch Collectors for subsequent state changes within the CallControlScope
        // These will be automatically cancelled when the CallControlScope ends (e.g., on
        // disconnect)
        launch {
            currentCallEndpoint.collect { endpoint ->
                Log.i(TAG, "[$callId] Current Endpoint updated: ${endpoint.name}")
                updateCallDataInternal(callId) { it.copy(currentEndpoint = endpoint) }
            }
        }
        launch {
            availableEndpoints.collect { endpoints ->
                Log.i(
                    TAG,
                    "[$callId] Available Endpoints updated: ${endpoints.joinToString { it.name }}"
                )
                updateCallDataInternal(callId) { it.copy(availableEndpoints = endpoints) }
            }
        }
        launch {
            isMuted.collect { muted ->
                Log.i(TAG, "[$callId] Mute state updated: $muted")
                updateCallDataInternal(callId) { it.copy(isMuted = muted) }
            }
        }
    }

    /** Creates the initial CallData object. */
    private fun createInitialCallData(
        callId: String,
        attributes: CallAttributesCompat,
        state: CallState,
        exception: CallException? = null
    ): CallData {
        return CallData(
            callId = callId,
            attributes = attributes,
            callState = state,
            isMuted = false,
            callException = exception,
            currentEndpoint = null,
            availableEndpoints = emptyList()
        )
    }

    private fun updateCallDataInternal(callId: String, update: (CallData) -> CallData) {
        _callDataList.update { currentList ->
            currentList.map { callData ->
                if (callData.callId == callId) {
                    val updated = update(callData)
                    Log.v(TAG, "[$callId] Updating CallData: $updated")
                    updated
                } else {
                    callData
                }
            }
        }
    }

    private suspend fun handleCallActions(
        callId: String,
        callActions: CallActions,
        callControlScope: CallControlScope
    ) {
        Log.d(TAG, "[$callId] Starting action handler loop.")
        // Helper function to wrap control actions
        suspend fun executeControlAction(
            action: suspend () -> CallControlResult,
            successState: CallState? = null
        ) {
            handleControlResult(callId, action(), successState)
        }

        try {
            while (currentCoroutineContext().isActive) { // Loop while the scope is active
                select<Unit> { // Use select for handling multiple channels
                    callActions.setActiveChannel.onReceive {
                        Log.i(TAG, "[$callId] Action: setActiveChannel received")
                        executeControlAction({ callControlScope.setActive() }, CallState.ACTIVE)
                    }
                    callActions.setInactiveChannel.onReceive {
                        Log.i(TAG, "[$callId] Action: setInactiveChannel received")
                        executeControlAction({ callControlScope.setInactive() }, CallState.INACTIVE)
                    }
                    callActions.disconnectChannel.onReceive {
                        Log.i(TAG, "[$callId] Action: disconnectChannel received")
                        // 1. Tell the Core-Telecom Framework to disconnect
                        val cause = DisconnectCause(DisconnectCause.LOCAL)
                        callControlScope.disconnect(cause)
                        // 2. Launch cleanup asynchronously on the service's scope
                        // This allows the onReceive block to finish before cancellation happens.
                        Log.d(TAG, "[$callId] Launching async cleanup from onReceive.")
                        handleCallDisconnectedInternal(callId, cause)
                    }
                    callActions.switchAudioChannel.onReceive { e ->
                        Log.i(TAG, "[$callId] Action: switchAudioChannel(${e.name}) received")
                        executeControlAction({ callControlScope.requestEndpointChange(e) })
                    }
                }
            }
        } finally {
            Log.d(TAG, "[$callId] Action handler loop finished.")
        }
    }

    /** Handles the success/failure result of a CallControlScope action. */
    private fun handleControlResult(
        callId: String,
        result: CallControlResult,
        successState: CallState? = null // Optional state to set on success
    ) {
        when (result) {
            is CallControlResult.Success -> {
                Log.i(TAG, "[$callId] Control action success.")
                if (successState != null) {
                    updateCallDataInternal(callId) {
                        it.copy(callState = successState, callException = null)
                    }
                } else {
                    // Clear previous errors if any
                    updateCallDataInternal(callId) { it.copy(callException = null) }
                }
            }
            is CallControlResult.Error -> {
                Log.e(TAG, "[$callId] Control action failed: ${result.errorCode}")
                updateCallDataInternal(callId) {
                    it.copy(callException = CallException(result.errorCode))
                }
            }
        }
    }

    override fun setCallActive(callId: String) {
        Log.d(TAG, "[$callId] Requesting setCallActive")
        // Use trySend which is non-suspending, good for calls from non-coroutines
        // Or launch if the caller needs confirmation/error handling back
        activeCalls[callId]?.actions?.setActiveChannel?.trySend(Unit)
            ?: Log.w(TAG, "[$callId] setCallActive: No active call found")
    }

    override fun setCallInactive(callId: String) {
        Log.d(TAG, "[$callId] Requesting setCallInactive")
        activeCalls[callId]?.actions?.setInactiveChannel?.trySend(Unit)
            ?: Log.w(TAG, "[$callId] setCallInactive: No active call found")
    }

    override fun toggleGlobalMute(isMuted: Boolean) {
        audioManager.isMicrophoneMute = isMuted
    }

    override fun endCall(callId: String) {
        Log.d(TAG, "[$callId] Requesting endCall")
        activeCalls[callId]?.actions?.disconnectChannel?.trySend(Unit)
            ?: Log.w(TAG, "[$callId] endCall: No active call found, attempting direct cleanup")
    }

    override fun switchCallEndpoint(callId: String, endpoint: CallEndpointCompat) {
        Log.d(TAG, "[$callId] Requesting switchCallEndpoint to ${endpoint.name}")
        activeCalls[callId]?.actions?.switchAudioChannel?.trySend(endpoint)
            ?: Log.w(TAG, "[$callId] switchCallEndpoint: No active call found")
    }

    private fun handleCallDisconnectedInternal(callId: String, cause: DisconnectCause?) {
        Log.i(TAG, "[$callId] Handling disconnection (cause: ${cause})")

        // 1. Update State to DISCONNECTED
        updateCallDataInternal(callId) { it.copy(callState = CallState.DISCONNECTED) }

        // 2. Cancel any previous removal job for this call
        delayedRemovalJobs.remove(callId)?.cancel()

        // 3. Schedule removal job (if the call exists in the list)
        if (_callDataList.value.any { it.callId == callId }) {
            Log.i(TAG, "[$callId] scheduling delayed removal from the call data list.")
            val removalJob =
                lifecycleScope.launch {
                    delay(CALL_REMOVE_DELAY)
                    Log.i(TAG, "[$callId] Removing CallData after delay.")
                    _callDataList.update { list -> list.filterNot { it.callId == callId } }
                    // 4. Cancel the main call handling job associated with this callId
                    // This stops the handleCallActions loop and collectors for this specific call.
                    // It's safe to cancel even if already completing.
                    activeCalls[callId]
                        ?.job
                        ?.cancel("Call disconnected, cleaning up controller job")
                    activeCalls.remove(callId) // Clean up controller map
                    delayedRemovalJobs.remove(callId) // Clean up self
                }
            delayedRemovalJobs[callId] = removalJob
        } else {
            Log.i(
                TAG,
                "[$callId] CallData not found in list during disconnect, removing controller."
            )
        }
    }

    /** Ensures active call resources are removed if a call fails unexpectedly. */
    private fun ensureActiveCallRemoved(callId: String) {
        activeCalls.remove(callId)?.job?.cancel("Ensuring removal after failure")
        delayedRemovalJobs.remove(callId)?.cancel()
        _callDataList.update { list -> list.filterNot { it.callId == callId } }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        Log.d(TAG, "onBind: Received bind request from $intent")
        return localBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Received unbind request from $intent")
        // work around a stupid bug where InCallService assumes that the unbind request can only
        // come from telecom
        if (intent?.action != null) {
            return super.onUnbind(intent)
        }
        return false
    }
}
