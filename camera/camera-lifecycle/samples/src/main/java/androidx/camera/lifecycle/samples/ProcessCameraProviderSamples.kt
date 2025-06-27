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

package androidx.camera.lifecycle.samples

import android.content.Context
import android.os.Handler
import androidx.annotation.Sampled
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.ProcessCameraProvider.Companion.configureInstance
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

@Sampled
fun bindConcurrentCameraSample(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    frontPreviewView: PreviewView,
    backPreviewView: PreviewView
) {
    var cameraSelectorPrimary: CameraSelector? = null
    var cameraSelectorSecondary: CameraSelector? = null
    for (cameraInfoList in cameraProvider.availableConcurrentCameraInfos) {
        for (cameraInfo in cameraInfoList) {
            if (cameraInfo.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                cameraSelectorPrimary = cameraInfo.getCameraSelector()
            } else if (cameraInfo.lensFacing == CameraSelector.LENS_FACING_BACK) {
                cameraSelectorSecondary = cameraInfo.getCameraSelector()
            }
        }
    }
    if (cameraSelectorPrimary == null || cameraSelectorSecondary == null) {
        return
    }
    val previewFront = Preview.Builder().build()
    previewFront.surfaceProvider = frontPreviewView.getSurfaceProvider()
    val primary =
        SingleCameraConfig(
            cameraSelectorPrimary,
            UseCaseGroup.Builder().addUseCase(previewFront).build(),
            lifecycleOwner
        )
    val previewBack = Preview.Builder().build()
    previewBack.surfaceProvider = backPreviewView.getSurfaceProvider()
    val secondary =
        SingleCameraConfig(
            cameraSelectorSecondary,
            UseCaseGroup.Builder().addUseCase(previewBack).build(),
            lifecycleOwner
        )
    cameraProvider.bindToLifecycle(listOf(primary, secondary))
}

@Sampled
fun getCameraXConfigSample(executor: Executor, handler: Handler) {
    @Override
    fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setCameraExecutor(executor)
            .setSchedulerHandler(handler)
            .build()
    }
}

@Sampled
fun configureAndGetInstanceSample(executor: Executor, scheduleHandler: Handler) {
    var configured = false // Whether the camera provider has been configured or not.

    suspend fun getInstance(context: Context): ProcessCameraProvider {
        synchronized(CameraProvider::class.java) {
            if (!configured) {
                configured = true
                configureInstance(
                    CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                        .setCameraExecutor(executor)
                        .setSchedulerHandler(scheduleHandler)
                        .build()
                )
            }
        }
        return ProcessCameraProvider.getInstance(context).await()
    }
}
