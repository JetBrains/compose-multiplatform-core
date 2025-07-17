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

package androidx.camera.core.imagecapture;

import android.util.Size;

import com.google.auto.value.AutoValue;

import org.jspecify.annotations.NonNull;

/**
 * The postview settings when creating the image capture pipeline.
 */
@AutoValue
public abstract class PostviewSettings {
    /**
     * Returns the postivew resolution when creating the image capture pipeline.
     */
    public abstract @NonNull Size getResolution();

    /**
     * Returns the postview input format when creating the image capture pipeline.
     */
    public abstract int getInputFormat();

    /**
     * Creates an instance of {@link PostviewSettings}.
     */
    public static @NonNull PostviewSettings create(@NonNull Size resolution, int inputFormat) {
        return new AutoValue_PostviewSettings(resolution, inputFormat);
    }
}
