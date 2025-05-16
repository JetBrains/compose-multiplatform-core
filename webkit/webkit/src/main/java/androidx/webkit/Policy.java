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

import org.chromium.support_lib_boundary.WebViewBuilderBoundaryInterface;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Policies can be used to scope WebView behaviors to particular site patterns.
 *
 * <p>Apply a policy via {@link WebViewBuilder#setPolicy}
 */
@WebViewBuilder.Experimental
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class Policy {
    private final List<ConfigRunnable> mBehaviors;

    private Policy(List<ConfigRunnable> behaviors) {
        mBehaviors = behaviors;
    }

    void configure(WebViewBuilderBoundaryInterface.Config config) throws WebViewBuilderException {
        for (ConfigRunnable behavior : mBehaviors) {
            behavior.configure(config);
        }
    }

    /**
     * Policy builder. Attach the Policy produced to a WebView via {@link WebViewBuilder#setPolicy}.
     */
    public static final class Builder {
        final List<ConfigRunnable> mBehaviors = new ArrayList<>();

        /**
         * Constructs a new policy that can be attached via {@link
         * WebViewBuilder#setPolicy(Policy)}.
         *
         * @see WebViewBuilder
         */
        public @NonNull Policy build() {
            return new Policy(mBehaviors);
        }
    }

    private interface ConfigRunnable {
        void configure(WebViewBuilderBoundaryInterface.Config config)
                throws WebViewBuilderException;
    }
}
