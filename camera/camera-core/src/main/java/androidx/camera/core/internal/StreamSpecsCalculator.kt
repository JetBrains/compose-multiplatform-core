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

package androidx.camera.core.internal

import android.util.Pair
import android.util.Range
import android.util.Size
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.impl.AttachedSurfaceInfo
import androidx.camera.core.impl.CameraConfig
import androidx.camera.core.impl.CameraConfigs
import androidx.camera.core.impl.CameraDeviceSurfaceManager
import androidx.camera.core.impl.CameraInfoInternal
import androidx.camera.core.impl.CameraMode
import androidx.camera.core.impl.StreamSpec
import androidx.camera.core.impl.SurfaceConfig
import androidx.camera.core.impl.UseCaseConfig
import androidx.camera.core.impl.UseCaseConfigFactory
import androidx.camera.core.impl.stabilization.StabilizationMode
import androidx.camera.core.impl.utils.TransformUtils
import androidx.camera.core.streamsharing.StreamSharing

/**
 * Calculates the stream specs for a given combination of use cases and other settings/capabilities.
 */
public interface StreamSpecsCalculator {
    public fun setCameraDeviceSurfaceManager(
        cameraDeviceSurfaceManager: CameraDeviceSurfaceManager
    ) {
        // no-op by default
    }

    /**
     * Calculates the stream specs for a given combination of use cases and other
     * settings/capabilities.
     *
     * @throws kotlin.UninitializedPropertyAccessException if the camera device surface manager has
     *   not been set yet.
     */
    public fun calculateSuggestedStreamSpecs(
        @CameraMode.Mode cameraMode: Int,
        cameraInfoInternal: CameraInfoInternal,
        newUseCases: List<UseCase>,
        attachedUseCases: List<UseCase> = emptyList(),
        cameraConfig: CameraConfig = CameraConfigs.defaultConfig(),
        targetHighSpeedFrameRate: Range<Int> = StreamSpec.FRAME_RATE_RANGE_UNSPECIFIED,
        allowFeatureCombinationResolutions: Boolean = false
    ): Map<UseCase, StreamSpec>

    public companion object {
        @JvmField
        public val NO_OP_STREAM_SPECS_CALCULATOR: StreamSpecsCalculator =
            object : StreamSpecsCalculator {
                override fun calculateSuggestedStreamSpecs(
                    cameraMode: Int,
                    cameraInfoInternal: CameraInfoInternal,
                    newUseCases: List<UseCase>,
                    attachedUseCases: List<UseCase>,
                    cameraConfig: CameraConfig,
                    targetHighSpeedFrameRate: Range<Int>,
                    allowFeatureCombinationResolutions: Boolean
                ): Map<UseCase, StreamSpec> {
                    return emptyMap()
                }
            }

        // Since @JvmOverloads is not supported for interface methods and we may need to call this
        // from Java codes like Camera2CameraInfoImpl, creating an extension function for Java
        // callers wanting to take advantage of the default params.
        /**
         * Calculates the stream specs for a given combination of use cases and other
         * settings/capabilities.
         *
         * @throws kotlin.UninitializedPropertyAccessException if the camera device surface manager
         *   has not been set yet.
         */
        @JvmOverloads
        public fun StreamSpecsCalculator.calculateSuggestedStreamSpecsCompat(
            @CameraMode.Mode cameraMode: Int,
            cameraInfoInternal: CameraInfoInternal,
            newUseCases: List<UseCase>,
            cameraConfig: CameraConfig = CameraConfigs.defaultConfig(),
            allowFeatureCombinationResolutions: Boolean = false,
            attachedUseCases: List<UseCase> = emptyList(),
            targetHighSpeedFrameRate: Range<Int> = StreamSpec.FRAME_RATE_RANGE_UNSPECIFIED,
        ): Map<UseCase, StreamSpec> {
            return calculateSuggestedStreamSpecs(
                cameraMode = cameraMode,
                cameraInfoInternal = cameraInfoInternal,
                newUseCases = newUseCases,
                attachedUseCases = attachedUseCases,
                cameraConfig = cameraConfig,
                targetHighSpeedFrameRate = targetHighSpeedFrameRate,
                allowFeatureCombinationResolutions = allowFeatureCombinationResolutions,
            )
        }
    }
}

