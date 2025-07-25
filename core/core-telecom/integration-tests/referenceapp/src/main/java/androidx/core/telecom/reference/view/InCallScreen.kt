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

package androidx.core.telecom.reference.view

import android.os.ParcelUuid
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.reference.model.CallState
import androidx.core.telecom.reference.model.InCallItemUiState
import androidx.core.telecom.reference.viewModel.InCallViewModel
import java.util.UUID

/**
 * Returns the appropriate [ImageVector] icon based on the type of the [CallEndpointCompat].
 *
 * @param endpoint The call endpoint to get the icon for.
 * @return The corresponding icon, or a default 'QuestionMark' icon for unknown types.
 */
fun getEndpointIcon(endpoint: CallEndpointCompat): ImageVector {
    return when (endpoint.type) {
        CallEndpointCompat.TYPE_SPEAKER -> Icons.Filled.Speaker
        CallEndpointCompat.TYPE_BLUETOOTH -> Icons.Filled.Bluetooth
        CallEndpointCompat.TYPE_WIRED_HEADSET -> Icons.Filled.Headset
        CallEndpointCompat.TYPE_EARPIECE -> Icons.Filled.PhoneInTalk
        else -> {
            Icons.Filled.QuestionMark
        }
    }
}

/**
 * Returns a user-friendly string name based on the type of the [CallEndpointCompat].
 *
 * @param endpoint The call endpoint to get the name for.
 * @return A human-readable string representing the endpoint type (e.g., "Speaker", "Bluetooth").
 */
fun getEndpointName(endpoint: CallEndpointCompat): String {
    return when (endpoint.type) {
        CallEndpointCompat.TYPE_SPEAKER -> "Speaker"
        CallEndpointCompat.TYPE_BLUETOOTH -> "Bluetooth"
        CallEndpointCompat.TYPE_WIRED_HEADSET -> "Headset"
        CallEndpointCompat.TYPE_EARPIECE -> "Earpiece"
        else -> {
            "Unknown"
        }
    }
}

/**
 * The main composable function for the In-Call screen.
 *
 * Observes the list of current calls from the [InCallViewModel] and displays them using
 * [InCallListContent]. It also handles displaying an [AlertDialog] if any call enters an error
 * state (indicated by [InCallItemUiState.hasCallException]).
 *
 * @param inCallViewModel The ViewModel providing the state for the in-call screen.
 */
@Composable
fun InCallScreen(inCallViewModel: InCallViewModel) {
    val uiState by inCallViewModel.uiState.collectAsState()
    var showErrorDialog by remember { mutableStateOf(false) }
    var displayedErrorCallId by remember { mutableStateOf<String?>(null) }

    uiState.forEach { call ->
        if (call.hasCallException != null && displayedErrorCallId != call.callId) {
            LaunchedEffect(call.callId) {
                if (displayedErrorCallId == null || displayedErrorCallId != call.callId) {
                    showErrorDialog = true
                    displayedErrorCallId = call.callId
                }
            }
        }
    }

    if (showErrorDialog) {
        val errorCall = uiState.firstOrNull { it.callId == displayedErrorCallId }
        errorCall?.let {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text("Call Error") },
                text = { Text(it.hasCallException?.message ?: "An unknown error occurred.") },
                confirmButton = { Button(onClick = { showErrorDialog = false }) { Text("OK") } }
            )
        }
    }

    InCallListContent(
        calls = uiState,
        onMuteClick = inCallViewModel::toggleGlobalMute,
        onEndCallClick = inCallViewModel::endCall,
        onHoldClick = inCallViewModel::holdCall,
        onUnholdClick = inCallViewModel::setCallActive,
        onAudioRouteClick = inCallViewModel::onAudioRouteClicked
    )
}

/**
 * Displays the list of active/held calls using a [LazyColumn].
 *
 * If the list of calls is empty, it displays a "No active calls" message. Otherwise, it renders a
 * [CallCard] for each call in the list.
 *
 * @param calls The list of [InCallItemUiState] representing the calls to display.
 * @param onMuteClick Lambda function invoked when the mute/unmute button is clicked.
 * @param onEndCallClick Lambda function invoked when the end call button is clicked for a specific
 *   call.
 * @param onHoldClick Lambda function invoked when the hold button is clicked for a specific call.
 * @param onUnholdClick Lambda function invoked when the unhold/resume button is clicked for a
 *   specific call.
 * @param onAudioRouteClick Lambda function invoked when an audio route is selected for a specific
 *   call.
 */
