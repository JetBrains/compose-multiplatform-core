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

package androidx.webkit;

import android.os.CancellationSignal;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ServiceWorkerController;
import android.webkit.WebStorage;

import androidx.annotation.AnyThread;
import androidx.annotation.RequiresFeature;
import androidx.annotation.RequiresOptIn;
import androidx.annotation.UiThread;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A Profile represents one browsing session for WebView.
 * <p> You can have multiple profiles and each profile holds its own set of data. The creation
 * and deletion of the Profile is being managed by {@link ProfileStore}.
 */
public interface Profile {

    /**
     * Represents the name of the default profile which can't be deleted.
     */
    String DEFAULT_PROFILE_NAME = "Default";

    /**
     * @return the name of this Profile which was used to create the Profile from
     * ProfileStore create methods.
     */
    @AnyThread
    @RequiresFeature(name = WebViewFeature.MULTI_PROFILE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @NonNull
    String getName();

    /**
     * Returns the profile's cookie manager.
     * <p>
     * Can be called from any thread.
     *
     * @throws IllegalStateException if the profile has been deleted by
     *                               {@link ProfileStore#deleteProfile(String)}}.
     */
    @AnyThread
    @RequiresFeature(name = WebViewFeature.MULTI_PROFILE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @NonNull
    CookieManager getCookieManager();

    /**
     * Returns the profile's web storage.
     * <p>
     * Can be called from any thread.
     *
     * @throws IllegalStateException if the profile has been deleted by
     *                               {@link ProfileStore#deleteProfile(String)}}.
     */
    @AnyThread
    @RequiresFeature(name = WebViewFeature.MULTI_PROFILE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @NonNull
    WebStorage getWebStorage();

    /**
     * Returns the geolocation permissions of the profile.
     * <p>
     * Can be called from any thread.
     *
     * @throws IllegalStateException if the profile has been deleted by
     *                               {@link ProfileStore#deleteProfile(String)}}.
     */
    @AnyThread
    @RequiresFeature(name = WebViewFeature.MULTI_PROFILE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @NonNull
    GeolocationPermissions getGeolocationPermissions();

    /**
     * Returns the service worker controller of the profile.
     * <p>
     * Can be called from any thread.
     *
     * @throws IllegalStateException if the profile has been deleted by
     *                               {@link ProfileStore#deleteProfile(String)}}.
     */
    @AnyThread
    @RequiresFeature(name = WebViewFeature.MULTI_PROFILE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @NonNull
    ServiceWorkerController getServiceWorkerController();

    /**
     * Denotes that the UrlPrefetch API surface is experimental.
     * It may change without warning.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
    @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
    @interface ExperimentalUrlPrefetch {
    }

    /**
     * Starts a URL prefetch request. Must be called from the UI thread.
     * <p>
     * All WebViews associated with this Profile will use a URL request
     * matching algorithm during execution of all variants of
     * {@link android.webkit.WebView#loadUrl(String)} for determining if there
     * was already a prefetch request executed for the provided URL. This
     * includes prefetches that are "in progress". If a prefetch is matched,
     * WebView will leverage that for handling the URL, otherwise the URL
     * will be handled normally (i.e. through a network request).
     * <p>
     * Applications will still be responsible for calling
     * {@link android.webkit.WebView#loadUrl(String)} to display web contents
     * in a WebView.
     * <p>
     * NOTE: Additional headers passed to
     * {@link android.webkit.WebView#loadUrl(String, Map)} are not considered
     * in the matching algorithm for determining whether or not to serve a
     * prefetched response to a navigation.
     * <p>
     * For max latency saving benefits, it is recommended to call this method
     * as early as possible (i.e. before any WebView associated with this
     * profile is created).
     * <p>
     * Only supports HTTPS scheme.
     *
     * @param url                the url associated with the prefetch request.
     * @param cancellationSignal will make the best effort to cancel an
     *                           in-flight prefetch request, However cancellation is not
     *                           guaranteed.
     * @param callbackExecutor   the executor to resolve the callback with.
     * @param operationCallback  callbacks for reporting result back to application.
     * @throws IllegalArgumentException if the url or callback is null.
     */
    @RequiresFeature(name = WebViewFeature.PROFILE_URL_PREFETCH,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @UiThread
    @ExperimentalUrlPrefetch
    void prefetchUrlAsync(@NonNull String url,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull Executor callbackExecutor,
            @NonNull OutcomeReceiverCompat<Void, PrefetchException> operationCallback);

    /**
     * Starts a URL prefetch request. Must be called from the UI thread.
     * <p>
     * All WebViews associated with this Profile will use a URL request
     * matching algorithm during execution of all variants of
     * {@link android.webkit.WebView#loadUrl(String)} for determining if there
     * was already a prefetch request executed for the provided URL. This
     * includes prefetches that are "in progress". If a prefetch is matched,
     * WebView will leverage that for handling the URL, otherwise the URL
     * will be handled normally (i.e. through a network request).
     * <p>
     * Applications will still be responsible for calling
     * {@link android.webkit.WebView#loadUrl(String)} to display web contents
     * in a WebView.
     * <p>
     * NOTE: Additional headers passed to
     * {@link android.webkit.WebView#loadUrl(String, Map)} are not considered
     * in the matching algorithm for determining whether or not to serve a
     * prefetched response to a navigation.
     * <p>
     * For max latency saving benefits, it is recommended to call this method
     * as early as possible (i.e. before any WebView associated with this
     * profile is created).
     * <p>
     * Only supports HTTPS scheme.
     *
     * @param url                          the url associated with the prefetch request.
     * @param cancellationSignal           will make the best effort to cancel an
     *                                     in-flight prefetch request, However cancellation is not
     *                                     guaranteed.
     * @param callbackExecutor             the executor to resolve the callback with.
     * @param speculativeLoadingParameters parameters to customize the prefetch request.
     * @param operationCallback            callbacks for reporting result back to application.
     * @throws IllegalArgumentException if the url or callback is null.
     */
    @RequiresFeature(name = WebViewFeature.PROFILE_URL_PREFETCH,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @UiThread
    @ExperimentalUrlPrefetch
    void prefetchUrlAsync(@NonNull String url,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull Executor callbackExecutor,
            @NonNull SpeculativeLoadingParameters speculativeLoadingParameters,
            @NonNull OutcomeReceiverCompat<Void, PrefetchException> operationCallback);

    /**
     * Removes a cached prefetch response for the provided url
     * if it exists, otherwise does nothing.
     * <p>
     * Calling this does not guarantee that the prefetched response will
     * not be served to a WebView before it is cleared.
     * <p>
     *
     * @param url               the url associated with the prefetch request. Should be
     *                          an exact match with the URL passed to {@link #prefetchUrlAsync}.
     * @param callbackExecutor  the executor to resolve the callback with.
     * @param operationCallback runs when the clear operation is complete Or and error occurred
     *                          during it.
     * @throws IllegalArgumentException if the url or callback is null.
     */
    @RequiresFeature(name = WebViewFeature.PROFILE_URL_PREFETCH,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @UiThread
    @ExperimentalUrlPrefetch
    void clearPrefetchAsync(@NonNull String url,
            @NonNull Executor callbackExecutor,
            @NonNull OutcomeReceiverCompat<Void, PrefetchException> operationCallback);

    /**
     * Sets the {@link SpeculativeLoadingConfig} for the current profile session.
     * These configurations will be applied to any Prefetch requests made after they are set;
     * they will not be applied to in-flight requests.
     * <p>
     * These configurations will be applied to any prefetch requests initiated by
     * a prerender request. This applies specifically to WebViews that are
     * associated with this Profile.
     * <p>
     * @param speculativeLoadingConfig the config to set for this profile session.
     */
    @RequiresFeature(name = WebViewFeature.SPECULATIVE_LOADING_CONFIG,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    @UiThread
    @ExperimentalUrlPrefetch
    void setSpeculativeLoadingConfig(@NonNull SpeculativeLoadingConfig
            speculativeLoadingConfig);

}
