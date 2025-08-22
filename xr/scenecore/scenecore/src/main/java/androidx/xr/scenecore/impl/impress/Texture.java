/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.xr.scenecore.impl.impress;

import android.util.Log;

import androidx.annotation.RestrictTo;
import androidx.xr.runtime.internal.TextureResource;

import org.jspecify.annotations.NonNull;

/**
 * Texture class for the native Impress texture wrapper struct which is an implementation a
 * SceneCore TextureResource.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public final class Texture extends BindingsResource implements TextureResource {
    private final String TAG = getClass().getSimpleName();

    @SuppressWarnings("UnusedVariable")
    private final ImpressApi impressApi;

    private Texture(Builder builder) {
        super(builder.impressApi.getBindingsResourceManager(), builder.nativeTexture);
        this.impressApi = builder.impressApi;
    }

    @Override
    protected void releaseBindingsResource(long nativeHandle) {
        // TODO(b/433934447): Call into the JNI to release the native bindings resource.
        Log.d(TAG, "Texture is getting destroyed manually");
    }

    /** Use Builder to construct a Texture object instance. */
    public static class Builder {
        private ImpressApi impressApi;
        private long nativeTexture = -1;

        @NonNull
        public Builder setImpressApi(@NonNull ImpressApi impressApi) {
            this.impressApi = impressApi;
            return this;
        }

        @NonNull
        public Builder setNativeTexture(long nativeTexture) {
            this.nativeTexture = nativeTexture;
            return this;
        }

        @NonNull
        public Texture build() {
            if (impressApi == null || nativeTexture == -1) {
                throw new IllegalStateException("Texture not built properly.");
            }
            return new Texture(this);
        }
    }
}
