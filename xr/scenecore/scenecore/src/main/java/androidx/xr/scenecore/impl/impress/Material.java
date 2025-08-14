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
import androidx.xr.runtime.internal.MaterialResource;

import org.jspecify.annotations.NonNull;

/** Interface defining the common functionality of all materials. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public abstract class Material extends BindingsResource implements MaterialResource {
    private final String TAG = getClass().getSimpleName();
    @SuppressWarnings("UnusedVariable")
    private final ImpressApi impressApi;

    protected Material(@NonNull ImpressApi impressApi, long nativeMaterial) {
        super(impressApi.getBindingsResourceManager(), nativeMaterial);
        this.impressApi = impressApi;
    }

    @Override
    protected void releaseBindingsResource(long nativeHandle) {
        // TODO(b/433934447): Call into the JNI to release the native bindings resource.
        Log.d(TAG, "Material is getting destroyed manually");
    }
}
