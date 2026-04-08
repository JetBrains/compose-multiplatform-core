/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.xr.runtime.openxr

import android.content.Context
import androidx.annotation.RestrictTo

// TODO(b/461561664): Make this class internal and have arcore access it through a generic
// NativeData API in runtime.
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object OpenXrInstanceManager {

    private const val LIBRARY_NAME: String = "androidx.xr.runtime.openxr"

    internal val nativePointer: Long by lazy {
        // Attempt to load the test library instead if it was added based on the Gradle AndroidTest
        // variant. Else this is a non-test environment.
        try {
            System.loadLibrary("${LIBRARY_NAME}.test")
        } catch (e: UnsatisfiedLinkError) {
            System.loadLibrary(LIBRARY_NAME)
        }
        nativeCreateOpenXrInstanceManager()
    }

    private var instanceProcAddr: Long? = null
    private var instanceHandle: Long? = null

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public fun getXrInstanceProcAddr(): Long {
        instanceProcAddr?.let {
            return it
        }

        val procAddr = nativeGetGetInstanceProcAddr(nativePointer)
        instanceProcAddr = procAddr
        return procAddr
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public fun getXrInstanceHandle(context: Context): Long {
        instanceHandle?.let {
            return it
        }

        val handle = nativeGetOpenXrInstanceHandle(context, nativePointer)
        instanceHandle = handle
        return handle
    }

    private external fun nativeCreateOpenXrInstanceManager(): Long

    private external fun nativeGetOpenXrInstanceHandle(context: Context, nativePointer: Long): Long

    private external fun nativeGetGetInstanceProcAddr(nativePointer: Long): Long
}