@Composable
fun InCallListContent(
    calls: List<InCallItemUiState>,
    onMuteClick: (Boolean) -> Unit,
    onEndCallClick: (String) -> Unit,
    onHoldClick: (String) -> Unit,
    onUnholdClick: (String) -> Unit,
    onAudioRouteClick: (String, CallEndpointCompat) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier.fillMaxSize().padding(16.dp).background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(calls, key = { it.callId }) { call ->
            CallCard(
                uiState = call,
                onMuteClick = onMuteClick,
                onEndCallClick = onEndCallClick,
                onHoldClick = onHoldClick,
                onUnholdClick = onUnholdClick,
                onAudioRouteClick = onAudioRouteClick
            )
        }
        if (calls.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No active calls", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Displays the UI for a single call, including its state, display name, and action buttons.
 *
 * Shows information like display name and call state. Provides buttons for mute/unmute,
 * hold/unhold, ending the call, and changing the audio route via a dialog.
 *
 * @param uiState The [InCallItemUiState] containing the data for this specific call card.
 * @param onMuteClick Lambda function invoked when the mute/unmute button is clicked.
 * @param onEndCallClick Lambda function invoked when the end call button is clicked.
 * @param onHoldClick Lambda function invoked when the hold button is clicked.
 * @param onUnholdClick Lambda function invoked when the unhold/resume button is clicked.
 * @param onAudioRouteClick Lambda function invoked when an audio route is selected from the dialog.
 */
@Composable
fun CallCard(
    uiState: InCallItemUiState,
    onMuteClick: (Boolean) -> Unit,
    onEndCallClick: (String) -> Unit,
    onHoldClick: (String) -> Unit,
    onUnholdClick: (String) -> Unit,
    onAudioRouteClick: (String, CallEndpointCompat) -> Unit,
) {
    var showAudioRouteDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = uiState.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.callState.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Audio Route Icon
            if (uiState.currentEndpoint != null) {
                IconButton(
                    onClick = { showAudioRouteDialog = true },
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                ) {
                    Icon(
                        imageVector = getEndpointIcon(uiState.currentEndpoint),
                        contentDescription =
                            "Audio Route: ${getEndpointName(uiState.currentEndpoint)}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text("Error getting audio routes")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ActionIconButton(
                    icon = if (uiState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (uiState.isMuted) "Unmute" else "Mute",
                    onClick = { onMuteClick(!uiState.isMuted) }
                )
                if (uiState.callState == CallState.ACTIVE) {
                    ActionIconButton(
                        icon = Icons.Filled.Pause,
                        contentDescription = "Hold",
                        onClick = { onHoldClick(uiState.callId) }
                    )
                }
                if (uiState.callState == CallState.INACTIVE) {
                    ActionIconButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = "Unhold",
                        onClick = { onUnholdClick(uiState.callId) }
                    )
                }
                ActionIconButton(
                    icon = Icons.Filled.CallEnd,
                    contentDescription = "End Call",
                    onClick = { onEndCallClick(uiState.callId) },
                    tint = Color.Red
                )
            }
        }
    }
    if (showAudioRouteDialog && uiState.availableEndpoints != null) {
        AlertDialog(
            onDismissRequest = { showAudioRouteDialog = false },
            title = { Text("Select Audio Route") },
            text = {
                Column {
                    uiState.availableEndpoints.forEach { endpoint ->
                        TextButton(
                            onClick = {
                                onAudioRouteClick(uiState.callId, endpoint)
                                showAudioRouteDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = getEndpointIcon(endpoint),
                                contentDescription = null,
                                tint =
                                    if (endpoint == uiState.currentEndpoint)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getEndpointName(endpoint),
                                color =
                                    if (endpoint == uiState.currentEndpoint)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioRouteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).clip(CircleShape)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
}

// --- Previews Start Here ---

// Helper function to create sample endpoints for previews
fun createSampleEndpoint(type: Int, name: String): CallEndpointCompat {
    // CallEndpointCompat requires a name, type, and identifier Uri
    return CallEndpointCompat(name, type, ParcelUuid.fromString(UUID.randomUUID().toString()))
}

// Sample data for previews
val sampleEarpiece = createSampleEndpoint(CallEndpointCompat.TYPE_EARPIECE, "Earpiece")
val sampleSpeaker = createSampleEndpoint(CallEndpointCompat.TYPE_SPEAKER, "Speaker")
val sampleBluetooth = createSampleEndpoint(CallEndpointCompat.TYPE_BLUETOOTH, "Bluetooth Headset")
val sampleEndpoints = listOf(sampleEarpiece, sampleSpeaker, sampleBluetooth)

val sampleCallActive =
    InCallItemUiState(
        callId = "call_1",
        displayName = "Alice Wonderland",
        callState = CallState.ACTIVE,
        isMuted = false,
        currentEndpoint = sampleEarpiece,
        availableEndpoints = sampleEndpoints,
        hasCallException = null,
        isSpeakerOn = false,
        isVideoCall = false,
        isCallActive = true
    )

val sampleCallMuted =
    InCallItemUiState(
        callId = "call_2",
        displayName = "Bob The Builder",
        callState = CallState.ACTIVE,
        isMuted = true,
        currentEndpoint = sampleSpeaker,
        availableEndpoints = sampleEndpoints,
        hasCallException = null,
        isSpeakerOn = false,
        isVideoCall = true,
        isCallActive = false
    )

val sampleCallInactive =
    InCallItemUiState(
        callId = "call_3",
        displayName = "Charlie Chaplin",
        callState = CallState.INACTIVE, // Or CallState.HOLDING if that's used
        isMuted = false,
        currentEndpoint = sampleBluetooth,
        availableEndpoints = sampleEndpoints,
        hasCallException = null,
        isSpeakerOn = false,
        isVideoCall = false,
        isCallActive = false
    )

val sampleCallDialing =
    InCallItemUiState(
        callId = "call_4",
        displayName = "Diana Prince",
        callState = CallState.DIALING,
        isMuted = false,
        currentEndpoint = sampleEarpiece,
        availableEndpoints = sampleEndpoints,
        hasCallException = null,
        isSpeakerOn = true,
        isVideoCall = true,
        isCallActive = false
    )

// Preview for a single ActionIconButton
@Preview(showBackground = true)
@Composable
fun ActionIconButtonPreview() {
    // If you have a theme, wrap the preview content in it
    // YourAppTheme {
    MaterialTheme { // Use MaterialTheme if you don't have a specific app theme
        ActionIconButton(icon = Icons.Filled.Mic, contentDescription = "Mute", onClick = {})
    }
}

// Preview for a single CallCard (Active Call)
@Preview(showBackground = true, name = "Call Card Active")
@Composable
fun CallCardActivePreview() {
    MaterialTheme {
        CallCard(
            uiState = sampleCallActive,
            onMuteClick = {},
            onEndCallClick = {},
            onHoldClick = {},
            onUnholdClick = {},
            onAudioRouteClick = { _, _ -> }
        )
    }
}

// Preview for a single CallCard (Muted Call)
@Preview(showBackground = true, name = "Call Card Muted")
@Composable
fun CallCardMutedPreview() {
    MaterialTheme {
        CallCard(
            uiState = sampleCallMuted,
            onMuteClick = {},
            onEndCallClick = {},
            onHoldClick = {},
            onUnholdClick = {},
            onAudioRouteClick = { _, _ -> }
        )
    }
}

// Preview for a single CallCard (Inactive/Held Call)
@Preview(showBackground = true, name = "Call Card Inactive")
@Composable
fun CallCardInactivePreview() {
    MaterialTheme {
        CallCard(
            uiState = sampleCallInactive,
            onMuteClick = {},
            onEndCallClick = {},
            onHoldClick = {},
            onUnholdClick = {},
            onAudioRouteClick = { _, _ -> }
        )
    }
}

// Preview for the list content with multiple calls
@Preview(showBackground = true, name = "InCall List Content")
@Composable
fun InCallListContentPreview() {
    MaterialTheme {
        InCallListContent(
            calls =
                listOf(sampleCallActive, sampleCallMuted, sampleCallInactive, sampleCallDialing),
            onMuteClick = {},
            onEndCallClick = {},
            onHoldClick = {},
            onUnholdClick = {},
            onAudioRouteClick = { _, _ -> }
        )
    }
}

// Preview for the list content when empty
@Preview(showBackground = true, name = "InCall List Empty")
@Composable
fun InCallListContentEmptyPreview() {
    MaterialTheme {
        InCallListContent(
            calls = emptyList(),
            onMuteClick = {},
            onEndCallClick = {},
            onHoldClick = {},
            onUnholdClick = {},
            onAudioRouteClick = { _, _ -> }
        )
    }
}
