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

import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallException

/**
 * Represents the complete state and attributes of a single ongoing or recently completed call.
 *
 * This data class holds both the static information defined when the call was initiated (like
 * attributes) and the dynamic, mutable state that changes during the call's lifecycle (like call
 * state, mute status, endpoints, and potential errors).
 *
 * @property callId A unique identifier for this specific call instance.
 * @property attributes The immutable attributes of the call, such as display name, address (URI),
 *   direction (incoming/outgoing), and call type (audio/video). Defined using
 *   [CallAttributesCompat].
 * @property callState The current dynamic state of the call (e.g., [CallState.DIALING],
 *   [CallState.ACTIVE], [CallState.DISCONNECTED]). This property is mutable (`var`) as the state
 *   changes.
 * @property isMuted Indicates whether the microphone associated with the call is currently muted.
 * @property currentEndpoint The currently active audio endpoint for the call (e.g., speakerphone,
 *   earpiece, Bluetooth device). Nullable if no specific endpoint is active or known. Represented
 *   by [CallEndpointCompat].
 * @property availableEndpoints A list of audio endpoints that are currently available for selection
 *   for this call. Nullable if the information is not available. Represented by a List of
 *   [CallEndpointCompat].
 * @property callException Holds any exception that might have occurred, often indicating the reason
 *   for disconnection or failure. Nullable if no error has occurred. Represented by
 *   [CallException].
 */
data class CallData(
    val callId: String,
    val attributes: CallAttributesCompat,
    var callState: CallState,
    val isMuted: Boolean,
    val currentEndpoint: CallEndpointCompat?,
    val availableEndpoints: List<CallEndpointCompat>?,
    val callException: CallException?,
)