public class StreamSpecsCalculatorImpl(
    private val useCaseConfigFactory: UseCaseConfigFactory,
    private var cameraDeviceSurfaceManager: CameraDeviceSurfaceManager? = null,
) : StreamSpecsCalculator {
    override fun setCameraDeviceSurfaceManager(
        cameraDeviceSurfaceManager: CameraDeviceSurfaceManager
    ) {
        this.cameraDeviceSurfaceManager = cameraDeviceSurfaceManager
    }

    override fun calculateSuggestedStreamSpecs(
        @CameraMode.Mode cameraMode: Int,
        cameraInfoInternal: CameraInfoInternal,
        newUseCases: List<UseCase>,
        attachedUseCases: List<UseCase>,
        cameraConfig: CameraConfig,
        targetHighSpeedFrameRate: Range<Int>,
        allowFeatureCombinationResolutions: Boolean
    ): Map<UseCase, StreamSpec> {
        // Calculate stream specs for use cases already attached.
        val result =
            calculateSuggestedStreamSpecsForAttachedUseCases(
                cameraMode,
                cameraInfoInternal,
                attachedUseCases
            )

        // Calculate and add the stream specs for new use cases.
        return result.first +
            calculateSuggestedStreamSpecsForNewUseCases(
                cameraMode,
                cameraInfoInternal,
                newUseCases,
                result.second,
                CameraUseCaseAdapter.getConfigs(
                    newUseCases,
                    cameraConfig.useCaseConfigFactory,
                    useCaseConfigFactory,
                    targetHighSpeedFrameRate,
                ),
                allowFeatureCombinationResolutions
            )
    }

    private fun calculateSuggestedStreamSpecsForAttachedUseCases(
        @CameraMode.Mode cameraMode: Int,
        cameraInfoInternal: CameraInfoInternal,
        attachedUseCases: List<UseCase>,
    ): Pair<Map<UseCase, StreamSpec>, Map<AttachedSurfaceInfo, UseCase>> {
        val existingSurfaces: MutableList<AttachedSurfaceInfo?> = ArrayList<AttachedSurfaceInfo?>()
        val cameraId = cameraInfoInternal.getCameraId()
        val suggestedStreamSpecs = mutableMapOf<UseCase, StreamSpec>()
        val surfaceInfoUseCaseMap = mutableMapOf<AttachedSurfaceInfo, UseCase>()

        // Get resolution for current use cases.
        for (useCase in attachedUseCases) {
            val attachedStreamSpec =
                requireNotNull(useCase.attachedStreamSpec) {
                    "Attached stream spec cannot be null for already attached use cases."
                }

            val surfaceConfig: SurfaceConfig? =
                checkNotNull(cameraDeviceSurfaceManager)
                    .transformSurfaceConfig(
                        cameraMode,
                        cameraId,
                        useCase.imageFormat,
                        requireNotNull(useCase.attachedSurfaceResolution) {
                            "Attached surface resolution cannot be null for already attached use cases."
                        }
                    )

            val attachedSurfaceInfo =
                AttachedSurfaceInfo.create(
                    surfaceConfig!!,
                    useCase.imageFormat,
                    useCase.attachedSurfaceResolution!!,
                    attachedStreamSpec.dynamicRange,
                    StreamSharing.getCaptureTypes(useCase),
                    attachedStreamSpec.getImplementationOptions(),
                    useCase.currentConfig.getTargetFrameRate(null),
                    requireNotNull(
                        useCase.currentConfig.getTargetHighSpeedFrameRate(
                            StreamSpec.FRAME_RATE_RANGE_UNSPECIFIED
                        )
                    )
                )
            existingSurfaces.add(attachedSurfaceInfo)
            surfaceInfoUseCaseMap.put(attachedSurfaceInfo, useCase)
            suggestedStreamSpecs.put(useCase, attachedStreamSpec)
        }

        return Pair(suggestedStreamSpecs, surfaceInfoUseCaseMap)
    }

    private fun calculateSuggestedStreamSpecsForNewUseCases(
        @CameraMode.Mode cameraMode: Int,
        cameraInfoInternal: CameraInfoInternal,
        newUseCases: List<UseCase>,
        attachedSurfaceInfoToUseCaseMap: Map<AttachedSurfaceInfo, UseCase>,
        configPairMap: Map<UseCase, CameraUseCaseAdapter.ConfigPair>,
        allowFeatureCombinationResolutions: Boolean
    ): Map<UseCase, StreamSpec> {
        val cameraId = cameraInfoInternal.getCameraId()
        val suggestedStreamSpecs = mutableMapOf<UseCase, StreamSpec>()

        // Calculate resolution for new use cases.
        if (!newUseCases.isEmpty()) {
            val configToUseCaseMap = mutableMapOf<UseCaseConfig<*>, UseCase>()
            val configToSupportedSizesMap: MutableMap<UseCaseConfig<*>?, MutableList<Size?>?> =
                HashMap<UseCaseConfig<*>?, MutableList<Size?>?>()
            val sensorRect =
                try {
                    cameraInfoInternal.getSensorRect()
                } catch (_: NullPointerException) {
                    // TODO(b/274531208): Remove the unnecessary SENSOR_INFO_ACTIVE_ARRAY_SIZE NPE
                    //  check related code only which is used for robolectric tests
                    null
                }
            val supportedOutputSizesSorter =
                SupportedOutputSizesSorter(
                    cameraInfoInternal,
                    if (sensorRect != null) TransformUtils.rectToSize(sensorRect) else null
                )
            var isPreviewStabilizationOn = false
            for (useCase in newUseCases) {
                val configPair: CameraUseCaseAdapter.ConfigPair =
                    requireNotNull(configPairMap[useCase])

                // Combine with default configuration.
                val combinedUseCaseConfig =
                    useCase.mergeConfigs(
                        cameraInfoInternal,
                        configPair.mExtendedConfig,
                        configPair.mCameraConfig
                    )
                configToUseCaseMap.put(combinedUseCaseConfig, useCase)
                configToSupportedSizesMap.put(
                    combinedUseCaseConfig,
                    supportedOutputSizesSorter.getSortedSupportedOutputSizes(combinedUseCaseConfig)
                )

                if (useCase is Preview || useCase is StreamSharing) {
                    // Let isPreviewStabilizationOn be true only if stabilization mode of Preview
                    // or StreamSharing (wrapping Preview) is on.
                    isPreviewStabilizationOn =
                        combinedUseCaseConfig.previewStabilizationMode == StabilizationMode.ON
                }
            }

            // Get suggested stream specifications and update the use case session configuration
            val streamSpecMaps =
                checkNotNull(cameraDeviceSurfaceManager)
                    .getSuggestedStreamSpecs(
                        cameraMode,
                        cameraId,
                        ArrayList<AttachedSurfaceInfo?>(attachedSurfaceInfoToUseCaseMap.keys),
                        configToSupportedSizesMap,
                        isPreviewStabilizationOn,
                        CameraUseCaseAdapter.hasVideoCapture(newUseCases),
                        allowFeatureCombinationResolutions
                    )

            for (entry in configToUseCaseMap.entries) {
                suggestedStreamSpecs.put(
                    entry.value,
                    requireNotNull(streamSpecMaps.first[entry.key])
                )
            }
            for (entry in streamSpecMaps.second!!.entries) {
                if (attachedSurfaceInfoToUseCaseMap.containsKey(entry.key)) {
                    suggestedStreamSpecs.put(
                        requireNotNull(attachedSurfaceInfoToUseCaseMap[entry.key]),
                        entry.value
                    )
                }
            }
        }
        return suggestedStreamSpecs
    }
}
