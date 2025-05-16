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

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * [DialerActivityViewModel] is a [ViewModel] responsible for handling and processing new [Intent]s
 * directed towards the Dialer activity.
 *
 * It exposes a [newIntentFlow] as a [kotlinx.coroutines.flow.Flow] of [Intent]s, allowing other
 * components to observe and react to new intents received by the activity.
 *
 * The [processNewIntent] function is used to receive incoming [Intent]s and emit them into the
 * [newIntentFlow]. This decouples the source of the intent (e.g., a broadcast receiver, another
 * activity) from the components that need to handle it within the Dialer activity.
 */
class DialerActivityViewModel : ViewModel() {
    private val _newIntentChannel = Channel<Intent>(Channel.BUFFERED)
    val newIntentFlow = _newIntentChannel.receiveAsFlow()

    fun processNewIntent(intent: Intent) {
        viewModelScope.launch {
            Log.d("ActivityViewModel", "Received intent, sending to channel")
            _newIntentChannel.send(intent)
        }
    }
}
