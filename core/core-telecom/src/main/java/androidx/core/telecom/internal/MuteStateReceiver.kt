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

package androidx.core.telecom.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * This class is responsible for listening to changes in the global mute state for sdks 33 and
 * lower. Every global mute state change is echoed out via the
 * [androidx.core.telecom.CallControlScope.isMuted] flow.
 */
internal class MuteStateReceiver() : BroadcastReceiver() {
    private var mOnGlobalMuteChanged: ((Boolean) -> Unit)? = null

    constructor(onGlobalMuteChanged: (Boolean) -> Unit) : this() {
        mOnGlobalMuteChanged = onGlobalMuteChanged
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (AudioManager.ACTION_MICROPHONE_MUTE_CHANGED == intent?.action) {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (mOnGlobalMuteChanged != null) {
                mOnGlobalMuteChanged!!(audioManager.isMicrophoneMute)
            }
        }
    }
}
