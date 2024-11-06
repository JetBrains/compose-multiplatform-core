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

package androidx.webkit;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/**
 * Configuration object for
 * {@link WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}.
 * <p>
 * This is different from {@link ProcessGlobalConfig}. This object defines the configuration for
 * a particular call to
 * {@link WebViewCompat#startUpWebView(WebViewStartUpConfig, WebViewCompat.WebViewStartUpCallback)}.
 */
@WebViewCompat.ExperimentalAsyncStartUp
public final class WebViewStartUpConfig {
    private final Executor mExecutor;

    private WebViewStartUpConfig(@NonNull Executor executor) {
        mExecutor = executor;
    }

    public @NonNull Executor getBackgroundExecutor() {
        return mExecutor;
    }

    @WebViewCompat.ExperimentalAsyncStartUp
    public static final class Builder {
        private final Executor mExecutor;

        /**
         * Builder for {@link WebViewStartUpConfig}.
         *
         * @param executor The portions of WebView startup that can run on a background
         * thread are scheduled on this executor. Blocking tasks will be run on the executor.
         */
        public Builder(@NonNull Executor executor) {
            mExecutor = executor;
        };

        /**
         * Build and return a {@link WebViewStartUpConfig} object.
         *
         * @return immutable {@link WebViewStartUpConfig} object.
         */
        public @NonNull WebViewStartUpConfig build() {
            return new WebViewStartUpConfig(mExecutor);
        }
    }
}
