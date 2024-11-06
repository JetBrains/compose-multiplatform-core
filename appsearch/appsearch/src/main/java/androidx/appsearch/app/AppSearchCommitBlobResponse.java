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
package androidx.appsearch.app;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appsearch.app.aidl.AppSearchBatchResultGeneralKeyParcel;
import androidx.appsearch.flags.FlaggedApi;
import androidx.appsearch.flags.Flags;
import androidx.appsearch.safeparcel.AbstractSafeParcelable;
import androidx.appsearch.safeparcel.SafeParcelable;
import androidx.appsearch.safeparcel.stub.StubCreators.CommitBlobResponseCreator;
import androidx.core.util.Preconditions;

/**
 * The response to provide batch operation results of
 * {@link AppSearchSession#commitBlobAsync}.
 *
 * <p> This class is used to retrieve the result of a batch commit operation on a collection of
 * blob handles.
 */
@FlaggedApi(Flags.FLAG_ENABLE_BLOB_STORE)
@SuppressWarnings("HiddenSuperclass")
@SafeParcelable.Class(creator = "CommitBlobResponseCreator")
@ExperimentalAppSearchApi
public final class AppSearchCommitBlobResponse extends AbstractSafeParcelable {

    @NonNull
    public static final Parcelable.Creator<AppSearchCommitBlobResponse> CREATOR =
            new CommitBlobResponseCreator();

    @Field(id = 1, getter = "getResponseParcel")
    private final AppSearchBatchResultGeneralKeyParcel<AppSearchBlobHandle, Void> mResultParcel;

    /**
     * Creates a {@link AppSearchCommitBlobResponse} with given {@link AppSearchBatchResult}.
     */
    public AppSearchCommitBlobResponse(
            @NonNull AppSearchBatchResult<AppSearchBlobHandle, Void> result) {
        this(AppSearchBatchResultGeneralKeyParcel.fromBlobHandleToVoid(result));
    }

    @Constructor
    AppSearchCommitBlobResponse(
            @Param(id = 1) @NonNull AppSearchBatchResultGeneralKeyParcel<AppSearchBlobHandle, Void>
                    resultParcel) {
        mResultParcel = Preconditions.checkNotNull(resultParcel);
    }

    /**
     * Returns the {@link AppSearchBatchResult} object containing the results of the
     * commit operation for each {@link AppSearchBlobHandle}.
     *
     * @return A {@link AppSearchBatchResult} maps {@link AppSearchBlobHandle}s which is a unique
     * identifier for a specific blob being committed to the outcome of that commit. If the
     * operation was successful, the result for that handle is {@code null}; if there was an error,
     * the result contains an {@link AppSearchResult} with details of the failure.
     */
    @NonNull
    public AppSearchBatchResult<AppSearchBlobHandle, Void> getResult() {
        return mResultParcel.getResult();
    }

    /**
     * Retrieves the underlying parcel representation of the batch result.
     * @exportToFramework:hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public AppSearchBatchResultGeneralKeyParcel<AppSearchBlobHandle, Void> getResponseParcel() {
        return mResultParcel;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CommitBlobResponseCreator.writeToParcel(this, dest, flags);
    }
}
