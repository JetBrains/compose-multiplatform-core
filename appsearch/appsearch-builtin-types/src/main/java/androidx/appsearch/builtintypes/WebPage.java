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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appsearch.annotation.Document;
import androidx.core.util.Preconditions;

import java.util.List;

/**
 * AppSearch document representing a {@link WebPage} entity.
 *
 * <p>See <a href="https://schema.org/WebPage">https://schema.org/WebPage</a> for more context.
 */
@Document(name = WebPage.SCHEMA_NAME)
public final class WebPage extends Thing {

    // DO NOT CHANGE since it will alter schema definition
    public static final String SCHEMA_NAME = "builtin:WebPage";

    @Nullable
    @Document.DocumentProperty
    private final ImageObject mFavicon;

    WebPage(@NonNull String namespace, @NonNull String id, int documentScore,
            long creationTimestampMillis, long documentTtlMillis, @Nullable String name,
            @Nullable List<String> alternateNames,
            @Nullable String description,
            @Nullable String image, @Nullable String url,
            @Nullable List<PotentialAction> potentialActions, @Nullable ImageObject favicon) {
        super(namespace, id, documentScore, creationTimestampMillis, documentTtlMillis, name,
                alternateNames, description, image, url, potentialActions);
        mFavicon = favicon;
    }

    /**
     * Returns a favicon that represents the web page.
     */
    @Nullable
    public ImageObject getFavicon() {
        return mFavicon;
    }

    /** Builder for {@link WebPage}. */
    @Document.BuilderProducer
    public static final class Builder extends BuilderImpl<Builder> {

        /** Constructs {@link WebPage.Builder} with given {@code namespace} and {@code id} */
        public Builder(@NonNull String namespace, @NonNull String id) {
            super(namespace, id);
        }

        /** Constructs {@link WebPage.Builder} from existing values in given {@link WebPage}. */
        public Builder(@NonNull WebPage webpage) {
            super(webpage);
        }
    }

    @SuppressWarnings("unchecked")
    static class BuilderImpl<Self extends BuilderImpl<Self>> extends Thing.BuilderImpl<Self> {

        private ImageObject mFavicon;

        BuilderImpl(@NonNull String namespace, @NonNull String id) {
            super(namespace, id);
        }

        BuilderImpl(@NonNull WebPage webPage) {
            super(new Thing.Builder(Preconditions.checkNotNull(webPage)).build());
            mFavicon = webPage.getFavicon();
        }

        /**
         * Returns a favicon that represents the web page.
         */
        @NonNull
        public Self setFavicon(@Nullable ImageObject favicon) {
            mFavicon = favicon;
            return (Self) this;
        }

        /** Builds the {@link WebPage}. */
        @NonNull
        @Override
        public WebPage build() {
            return new WebPage(
                    mNamespace,
                    mId,
                    mDocumentScore,
                    mCreationTimestampMillis,
                    mDocumentTtlMillis,
                    mName,
                    mAlternateNames,
                    mDescription,
                    mImage,
                    mUrl,
                    mPotentialActions,
                    mFavicon);
        }
    }
}
