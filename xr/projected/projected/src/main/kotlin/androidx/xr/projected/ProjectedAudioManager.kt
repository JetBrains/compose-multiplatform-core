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

package androidx.xr.projected

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.xr.projected.ProjectedAudioManager.Companion.create

/**
 * Class providing Projected device-specific functionality for clients using the
 * [android.media.AudioManager].
 *
 * This class provides a way to connect to and communicate with the ProjectedAudioManager, which is
 * expected to be running in the host process.
 *
 * Use [create] to create an instance of this class. The connection is managed automatically with
 * the provided [LifecycleOwner].
 */
public class ProjectedAudioManager
private constructor(private val projectedService: IProjectedService) {

    /**
     * Retrieves the supported audio capture configurations for the Projected device.
     *
     * @return A list of [ProjectedAudioConfig] objects containing the supported audio capture
     *   configurations.
     * @throws IllegalStateException if the supported audio configs could not be retrieved.
     */
    public fun getSupportedAudioCaptureConfigs(): List<ProjectedAudioConfig> {
        val projectedAudioConfig = projectedService.supportedCaptureAudioConfigs

        checkNotNull(projectedAudioConfig) { "Supported audio capture configs are null." }

        return projectedAudioConfig
            .map { audioConfig ->
                ProjectedAudioConfig(
                    audioConfig.sourceType,
                    audioConfig.sampleRatesHz,
                    audioConfig.channelCounts,
                )
            }
            .toList()
    }

    public companion object {
        /**
         * Connects to the service providing features for Projected devices and returns the
         * [ProjectedAudioManager] when the connection is established.
         *
         * @param context The context to use for binding to the service.
         * @param lifecycleOwner The lifecycle owner to scope the service connection to.
         * @return A [ProjectedAudioManager] instance.
         */
        @JvmStatic
        public suspend fun create(
            context: Context,
            lifecycleOwner: LifecycleOwner,
        ): ProjectedAudioManager =
            ProjectedAudioManager(ProjectedServiceConnection.connect(context, lifecycleOwner))
    }
}
