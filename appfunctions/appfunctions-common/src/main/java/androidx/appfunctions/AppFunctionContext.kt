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

package androidx.appfunctions

import android.content.Context
import android.content.pm.SigningInfo

/** The execution context of app function. */
public interface AppFunctionContext {
    /** The Android context. */
    public val context: Context

    /**
     * Return the name of the package that invoked this AppFunction. You can use this information to
     * validate the caller.
     */
    public val callingPackageName: String

    /**
     * Return the [android.content.pm.SigningInfo] of the package that invoked this AppFunction. You
     * can use this information to validate the caller.
     */
    public val callingPackageSigningInfo: SigningInfo
}
