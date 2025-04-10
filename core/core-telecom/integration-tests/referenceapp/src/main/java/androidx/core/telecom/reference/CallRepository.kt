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

package androidx.core.telecom.reference

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.reference.model.CallData
import androidx.core.telecom.reference.service.LocalIcsBinder
import androidx.core.telecom.reference.service.TelecomVoipService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/**
 * Repository class responsible for managing call-related operations and data. It acts as an
 * intermediary between the UI/business logic and the VoipService.
 *
 * This class provides methods to interact with the VoipService, such as adding calls, setting call
 * states, ending calls, switching endpoints, and toggling global mute.
 *
 * It also exposes a SharedFlow of CallData updates for observing call state changes.
 */
class CallRepository {
    // Define a scope for collecting from the service flow
    // Use SupervisorJob so failure in collection doesn't cancel the whole scope
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var serviceCollectionJob: Job? = null // Keep track of the collection job

    data class LocalServiceConnection(
        val isConnected: Boolean,
        val context: Context? = null,
        val serviceConnection: ServiceConnection? = null,
        val connection: LocalIcsBinder? = null
    )

    private val connectedService: MutableStateFlow<LocalServiceConnection> =
        MutableStateFlow(LocalServiceConnection(false))

    companion object {
        val LOG_TAG = "CallRepository"
    }

    /** Bind to the app's [LocalIcsBinder.Connector] Service implementation */
    fun connectService(context: Context) {
        Log.i(LOG_TAG, "connectionService: isConnected=[${connectedService.value.isConnected}]")
        if (connectedService.value.isConnected) return
        val intent = Intent(context, TelecomVoipService::class.java)
        val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    Log.i(LOG_TAG, "connectionService: onServiceConnected")
                    if (service == null) return
                    val localServiceBinder = service as LocalIcsBinder.Connector
                    val voipService = localServiceBinder.getService()
                    connectedService.value =
                        LocalServiceConnection(true, context, this, voipService)
                    serviceCollectionJob?.cancel() // Cancel potential previous job
                    serviceCollectionJob =
                        repositoryScope.launch {
                            Log.i(
                                LOG_TAG,
                                "connectionService: Starting collection from service flow"
                            )
                            voipService.callDataUpdates.collect { dataList ->
                                Log.v(
                                    LOG_TAG,
                                    "connectionService: Received data update from" +
                                        " service: ${dataList.size} calls"
                                )
                                _callDataFlow.value = dataList // Update the repository's StateFlow
                            }
                        }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // Unlikely since the Service is in the same process.
                    // Re-evaluate if the service is moved to another process.
                    Log.w(
                        LOG_TAG,
                        "connectionService: onServiceDisconnected: Unexpected disconnect" +
                            " request"
                    )
                }
            }
        Log.i(LOG_TAG, "connectionService: Binding to VoipService locally")
        context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    /** Disconnect from the app;s [LocalIcsBinder.Connector] Service implementation */
    fun disconnectService() {
        val localConnection = connectedService.getAndUpdate { LocalServiceConnection(false) }
        localConnection.serviceConnection?.let { conn ->
            Log.i(LOG_TAG, "disconnectService: Unbinding from VoipService locally")
            localConnection.context?.unbindService(conn)
        }
    }

    /**
     * Private backing property for the callDataFlow. Holds the StateFlow of CallData updates from
     * the VoipService.
     */
    private val _callDataFlow = MutableStateFlow<List<CallData>>(emptyList())

    /**
     * Public read-only property to access the StateFlow of CallData updates. Allows observing call
     * state changes from the UI/business logic.
     */
    val callDataFlow: StateFlow<List<CallData>> = _callDataFlow.asStateFlow()

    /**
     * Adds an outgoing call using the provided CallAttributesCompat. Delegates the call addition to
     * the VoipService.
     *
     * @param callAttributesCompat The attributes of the outgoing call.
     */
    fun addOutgoingCall(callAttributesCompat: CallAttributesCompat) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "addOutgoingCall: Service is not connected")
            return
        }
        connectedService.value.connection!!.addCall(callAttributesCompat)
    }

    /**
     * Sets the specified call as active. Delegates the call activation to the VoipService.
     *
     * @param callId The ID of the call to set as active.
     */
    fun setCallActive(callId: String) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "setCallActive: Service is not connected")
            return
        }
        connectedService.value.connection!!.setCallActive(callId)
    }

    /**
     * Sets the specified call as inactive. Delegates the call deactivation to the VoipService.
     *
     * @param callId The ID of the call to set as inactive.
     */
    fun setCallInactive(callId: String) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "Service is not connected")
            return
        }
        connectedService.value.connection!!.setCallInactive(callId)
    }

    /**
     * Ends the specified call. Delegates the call ending to the VoipService.
     *
     * @param callId The ID of the call to end.
     */
    fun endCall(callId: String) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "Service is not connected")
            return
        }
        connectedService.value.connection!!.endCall(callId)
    }

    /**
     * Switches the endpoint of the specified call. Delegates the endpoint switching to the
     * VoipService.
     *
     * @param callId The ID of the call.
     * @param endpointCompat The new endpoint for the call.
     */
    fun switchCallEndpoint(callId: String, endpointCompat: CallEndpointCompat) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "Service is not connected")
            return
        }
        connectedService.value.connection!!.switchCallEndpoint(callId, endpointCompat)
    }

    /**
     * Toggles the global mute state. Delegates the mute state toggling to the VoipService.
     *
     * @param isMuted True to mute, false to unmute.
     */
    fun toggleGlobalMute(isMuted: Boolean) {
        if (!connectedService.value.isConnected) {
            Log.w(LOG_TAG, "Service is not connected")
            return
        }
        connectedService.value.connection!!.toggleGlobalMute(isMuted)
    }
}
