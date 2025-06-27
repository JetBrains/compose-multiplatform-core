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

package androidx.core.telecom.reference.viewModel

import androidx.core.telecom.CallAttributesCompat.Companion.CALL_TYPE_VIDEO_CALL
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.reference.CallRepository
import androidx.core.telecom.reference.model.CallState
import androidx.core.telecom.reference.model.InCallItemUiState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InCallViewModel(private val callRepository: CallRepository = CallRepository()) : ViewModel() {
    val uiState: StateFlow<List<InCallItemUiState>> =
        callRepository.callDataFlow
            .map { calls ->
                calls.map { call ->
                    InCallItemUiState(
                        callId = call.callId.toString(),
                        displayName = call.attributes.displayName.toString(),
                        callState = call.callState,
                        isMuted = call.isMuted,
                        isVideoCall = call.attributes.callType == CALL_TYPE_VIDEO_CALL,
                        isCallActive = call.callState == CallState.ACTIVE,
                        currentEndpoint = call.currentEndpoint,
                        availableEndpoints = call.availableEndpoints,
                        hasCallException = call.callException
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun holdCall(callId: String) {
        viewModelScope.launch { callRepository.setCallInactive(callId) }
    }

    fun endCall(callId: String) {
        viewModelScope.launch { callRepository.endCall(callId) }
    }

    fun setCallActive(callId: String) {
        viewModelScope.launch { callRepository.setCallActive(callId) }
    }

    fun toggleGlobalMute(isMuted: Boolean) {
        callRepository.toggleGlobalMute(isMuted)
    }

    fun onAudioRouteClicked(callId: String, endpoint: CallEndpointCompat) {
        viewModelScope.launch { callRepository.switchCallEndpoint(callId, endpoint) }
    }
}
