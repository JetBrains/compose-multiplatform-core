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

package androidx.camera.core;

import android.graphics.Matrix;

import androidx.annotation.RestrictTo;
import androidx.camera.core.impl.TagBundle;
import androidx.camera.core.impl.utils.ExifData;

import com.google.auto.value.AutoValue;

import org.jspecify.annotations.NonNull;

/**
 */
@AutoValue
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ImmutableImageInfo implements ImageInfo {

    /**
     * Creates an instance of {@link ImmutableImageInfo}.
     */
    public static @NonNull ImageInfo create(@NonNull TagBundle tag, long timestamp,
            int rotationDegrees, @NonNull Matrix sensorToBufferTransformMatrix,
            @FlashState.FlashState int flashState) {
        return new AutoValue_ImmutableImageInfo(
                tag,
                timestamp,
                rotationDegrees,
                sensorToBufferTransformMatrix,
                flashState);
    }

    @Override
    public abstract @NonNull TagBundle getTagBundle();

    @Override
    public abstract long getTimestamp();

    @Override
    public abstract int getRotationDegrees();

    @Override
    public abstract @NonNull Matrix getSensorToBufferTransformMatrix();

    @Override
    public abstract @FlashState.FlashState int getFlashState();

    @Override
    public void populateExifData(ExifData.@NonNull Builder exifBuilder) {
        // Only have access to orientation information.
        exifBuilder.setOrientationDegrees(getRotationDegrees());
    }
}
