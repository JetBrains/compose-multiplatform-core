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
package androidx.appsearch.builtintypes;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.RequiresPermission;
import androidx.appsearch.app.ExperimentalAppSearchApi;
import androidx.appsearch.app.SearchSpec;
import androidx.core.util.Preconditions;

import org.jspecify.annotations.NonNull;

/**
 * Methods to add filters to {@link SearchSpec.Builder}s to search over on-device corpora for
 * builtin types {@link Person} and {@link MobileApplication}.
 */
@ExperimentalAppSearchApi
public class BuiltInCorpusFilters {
    /**
     * Adds a filter for the {@link MobileApplication} corpus to the given {@link
     * SearchSpec.Builder}.
     *
     * <p>The MobileApplication corpus is a corpus of all apps on the device. Each document contains
     * information about the app such as label, package id, and icon uri. This corpus is useful for
     * displaying a list of apps available on device, such as from a search app.
     *
     * <p>The corpus is set up such that if an app has visibility to a package via {@link
     * PackageManager#canPackageQuery}, the app can retrieve the document corresponding to the
     * package. If an app has the QUERY_ALL_PACKAGES permission, they can also retrieve all
     * MobileApplication documents. Because of this visibility setup, each MobileApplication
     * document has its own schema. The namespace is provided here as all MobileApplication
     * documents share a common namespace.
     *
     * <p>The "android" package is the package that indexes the MobileApplication corpus.
     *
     * @param builder The {@link SearchSpec.Builder} to add the filter to.
     * @return The modified {@link SearchSpec.Builder}.
     */
    @RequiresPermission(value = Manifest.permission.QUERY_ALL_PACKAGES, conditional = true)
    public static SearchSpec.@NonNull Builder searchMobileApplicationCorpus(
            SearchSpec.@NonNull Builder builder) {
        return Preconditions.checkNotNull(builder)
                .addFilterPackageNames("android")
                .addFilterNamespaces("apps");
    }

    /**
     * Adds a filter for the {@link Person} corpus to the given {@link SearchSpec.Builder}.
     *
     * <p>The Person corpus is a corpus of all the contacts available on the device. Each document
     * contains information about the contact such as given name, family name, notes, relations, and
     * contact points. This corpus is useful for apps that need a list of contacts available on
     * device, such as a contacts app or a dialer app.
     *
     * <p>The READ_CONTACTS permission is required to query this corpus. Without this permission,
     * no documents will be returned on search. The schema type is provided here as all Person
     * documents a schema type, so they can be queried by filtering on the schema type as shown in
     * the following code. Person documents do also share a namespace as well, however to restrict
     * a search to the Person corpus, it is sufficient to filter on the schema.
     *
     * <p>The "android" package is the package that indexes the Person corpus.
     *
     * @param builder The {@link SearchSpec.Builder} to add the filter to.
     * @return The modified {@link SearchSpec.Builder}.
     */
    @RequiresPermission(value = Manifest.permission.READ_CONTACTS)
    public static SearchSpec.@NonNull Builder searchPersonCorpus(
            SearchSpec.@NonNull Builder builder) {
        return Preconditions.checkNotNull(builder)
                .addFilterPackageNames("android")
                .addFilterSchemas("builtin:Person");
    }

    /** Private constructor for util. */
    private BuiltInCorpusFilters() {}
}
