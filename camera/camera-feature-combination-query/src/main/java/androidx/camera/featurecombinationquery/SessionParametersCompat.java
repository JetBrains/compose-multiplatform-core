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

package androidx.camera.featurecombinationquery;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.SessionConfiguration;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A compat class to store Session Parameters for querying support. This class is a simple holder
 * class for holding {@link CaptureRequest.Key}s and associated values, similar to what
 * {@link CaptureRequest} does in
 * {@link android.hardware.camera2.params.SessionConfiguration#setSessionParameters}.
 * <p>
 * This class <i>must not</i> be used for camera devices that support
 * {@link android.hardware.camera2.CameraDevice.CameraDeviceSetup}.
 * For device that support {@link android.hardware.camera2.CameraDevice.CameraDeviceSetup}, use
 * {@link android.hardware.camera2.CameraDevice.CameraDeviceSetup#createCaptureRequest} for
 * session parameters instead.
 * <p>
 * In situations where {@link android.hardware.camera2.CameraDevice} object is available, it is
 * strongly recommended to use
 * {@link CameraDeviceSetupCompat#isSessionConfigurationSupported(SessionConfiguration)} instead of
 * {@link CameraDeviceSetupCompat#isSessionConfigurationSupported(SessionConfigurationCompat)} as
 * {@link CameraDeviceSetupCompat#isSessionConfigurationSupported(SessionConfigurationCompat)} may
 * lead to inaccurate results if not constructed accurately.
 */
public class SessionParametersCompat {
    @NonNull
    private final Map<CaptureRequest.Key<?>, Object> mKeyVal;

    private SessionParametersCompat(@NonNull Map<CaptureRequest.Key<?>, Object> keyValMap) {
        mKeyVal = keyValMap;
    }

    /**
     * Returns a {@link Set} of the keys contained in this map.
     * <p>
     * The returned set is not modifiable, so any attempts to modify it will throw an
     * {@link UnsupportedOperationException}.
     * <p>
     * All values retrieved by a key from this set with {@link #get} are guaranteed to be non-null.
     *
     * @return set of all keys in the map
     */
    @NonNull
    public Set<CaptureRequest.Key<?>> getKeys() {
        return Set.copyOf(mKeyVal.keySet());
    }

    /**
     * Return the capture request field value associated with {@code key}.
     * <p>
     * The field definitions can be found in {@link CaptureRequest}.
     *
     * @param key The key whose value should be returned
     * @return The value of the passed {@code key}, or {@code null} if the {@code key} is not set
     */
    @SuppressWarnings({"unchecked", "KotlinOperator"})
    @Nullable
    public <T> T get(CaptureRequest.@NonNull Key<T> key) {
        return (T) mKeyVal.get(key);
    }

    /**
     * Simple builder class to build a {@link SessionParametersCompat} object.
     */
    public static final class Builder {
        @NonNull
        private final HashMap<CaptureRequest.Key<?>, Object> mKeyVal = new HashMap<>();

        public Builder() {
        }

        /**
         * Set a capture request field to a value. Updates the value if the key was already
         * added before.
         *
         * @param key the {@link CaptureRequest.Key} set.
         * @param val the value to associate with {@code key}
         * @return the current builder
         */
        @SuppressWarnings("KotlinOperator")
        @NonNull
        public <T> Builder set(CaptureRequest.@NonNull Key<T> key, @NonNull T val) {
            mKeyVal.put(key, val);
            return this;
        }

        /**
         * Builds the {@link SessionParametersCompat} object with the values set by {@link #set}
         *
         * @return a new {@link SessionParametersCompat} object.
         */
        @NonNull
        public SessionParametersCompat build() {
            return new SessionParametersCompat(Map.copyOf(mKeyVal));
        }

    }
}
