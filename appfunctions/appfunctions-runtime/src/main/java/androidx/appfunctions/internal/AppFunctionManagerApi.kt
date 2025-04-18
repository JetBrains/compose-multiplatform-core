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

package androidx.appfunctions.internal

import androidx.appfunctions.AppFunctionManagerCompat
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse

/** Provides the backend to the [android.app.appfunctions.AppFunctionManager] API. */
internal interface AppFunctionManagerApi {
    /**
     * Execute the app function.
     *
     * @param request the app function details and the arguments.
     * @return the result of the attempt to execute the function.
     */
    suspend fun executeAppFunction(
        request: ExecuteAppFunctionRequest,
    ): ExecuteAppFunctionResponse

    /**
     * Checks if [functionId] in [packageName] is enabled.
     *
     * @param packageName The package name of the owner of [functionId].
     * @param functionId The identifier of the app function.
     * @throws IllegalArgumentException If the [functionId] is not available under [packageName].
     */
    suspend fun isAppFunctionEnabled(packageName: String, functionId: String): Boolean

    /**
     * Sets [newEnabledState] to an app function [functionId] owned by the calling package.
     *
     * @param functionId The identifier of the app function.
     * @param newEnabledState The new state of the app function.
     * @throws IllegalArgumentException If the [functionId] is not available.
     */
    suspend fun setAppFunctionEnabled(
        functionId: String,
        @AppFunctionManagerCompat.EnabledState newEnabledState: Int,
    )
}
