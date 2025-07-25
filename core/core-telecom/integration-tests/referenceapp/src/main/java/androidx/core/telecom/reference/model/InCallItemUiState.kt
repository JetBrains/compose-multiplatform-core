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

package androidx.core.telecom.reference.model

import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException

/**
 * Represents the UI-specific state for a single call item displayed on the In-Call screen.
 *
 * This data class transforms the raw call data (like [CallData]) into a format directly usable by
 * the UI composable responsible for rendering information and controls for one specific ongoing or
 * held call.
 *
 * @property callId The unique identifier for the call this UI state represents.
 * @property displayName The name associated with the call, suitable for display to the user.
 * @property callState The current state of the call (e.g., ACTIVE, INACTIVE/HOLDING), represented
 *   by the [CallState] enum.
 * @property isMuted A boolean flag indicating if the microphone is currently muted for this call.
 * @property isSpeakerOn A boolean flag indicating if the speakerphone audio route is currently
 *   active for this call. Defaults to `false`.
 * @property isVideoCall A boolean flag indicating if this is a video call.
 * @property isCallActive A convenience boolean flag derived from [callState], typically `true` if
 *   the call is in a state where communication is possible or being established (e.g., ACTIVE,
 *   DIALING, RINGING), and `false` otherwise (e.g., INACTIVE, DISCONNECTED).
 * @property currentEndpoint The currently selected audio endpoint for this call (e.g., earpiece,
 *   speaker, Bluetooth). Nullable if unknown or not applicable. Represented by
 *   [CallEndpointCompat].
 * @property availableEndpoints A list of audio endpoints the user can switch to for this call.
 *   Nullable if the list is not available or empty. Represented by a List of [CallEndpointCompat].
 * @property hasCallException Contains the [CallException] associated with the call if an error
 *   occurred (e.g., disconnection reason), otherwise `null`. Defaults to `null`.
 */
data class InCallItemUiState(
    val callId: String,
    val displayName: String,
    val callState: CallState,
    val isMuted: Boolean,
    val isSpeakerOn: Boolean = false,
    val isVideoCall: Boolean,
    val isCallActive: Boolean,
    val currentEndpoint: CallEndpointCompat?,
    val availableEndpoints: List<CallEndpointCompat>?,
    val hasCallException: CallException? = null
)
