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

package androidx.xr.scenecore.impl.impress;

/** Helper class that provides JNI hooks for testing the Impress API bindings. */
public final class ImpressApiTestHelper {

    public static native void nativeResetTestState();

    // Hooks for loadGltfAsset.
    public static native void nativeSetExpectedLoadGltfPath(String path);

    public static native void nativeSetLoadGltfAssetSuccess(long token);

    public static native void nativeSetLoadGltfAssetFailure(String message);

    private ImpressApiTestHelper() {}
}
