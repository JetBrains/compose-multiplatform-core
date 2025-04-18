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

package androidx.camera.extensions.internal;

import android.hardware.camera2.CaptureRequest;

import androidx.annotation.VisibleForTesting;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.MutableOptionsBundle;
import androidx.camera.core.impl.OptionsBundle;
import androidx.camera.core.impl.ReadableConfig;

import org.jspecify.annotations.NonNull;

/**
 * Builder for creating {@link Config} that contains capture request options.
 */
public class RequestOptionConfig implements ReadableConfig {
    static final String CAPTURE_REQUEST_ID_STEM = "camera2.captureRequest.option.";

    private @NonNull Config mConfig;

    private RequestOptionConfig(@NonNull Config config) {
        mConfig = config;
    }

    @Override
    public @NonNull Config getConfig() {
        return mConfig;
    }

    @VisibleForTesting
    static @NonNull Option<Object> createOptionFromKey(CaptureRequest.@NonNull Key<?> key) {
        return Option.create(CAPTURE_REQUEST_ID_STEM + key.getName(),
                Object.class,
                key);
    }

    /**
     * Builder for constructing {@link RequestOptionConfig} instances.
     */
    public static class Builder {
        private MutableOptionsBundle mMutableOptionsBundle = MutableOptionsBundle.create();

        /**
         * Extract the capture request options from the given {@link Config} and create a
         * {@link Builder} consisting of these capture request options.
         */
        public static @NonNull Builder from(@NonNull Config config) {
            Builder builder = new Builder();
            config.findOptions(
                    CAPTURE_REQUEST_ID_STEM,
                    option -> {
                        @SuppressWarnings("unchecked")
                        Config.Option<Object> objectOpt = (Config.Option<Object>) option;
                        builder.mMutableOptionsBundle.insertOption(objectOpt,
                                config.getOptionPriority(objectOpt),
                                config.retrieveOption(objectOpt));
                        return true;
                    });
            return builder;
        }

        /**
         * Sets the capture request option.
         */
        public <ValueT> @NonNull Builder setCaptureRequestOption(
                CaptureRequest.@NonNull Key<ValueT> key, @NonNull ValueT value) {
            Option<Object> option = createOptionFromKey(key);
            mMutableOptionsBundle.insertOption(option, value);
            return this;
        }

        /**
         * Construct the instance.
         */
        public @NonNull RequestOptionConfig build() {
            return new RequestOptionConfig(OptionsBundle.from(mMutableOptionsBundle));
        }
    }
}
