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

import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;

import androidx.annotation.RequiresFeature;
import androidx.annotation.RestrictTo;
import androidx.webkit.internal.ApiFeature;
import androidx.webkit.internal.WebViewFeatureInternal;

import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility version of {@link WebResourceResponse}. This class can be used as a direct
 * replacement of the framework class, but adds new functionality.
 *
 * @see WebResourceResponse
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class WebResourceResponseCompat extends WebResourceResponse {
    private @NonNull String mSerializedCookieValues = "";

    /**
     * Constructor matching
     * {@link WebResourceResponse#WebResourceResponse(String, String, InputStream)}
     */
    public WebResourceResponseCompat(@NonNull String mimeType, @Nullable String encoding,
            @Nullable InputStream data) {
        super(mimeType, encoding, data);
    }

    /**
     * Constructor matching
     * {@link WebResourceResponse#WebResourceResponse(String, String, int, String, Map, InputStream)}
     */
    public WebResourceResponseCompat(@NonNull String mimeType, @Nullable String encoding,
            int statusCode, @NonNull String reasonPhrase,
            @Nullable Map<String, String> responseHeaders, @Nullable InputStream data) {
        super(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data);
    }

    /**
     * Set the list of {@code Set-Cookie} header values applicable to this response.
     *
     * <p>Note that these values will only be used by WebView if
     * {@link WebSettingsCompat#getIncludeCookiesOnShouldInterceptRequest(WebSettings)} is
     * {@code true}, and by service workers if
     * {@link ServiceWorkerWebSettingsCompat#getIncludeCookiesOnShouldInterceptRequest()}
     * is {@code true}. Otherwise the values will be ignored.
     *
     * <p>It is safe to use this method even if the map of response headers provided in the
     * constructor or through {@link #setResponseHeaders(Map)} already contains a
     * {@code Set-Cookie} value. In such cases, all values will be applied.
     * However, it is recommended to only use this method to supply {@code Set-Cookie} header
     * values. A {@code Set-Cookie} value in the header map will also only be used according to the
     * restrictions mentioned above.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)} returns true for
     * {@link WebViewFeature#COOKIE_INTERCEPT}.
     *
     * @param headerValues List of valid {@code Set-Cookie} header values
     */
    @RequiresFeature(name = WebViewFeature.COOKIE_INTERCEPT,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public void setCookies(@NonNull List<@NonNull String> headerValues) {
        final ApiFeature.NoFramework feature = WebViewFeatureInternal.COOKIE_INTERCEPT;
        if (!feature.isSupportedByWebView()) {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
        mSerializedCookieValues = serializeMultiCookieHeader(headerValues);
    }

    /**
     * Gets the headers for the resource response.
     *
     * <p>This method returns a new map instance, and modifications to the returned value will
     * not be visible to this {@link WebResourceResponseCompat}.
     *
     * <p>If values have been provided through {@link #setCookies(List)}, then
     * the returned map will contain an extra synthetic header key. This mapping is used by
     * WebView to deserialize the {@code Set-Cookie} values.
     *
     * @return The headers for the resource response. Will be non-null even if no headers were set.
     */
    @Override
    @NonNull
    public Map<String, String> getResponseHeaders() {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> superHeaders = super.getResponseHeaders();
        if (superHeaders != null) {
            headers.putAll(superHeaders);
        }
        if (!mSerializedCookieValues.isEmpty()) {
            headers.put(WebViewProviderFactoryBoundaryInterface.MULTI_COOKIE_HEADER_NAME,
                    mSerializedCookieValues);
        }
        return headers;
    }

    /**
     * Serializes the {@code cookieValues} list into a string that can be unpacked by WebView code.
     *
     * @param cookieValues List of values to serialize
     */
    @NonNull
    private String serializeMultiCookieHeader(@NonNull List<String> cookieValues) {
        if (cookieValues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String cookieValue : cookieValues) {
            if (!cookieValue.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(WebViewProviderFactoryBoundaryInterface.MULTI_COOKIE_VALUE_SEPARATOR);
                }
                sb.append(cookieValue.trim());
            }
        }
        return sb.toString();
    }
}
