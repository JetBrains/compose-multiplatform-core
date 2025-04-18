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

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import androidx.camera.core.impl.CameraCaptureMetaData.AeMode;
import androidx.camera.core.impl.CameraCaptureMetaData.AeState;
import androidx.camera.core.impl.CameraCaptureMetaData.AfMode;
import androidx.camera.core.impl.CameraCaptureMetaData.AfState;
import androidx.camera.core.impl.CameraCaptureMetaData.AwbMode;
import androidx.camera.core.impl.CameraCaptureMetaData.AwbState;
import androidx.camera.core.impl.CameraCaptureMetaData.FlashState;
import androidx.camera.core.impl.utils.ExifData;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The result of a single image capture.
 */
public interface CameraCaptureResult {

    /** Returns the current auto focus mode of operation. */
    @NonNull AfMode getAfMode();

    /** Returns the current auto focus state. */
    @NonNull AfState getAfState();

    /** Returns the current auto exposure state. */
    @NonNull AeState getAeState();

    /** Returns the current auto white balance state. */
    @NonNull AwbState getAwbState();

    /** Returns the current flash state. */
    @NonNull FlashState getFlashState();

    /** Returns the current auto exposure mode. */
    @NonNull AeMode getAeMode();

    /** Returns the current auto white balance mode. */
    @NonNull AwbMode getAwbMode();
    /**
     * Returns the timestamp in nanoseconds.
     *
     * <p> If the timestamp was unavailable then it will return {@code -1L}.
     */
    long getTimestamp();

    /** Returns the TagBundle object associated with the capture request. */
    @NonNull TagBundle getTagBundle();

    /** Populates the given Exif.Builder with attributes from this CameraCaptureResult. */
    default void populateExifData(ExifData.@NonNull Builder exifBuilder) {
        exifBuilder.setFlashState(getFlashState());
    }

    /**
     * Returns the {@link CaptureResult} for reprocessable capture request.
     *
     * @return The {@link CaptureResult}.
     * @see CameraDevice#createReprocessCaptureRequest(TotalCaptureResult)
     */
    default @Nullable CaptureResult getCaptureResult() {
        return null;
    }

    /** An implementation of CameraCaptureResult which always return default results. */
    final class EmptyCameraCaptureResult implements CameraCaptureResult {

        public static @NonNull CameraCaptureResult create() {
            return new EmptyCameraCaptureResult();
        }

        @Override
        public @NonNull AfMode getAfMode() {
            return AfMode.UNKNOWN;
        }

        @Override
        public @NonNull AfState getAfState() {
            return AfState.UNKNOWN;
        }

        @Override
        public @NonNull AeState getAeState() {
            return AeState.UNKNOWN;
        }

        @Override
        public @NonNull AwbState getAwbState() {
            return AwbState.UNKNOWN;
        }

        @Override
        public @NonNull FlashState getFlashState() {
            return FlashState.UNKNOWN;
        }

        @Override
        public @NonNull AeMode getAeMode() {
            return AeMode.UNKNOWN;
        }

        @Override
        public @NonNull AwbMode getAwbMode() {
            return AwbMode.UNKNOWN;
        }

        @Override
        public long getTimestamp() {
            return -1L;
        }

        @Override
        public @NonNull TagBundle getTagBundle() {
            return TagBundle.emptyBundle();
        }

        @Override
        public @Nullable CaptureResult getCaptureResult() {
            return null;
        }
    }
}
