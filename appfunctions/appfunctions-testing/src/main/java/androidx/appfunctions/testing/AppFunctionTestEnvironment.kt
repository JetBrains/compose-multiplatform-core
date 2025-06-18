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

package androidx.appfunctions.testing

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionManagerCompat
import androidx.appfunctions.testing.internal.FakeAppFunctionManagerApi
import androidx.appfunctions.testing.internal.FakeAppFunctionReader

/**
 * Test environment for testing AppFunction API(s).
 *
 * Prefer using real setup if possible and only rely on this environment for unit or robolectric
 * tests to simulate cross app interactions via app functions.
 *
 * Functions defined via [androidx.appfunctions.service.AppFunction] annotation alongside test code
 * will already be registered to this environment on initialization, if `appfunctions-compiler` is
 * applied.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class AppFunctionTestEnvironment(private val context: Context) {
    // TODO: b/418017070 - Dynamic registration and changing app function enabled state API(s).
    // TODO: b/418017070 - Support function execution.

    private val appFunctionReader = FakeAppFunctionReader(context)
    private val appFunctionManagerApi = FakeAppFunctionManagerApi(context, appFunctionReader)

    /**
     * Returns an [AppFunctionManagerCompat] instance that can be used to interact with AppFunctions
     * registered in this environment.
     */
    internal fun getAppFunctionManagerCompat(): AppFunctionManagerCompat =
        AppFunctionManagerCompat(context, appFunctionReader, appFunctionManagerApi)
}
