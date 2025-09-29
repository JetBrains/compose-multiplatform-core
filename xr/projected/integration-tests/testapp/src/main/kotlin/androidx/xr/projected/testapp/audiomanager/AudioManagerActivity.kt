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

package androidx.xr.projected.testapp.audiomanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.xr.projected.ProjectedAudioConfig
import androidx.xr.projected.ProjectedAudioManager

/**
 * The AudioManagerActivity creates a ProjectedAudioManager on the device and queries the
 * ProjectedAudioConfigs it will output them on the phone display.
 */
class AudioManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CreateProjectedAudioManager(this) }
    }

    @Composable
    private fun CreateProjectedAudioManager(activity: ComponentActivity) {
        val configs = remember { mutableStateListOf<ProjectedAudioConfig>() }

        LaunchedEffect(Unit) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val manager = ProjectedAudioManager.create(activity, activity)
                configs.apply {
                    configs.clear()
                    configs.addAll(manager.getSupportedAudioCaptureConfigs())
                }
                Log.i(TAG, "Found ${configs.count()} audio configs")
            }
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Configs:")
            for (config in configs) {
                Text("Config: $config")
            }
        }
    }

    private companion object {
        const val TAG = "AudioManagerActivity"
    }
}
