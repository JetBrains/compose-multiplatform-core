/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.paging.samples.java;

import androidx.paging.PagingState;
import androidx.paging.rxjava2.RxPagingSource;
import androidx.paging.samples.shared.ExampleRxBackendService;
import androidx.paging.samples.shared.SearchUserResponse;
import androidx.paging.samples.shared.User;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class PagingSourceRxSample extends RxPagingSource<Integer, User> {
    private @NonNull ExampleRxBackendService mBackend;
    private @NonNull String mQuery;

    PagingSourceRxSample(@NonNull ExampleRxBackendService backend,
            @NonNull String query) {
        mBackend = backend;
        mQuery = query;
    }

    @Override
    public @NonNull Single<LoadResult<Integer, User>> loadSingle(
            @NonNull LoadParams<Integer> params) {
        // Start refresh at page 1 if undefined.
        Integer nextPageNumber = params.getKey();
        if (nextPageNumber == null) {
            nextPageNumber = 1;
        }

        return mBackend.searchUsers(mQuery, nextPageNumber)
                .subscribeOn(Schedulers.io())
                .map(this::toLoadResult)
                .onErrorReturn(LoadResult.Error::new);
    }

    private LoadResult<Integer, User> toLoadResult(
            @NonNull SearchUserResponse response) {
        return new LoadResult.Page<>(
                response.getUsers(),
                null, // Only paging forward.
                response.getNextPageNumber(),
                LoadResult.Page.COUNT_UNDEFINED,
                LoadResult.Page.COUNT_UNDEFINED);
    }

    @Override
    public @Nullable Integer getRefreshKey(@NonNull PagingState<Integer, User> state) {
        Integer anchorPosition = state.getAnchorPosition();
        if (anchorPosition == null) {
            return null;
        }

        LoadResult.Page<Integer, User> anchorPage = state.closestPageToPosition(anchorPosition);
        if (anchorPage == null) {
            return null;
        }

        Integer prevKey = anchorPage.getPrevKey();
        if (prevKey != null) {
            return prevKey + 1;
        }

        Integer nextKey = anchorPage.getNextKey();
        if (nextKey != null) {
            return nextKey + 1;
        }

        return null;
    }
}
