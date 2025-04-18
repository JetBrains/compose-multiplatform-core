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

package androidx.camera.lifecycle.samples

import android.content.Context
import androidx.annotation.Sampled
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.LifecycleCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

@Sampled
suspend fun configureAndCreateInstances(
    context1: Context,
    context2: Context,
    lifecycleOwner1: LifecycleOwner,
    lifecycleOwner2: LifecycleOwner,
    executor1: Executor,
    executor2: Executor,
    useCase1: UseCase,
    useCase2: UseCase
) {
    suspend fun createInstance(context: Context, executor: Executor): LifecycleCameraProvider {
        val config =
            CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                .setCameraExecutor(executor)
                .build()
        return LifecycleCameraProvider.createInstance(context, config)
    }

    val cameraProvider1 = createInstance(context1, executor1)
    cameraProvider1.bindToLifecycle(lifecycleOwner1, CameraSelector.DEFAULT_FRONT_CAMERA, useCase1)

    // ...
    // Switch to different lifecycle.

    val cameraProvider2 = createInstance(context2, executor2)
    cameraProvider2.bindToLifecycle(lifecycleOwner2, CameraSelector.DEFAULT_BACK_CAMERA, useCase2)
}
