/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.core.impl;

import static androidx.camera.core.impl.CameraMode.ULTRA_HIGH_RESOLUTION_CAMERA;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession.StateCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.util.Size;

import androidx.camera.core.internal.utils.SizeUtil;

import com.google.auto.value.AutoValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Surface configuration type and size pair
 *
 * <p>{@link CameraDevice#createCaptureSession} defines the default
 * guaranteed stream combinations for different hardware level devices. It defines what combination
 * of surface configuration type and size pairs can be supported for different hardware level camera
 * devices.
 */
@AutoValue
public abstract class SurfaceConfig {
    public static final long DEFAULT_STREAM_USE_CASE_VALUE = 0;
    public static final ConfigSize[] FEATURE_COMBO_QUERY_SUPPORTED_SIZES = {
            ConfigSize.S720P_16_9,
            ConfigSize.S1080P_4_3,
            ConfigSize.S1080P_16_9,
            ConfigSize.S1440P_16_9,
            ConfigSize.UHD,
            ConfigSize.X_VGA,
    };

    /** Prevent subclassing */
    SurfaceConfig() {
    }

    /**
     * Creates a new instance of SurfaceConfig with the given parameters.
     */
    public static @NonNull SurfaceConfig create(@NonNull ConfigType type,
            @NonNull ConfigSize size) {
        return new AutoValue_SurfaceConfig(type, size, DEFAULT_STREAM_USE_CASE_VALUE);
    }

    /**
     * Creates a new instance of SurfaceConfig with the given parameters.
     */
    public static @NonNull SurfaceConfig create(@NonNull ConfigType type, @NonNull ConfigSize size,
            long streamUseCase) {
        return new AutoValue_SurfaceConfig(type, size, streamUseCase);
    }

    /** Returns the configuration type. */
    public abstract @NonNull ConfigType getConfigType();

    /** Returns the configuration size. */
    public abstract @NonNull ConfigSize getConfigSize();

    /**
     * Returns the stream use case.
     * <p>Stream use case constants are implementation-specific constants that allow the
     * implementation to optimize power and quality characteristics of a stream depending on how
     * it will be used.
     * <p> Stream use case is an int flag used to specify the purpose of the stream associated
     * with this surface. Use cases for the camera2 implementation that are available on devices can
     * be found in
     * {@link CameraCharacteristics#SCALER_AVAILABLE_STREAM_USE_CASES}
     *
     * <p>See {@link android.hardware.camera2.params.OutputConfiguration#setStreamUseCase}
     * to see how Camera2 framework uses this.
     */
    public abstract long getStreamUseCase();

    /**
     * Check whether the input surface configuration has a smaller size than this object and can be
     * supported
     *
     * @param surfaceConfig the surface configuration to be compared
     * @return the check result that whether it could be supported
     */
    public final boolean isSupported(@NonNull SurfaceConfig surfaceConfig) {
        boolean isSupported = false;
        ConfigType configType = surfaceConfig.getConfigType();
        ConfigSize configSize = surfaceConfig.getConfigSize();

        // Check size and type to make sure it could be supported
        if (configSize.getId() <= getConfigSize().getId() && configType == getConfigType()) {
            isSupported = true;
        }
        return isSupported;
    }

    /**
     * Gets {@link ConfigType} from image format.
     *
     * <p> PRIV refers to any target whose available sizes are found using
     * StreamConfigurationMap.getOutputSizes(Class) with no direct application-visible format,
     * YUV refers to a target Surface using the ImageFormat.YUV_420_888 format, JPEG refers to
     * the ImageFormat.JPEG or ImageFormat.JPEG_R format, and RAW refers to the
     * ImageFormat.RAW_SENSOR format.
     */
    public static SurfaceConfig.@NonNull ConfigType getConfigType(int imageFormat) {
        if (imageFormat == ImageFormat.YUV_420_888) {
            return SurfaceConfig.ConfigType.YUV;
        } else if (imageFormat == ImageFormat.JPEG) {
            return SurfaceConfig.ConfigType.JPEG;
        } else if (imageFormat == ImageFormat.JPEG_R) {
            return SurfaceConfig.ConfigType.JPEG_R;
        } else if (imageFormat == ImageFormat.RAW_SENSOR) {
            return SurfaceConfig.ConfigType.RAW;
        } else {
            return SurfaceConfig.ConfigType.PRIV;
        }
    }

    /**
     * Transform to a SurfaceConfig object with image format and size info
     *
     * @param cameraMode            the working camera mode.
     * @param imageFormat           the image format info for the surface configuration object
     * @param size                  the size info for the surface configuration object
     * @param surfaceSizeDefinition the surface definition for the surface configuration object
     * @return new {@link SurfaceConfig} object
     */
    public static @NonNull SurfaceConfig transformSurfaceConfig(
            @CameraMode.Mode int cameraMode,
            int imageFormat,
            @NonNull Size size,
            @NonNull SurfaceSizeDefinition surfaceSizeDefinition,
            @NonNull ConfigSource configSource) {
        ConfigType configType =
                SurfaceConfig.getConfigType(imageFormat);
        ConfigSize configSize = ConfigSize.NOT_SUPPORT;

        // Compare with surface size definition to determine the surface configuration size
        int sizeArea = SizeUtil.getArea(size);

        if (cameraMode == CameraMode.CONCURRENT_CAMERA) {
            if (sizeArea <= SizeUtil.getArea(surfaceSizeDefinition.getS720pSize(imageFormat))) {
                configSize = ConfigSize.S720P_16_9;
            } else if (sizeArea <= SizeUtil.getArea(surfaceSizeDefinition.getS1440pSize(
                    imageFormat))) {
                configSize = ConfigSize.S1440P_4_3;
            }
        } else if (configSource == ConfigSource.FEATURE_COMBINATION_TABLE) {
            Size maximumSize = surfaceSizeDefinition.getMaximumSize(imageFormat);

            // Try all fixed sizes first for exact match
            for (ConfigSize supportedSize : FEATURE_COMBO_QUERY_SUPPORTED_SIZES) {
                if (size.equals(supportedSize.getRelatedFixedSize())) {
                    configSize = supportedSize;
                    break;
                }
            }

            // There was no fixed size match, so try the max supported size next
            if (configSize == ConfigSize.NOT_SUPPORT) {

                if (size.equals(maximumSize)) {
                    configSize = ConfigSize.MAXIMUM;
                }
            }
        } else {
            if (sizeArea <= SizeUtil.getArea(surfaceSizeDefinition.getAnalysisSize())) {
                configSize = ConfigSize.VGA;
            } else if (sizeArea <= SizeUtil.getArea(surfaceSizeDefinition.getPreviewSize())) {
                configSize = ConfigSize.PREVIEW;
            } else if (sizeArea <= SizeUtil.getArea(surfaceSizeDefinition.getRecordSize())) {
                configSize = ConfigSize.RECORD;
            } else {
                Size maximumSize = surfaceSizeDefinition.getMaximumSize(imageFormat);
                Size ultraMaximumSize = surfaceSizeDefinition.getUltraMaximumSize(imageFormat);
                // On some devices, when extensions is on, some extra formats might be supported
                // for extensions. But those formats are not supported in the normal mode. In
                // that case, MaximumSize could be null. Directly make configSize as MAXIMUM for
                // the case.
                if ((maximumSize == null || sizeArea <= SizeUtil.getArea(maximumSize))
                        && cameraMode != ULTRA_HIGH_RESOLUTION_CAMERA) {
                    configSize = ConfigSize.MAXIMUM;
                } else if (ultraMaximumSize != null && sizeArea <= SizeUtil.getArea(
                        ultraMaximumSize)) {
                    configSize = ConfigSize.ULTRA_MAXIMUM;
                }
            }
        }

        return SurfaceConfig.create(configType, configSize);
    }

    /**
     * The Camera2 configuration type for the surface.
     *
     * <p>These are the enumerations defined in {@link
     * CameraDevice#createCaptureSession(List, StateCallback, Handler)} or {@link
     * CameraCharacteristics#INFO_SESSION_CONFIGURATION_QUERY_VERSION}.
     */
    public enum ConfigType {
        PRIV,
        YUV,
        JPEG,
        JPEG_R,
        RAW
    }

    /**
     * Represents the source of config sizes, usually some stream config table defined in
     * {@link CameraDevice#createCaptureSession(List, StateCallback, Handler)}
     * or
     * {@link CameraCharacteristics#INFO_SESSION_CONFIGURATION_QUERY_VERSION}.
     */
    public enum ConfigSource {
        /**
         * Represents the stream config table defined in
         * {@link CameraCharacteristics#INFO_SESSION_CONFIGURATION_QUERY_VERSION}.
         */
        FEATURE_COMBINATION_TABLE,

        /**
         * Represents the guaranteed stream config tables defined in
         * {@link CameraDevice#createCaptureSession(List, StateCallback, Handler)}.
         */
        CAPTURE_SESSION_TABLES,
    }

    /**
     * The Camera2 stream sizes for the surface.
     *
     * <p>These are the enumerations defined in {@link
     * CameraDevice#createCaptureSession(List, StateCallback, Handler)}
     * for most cases, or
     * {@link CameraCharacteristics#INFO_SESSION_CONFIGURATION_QUERY_VERSION}
     * for feature combination cases.
     */
    public enum ConfigSize {
        /** Default VGA size is 640x480, which is the default size of Image Analysis. */
        VGA(0, new Size(640, 480)),
        /** X_VGA size refers to 1024x768 which is of 4:3 aspect ratio. */
        X_VGA(1, new Size(1024, 768)),
        /**
         * Represents 720P (1280x720) resolution of 16:9 resolution.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to 720p (1280x720),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly 720P (1280x720).
         */
        S720P_16_9(2, new Size(1280, 720)),
        /**
         * PREVIEW refers to the best size match to the device's screen resolution, or to 1080p
         * (1920x1080), whichever is smaller.
         */
        PREVIEW(3),
        /**
         * Represents 1080P (1440x1080) resolution of 4:3 aspect ratio.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to 1080P (1440x1080),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly 1080P (1440x1080).
         */
        S1080P_4_3(4, new Size(1440, 1080)),
        /**
         * Represents 1080P (1920x1080) resolution of 16:9 aspect ratio.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to 1080P (1920x1080),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly 1080P (1920x1080).
         */
        S1080P_16_9(5, new Size(1920, 1080)),
        /**
         * Represents 1440P (1920x1440) resolution of 4:3 aspect ratio.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to 1440P (1920x1440),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly 1440P (1920x1440).
         */
        S1440P_4_3(6, new Size(1920, 1440)),
        /**
         * Represents 1440P (2560x1440) resolution of 16:9 aspect ratio.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to 1440P (2560x1440),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly 1440P (2560x1440).
         */
        S1440P_16_9(7, new Size(2560, 1440)),
        /**
         * Represents UHD (3840x2160) resolution, which is of 16:9 aspect ratio.
         *
         * <p> For cases like concurrent camera which supports lower resolutions as well for a
         * specified stream size, it refers to the camera device's maximum resolution for that
         * format from {@link StreamConfigurationMap#getOutputSizes(int)} or to UHD (3840x2160),
         * whichever is smaller.
         *
         * <p> For cases like feature combination which supports only the exact resolutions for a
         * specified stream size, not lower resolutions, it refers to exactly UHD (3840x2160).
         */
        UHD(8, new Size(3840, 2160)),
        /**
         * RECORD refers to the camera device's maximum supported recording resolution, as
         * determined by CamcorderProfile.
         */
        RECORD(9),
        /**
         * MAXIMUM refers to the camera device's maximum output resolution for that format or
         * target from StreamConfigurationMap.getOutputSizes() or getHighResolutionOutputSizes()
         * in the default sensor pixel mode.
         */
        MAXIMUM(10),
        /**
         * Refers to the camera device's maximum 4:3 output resolution for that format or target
         * from {@link StreamConfigurationMap#getOutputSizes} or
         * {@link StreamConfigurationMap#getHighResolutionOutputSizes} in the default sensor pixel
         * mode.
         */
        MAXIMUM_4_3(11),
        /**
         * Refers to the camera device's maximum 16:9 output resolution for that format or target
         * from {@link StreamConfigurationMap#getOutputSizes} or
         * {@link StreamConfigurationMap#getHighResolutionOutputSizes} in the default sensor pixel
         * mode.
         */
        MAXIMUM_16_9(12),
        /**
         * ULTRA_MAXIMUM refers to the camera device's maximum output resolution for that format or
         * target from StreamConfigurationMap.getOutputSizes() or getHighResolutionOutputSizes()
         * in the maximum resolution sensor pixel mode.
         */
        ULTRA_MAXIMUM(13),
        /** NOT_SUPPORT is for the size larger than MAXIMUM */
        NOT_SUPPORT(14);

        final int mId;
        final Size mRelatedFixedSize;

        ConfigSize(int id) {
            this(id, null);
        }

        ConfigSize(int id, Size relatedFixedSize) {
            mId = id;
            mRelatedFixedSize = relatedFixedSize;
        }

        int getId() {
            return mId;
        }

        /**
         * Returns the fixed size that is related to the provided {@link ConfigSize}, null if none.
         *
         * <p> Depending on the source table, it may act as the upper bound for the config size
         * (e.g. concurrent camera guaranteed stream combo table) or it may be the exact size (e.g.
         * feature combination table).
         *
         * <p> For config sizes without any pre-defined fixed related to it (e.g.
         * {@link ConfigSize#RECORD}), a null value will be returned.
         */
        public @Nullable Size getRelatedFixedSize() {
            return mRelatedFixedSize;
        }
    }
}
