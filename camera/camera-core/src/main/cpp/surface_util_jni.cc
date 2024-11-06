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

#include <android/native_window_jni.h>

#include <cassert>

extern "C" {

/**
 * Returns an int array of length 3 that the format, width and height values stored at position
 * 0, 1, 2 correspondingly.
 */
JNIEXPORT jintArray JNICALL
Java_androidx_camera_core_impl_utils_SurfaceUtil_nativeGetSurfaceInfo(JNIEnv *env, jclass clazz,
                                                                      jobject jsurface) {
    // Retrieves surface info via native mothods
    ANativeWindow *nativeWindow = ANativeWindow_fromSurface(env, jsurface);
    assert(nativeWindow != nullptr);
    int32_t format = ANativeWindow_getFormat(nativeWindow);
    int32_t width = ANativeWindow_getWidth(nativeWindow);
    int32_t height = ANativeWindow_getHeight(nativeWindow);
    ANativeWindow_release(nativeWindow);

    jintArray resultArray = env->NewIntArray(3);
    jint surfaceInfo[3] = {format, width, height};
    env->SetIntArrayRegion(resultArray, 0, 3, surfaceInfo);

    return resultArray;
}
}