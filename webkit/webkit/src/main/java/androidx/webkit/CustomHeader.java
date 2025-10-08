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

package androidx.webkit;


import androidx.annotation.RestrictTo;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Representation of a custom header mapping.
 *
 * @see Profile#addCustomHeader(CustomHeader)
 * @see Profile#getCustomHeaders()
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CustomHeader {
    private final @NonNull String mName;
    private final @NonNull String mValue;
    private final @NonNull Set<String> mRules;

    /**
     * Create a new custom header for the given name and value, to be added to requests that
     * match the given origin originRules
     *
     * @param name        A
     *                    <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.2">valid HTTP header name string</a>
     * @param value       A
     *                    <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.2">valid HTTP value name string</a>
     * @param originRules a set of origin originRules following the same format as
     *                    {@link WebViewCompat#addWebMessageListener}
     */
    public CustomHeader(@NonNull String name, @NonNull String value,
            @NonNull Set<String> originRules) {
        mName = name;
        mValue = value;
        mRules = originRules;
    }

    /** Header name */
    public @NonNull String getName() {
        return mName;
    }

    /** Header value */
    public @NonNull String getValue() {
        return mValue;
    }

    /** Origin patterns where this header is being sent. */
    public @NonNull Set<String> getRules() {
        return mRules;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mValue, mRules);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof CustomHeader)) {
            return false;
        }
        CustomHeader other = (CustomHeader) obj;
        return Objects.equals(this.mName, other.mName) && Objects.equals(this.mValue, other.mValue)
                && Objects.equals(this.mRules, other.mRules);
    }
}
