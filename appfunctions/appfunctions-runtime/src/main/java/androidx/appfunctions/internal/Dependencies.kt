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

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appfunctions.internal.Constants.APP_FUNCTIONS_TAG

/** Provides manual dependency injection for AppFunction runtime infrastructure. */
@RequiresApi(Build.VERSION_CODES.S)
internal object Dependencies {

    /** The instance of [AggregatedAppFunctionInventory]. */
    internal val aggregatedAppFunctionInventory: AggregatedAppFunctionInventory by lazy {
        AggregatedAppFunctionInventory::class
            .java
            .findImpl(
                prefix = "$",
                suffix = "_Impl",
            )
    }

    /** The instance of [AggregatedAppFunctionInvoker]. */
    internal val aggregatedAppFunctionInvoker: AggregatedAppFunctionInvoker by lazy {
        AggregatedAppFunctionInvoker::class
            .java
            .findImpl(
                prefix = "$",
                suffix = "_Impl",
            )
    }

    internal val translatorSelector: TranslatorSelector by lazy {
        try {
            TranslatorSelector::class
                .java
                .findImpl(
                    prefix = "",
                    suffix = "Impl",
                )
        } catch (ex: Exception) {
            Log.d(APP_FUNCTIONS_TAG, "Cannot find TranslatorSelectorImpl")
            NullTranslatorSelector()
        }
    }
}
