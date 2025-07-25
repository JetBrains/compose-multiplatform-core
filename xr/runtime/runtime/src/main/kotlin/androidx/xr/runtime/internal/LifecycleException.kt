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

package androidx.xr.runtime.internal

import androidx.annotation.RestrictTo

/** Custom class for exceptions that may be thrown by a [LifecycleManager]. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
open public class LifecycleException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Required permissions have not yet been granted to the application. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class PermissionNotGrantedException(
    public val permissions: List<String> = listOf(),
    cause: Throwable? = null,
) : LifecycleException("Required permission(s) are not granted: $permissions", cause)

/** A [Feature] attempting to be enabled is not supported by the current runtime. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ConfigurationNotSupportedException() :
    LifecycleException("Failed to configure session, requested configuration is not supported.")
