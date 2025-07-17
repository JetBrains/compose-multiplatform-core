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

package androidx.webkit.internal;


import org.chromium.support_lib_boundary.WebStorageBoundaryInterface;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Executor;

public class WebStorageAdapter implements WebStorageBoundaryInterface {

    @NonNull
    final WebStorageBoundaryInterface mImpl;

    public WebStorageAdapter(@NonNull WebStorageBoundaryInterface impl) {
        mImpl = impl;
    }

    @Override
    public void deleteBrowsingData(@NonNull Executor callbackExecutor,
            @NonNull Runnable doneCallback) {
        mImpl.deleteBrowsingData(callbackExecutor, doneCallback);
    }

    @Override
    public @NonNull String deleteBrowsingDataForSite(@NonNull String domainOrUrl,
            @NonNull Executor callbackExecutor, @NonNull Runnable doneCallback) {
        return mImpl.deleteBrowsingDataForSite(domainOrUrl, callbackExecutor, doneCallback);
    }
}
