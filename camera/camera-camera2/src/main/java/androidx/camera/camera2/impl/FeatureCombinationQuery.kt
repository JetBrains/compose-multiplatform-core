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

package androidx.camera.camera2.impl

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE
import android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import androidx.annotation.RequiresApi
import androidx.camera.camera2.internal.CameraUnavailableExceptionHelper
import androidx.camera.camera2.internal.compat.CameraAccessExceptionCompat
import androidx.camera.camera2.internal.compat.CameraCharacteristicsCompat
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import androidx.camera.camera2.internal.compat.params.DynamicRangeConversions
import androidx.camera.camera2.internal.compat.params.DynamicRangesCompat
import androidx.camera.core.featurecombination.impl.FeatureCombinationQuery
import androidx.camera.core.impl.SessionConfig
import androidx.camera.core.impl.stabilization.StabilizationMode
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.featurecombinationquery.CameraDeviceSetupCompat
import androidx.camera.featurecombinationquery.CameraDeviceSetupCompatFactory

// TODO: b/417839748 - Decide on the appropriate API level for CameraX feature combo API
@RequiresApi(35)
public class FeatureCombinationQueryImpl(
    context: Context,
    private val cameraId: String,
    private val cameraManagerCompat: CameraManagerCompat,
) : FeatureCombinationQuery {
    // creating cameraDeviceSetupCompat may have some latency since it leads to binder call
    private val cameraDeviceSetupCompat by lazy {
        CameraDeviceSetupCompatFactory(context).getCameraDeviceSetupCompat(cameraId)
    }

    /**
     * This non-compat cameraDeviceSetupCompat is required because [CameraDeviceSetupCompat] doesn't
     * expose an API to create CaptureRequest, it should only be used to create a
     * [CaptureRequest.Builder].
     *
     * There may be some latency involved due to binder call under-the-hood, so the value is cached
     * through lazy initialization.
     *
     * While [CameraDeviceSetupCompat] is used for querying the feature combination, the actual
     * creation of the CaptureRequest needed for the query requires the internal CameraDeviceSetup
     * which is not exposed by the Jetpack feature combination query library.
     */
    private val cameraDeviceSetup: CameraDevice.CameraDeviceSetup? by lazy {
        if (cameraManagerCompat.unwrap().isCameraDeviceSetupSupported(cameraId)) {
            cameraManagerCompat.unwrap().getCameraDeviceSetup(cameraId)
        } else {
            null
        }
    }

    private val cameraCharacteristics: CameraCharacteristicsCompat by lazy {
        try {
            cameraManagerCompat.getCameraCharacteristicsCompat(cameraId)
        } catch (e: CameraAccessExceptionCompat) {
            throw CameraUnavailableExceptionHelper.createFrom(e)
        }
    }

    private val dynamicRangeProfiles by lazy {
        DynamicRangesCompat.fromCameraCharacteristics(cameraCharacteristics)
            .toDynamicRangeProfiles()
    }

    override fun isSupported(sessionConfig: SessionConfig): Boolean {
        val outputConfigs = createOutputConfigurations(sessionConfig)

        val camera2SessionConfiguration =
            getCamera2SessionConfiguration(outputConfigs, sessionConfig) ?: return false

        return cameraDeviceSetupCompat
            .isSessionConfigurationSupported(camera2SessionConfiguration)
            .supported == CameraDeviceSetupCompat.SupportQueryResult.RESULT_SUPPORTED
    }

    @SuppressLint("WrongConstant") // for OutputConfiguration(...) format parameter
    private fun createOutputConfigurations(
        sessionConfig: SessionConfig
    ): List<OutputConfiguration> {
        val outputConfigs =
            sessionConfig.outputConfigs.map { outputConfig ->
                val surfaceClass = outputConfig.surface.containerClass
                if (surfaceClass != null) {
                    // e.g. Preview, VideoCapture
                    OutputConfiguration(
                            requireNotNull(outputConfig.surface.prescribedSize),
                            surfaceClass,
                        )
                        .apply { applyDynamicRange(outputConfig) }
                } else {
                    // TODO: b/402156713 - Support ImageCapture output config in older devices,
                    //  possibly through a temporary ImageReader

                    // e.g. ImageCapture
                    OutputConfiguration(
                        outputConfig.surface.prescribedStreamFormat,
                        outputConfig.surface.prescribedSize,
                    )
                }
            }

        return outputConfigs
    }

    private fun OutputConfiguration.applyDynamicRange(outputConfig: SessionConfig.OutputConfig) {
        val dynamicRangeProfiles = dynamicRangeProfiles // snapshot for smart-cast
        if (dynamicRangeProfiles == null) {
            return
        }

        dynamicRangeProfile =
            requireNotNull(
                DynamicRangeConversions.dynamicRangeToFirstSupportedProfile(
                    outputConfig.dynamicRange,
                    dynamicRangeProfiles,
                )
            )
    }

    private fun getCamera2SessionConfiguration(
        outputConfigs: List<OutputConfiguration>,
        cameraXSessionConfig: SessionConfig,
    ): SessionConfiguration? {
        val camera2SessionConfig =
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigs,
                CameraXExecutors.directExecutor(),
                NO_OP_CALLBACK,
            )

        val cameraDeviceSetup = cameraDeviceSetup ?: return null

        camera2SessionConfig.sessionParameters =
            cameraDeviceSetup
                .createCaptureRequest(cameraXSessionConfig.templateType)
                .apply {
                    set(CONTROL_AE_TARGET_FPS_RANGE, cameraXSessionConfig.expectedFrameRateRange)

                    if (
                        cameraXSessionConfig.repeatingCaptureConfig.previewStabilizationMode ==
                            StabilizationMode.ON
                    ) {
                        set(
                            CONTROL_VIDEO_STABILIZATION_MODE,
                            CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION,
                        )
                    }
                }
                .build()

        return camera2SessionConfig
    }

    internal companion object {
        private const val TAG = "FeatureCombinationQuery"

        private val NO_OP_CALLBACK =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(p0: CameraCaptureSession) {
                    // no-op
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    // no-op
                }
            }
    }
}
