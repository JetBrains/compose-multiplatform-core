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

package androidx.camera.camera2.pipe.integration.internal

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
import android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.camera.camera2.pipe.CameraMetadata
import androidx.camera.camera2.pipe.integration.compat.StreamConfigurationMapCompat
import androidx.camera.camera2.pipe.integration.compat.workaround.OutputSizesCorrector
import androidx.camera.core.Logger
import androidx.camera.core.impl.AttachedSurfaceInfo
import androidx.camera.core.impl.StreamSpec.FRAME_RATE_RANGE_UNSPECIFIED
import androidx.camera.core.impl.UseCaseConfig
import androidx.camera.core.internal.utils.SizeUtil.getArea
import androidx.core.util.Preconditions.checkArgument

/** A class responsible for resolving parameters for high-speed session scenario. */
public class HighSpeedResolver(private val cameraMetadata: CameraMetadata) {

    /** Indicates whether the camera supports high-speed session. */
    public val isHighSpeedSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= 23 &&
            cameraMetadata[REQUEST_AVAILABLE_CAPABILITIES]?.any {
                it == REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
            } == true
    }

    /** The maximum supported size based on area, or `null` if there are no supported sizes. */
    public val maxSize: Size? by lazy {
        supportedSizes.takeIf { it.isNotEmpty() }?.maxBy { getArea(it) }
    }

    private val streamConfigurationMapCompat: StreamConfigurationMapCompat by lazy {
        val map =
            cameraMetadata[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                ?: throw IllegalArgumentException("Cannot retrieve SCALER_STREAM_CONFIGURATION_MAP")
        StreamConfigurationMapCompat(map, OutputSizesCorrector(cameraMetadata, map))
    }

    private val supportedSizes: List<Size> by lazy {
        streamConfigurationMapCompat.getHighSpeedVideoSizes()?.toList() ?: emptyList()
    }

    /**
     * Filters supported sizes for each use case, retaining only the sizes common to all use cases
     * and present in the overall supported sizes.
     *
     * This function analyzes a map of use case configurations and their corresponding lists of
     * supported sizes. It identifies the sizes common to all use cases and filters each use case's
     * supported sizes, retaining only those that are both common across all use cases and present
     * in the `supportedSizes` list. The original order of the supported sizes for each use case is
     * preserved.
     *
     * @param sizesMap A map where keys represent use case configurations and values are lists of
     *   `Size` objects representing the supported sizes for each use case.
     * @return A new map with the same keys as the input `sizesMap`, but with the values (lists of
     *   sizes) filtered to contain only the common supported sizes that are also present in the
     *   `supportedSizes` list, while maintaining the original order.
     */
    public fun <T> filterCommonSupportedSizes(sizesMap: Map<T, List<Size>>): Map<T, List<Size>> {
        val commonSupportedSizes =
            sizesMap.values.toList().findCommonElements().filter { it in supportedSizes }
        return sizesMap.mapValues { (_, sizes) -> sizes.filter { it in commonSupportedSizes } }
    }

    /**
     * Returns the maximum frame rate supported for a given size in a high-speed session.
     *
     * This method retrieves the supported high-speed FPS ranges for the given size from the camera
     * characteristics. It then returns the maximum frame rate (upper bound) among those ranges.
     *
     * @param size The size for which to find the maximum supported high-speed frame rate.
     * @return The maximum high-speed frame rate supported for the given size, or 0 if no high-speed
     *   FPS ranges are supported for that size.
     */
    public fun getMaxFrameRate(size: Size): Int {
        val supportedFpsRangesForSize =
            getHighSpeedVideoFpsRangesFor(size).takeIf { it.isNotEmpty() }
                ?: run {
                    Logger.w(TAG, "No supported high speed  fps for $size")
                    return 0
                }

        return supportedFpsRangesForSize.maxOf { it.upper }
    }

    /**
     * Returns size arrangements where all inner lists have the same size, maintaining order.
     *
     * This method takes a list of lists of sizes, where each inner list represents the supported
     * sizes for a specific use case. It finds the common sizes across all use cases and creates
     * arrangements where each use case has the same size. The order in the first list of the input
     * determines the order of the common sizes in the output.
     *
     * This method is necessary due to a limitation in high-speed session configuration, where all
     * streams (use cases) in a high-speed session must have the same size.
     *
     * @param sizesList A list of lists of sizes. Each inner list represents the supported sizes for
     *   a use case. The first dimension represents the use case, and the second dimension is the
     *   supported sizes.
     * @return A list of size arrangements where each inner list contains the same size. Returns an
     *   empty list if the input is empty or null.
     */
    public fun getSizeArrangements(sizesList: List<List<Size>>): List<List<Size>> {
        if (sizesList.isEmpty()) {
            return emptyList()
        }

        val commonSizes = sizesList.findCommonElements()

        // Generate arrangements with common sizes.
        return commonSizes.map { commonSize -> List(sizesList.size) { commonSize } }
    }

    /**
     * Returns the supported frame rate ranges for high-speed capture sessions with the given
     * surface sizes.
     *
     * High-speed sessions have restrictions:
     * 1. Maximum 2 surfaces.
     * 2. All surfaces must have the same size. When the restrictions are not met, this method will
     *    return null.
     *
     * @param surfaceSizes The list of surface sizes.
     * @return An array of supported frame rate ranges, or null if the input is invalid or no
     *   supported ranges are found.
     */
    public fun getFrameRateRangesFor(surfaceSizes: List<Size>): Array<Range<Int>>? {
        // High-speed capture sessions have restrictions:
        // 1. Maximum 2 surfaces.
        // 2. All surfaces must have the same size.
        if (surfaceSizes.size !in 1..2 || surfaceSizes.distinct().size != 1) {
            return null
        }

        val supportedFpsRanges =
            getHighSpeedVideoFpsRangesFor(surfaceSizes[0]).takeIf { it.isNotEmpty() } ?: return null

        // For 2 surfaces case, the FPS range must be fixed (lower == upper). See
        // CameraConstrainedHighSpeedCaptureSession#createHighSpeedRequestList.
        return if (surfaceSizes.size == 2) {
                supportedFpsRanges.filter { it.lower == it.upper }
            } else {
                supportedFpsRanges
            }
            .toTypedArray()
    }

    /**
     * Finds the common elements present in all given lists, preserving the order from the first
     * list.
     *
     * This function takes a list of lists and returns a new list containing only the elements that
     * appear in every input list. The order of elements in the output list matches their order in
     * the first list.
     *
     * @return A list containing only the elements found in all input lists, ordered according to
     *   their presence in the first list.
     */
    private fun <T> List<List<T>>.findCommonElements(): List<T> {
        if (isEmpty()) return emptyList()

        val commonElements = this.first().toMutableList()
        this.drop(1).forEach { commonElements.retainAll(it) }
        return commonElements
    }

    private fun getHighSpeedVideoFpsRangesFor(size: Size): List<Range<Int>> {
        return runCatching { streamConfigurationMapCompat.getHighSpeedVideoFpsRangesFor(size) }
            .getOrNull()
            ?.filterNotNull()
            ?.toList() ?: emptyList()
    }

    public companion object {
        private const val TAG = "HighSpeedResolver"

        /**
         * Retrieves the target high-speed frame rate from attached surface information and use case
         * configurations.
         *
         * This function ensures that all provided surfaces and configurations have the same target
         * high-speed frame rate. If any inconsistencies are found, an `IllegalArgumentException` is
         * thrown.
         *
         * @param attachedSurfaces A collection of `AttachedSurfaceInfo` objects.
         * @param newUseCaseConfigs A collection of `UseCaseConfig` objects representing the use
         *   cases for which to retrieve the target high-speed frame rate range.
         * @return The target high-speed frame rate range common to all use cases, or
         *   `FRAME_RATE_RANGE_UNSPECIFIED` if no frame rate is specified.
         * @throws IllegalArgumentException if the target high-speed frame rate ranges specified are
         *   not the same.
         */
        @JvmStatic
        public fun getTargetHighSpeedFrameRate(
            attachedSurfaces: Collection<AttachedSurfaceInfo>,
            newUseCaseConfigs: Collection<UseCaseConfig<*>>
        ): Range<Int> {
            val frameRates =
                attachedSurfaces.map { it.targetHighSpeedFrameRate } +
                    newUseCaseConfigs.map {
                        it.getTargetHighSpeedFrameRate(FRAME_RATE_RANGE_UNSPECIFIED)!!
                    }

            if (frameRates.isEmpty()) {
                return FRAME_RATE_RANGE_UNSPECIFIED
            }

            val firstRate = frameRates.first()
            checkArgument(frameRates.all { it == firstRate }) {
                "targetHighSpeedFrameRate should be the same."
            }
            return firstRate
        }
    }
}
