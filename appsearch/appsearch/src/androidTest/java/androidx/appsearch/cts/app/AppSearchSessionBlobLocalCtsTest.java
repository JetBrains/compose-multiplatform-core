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

package androidx.appsearch.cts.app;

import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.localstorage.LocalStorage;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;

public class AppSearchSessionBlobLocalCtsTest extends AppSearchSessionBlobCtsTestBase {

    @Override
    protected ListenableFuture<AppSearchSession> createSearchSessionAsync(@NonNull String dbName) {
        LocalStorage.SearchContext searchContext =
                new LocalStorage.SearchContext.Builder(
                        ApplicationProvider.getApplicationContext(), dbName).build();
        return LocalStorage.createSearchSessionAsync(searchContext);
    }
}
