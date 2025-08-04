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

package androidx.xr.runtime.internal

import androidx.annotation.RestrictTo

/**
 * Defines the contract for a platform-agnostic runtime that manages the scene graph and spatial
 * logic backend.
 *
 * This interface is responsible for the logical structure of the XR experience, managing the
 * hierarchy of entities, their transformations, and their behaviors. It provides the core
 * functionalities for spatial computing, such as world tracking, plane detection, and anchoring
 * objects to the physical environment. It also handles user interaction components and spatial
 * audio.
 *
 * This API is intended for internal use only and is not a public API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface SceneRuntime {
    /** Disposes of the resources used by this runtime */
    public fun dispose()
}
