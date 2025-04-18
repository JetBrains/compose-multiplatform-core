/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.camera.video.internal.config;

import androidx.camera.core.impl.EncoderProfilesProxy;
import androidx.camera.video.internal.encoder.EncoderConfig;

import com.google.auto.value.AutoValue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Data class containing information about a audio mime.
 *
 * <p>The information includes all information from {@link MimeInfo} as well as
 * compatible configuration types that can be used to resolve settings, such as
 * {@link EncoderProfilesProxy.AudioProfileProxy}.
 */
@SuppressWarnings("NullableProblems") // Problem from AutoValue generated class.
@AutoValue
public abstract class AudioMimeInfo extends MimeInfo {

    /**
     * Returns compatible {@link EncoderProfilesProxy.AudioProfileProxy} that can be used to
     * resolve settings.
     *
     * <p>If no AudioProfileProxy is provided, returns {@code null}.
     */
    public abstract EncoderProfilesProxy.@Nullable AudioProfileProxy getCompatibleAudioProfile();

    /** Creates a builder for the given mime type */
    public static AudioMimeInfo.@NonNull Builder builder(@NonNull String mimeType) {
        return new AutoValue_AudioMimeInfo.Builder()
                .setMimeType(mimeType)
                .setProfile(EncoderConfig.CODEC_PROFILE_NONE);
    }

    /** A Builder for an {@link AudioMimeInfo}. */
    @SuppressWarnings("NullableProblems") // Problem from AutoValue generated class.
    @AutoValue.Builder
    public abstract static class Builder extends MimeInfo.Builder<Builder> {
        /** Sets a compatible {@link EncoderProfilesProxy.AudioProfileProxy} */
        public abstract @NonNull Builder setCompatibleAudioProfile(
                EncoderProfilesProxy.@Nullable AudioProfileProxy audioProfile);

        /** Builds a AudioMimeInfo. */
        @Override
        public abstract @NonNull AudioMimeInfo build();
    }
}
