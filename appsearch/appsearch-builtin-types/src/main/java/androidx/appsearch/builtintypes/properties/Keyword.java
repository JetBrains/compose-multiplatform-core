/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appsearch.builtintypes.properties;

import static androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES;
import static androidx.core.util.Preconditions.checkNotNull;

import androidx.appsearch.annotation.Document;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Holds a value assignable to
 * <a href="http://schema.org/keywords">http://schema.org/keywords</a>.
 *
 * <p>Contains exactly one of:
 *
 * <ul>
 *     <li>Text i.e. {@link String}</li>
 * </ul>
 *
 * <p>Note: More types may be added over time.
 */
@Document
public final class Keyword {
    @Document.Namespace
    @NonNull String mNamespace = "";

    @Document.Id
    @NonNull String mId = "";

    @Document.StringProperty(indexingType = INDEXING_TYPE_PREFIXES)
    final @Nullable String mAsText;

    public Keyword(@NonNull String asText) {
        mAsText = checkNotNull(asText);
    }

    /**
     * Returns the Text i.e. {@link String} variant, if populated. Otherwise, null.
     */
    public @Nullable String asText() {
        return mAsText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Keyword keyword = (Keyword) o;
        return Objects.equals(mAsText, keyword.mAsText);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAsText);
    }
}
