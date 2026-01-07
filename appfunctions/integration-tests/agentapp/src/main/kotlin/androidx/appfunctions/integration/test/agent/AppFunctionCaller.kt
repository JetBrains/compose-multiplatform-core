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

package androidx.appfunctions.integration.test.agent

import android.content.Context
import androidx.appfunctions.AppFunctionManagerCompat
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import kotlinx.coroutines.flow.Flow

class AppFunctionCaller(val context: Context) {

    val appFunctionManagerCompat = checkNotNull(AppFunctionManagerCompat.getInstance(context))

    suspend fun executeAppFunction(request: ExecuteAppFunctionRequest): ExecuteAppFunctionResponse =
        appFunctionManagerCompat.executeAppFunction(request)

    suspend fun observeAppFunctions(
        searchSpec: AppFunctionSearchSpec
    ): Flow<List<AppFunctionPackageMetadata>> =
        appFunctionManagerCompat.observeAppFunctions(searchSpec)
}
