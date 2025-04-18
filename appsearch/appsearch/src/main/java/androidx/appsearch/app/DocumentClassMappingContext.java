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
package androidx.appsearch.app;

import androidx.annotation.RestrictTo;
import androidx.appsearch.annotation.Document;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A context object that holds mapping information for document classes and their parent types.
 *
 * <p>This class encapsulates the {@code documentClassMap} and {@code parentTypeMap} used during
 * the deserialization of {@link GenericDocument} instances into specific document classes.
 *
 * @see GenericDocument#toDocumentClass(Class, DocumentClassMappingContext)
 */
public class DocumentClassMappingContext {
    /** An empty {@link DocumentClassMappingContext} instance. */
    @ExperimentalAppSearchApi
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final DocumentClassMappingContext EMPTY =
            new DocumentClassMappingContext(/* documentClassMap= */null, /* parentTypeMap= */null);

    private final @NonNull Map<String, List<String>> mDocumentClassMap;
    private final @NonNull Map<String, List<String>> mParentTypeMap;

    /**
     * Constructs a new {@link DocumentClassMappingContext}.
     *
     * @param documentClassMap A map from AppSearch's type name specified by {@link Document#name()}
     *                         to the list of the fully qualified names of the corresponding
     *                         document classes. In most cases, passing the value returned by
     *                         {@link AppSearchDocumentClassMap#getGlobalMap()} will be sufficient.
     * @param parentTypeMap    A map from AppSearch's type name specified by {@link Document#name()}
     *                         to the list of its parent type names. In most cases, passing the
     *                         value returned by {@link SearchResult#getParentTypeMap()} will be
     *                         sufficient.
     */
    @ExperimentalAppSearchApi
    public DocumentClassMappingContext(
            @Nullable Map<String, List<String>> documentClassMap,
            @Nullable Map<String, List<String>> parentTypeMap) {
        mDocumentClassMap = documentClassMap != null ? Collections.unmodifiableMap(documentClassMap)
                : Collections.emptyMap();
        mParentTypeMap = parentTypeMap != null ? Collections.unmodifiableMap(parentTypeMap)
                : Collections.emptyMap();
    }

    /**
     * Returns the document class map.
     */
    @ExperimentalAppSearchApi
    public @NonNull Map<String, List<String>> getDocumentClassMap() {
        return mDocumentClassMap;
    }

    /**
     * Returns the parent type map.
     */
    @ExperimentalAppSearchApi
    public @NonNull Map<String, List<String>> getParentTypeMap() {
        return mParentTypeMap;
    }
}
