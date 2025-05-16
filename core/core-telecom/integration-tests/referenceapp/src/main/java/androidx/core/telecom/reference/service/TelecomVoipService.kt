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

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.telecom.DisconnectCause
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallAttributesCompat.Companion.DIRECTION_INCOMING
import androidx.core.telecom.CallAttributesCompat.Companion.DIRECTION_OUTGOING
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException
import androidx.core.telecom.CallsManager
import androidx.core.telecom.CallsManager.Companion.CAPABILITY_SUPPORTS_VIDEO_CALLING
import androidx.core.telecom.reference.CallNotificationManager
import androidx.core.telecom.reference.Constants.ACTION_ANSWER_CALL
import androidx.core.telecom.reference.Constants.ACTION_DECLINE_CALL
import androidx.core.telecom.reference.Constants.ACTION_HANGUP_CALL
import androidx.core.telecom.reference.Constants.EXTRA_REMOTE_USER_NAME
import androidx.core.telecom.reference.Constants.EXTRA_SIMULATED_NUMBER
import androidx.core.telecom.reference.model.CallData
import androidx.core.telecom.reference.model.CallState
import androidx.core.telecom.reference.view.loadPhoneNumberPrefix
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
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
    private lateinit var mCallNotificationManager: CallNotificationManager
    var mContext: Context? = null
    private val mCallsManager: CallsManager by lazy { CallsManager(mContext!!) }
    private val mAudioManager: AudioManager by lazy {
        mContext!!.getSystemService(AUDIO_SERVICE) as AudioManager
    }
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
    private val mActiveCalls = mutableMapOf<String, CallController>()
    // Map to hold jobs that delay the removal of call data from the UI.  2 second delay was
    // added to show calls are disconnected before removal.
    private val mDelayedRemovalJobs = mutableMapOf<String, Job>()
    private var mIsCurrentlyInForeground = false // Flag to track foreground state

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
        mCallsManager.registerAppWithTelecom(CAPABILITY_SUPPORTS_VIDEO_CALLING)
        mCallNotificationManager = CallNotificationManager(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy service destroyed")
        mActiveCalls.values.forEach { it.job.cancel() }
        mActiveCalls.clear()
        mDelayedRemovalJobs.values.forEach { it.cancel() }
        mDelayedRemovalJobs.clear()
        // Reset state
        _callDataList.value = emptyList()
    }

    override fun addCall(callAttributes: CallAttributesCompat, notificationId: Int) {
        val callId = notificationId.toString()
        Log.d(TAG, "[$callId] addCall")
        val callActions = CallActions()
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
                        mCallsManager.addCall(
                            callAttributes,
                            { /* onAnswer */
                                updateCallDataInternal(callId) {
                                    val updatedData = it.copy(callState = CallState.ACTIVE)
                                    mCallNotificationManager.showOrUpdateCallNotification(
                                        updatedData
                                    )
                                    updatedData
                                }
                            },
                            { /* onDisconnect */
                                handleCallDisconnectedInternal(callId, it)
                            },
                            { /* onSetActive */
                                updateCallDataInternal(callId) {
                                    val updatedData = it.copy(callState = CallState.ACTIVE)
                                    mCallNotificationManager.showOrUpdateCallNotification(
                                        updatedData
                                    )
                                    updatedData
                                }
                            },
                            { /* onSetInactive */
                                updateCallDataInternal(callId) {
                                    val updatedData = it.copy(callState = CallState.INACTIVE)
                                    mCallNotificationManager.showOrUpdateCallNotification(
                                        updatedData
                                    )
                                    updatedData
                                }
                            },
                        ) {
                            Log.d(TAG, "[$callId] CallControlScope active")
                            val callControlScope: CallControlScope = this
                            launch {
                                Log.d(TAG, "[$callId] CallControlScope: init block")
                                initializeAndMonitorCall(callId, callAttributes)
                            }
                            launch {
                                Log.d(TAG, "[$callId] CallControlScope: handle actions")
                                // Keep this coroutine alive by handling actions until cancelled
                                handleCallActions(callId, callActions, callControlScope)
                            }
                            launch {
                                Log.d(TAG, "[$callId] CallControlScope: setCallActive")
                                if (callAttributes.direction == DIRECTION_OUTGOING) {
                                    // For outgoing calls, simulate the remote user connecting
                                    delay(CALL_CONNECTS_DELAY)
                                    setCallActive(callId)
                                } else {
                                    // For incoming calls, the call can only be answered via the
                                    // notification. This block will only be entered if onAnswer
                                    // is clicked on the call-style notification
                                    setCallActive(callId)
                                }
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
        mActiveCalls[callId] = CallController(callJob, callActions)
    }

    /**
     * Executed within the CallControlScope after Telecom successfully adds the call. Initializes
     * the CallData state and starts collecting updates from Telecom flows.
     */
    private fun CallControlScope.initializeAndMonitorCall(
        callId: String,
        callAttributes: CallAttributesCompat,
    ) {
        // Set the initial call state
        val initialState =
            when (callAttributes.direction) {
                DIRECTION_OUTGOING -> CallState.DIALING
                else -> CallState.RINGING
            }

        //  Add Initial CallData to the StateFlow
        val initialCallData = createInitialCallData(callId, callAttributes, initialState)
        Log.d(TAG, "initAndMonCall: initialCallData=[$initialCallData]")

        _callDataList.update { currentList ->
            // Avoid duplicates if somehow added already
            if (currentList.any { it.callId == callId }) currentList
            else currentList + initialCallData
        }

        // If this is an outgoing call, start the foreground service
        if (callAttributes.direction == DIRECTION_OUTGOING) {
            val notificationId = callId.toInt()
            startForegroundWithNotification(
                notificationId,
                mCallNotificationManager.buildOutgoingCallNotification(
                    notificationId,
                    callAttributes
                )
            )
        }

        // Launch Collectors for subsequent state changes within the CallControlScope
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

    private fun updateCallDataInternal(callId: String, update: (CallData) -> CallData): CallData? {
        var updatedData: CallData? = null

        _callDataList.update { currentList ->
            currentList.map { callData ->
                if (callData.callId == callId) {
                    val updated = update(callData) // Perform update
                    updatedData = updated
                    Log.v(TAG, "[$callId] Updating CallData: $updated")
                    updated
                } else {
                    callData
                }
            }
        }

        if (updatedData == null) {
            Log.w(TAG, "[$callId] CallData not found during internal update.")
        }

        // Return the result (which is null if not found, or the updated CallData if found)
        return updatedData
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
        successState: CallState? = null
    ) {
        when (result) {
            is CallControlResult.Success -> {
                Log.i(TAG, "[$callId] Control action success.")
                var dataToNotify: CallData? = null
                if (successState != null) {
                    dataToNotify =
                        updateCallDataInternal(callId) {
                            it.copy(callState = successState, callException = null)
                        }
                } else {
                    // Clear previous errors if any
                    dataToNotify = updateCallDataInternal(callId) { it.copy(callException = null) }
                }
                // *** Delegate notification update if state changed ***
                dataToNotify?.let { mCallNotificationManager.showOrUpdateCallNotification(it) }
            }
            is CallControlResult.Error -> {
                Log.e(TAG, "[$callId] Control action failed: ${result.errorCode}")
                val errorData =
                    updateCallDataInternal(callId) {
                        it.copy(callException = CallException(result.errorCode))
                    }
                // *** Delegate notification update for error state ***
                errorData?.let { mCallNotificationManager.showOrUpdateCallNotification(it) }
            }
        }
    }

    override fun setCallActive(callId: String) {
        Log.d(TAG, "[$callId] Requesting setCallActive")
        // Use trySend which is non-suspending, good for calls from non-coroutines
        // Or launch if the caller needs confirmation/error handling back
        mActiveCalls[callId]?.actions?.setActiveChannel?.trySend(Unit)
            ?: Log.w(TAG, "[$callId] setCallActive: No active call found")
    }

    override fun setCallInactive(callId: String) {
        Log.d(TAG, "[$callId] Requesting setCallInactive")
        mActiveCalls[callId]?.actions?.setInactiveChannel?.trySend(Unit)
            ?: Log.w(TAG, "[$callId] setCallInactive: No active call found")
    }

    override fun toggleGlobalMute(isMuted: Boolean) {
        mAudioManager.isMicrophoneMute = isMuted
    }

    override fun endCall(callId: String) {
        Log.d(TAG, "[$callId] Requesting endCall")
        mActiveCalls[callId]?.actions?.disconnectChannel?.trySend(Unit)
            ?: {
                Log.w(
                    TAG,
                    "[$callId] endCall: No active call found, attempting" + " direct cleanup"
                )
                ensureActiveCallRemoved(callId)
            }
    }

    override fun switchCallEndpoint(callId: String, endpoint: CallEndpointCompat) {
        Log.d(TAG, "[$callId] Requesting switchCallEndpoint to ${endpoint.name}")
        mActiveCalls[callId]?.actions?.switchAudioChannel?.trySend(endpoint)
            ?: Log.w(TAG, "[$callId] switchCallEndpoint: No active call found")
    }

    private fun handleCallDisconnectedInternal(callId: String, cause: DisconnectCause?) {
        Log.i(TAG, "[$callId] Handling disconnection (cause: ${cause})")
        // Update State to DISCONNECTED
        val callData =
            updateCallDataInternal(callId) { it.copy(callState = CallState.DISCONNECTED) }

        // show call is disconnecting on notification temporarily that will be removed shortly
        callData?.let { mCallNotificationManager.showOrUpdateCallNotification(it) }

        // Cancel any previous removal job for this call
        mDelayedRemovalJobs.remove(callId)?.cancel()

        // Schedule removal job (if the call exists in the list)
        if (_callDataList.value.any { it.callId == callId }) {
            Log.i(TAG, "[$callId] scheduling delayed removal from the call data list.")
            val removalJob =
                lifecycleScope.launch {
                    delay(CALL_REMOVE_DELAY)
                    Log.i(TAG, "[$callId] Removing CallData after delay.")
                    _callDataList.update { list -> list.filterNot { it.callId == callId } }
                    // Cancel the main call handling job associated with this callId
                    // This stops the handleCallActions loop and collectors for this specific call.
                    // It's safe to cancel even if already completing.
                    mActiveCalls[callId]
                        ?.job
                        ?.cancel("Call disconnected, cleaning up controller job")
                    mActiveCalls.remove(callId) // Clean up controller map
                    mDelayedRemovalJobs.remove(callId) // Clean up self
                }
            mDelayedRemovalJobs[callId] = removalJob
        } else {
            Log.i(
                TAG,
                "[$callId] CallData not found in list during disconnect, removing controller."
            )
        }
    }

    /** Ensures active call resources are removed if a call fails unexpectedly. */
    private fun ensureActiveCallRemoved(callId: String) {
        mActiveCalls.remove(callId)?.job?.cancel("Ensuring removal after failure")
        mDelayedRemovalJobs.remove(callId)?.cancel()
        _callDataList.update { list -> list.filterNot { it.callId == callId } }
        checkAndStopForegroundIfNeeded()
        mCallNotificationManager.cancelCallNotification(callId)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        Log.d(TAG, "onBind: Received bind request from $intent")
        return localBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Received unbind request from $intent")
        super.onUnbind(intent)
        // Return 'false' to indicate that onRebind should NOT be used.
        // If new clients bind later, their onServiceConnected method will be called.
        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")
        val callId = intent?.getStringExtra("EXTRA_CALL_ID")
        if (callId != null) {
            when (intent.action) {
                ACTION_ANSWER_CALL -> {
                    Log.i(TAG, "[$callId] Notification Action: Answer")
                    val number = intent.getStringExtra(EXTRA_SIMULATED_NUMBER)
                    val name = intent.getStringExtra(EXTRA_REMOTE_USER_NAME)
                    if (number != null && name != null) {
                        val attributes =
                            CallAttributesCompat(
                                name,
                                (loadPhoneNumberPrefix(mContext!!) + number).toUri(),
                                DIRECTION_INCOMING
                            )
                        val notificationId = callId.toInt()
                        val notification =
                            mCallNotificationManager.buildIncomingCallNotification(
                                notificationId,
                                attributes
                            )
                        startForegroundWithNotification(notificationId, notification)
                        addCall(attributes, notificationId)
                    } else {
                        Log.w(
                            TAG,
                            "addNewIncomingCall received " + "without $EXTRA_SIMULATED_NUMBER"
                        )
                        stopSelf(startId) // Stop this specific start request if data is missing
                    }
                }
                ACTION_DECLINE_CALL -> {
                    Log.i(TAG, "[$callId] Notification Action: Decline")
                    checkAndStopForegroundIfNeeded()
                    mCallNotificationManager.cancelCallNotification(callId)
                }
                ACTION_HANGUP_CALL -> {
                    Log.i(TAG, "[$callId] Notification Action: Hangup")
                    endCall(callId)
                }
            }
        } else {
            Log.w(TAG, "onStartCommand received w/out callId: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(notificationId: Int, notification: Notification?) {
        if (notification == null) {
            Log.w(TAG, "[$notificationId] Attempted to show null notification.")
            return
        }

        Log.i(TAG, "[$notificationId] isCurrentlyInForeground=[$mIsCurrentlyInForeground].")
        try {
            // Use the *first* notification that triggers this to establish the foreground state
            startForeground(
                notificationId,
                notification,
                /* foregroundServiceType */ (ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            )
            mIsCurrentlyInForeground = true // Set flag AFTER successful call
            Log.i(TAG, "[$notificationId] Called startForeground successfully.")

            // It's generally good practice to still ensure the notification is posted via the
            // manager, especially if startForeground behavior across APIs varies slightly or
            // if you want a single code path for posting.
            mCallNotificationManager.immediatelyPostNotification(notificationId, notification)

            Log.w(TAG, "[$notificationId] Re-posting notifications for other active" + " calls.")
            repostExistingCallNotifications(notificationId)
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "[$notificationId] Permission error calling" + " startForeground:${e.message}",
                e
            )
            mIsCurrentlyInForeground = false // Ensure flag is false on error
            // Handle lack of permission
            stopSelf()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "[$notificationId] Generic error calling startForeground:" + " ${e.message}",
                e
            )
            mIsCurrentlyInForeground = false // Ensure flag is false on error
            // Handle other unexpected errors
            stopSelf()
        }
    }

    /**
     * This function is *ONLY* called when a new call is started and becomes the new foreground
     * call. When this happens, the previous call-style notifications are cleared from the
     * notification tray so they need to be reposted manually!
     */
    private fun repostExistingCallNotifications(currentForegroundNotification: Int) {
        val currentCalls = _callDataList.value
        for (callData in currentCalls) {
            val otherCallIdInt = callData.callId.toIntOrNull()
            // Check if it's an "active" call (not disconnected/unknown)
            // AND it's NOT the call that just triggered startForeground
            val isActiveState =
                callData.callState != CallState.DISCONNECTED &&
                    callData.callState != CallState.UNKNOWN
            if (
                otherCallIdInt != null &&
                    otherCallIdInt != currentForegroundNotification &&
                    isActiveState
            ) {
                mCallNotificationManager.showOrUpdateCallNotification(callData)
            }
        }
    }

    /**
     * Checks if the service should remain in the foreground state. If there are no active calls
     * (list is empty or all calls are disconnected), it stops the foreground state and removes the
     * associated notification.
     */
    private fun checkAndStopForegroundIfNeeded() {
        val currentCalls = _callDataList.value // Get the current state

        // Condition: No calls OR all existing calls are in the DISCONNECTED state
        val shouldStopForeground =
            currentCalls.isEmpty() || currentCalls.all { it.callState == CallState.DISCONNECTED }

        if (shouldStopForeground) {
            Log.i(
                TAG,
                "checkAndStopForegroundIfNeeded: No active calls remaining." +
                    " Stopping foreground state."
            )
            // Stop foreground state and remove the *last* associated notification.
            // Note: The specific notification removed depends on the last ID used with
            // startForeground,  but stopping foreground state removes *any* notification tied to
            // it.  We are already cancelling individual notifications in disconnect handlers.
            stopForeground(STOP_FOREGROUND_REMOVE)

            mIsCurrentlyInForeground = false // *** Reset flag only on successful stop ***

            // The service has truly no more work to do so call StopSelf() as well
            stopSelf()
        } else {
            Log.v(
                TAG,
                "checkAndStopForegroundIfNeeded: Active calls still present." +
                    " Maintaining foreground state."
            )
            // You might want to ensure the foreground notification reflects the current primary
            // call if you have multiple active calls and one disconnects, but that logic
            // would likely reside within your notification update flow.
        }
    }
}
