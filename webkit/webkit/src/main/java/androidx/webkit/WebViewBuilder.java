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

import android.content.Context;
import android.webkit.WebView;

import androidx.annotation.RequiresFeature;
import androidx.annotation.RequiresOptIn;
import androidx.annotation.RestrictTo;
import androidx.annotation.UiThread;
import androidx.webkit.internal.ApiFeature;
import androidx.webkit.internal.WebViewFeatureInternal;
import androidx.webkit.internal.WebViewGlueCommunicator;

import org.chromium.support_lib_boundary.WebViewBuilderBoundaryInterface;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The WebViewBuilder can be used in place of {@link android.webkit.WebView}'s constructor.
 *
 * <p>This API allows you to declare how the WebView will be used via APIs like {@link
 * androidx.webkit.Policy.Builder}.
 *
 * <p>WebView instances constructed by this builder can be used as direct drop-in replacements for
 * WebView's created by the class constructor with no additional code changes.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class WebViewBuilder {
    private @Nullable Policy mPolicy;

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
    @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
    public @interface Experimental {}

    @Nullable WebViewBuilderBoundaryInterface mBuilderStateBoundary;

    /**
     * This builder is able to construct WebView with some initial configuration before it will be
     * used. It is able to apply policies via {@link #setPolicy(Policy)} to apply controls over how
     * WebView may be used.
     */
    public WebViewBuilder() {}

    /**
     * Set a WebView policy to introduce restrictions over what WebViews built are capable of doing.
     *
     * @param policy The policy that will apply to all WebViews built.
     */
    @Experimental
    public @NonNull WebViewBuilder setPolicy(@NonNull Policy policy) {
        mPolicy = policy;
        return this;
    }

    /**
     * Constructs a new WebView with all the properties defined.
     *
     * @param context an Activity Context to access application assets
     */
    @Experimental
    @UiThread
    @RequiresFeature(
            name = WebViewFeature.WEBVIEW_BUILDER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public @NonNull WebView build(@NonNull Context context) throws WebViewBuilderException {
        final ApiFeature.NoFramework feature = WebViewFeatureInternal.WEBVIEW_BUILDER;
        if (!feature.isSupportedByWebView()) {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }

        // The boundary interface is lazy loaded but it is built with the
        // assumption that on every call to build, we can re-use the same instance.
        // Configure and build must be called every time in case the
        // builder state changes.
        if (mBuilderStateBoundary == null) {
            mBuilderStateBoundary = WebViewGlueCommunicator.getFactory().getWebViewBuilder();
        }

        WebViewBuilderBoundaryInterface.Config config =
                new WebViewBuilderBoundaryInterface.Config();
        mPolicy.configure(config);

        return mBuilderStateBoundary.build(context, config);
    }
}
