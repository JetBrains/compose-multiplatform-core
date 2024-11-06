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
// @exportToFramework:skipFile()
package androidx.appsearch.app.aidl;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchBlobHandle;
import androidx.appsearch.app.ExperimentalAppSearchApi;
import androidx.core.util.Preconditions;

/**
 * A dummy version of AppSearchBatchResultGeneralKeyParcel in jetpack.
 * @param <KeyType> The type of keys in the batch result, such as {@link AppSearchBlobHandle}.
 * @param <ValueType> The type of values in the batch result, such as {@link ParcelFileDescriptor}
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@ExperimentalAppSearchApi
public final class AppSearchBatchResultGeneralKeyParcel<KeyType, ValueType> {
    private final AppSearchBatchResult<KeyType, ValueType> mResult;

    private AppSearchBatchResultGeneralKeyParcel(
            @NonNull AppSearchBatchResult<KeyType, ValueType> result) {
        mResult = Preconditions.checkNotNull(result);
    }

    /**
     * Creates an instance of {@link AppSearchBatchResultGeneralKeyParcel} with key type
     * {@link AppSearchBlobHandle} and value type {@link ParcelFileDescriptor}.
     */
    @NonNull
    public static AppSearchBatchResultGeneralKeyParcel<AppSearchBlobHandle, ParcelFileDescriptor>
            fromBlobHandleToPfd(
            @NonNull AppSearchBatchResult<AppSearchBlobHandle, ParcelFileDescriptor> result) {
        return new AppSearchBatchResultGeneralKeyParcel<>(result);
    }

    /**
     * Creates an instance of {@link AppSearchBatchResultGeneralKeyParcel} with key type
     * {@link AppSearchBlobHandle} and value type {@link Void}.
     */
    @NonNull
    public static AppSearchBatchResultGeneralKeyParcel<AppSearchBlobHandle, Void>
            fromBlobHandleToVoid(
            @NonNull AppSearchBatchResult<AppSearchBlobHandle, Void> result) {
        return new AppSearchBatchResultGeneralKeyParcel<>(result);
    }

    /** Returns the wrapped batch result.  */
    @NonNull
    public AppSearchBatchResult<KeyType, ValueType> getResult() {
        return mResult;
    }
}
